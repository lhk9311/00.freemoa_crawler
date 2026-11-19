package com.lhk.freemoa.crawler;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * [6단계] 수집 전용 스레드 풀.
 *
 * 스레드 1개, 대기열 0
 *  - 수집은 어차피 동시에 1개만 돌아야 한다 (DB의 UX_CRAWL_RUN_RUNNING 인덱스로도 막혀 있다).
 *  - 대기열을 두면 "접수는 됐는데 몇 분 뒤에 시작"하는 작업이 생겨서 이력(RUNNING)과 실제 상태가 어긋난다.
 *  - 스레드가 사용 중이면 TaskRejectedException -> CrawlJobLauncher가 409로 바꾼다.
 *
 * 스레드 이름 접두사 "crawl-"
 *  - 로그에 [crawl-1]로 찍혀서 요청 스레드(http-nio-8080-exec-N)와 구분된다.
 */
@Configuration
public class CrawlAsyncConfig {

    public static final String CRAWL_EXECUTOR = "crawlExecutor";

    @Bean(name = CRAWL_EXECUTOR)
    public ThreadPoolTaskExecutor crawlExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("crawl-");
        // 서버 종료 시 수집이 끝날 때까지 기다리지 않는다. 남은 RUNNING은 다음 기동 때 CrawlRunRecovery가 정리한다.
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor; // initialize()는 스프링이 빈 생성 후 자동으로 호출한다 (InitializingBean)
    }
}
