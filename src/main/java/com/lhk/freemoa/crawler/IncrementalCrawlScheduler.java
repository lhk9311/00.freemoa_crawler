package com.lhk.freemoa.crawler;

import com.lhk.freemoa.crawler.run.CrawlAlreadyRunningException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * [6단계] 증분 수집 배치. 정해진 시각마다 새 공고만 가져온다.
 *
 * 동작 방식
 *  - @EnableScheduling: 스프링이 스케줄러 스레드(scheduling-1)를 띄우고 @Scheduled 메서드를 시간 맞춰 호출한다.
 *  - cron은 application.yml의 crawler.incremental.cron. 코드 수정 없이 주기를 바꿀 수 있다.
 *  - crawler.incremental.enabled=false면 이 클래스 자체가 빈으로 안 만들어져서 배치가 꺼진다 (@ConditionalOnProperty).
 *
 * cron 표현식 (스프링은 6자리: 초 분 시 일 월 요일)
 *   "0 0 9-18 * * MON-FRI"  -> 평일 9시~18시 매시 정각
 *   "0 *&#47;10 * * * *"         -> 10분마다 (테스트용. 주석 안에서 별-슬래시가 주석 끝으로 읽혀서 &#47;로 적음)
 *   "0 30 8 * * *"          -> 매일 8시 30분
 *
 * 수동 수집이나 전체 수집이 돌고 있으면?
 *  - crawlRunService.start()가 UX_CRAWL_RUN_RUNNING 유니크 인덱스에 걸려 CrawlAlreadyRunningException을 던진다.
 *  - 배치는 "이번 회차는 건너뜀"으로 처리하고 다음 시각에 다시 돈다. 4단계에서 만든 동시 실행 방지가 여기서 쓰인다.
 *
 * 한계
 *  - 내 PC에서 서버가 켜져 있을 때만 돈다. 꺼져 있던 시간대의 회차는 실행되지 않는다 (다음 회차가 밀린 만큼 더 가져간다).
 *  - 서버가 2대 이상이면 각자 돌아서 하나는 매번 409로 건너뛴다. 실무에선 ShedLock 같은 분산 락이나 배치 전용 서버를 쓴다.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "crawler.incremental", name = "enabled", havingValue = "true")
public class IncrementalCrawlScheduler {

    private static final Logger log = LoggerFactory.getLogger(IncrementalCrawlScheduler.class);

    private final CrawlService crawlService;

    public IncrementalCrawlScheduler(CrawlService crawlService) {
        this.crawlService = crawlService;
    }

    @Scheduled(cron = "${crawler.incremental.cron}", zone = "Asia/Seoul")
    public void run() {
        try {
            CrawlResult r = crawlService.crawlIncremental();
            log.info("[배치] 증분 수집 완료 run={} 신규 {}건, 갱신 {}건", r.runId(), r.inserted(), r.updated());
        } catch (CrawlAlreadyRunningException e) {
            log.info("[배치] 다른 수집이 진행 중이라 이번 회차는 건너뜀");
        } catch (RuntimeException e) {
            // 여기서 예외를 밖으로 던져도 스케줄러는 다음 회차를 계속 돌린다. 다만 로그를 우리 형식으로 남기려고 잡는다.
            // 실패 이력은 crawlService.execute()가 이미 TB_CRAWL_RUN에 남겼다.
            log.warn("[배치] 증분 수집 실패: {}", e.getMessage());
        }
    }
}
