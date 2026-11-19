package com.lhk.freemoa.crawler;

import com.lhk.freemoa.crawler.run.CrawlAlreadyRunningException;
import com.lhk.freemoa.crawler.run.CrawlRunService;
import com.lhk.freemoa.crawler.run.CrawlRunType;
import com.lhk.freemoa.crawler.run.CrawlStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;

/**
 * [6단계] 오래 걸리는 수집을 백그라운드 스레드에서 돌린다.
 *
 * 흐름
 *   요청 스레드 (http-nio-8080-exec-N)          수집 스레드 (crawl-1)
 *   ─────────────────────────────────          ─────────────────────────
 *   crawlRunService.start()  -> runId
 *   executor.execute(작업) ───────────────────▶ crawlService.execute(runId, ...)
 *   return runId  (즉시 응답, 202)                  페이지마다 저장 + 진행 건수 갱신
 *                                                  finish()
 *
 * 왜 @Async가 아니라 TaskExecutor를 직접 쓰나
 *  - start()는 요청 스레드에서 해야 한다. 그래야 runId를 바로 돌려주고, 이미 실행 중이면 409를 바로 줄 수 있다.
 *  - 그런데 같은 클래스 안에서 @Async 메서드를 this.xxx()로 부르면 프록시를 안 거쳐서 비동기로 안 돈다.
 *    (4단계 @Transactional self-invocation 함정과 똑같다)
 *  - @Async를 쓰려면 클래스를 하나 더 쪼개야 하는데, 이 정도면 executor.execute(람다)가 더 읽기 쉽다.
 *
 * 왜 스레드를 직접 new Thread() 하지 않나
 *  - 요청마다 스레드가 무한정 생길 수 있다. 풀(CrawlAsyncConfig)을 쓰면 개수 제한, 이름, 종료 처리를 한 곳에서 관리한다.
 */
@Component
public class CrawlJobLauncher {

    private static final Logger log = LoggerFactory.getLogger(CrawlJobLauncher.class);

    private final CrawlService crawlService;
    private final CrawlRunService crawlRunService;
    private final CrawlerProperties properties;
    private final TaskExecutor crawlExecutor;

    public CrawlJobLauncher(CrawlService crawlService,
                            CrawlRunService crawlRunService,
                            CrawlerProperties properties,
                            @Qualifier(CrawlAsyncConfig.CRAWL_EXECUTOR) TaskExecutor crawlExecutor) {
        this.crawlService = crawlService;
        this.crawlRunService = crawlRunService;
        this.properties = properties;
        this.crawlExecutor = crawlExecutor;
    }

    /**
     * 마지막 페이지까지 전체 수집을 접수하고 runId를 바로 돌려준다.
     * 안전장치로 crawler.full-max-pages 페이지에서는 멈춘다.
     */
    public long submitFull(String keyword) {
        long runId = crawlRunService.start(keyword, CrawlRunType.FULL); // 이미 실행 중이면 CrawlAlreadyRunningException -> 409

        try {
            crawlExecutor.execute(() -> runSafely(runId, keyword, properties.fullMaxPages()));
        } catch (TaskRejectedException e) {
            // 직전 수집 스레드가 finish() 직후 아직 정리 중인 아주 짧은 순간에 들어온 경우.
            // RUNNING으로 남기면 안 되니까 바로 FAIL로 닫는다.
            crawlRunService.finish(runId, CrawlStatus.FAIL, 0, 0, 0, 0, 0, "수집 스레드가 사용 중이라 접수하지 못함");
            throw new CrawlAlreadyRunningException("이전 수집이 정리 중입니다. 잠시 뒤 다시 시도하세요.", e);
        }

        log.info("[run={}] 전체 수집 접수 (keyword='{}')", runId, keyword);
        return runId;
    }

    /**
     * 백그라운드 스레드에서 던진 예외는 요청한 쪽에 전달될 곳이 없다.
     * execute()가 이미 이력에 FAIL/PARTIAL을 남기고 로그도 찍으니, 여기서는 삼켜서 스레드가 조용히 끝나게 한다.
     */
    private void runSafely(long runId, String keyword, int maxPages) {
        try {
            crawlService.execute(runId, keyword, maxPages, CrawlService.NO_STOP);
        } catch (RuntimeException e) {
            log.debug("[run={}] 백그라운드 수집 종료 (실패 처리 완료)", runId);
        }
    }
}
