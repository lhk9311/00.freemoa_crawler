/* =====================================================================
 * 99_drop_tables.sql  -  전체 초기화용 (데이터 전부 삭제됨!)
 * 자식 테이블(FK를 가진 쪽)부터 지워야 한다. 순서를 바꾸면 ORA-02449 발생.
 * PURGE: 휴지통(RECYCLEBIN)에 남기지 않고 완전 삭제
 * ===================================================================== */
DROP TABLE TB_PROJECT_SNAPSHOT PURGE;
DROP TABLE TB_PROJECT_SKILL    PURGE;
DROP TABLE TB_CRAWL_RUN        PURGE;
DROP TABLE TB_SKILL            PURGE;
DROP TABLE TB_PROJECT          PURGE;

DROP SEQUENCE SEQ_SKILL;
DROP SEQUENCE SEQ_CRAWL_RUN;
