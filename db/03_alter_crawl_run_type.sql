/* =====================================================================
 * 03_alter_crawl_run_type.sql   [6단계]
 * 실행 계정 : freemoa  /  Alt + X
 *
 * 수집 종류가 3가지가 됐다.
 *   MANUAL      : 화면/API에서 페이지 수를 정해서 돌린 것 (기존 방식)
 *   FULL        : 마지막 페이지까지 전체 수집 (/api/crawl/all)
 *   INCREMENTAL : 스케줄러가 정해진 시간마다 새 공고만 가져오는 것
 * 이력만 보고 "이건 배치가 돈 거다"를 구분할 수 있게 컬럼을 추가한다.
 * ===================================================================== */

-- 1) 컬럼 추가
--    NOT NULL 컬럼을 이미 행이 있는 테이블에 추가하려면 DEFAULT가 있어야 한다.
--    DEFAULT 'MANUAL'을 주면 기존 행(1~N번 수집)은 전부 'MANUAL'로 채워진다. 지금까지는 실제로 다 수동이었다.
ALTER TABLE TB_CRAWL_RUN ADD (
    RUN_TYPE  VARCHAR2(20)  DEFAULT 'MANUAL' NOT NULL
);

COMMENT ON COLUMN TB_CRAWL_RUN.RUN_TYPE IS 'MANUAL(수동) / FULL(전체) / INCREMENTAL(증분 배치)';


-- 2) 정해진 값만 들어가게 CHECK 제약조건
ALTER TABLE TB_CRAWL_RUN ADD CONSTRAINT CK_CRAWL_RUN_TYPE
    CHECK (RUN_TYPE IN ('MANUAL', 'FULL', 'INCREMENTAL'));


-- 확인
-- SELECT RUN_ID, RUN_TYPE, STATUS, KEYWORD, STARTED_AT FROM TB_CRAWL_RUN ORDER BY RUN_ID DESC;
