package com.lhk.freemoa.txlab;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * [4단계] 트랜잭션 실험실. 실제 Oracle에 쓰고 지우면서 동작을 눈으로 확인한다.
 *
 * 실행 조건: 테스트 실행 설정에도 환경변수 DB_PASSWORD가 있어야 한다. (없으면 건너뜀)
 *   IntelliJ: 이 클래스 우클릭 > Modify Run Configuration > Environment variables
 *
 * 실험 데이터는 TB_CRAWL_RUN에 STATUS='FAIL', ERROR_MSG='TX_LAB:...' 로 넣고, 매 테스트 전후로 지운다.
 * (STATUS를 RUNNING으로 넣으면 동시 실행 방지 인덱스에 걸리기 때문에 FAIL 사용)
 *
 * 주의: 이 테스트 클래스 자체에는 @Transactional을 붙이지 않았다.
 * 붙이면 테스트 메서드 전체가 하나의 트랜잭션이 되고 끝나면 자동 롤백돼서, 실험하려는 동작이 가려진다.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class TransactionLabTest {

    private static final String MARKER_PREFIX = "TX_LAB:";

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    Outer outer;
    @Autowired
    SelfCaller selfCaller;
    @Autowired
    CheckedThrower checkedThrower;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM TB_CRAWL_RUN WHERE ERROR_MSG LIKE ?", MARKER_PREFIX + "%");
    }

    private int count(String marker) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM TB_CRAWL_RUN WHERE ERROR_MSG = ?", Integer.class, MARKER_PREFIX + marker);
        return n == null ? 0 : n;
    }

    @Nested
    @DisplayName("1. 전파 속성 (Propagation)")
    class PropagationTest {

        @Test
        @DisplayName("REQUIRED: 안쪽이 바깥 트랜잭션에 합류 -> 바깥이 롤백되면 안쪽 INSERT도 사라진다")
        void required() {
            assertThatThrownBy(() -> outer.callRequiredThenFail("required"))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(count("required")).isZero();
        }

        @Test
        @DisplayName("REQUIRES_NEW: 안쪽은 별도 트랜잭션으로 먼저 커밋 -> 바깥이 롤백돼도 남는다 (수집 이력이 이 방식)")
        void requiresNew() {
            assertThatThrownBy(() -> outer.callRequiresNewThenFail("requires_new"))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(count("requires_new")).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("2. 프록시 함정 (self-invocation)")
    class SelfInvocationTest {

        @Test
        @DisplayName("밖에서 프록시를 통해 호출하면 트랜잭션이 적용된다 -> 예외 시 롤백")
        void throughProxy() {
            assertThatThrownBy(() -> selfCaller.insertAndFail("proxy"))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(count("proxy")).isZero();
            assertThat(selfCaller.wasTxActive()).isTrue();
        }

        @Test
        @DisplayName("같은 클래스 안에서 this로 호출하면 @Transactional이 무시된다 -> 예외가 나도 INSERT가 남는다")
        void selfInvocation() {
            assertThatThrownBy(() -> selfCaller.callInternally("self"))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(count("self")).isEqualTo(1); // 롤백되지 않았다!
            assertThat(selfCaller.wasTxActive()).isFalse(); // 메서드 안에서 트랜잭션이 아예 없었다
        }
    }

    @Nested
    @DisplayName("3. 롤백 규칙 (체크 예외)")
    class RollbackRuleTest {

        @Test
        @DisplayName("체크 예외(Exception)는 기본적으로 롤백하지 않고 커밋한다")
        void checkedExceptionCommits() {
            assertThatThrownBy(() -> checkedThrower.insertThenThrowChecked("checked"))
                    .isInstanceOf(Exception.class);

            assertThat(count("checked")).isEqualTo(1);
        }

        @Test
        @DisplayName("rollbackFor = Exception.class를 주면 체크 예외에서도 롤백한다")
        void rollbackFor() {
            assertThatThrownBy(() -> checkedThrower.insertThenThrowCheckedWithRollbackFor("rollback_for"))
                    .isInstanceOf(Exception.class);

            assertThat(count("rollback_for")).isZero();
        }
    }

    // ===================================================================
    // 실험용 빈들. @Bean으로 등록해야 스프링이 프록시를 씌워서 @Transactional이 동작한다.
    // ===================================================================

    @TestConfiguration
    static class LabConfig {
        @Bean
        Inner inner(JdbcTemplate jdbc) {
            return new Inner(jdbc);
        }

        @Bean
        Outer outer(Inner inner, JdbcTemplate jdbc) {
            return new Outer(inner, jdbc);
        }

        @Bean
        SelfCaller selfCaller(JdbcTemplate jdbc) {
            return new SelfCaller(jdbc);
        }

        @Bean
        CheckedThrower checkedThrower(JdbcTemplate jdbc) {
            return new CheckedThrower(jdbc);
        }
    }

    static void insertMarker(JdbcTemplate jdbc, String marker) {
        jdbc.update("INSERT INTO TB_CRAWL_RUN (RUN_ID, STATUS, ERROR_MSG) VALUES (SEQ_CRAWL_RUN.NEXTVAL, 'FAIL', ?)",
                MARKER_PREFIX + marker);
    }

    static class Inner {
        private final JdbcTemplate jdbc;

        Inner(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Transactional(propagation = Propagation.REQUIRED)
        public void insertRequired(String marker) {
            insertMarker(jdbc, marker);
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void insertRequiresNew(String marker) {
            insertMarker(jdbc, marker);
        }
    }

    static class Outer {
        private final Inner inner;
        private final JdbcTemplate jdbc;

        Outer(Inner inner, JdbcTemplate jdbc) {
            this.inner = inner;
            this.jdbc = jdbc;
        }

        @Transactional
        public void callRequiredThenFail(String marker) {
            inner.insertRequired(marker);
            throw new IllegalStateException("바깥 트랜잭션 실패");
        }

        @Transactional
        public void callRequiresNewThenFail(String marker) {
            inner.insertRequiresNew(marker);
            throw new IllegalStateException("바깥 트랜잭션 실패");
        }
    }

    static class SelfCaller {
        private final JdbcTemplate jdbc;
        private boolean txActive;

        SelfCaller(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        /**
         * 필드를 직접 읽지 않고 메서드로 읽는 이유:
         * 테스트가 주입받은 건 프록시 객체라서, 필드를 바로 읽으면 진짜 객체가 아닌 프록시의 빈 필드를 읽게 된다.
         * 메서드 호출은 프록시가 진짜 객체로 넘겨주므로 실제 값을 얻는다.
         */
        public boolean wasTxActive() {
            return txActive;
        }

        /** 트랜잭션 없는 메서드에서 같은 클래스의 @Transactional 메서드를 this로 호출 */
        public void callInternally(String marker) {
            this.insertAndFail(marker);
        }

        @Transactional
        public void insertAndFail(String marker) {
            txActive = TransactionSynchronizationManager.isActualTransactionActive();
            insertMarker(jdbc, marker);
            throw new IllegalStateException("실패");
        }
    }

    static class CheckedThrower {
        private final JdbcTemplate jdbc;

        CheckedThrower(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Transactional
        public void insertThenThrowChecked(String marker) throws Exception {
            insertMarker(jdbc, marker);
            throw new Exception("체크 예외");
        }

        @Transactional(rollbackFor = Exception.class)
        public void insertThenThrowCheckedWithRollbackFor(String marker) throws Exception {
            insertMarker(jdbc, marker);
            throw new Exception("체크 예외");
        }
    }
}
