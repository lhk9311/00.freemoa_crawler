/* =====================================================================
 * 02_alter_crawl_run.sql   [4단계]
 * 실행 계정 : freemoa  /  Alt + X
 *
 * 01_create_tables.sql을 고치지 않고 새 파일로 변경분만 추가한다.
 *  -> 01을 이미 실행한 DB(내 PC, 다른 개발자 PC, 운영 DB)에도 이 파일만 돌리면 똑같이 맞춰진다.
 *  -> SI 현장에서 "형상관리되는 DB 변경 스크립트"를 이렇게 번호 붙여 쌓는다.
 * ===================================================================== */

-- 1) 컬럼 추가: 실패 건수, 검색어
ALTER TABLE TB_CRAWL_RUN ADD (
    FAILED_CNT  NUMBER(7)          DEFAULT 0 NOT NULL,
    KEYWORD     VARCHAR2(100 CHAR)
);

COMMENT ON COLUMN TB_CRAWL_RUN.FAILED_CNT IS '저장 실패 건수';
COMMENT ON COLUMN TB_CRAWL_RUN.KEYWORD    IS '수집 검색어';


-- 2) 상태값에 PARTIAL(일부 실패) 추가
--    CHECK 제약조건은 수정(ALTER)이 안 돼서, 지우고 다시 만든다.
ALTER TABLE TB_CRAWL_RUN DROP CONSTRAINT CK_CRAWL_RUN_STATUS;
ALTER TABLE TB_CRAWL_RUN ADD CONSTRAINT CK_CRAWL_RUN_STATUS
    CHECK (STATUS IN ('RUNNING', 'SUCCESS', 'PARTIAL', 'FAIL'));

COMMENT ON COLUMN TB_CRAWL_RUN.STATUS IS 'RUNNING / SUCCESS / PARTIAL(일부 실패) / FAIL';


-- 3) 동시 실행 방지: RUNNING 상태인 행은 "딱 1개"만 존재할 수 있게 DB가 막는다.
--
--    함수 기반 유니크 인덱스 트릭:
--    - STATUS가 'RUNNING'이면 1, 아니면 NULL을 인덱스 키로 쓴다.
--    - Oracle B-Tree 인덱스는 키가 전부 NULL인 행을 저장하지 않는다.
--    - 그래서 SUCCESS/FAIL 행은 몇 개든 상관없고, RUNNING 행은 1개만 허용된다.
--    - 두 번째 RUNNING INSERT는 ORA-00001(유니크 위반) -> 스프링이 DuplicateKeyException으로 바꿔준다.
--
--    "SELECT로 RUNNING이 있는지 확인 후 INSERT" 방식은 두 요청이 동시에 확인하면 둘 다 통과해버린다.
--    확인과 등록을 DB 제약조건 한 번으로 처리해야 동시성에 안전하다.
CREATE UNIQUE INDEX UX_CRAWL_RUN_RUNNING
    ON TB_CRAWL_RUN (CASE WHEN STATUS = 'RUNNING' THEN 1 END);
