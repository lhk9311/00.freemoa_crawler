package com.lhk.freemoa.crawler.run;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * [6단계] 서버가 뜰 때 1번 실행된다 (ApplicationRunner).
 *
 * 전체 수집은 10분 가까이 걸린다. 그 사이 IntelliJ에서 서버를 멈추면 finish()가 호출되지 못해
 * TB_CRAWL_RUN에 RUNNING 행이 남고, 유니크 인덱스(UX_CRAWL_RUN_RUNNING) 때문에 다음 수집이 전부 409로 막힌다.
 * 서버가 막 켜진 시점에는 이 서버에서 돌고 있는 수집이 있을 수 없으니, 남은 RUNNING은 전부 중단된 것이다.
 */
@Component
public class CrawlRunRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CrawlRunRecovery.class);

    private final CrawlRunService crawlRunService;

    public CrawlRunRecovery(CrawlRunService crawlRunService) {
        this.crawlRunService = crawlRunService;
    }

    @Override
    public void run(ApplicationArguments args) {
        int cleaned = crawlRunService.failStaleRunning();
        if (cleaned > 0) {
            log.warn("중단된 수집 이력 {}건을 FAIL로 정리했습니다", cleaned);
        }
    }
}
