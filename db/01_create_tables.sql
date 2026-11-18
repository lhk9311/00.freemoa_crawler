/* =====================================================================
 * 01_create_tables.sql
 * 실행 계정 : freemoa  (FREEPDB1)
 * 실행 방법 : DBeaver에서 이 파일 전체를 열고 Alt + X (스크립트 실행)
 * 재실행    : 99_drop_tables.sql 먼저 실행 후 다시 실행
 *
 * 테이블 구성
 *   TB_PROJECT          공고 마스터 (프리모아 proj_idx 1건 = 1행)
 *   TB_SKILL            기술스택 마스터 (PHP, Java, MySQL ...)
 *   TB_PROJECT_SKILL    공고-기술스택 N:M 매핑
 *   TB_CRAWL_RUN        수집 실행 이력 (1번 수집 = 1행)
 *   TB_PROJECT_SNAPSHOT 수집 시점별 공고 상태 (지원자 수 추이 분석용)
 * ===================================================================== */


/* ---------------------------------------------------------------------
 * 시퀀스
 * Oracle 12c부터 IDENTITY 컬럼도 있지만, SI 현장 레거시는 대부분 시퀀스를 쓴다.
 * NOCACHE: 서버 재시작 시 번호가 건너뛰는 것을 줄임 (대신 채번 성능은 약간 손해)
 * --------------------------------------------------------------------- */
CREATE SEQUENCE SEQ_SKILL     START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEQ_CRAWL_RUN START WITH 1 INCREMENT BY 1 NOCACHE;


/* ---------------------------------------------------------------------
 * TB_PROJECT : 공고 마스터
 * PK는 시퀀스가 아니라 프리모아의 proj_idx를 그대로 쓴다 (자연키).
 *  -> 같은 공고를 여러 번 수집해도 1행만 유지되고, MERGE(upsert) 기준키가 된다.
 * 의뢰인 이메일(client_id)은 개인정보라 수집하지 않는다.
 * --------------------------------------------------------------------- */
CREATE TABLE TB_PROJECT (
    PROJ_IDX          NUMBER(10)          NOT NULL,
    TITLE             VARCHAR2(500 CHAR)  NOT NULL,
    CATEGORY          VARCHAR2(100 CHAR),
    SUB_CATEGORY      VARCHAR2(100 CHAR),
    REGION            VARCHAR2(50 CHAR),
    PLAN_STATUS       VARCHAR2(100 CHAR),
    WORK_TYPE         VARCHAR2(10 CHAR),
    DURATION_DAYS     NUMBER(5),
    COST_MIN          NUMBER(10),
    COST_MAX          NUMBER(10),
    APPLY_COUNT       NUMBER(6)           DEFAULT 0 NOT NULL,
    SKILL_RAW         VARCHAR2(1000 CHAR),
    SUMMARY           CLOB,
    POSTED_AT         DATE,
    DEADLINE          DATE,
    FIRST_CRAWLED_AT  DATE                DEFAULT SYSDATE NOT NULL,
    LAST_CRAWLED_AT   DATE                DEFAULT SYSDATE NOT NULL,
    CONSTRAINT PK_PROJECT PRIMARY KEY (PROJ_IDX),
    CONSTRAINT CK_PROJECT_COST CHECK (COST_MIN IS NULL OR COST_MAX IS NULL OR COST_MIN <= COST_MAX)
);

COMMENT ON TABLE  TB_PROJECT                  IS '프리모아 프로젝트 공고';
COMMENT ON COLUMN TB_PROJECT.PROJ_IDX         IS '프리모아 공고번호 (proj_idx)';
COMMENT ON COLUMN TB_PROJECT.TITLE            IS '공고 제목';
COMMENT ON COLUMN TB_PROJECT.CATEGORY         IS '분야 대분류 (fld)';
COMMENT ON COLUMN TB_PROJECT.SUB_CATEGORY     IS '분야 소분류 (fld_nm_2nd)';
COMMENT ON COLUMN TB_PROJECT.REGION           IS '지역 (pv_smallnm)';
COMMENT ON COLUMN TB_PROJECT.PLAN_STATUS      IS '기획 상태 (plan_nm)';
COMMENT ON COLUMN TB_PROJECT.WORK_TYPE        IS '근무 형태 코드 (workType)';
COMMENT ON COLUMN TB_PROJECT.DURATION_DAYS    IS '예상 기간(일)';
COMMENT ON COLUMN TB_PROJECT.COST_MIN         IS '최소 예산(만원), 0/미입력은 NULL';
COMMENT ON COLUMN TB_PROJECT.COST_MAX         IS '최대 예산(만원), 0/미입력은 NULL';
COMMENT ON COLUMN TB_PROJECT.APPLY_COUNT      IS '최근 수집 시점 지원자 수';
COMMENT ON COLUMN TB_PROJECT.SKILL_RAW        IS '기술스택 원문 (정규화 전, 콤마 구분)';
COMMENT ON COLUMN TB_PROJECT.SUMMARY          IS '공고 본문 요약';
COMMENT ON COLUMN TB_PROJECT.POSTED_AT        IS '공고 등록일시 (INS_TIME)';
COMMENT ON COLUMN TB_PROJECT.DEADLINE         IS '모집 마감일 (edate)';
COMMENT ON COLUMN TB_PROJECT.FIRST_CRAWLED_AT IS '최초 수집일시';
COMMENT ON COLUMN TB_PROJECT.LAST_CRAWLED_AT  IS '최종 수집일시';

-- 마감 임박 공고 조회 / 등록일 기준 정렬이 잦을 것이라 인덱스 생성
CREATE INDEX IX_PROJECT_DEADLINE  ON TB_PROJECT (DEADLINE);
CREATE INDEX IX_PROJECT_POSTED_AT ON TB_PROJECT (POSTED_AT);


/* ---------------------------------------------------------------------
 * TB_SKILL : 기술스택 마스터
 * SKILL_NAME : 화면 표시용 원래 이름 ('MySQL')
 * SKILL_KEY  : 중복 판단용 정규화 키 ('MYSQL')  -> 'mysql', 'MySQL ' 이 같은 기술로 묶이도록
 * --------------------------------------------------------------------- */
CREATE TABLE TB_SKILL (
    SKILL_ID    NUMBER(10)          NOT NULL,
    SKILL_NAME  VARCHAR2(100 CHAR)  NOT NULL,
    SKILL_KEY   VARCHAR2(100 CHAR)  NOT NULL,
    CREATED_AT  DATE                DEFAULT SYSDATE NOT NULL,
    CONSTRAINT PK_SKILL PRIMARY KEY (SKILL_ID),
    CONSTRAINT UK_SKILL_KEY UNIQUE (SKILL_KEY)
);

COMMENT ON TABLE  TB_SKILL            IS '기술스택 마스터';
COMMENT ON COLUMN TB_SKILL.SKILL_ID   IS '기술스택 ID (SEQ_SKILL)';
COMMENT ON COLUMN TB_SKILL.SKILL_NAME IS '표시용 이름';
COMMENT ON COLUMN TB_SKILL.SKILL_KEY  IS '정규화 키 (대문자, 공백 제거)';


/* ---------------------------------------------------------------------
 * TB_PROJECT_SKILL : 공고-기술스택 N:M 매핑
 * 공고 1건에 기술 여러 개, 기술 1개가 공고 여러 건에 쓰이므로 중간 테이블로 푼다.
 * PK(PROJ_IDX, SKILL_ID) 순서라 "공고 -> 기술" 조회는 PK 인덱스로 빠르지만,
 * "기술 -> 공고" 조회(예: Java 공고 목록)는 SKILL_ID가 선두가 아니라서 별도 인덱스가 필요하다.
 * --------------------------------------------------------------------- */
CREATE TABLE TB_PROJECT_SKILL (
    PROJ_IDX  NUMBER(10) NOT NULL,
    SKILL_ID  NUMBER(10) NOT NULL,
    CONSTRAINT PK_PROJECT_SKILL PRIMARY KEY (PROJ_IDX, SKILL_ID),
    CONSTRAINT FK_PS_PROJECT FOREIGN KEY (PROJ_IDX) REFERENCES TB_PROJECT (PROJ_IDX) ON DELETE CASCADE,
    CONSTRAINT FK_PS_SKILL   FOREIGN KEY (SKILL_ID) REFERENCES TB_SKILL (SKILL_ID)
);

COMMENT ON TABLE TB_PROJECT_SKILL IS '공고-기술스택 매핑 (N:M)';

CREATE INDEX IX_PROJECT_SKILL_SKILL ON TB_PROJECT_SKILL (SKILL_ID);


/* ---------------------------------------------------------------------
 * TB_CRAWL_RUN : 수집 실행 이력
 * 수집이 실패해도 "실패했다"는 기록은 남아야 한다 -> 다음 단계 트랜잭션(REQUIRES_NEW) 실습 포인트
 * --------------------------------------------------------------------- */
CREATE TABLE TB_CRAWL_RUN (
    RUN_ID        NUMBER(10)          NOT NULL,
    STATUS        VARCHAR2(10)        NOT NULL,
    STARTED_AT    DATE                DEFAULT SYSDATE NOT NULL,
    ENDED_AT      DATE,
    PAGE_COUNT    NUMBER(5)           DEFAULT 0 NOT NULL,
    FETCHED_CNT   NUMBER(7)           DEFAULT 0 NOT NULL,
    INSERTED_CNT  NUMBER(7)           DEFAULT 0 NOT NULL,
    UPDATED_CNT   NUMBER(7)           DEFAULT 0 NOT NULL,
    ERROR_MSG     VARCHAR2(4000 BYTE),
    CONSTRAINT PK_CRAWL_RUN PRIMARY KEY (RUN_ID),
    CONSTRAINT CK_CRAWL_RUN_STATUS CHECK (STATUS IN ('RUNNING', 'SUCCESS', 'FAIL'))
);

COMMENT ON TABLE  TB_CRAWL_RUN              IS '수집 실행 이력';
COMMENT ON COLUMN TB_CRAWL_RUN.STATUS       IS 'RUNNING / SUCCESS / FAIL';
COMMENT ON COLUMN TB_CRAWL_RUN.FETCHED_CNT  IS 'API에서 받아온 공고 수';
COMMENT ON COLUMN TB_CRAWL_RUN.INSERTED_CNT IS '신규 저장 건수';
COMMENT ON COLUMN TB_CRAWL_RUN.UPDATED_CNT  IS '기존 공고 갱신 건수';


/* ---------------------------------------------------------------------
 * TB_PROJECT_SNAPSHOT : 수집 시점별 공고 상태
 * TB_PROJECT는 "현재 상태" 1행만 갖고, 변화 이력은 여기에 쌓는다.
 *  -> 나중에 LAG() 윈도우 함수로 "지난 수집 대비 지원자 증가 수" 같은 분석을 연습한다.
 * --------------------------------------------------------------------- */
CREATE TABLE TB_PROJECT_SNAPSHOT (
    RUN_ID       NUMBER(10) NOT NULL,
    PROJ_IDX     NUMBER(10) NOT NULL,
    APPLY_COUNT  NUMBER(6)  NOT NULL,
    COST_MIN     NUMBER(10),
    COST_MAX     NUMBER(10),
    CAPTURED_AT  DATE       DEFAULT SYSDATE NOT NULL,
    CONSTRAINT PK_PROJECT_SNAPSHOT PRIMARY KEY (RUN_ID, PROJ_IDX),
    CONSTRAINT FK_SNAP_RUN     FOREIGN KEY (RUN_ID)   REFERENCES TB_CRAWL_RUN (RUN_ID),
    CONSTRAINT FK_SNAP_PROJECT FOREIGN KEY (PROJ_IDX) REFERENCES TB_PROJECT (PROJ_IDX) ON DELETE CASCADE
);

COMMENT ON TABLE TB_PROJECT_SNAPSHOT IS '수집 시점별 공고 상태 이력';

CREATE INDEX IX_SNAPSHOT_PROJ ON TB_PROJECT_SNAPSHOT (PROJ_IDX, CAPTURED_AT);
