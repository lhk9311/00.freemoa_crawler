package com.lhk.freemoa.crawler.run;

/** [6단계] TB_CRAWL_RUN.RUN_TYPE. 어떤 경로로 시작된 수집인지 (db/03_alter_crawl_run_type.sql) */
public enum CrawlRunType {
    /** 화면/API에서 페이지 수를 정해서 실행 */
    MANUAL,
    /** 마지막 페이지까지 전체 수집 (백그라운드) */
    FULL,
    /** 스케줄러가 주기적으로 새 공고만 수집 */
    INCREMENTAL
}
