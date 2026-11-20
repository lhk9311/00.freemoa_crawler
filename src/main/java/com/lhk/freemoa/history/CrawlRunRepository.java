package com.lhk.freemoa.history;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * [JPA] Spring Data JPA 리포지토리. 인터페이스만 만들면 구현체는 스프링이 실행 시점에 만들어 준다.
 *
 * JpaRepository<엔티티, PK 타입>를 상속하면 findById, findAll, count 같은 기본 메서드가 생긴다.
 *
 * 메서드 이름으로 쿼리 만들기 (쿼리 메서드)
 *   findByRunTypeOrderByRunIdDesc(runType, pageable)
 *   -> select ... from TB_CRAWL_RUN where RUN_TYPE = ? order by RUN_ID desc fetch first ? rows only
 *   이름 규칙: findBy + 필드명 + (And/Or 필드명) + OrderBy + 필드명 + Asc/Desc
 *   필드명은 DB 컬럼이 아니라 엔티티 필드(runType)다.
 */
public interface CrawlRunRepository extends JpaRepository<CrawlRunEntity, Long> {

    /** 최근 이력 (전체 종류). 개수는 Pageable로 */
    List<CrawlRunEntity> findAllByOrderByRunIdDesc(Pageable pageable);

    /** 최근 이력 (종류별) */
    List<CrawlRunEntity> findByRunTypeOrderByRunIdDesc(String runType, Pageable pageable);

    /**
     * 이름 규칙으로 표현하기 어려운 건 @Query로 JPQL을 직접 쓴다.
     * JPQL은 SQL과 비슷하지만 테이블/컬럼이 아니라 엔티티/필드 이름을 쓴다 (CrawlRunEntity, r.runType).
     * 결과는 인터페이스 프로젝션(RunTypeSummary)으로 받는다: 별칭(as runType)과 getter 이름(getRunType)을 맞춘다.
     */
    @Query("""
            select r.runType as runType,
                   count(r) as runCount,
                   sum(r.insertedCnt) as totalInserted,
                   max(r.startedAt) as lastStartedAt
              from CrawlRunEntity r
             group by r.runType
             order by r.runType
            """)
    List<RunTypeSummary> summarizeByRunType();
}
