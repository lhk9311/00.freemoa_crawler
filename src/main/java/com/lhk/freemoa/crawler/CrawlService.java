package com.lhk.freemoa.crawler;

import com.lhk.freemoa.crawler.domain.Project;
import com.lhk.freemoa.crawler.run.CrawlRunService;
import com.lhk.freemoa.crawler.run.CrawlRunType;
import com.lhk.freemoa.crawler.run.CrawlStatus;
import com.lhk.freemoa.project.ProjectSaveService;
import com.lhk.freemoa.project.SaveResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 수집(HTTP) -> 저장(DB) 흐름을 조율하고, 결과를 수집 이력에 남긴다.
 *
 * 트랜잭션 구조 (4단계)
 *
 *   CrawlService.crawl()                          트랜잭션 없음
 *    ├─ crawlRunService.start()                   [TX-1] REQUIRES_NEW : RUNNING 기록 즉시 커밋
 *    ├─ projectCollector.collectEachPage()        트랜잭션 없음 (HTTP, 느림)
 *    │    └─ 페이지마다 (6단계)
 *    │         ├─ projectSaveService.saveAll()    트랜잭션 없음 (반복만 담당)
 *    │         │    └─ projectWriter.saveOne() x 10   [TX] 공고 1건씩 각자 커밋/롤백
 *    │         │         └─ skillService.findOrCreate() [TX-별도] REQUIRES_NEW : 기술 마스터 즉시 커밋
 *    │         └─ crawlRunService.progress()      [TX] REQUIRES_NEW : 진행 건수 즉시 커밋
 *    └─ crawlRunService.finish()                  [TX-마지막] REQUIRES_NEW : SUCCESS/PARTIAL/FAIL 기록
 *
 * 이 클래스에 @Transactional을 붙이지 않는 이유
 *  1. HTTP 수집 동안 DB 커넥션을 붙잡게 된다 (3단계 설명)
 *  2. saveOne들이 바깥 트랜잭션에 합류해서 건별 커밋이 깨진다
 */
@Service
public class CrawlService {

    private static final Logger log = LoggerFactory.getLogger(CrawlService.class);
    /** execute()의 stopAfterIdlePages 값: 멈춤 조건 없이 maxPages 또는 마지막 페이지까지 */
    public static final int NO_STOP = 0;

    private final ProjectCollector projectCollector;
    private final ProjectSaveService projectSaveService;
    private final CrawlRunService crawlRunService;
    private final CrawlerProperties properties;

    public CrawlService(ProjectCollector projectCollector,
                        ProjectSaveService projectSaveService,
                        CrawlRunService crawlRunService,
                        CrawlerProperties properties) {
        this.projectCollector = projectCollector;
        this.projectSaveService = projectSaveService;
        this.crawlRunService = crawlRunService;
        this.properties = properties;
    }

    /**
     * 화면/API에서 바로 결과를 받는 수집 (최대 crawler.max-pages-limit 페이지).
     * 끝날 때까지 응답을 기다리므로 페이지 수가 적을 때만 쓴다.
     */
    public CrawlResult crawl(String keyword, int maxPages) {
        if (maxPages < 1 || maxPages > properties.maxPagesLimit()) {
            throw new IllegalArgumentException(
                    "maxPages는 1~" + properties.maxPagesLimit() + " 사이여야 합니다: " + maxPages);
        }
        long runId = crawlRunService.start(keyword, CrawlRunType.MANUAL); // 이미 실행 중이면 CrawlAlreadyRunningException
        return execute(runId, keyword, maxPages, NO_STOP);
    }

    /**
     * [6단계] 증분 수집: 새 공고만 가져온다. 스케줄러(IncrementalCrawlScheduler)와 수동 테스트 API가 부른다.
     *
     * 목록은 최신 공고가 앞에 온다. 1페이지부터 보다가 "새 공고가 하나도 없는 페이지"가
     * crawler.incremental.stop-after-idle-pages 번 연속 나오면, 그 뒤는 이미 아는 공고라고 보고 멈춘다.
     * 평소에는 1~2페이지만 보고 끝나고, 한동안 안 돌았으면 새 공고가 끝날 때까지 더 간다 (상한 max-pages).
     */
    public CrawlResult crawlIncremental() {
        CrawlerProperties.Incremental inc = properties.incremental();
        long runId = crawlRunService.start("", CrawlRunType.INCREMENTAL);
        return execute(runId, "", inc.maxPages(), inc.stopAfterIdlePages());
    }

    /**
     * 이미 RUNNING으로 기록된 runId로 실제 수집을 한다.
     * [6단계] 전체 수집은 CrawlJobLauncher가 start()를 먼저 부른 뒤, 백그라운드 스레드에서 이 메서드를 부른다.
     *
     * 페이지를 받을 때마다 바로 저장한다.
     * 전부 모은 뒤 저장하면 400페이지째에서 실패했을 때 앞에서 받은 것까지 전부 버리게 된다.
     */
    /**
     * @param stopAfterIdlePages 새 공고가 없는 페이지가 이만큼 연속되면 멈춘다. NO_STOP(0)이면 끝까지 간다.
     */
    public CrawlResult execute(long runId, String keyword, int maxPages, int stopAfterIdlePages) {
        long start = System.currentTimeMillis();
        log.info("[run={}] 수집 시작: keyword='{}', maxPages={}", runId, keyword, maxPages);

        // 람다 안에서는 바깥 지역변수를 다시 대입할 수 없다 (effectively final 규칙).
        // 그래서 int 변수 대신 필드를 가진 객체를 하나 만들어 그 안의 값을 바꾼다.
        Totals totals = new Totals();
        // 페이지를 넘기는 사이 새 공고가 올라오면 같은 공고가 다음 페이지에 또 나온다.
        // 페이지 단위 저장이라 ProjectSaveService의 중복 제거(한 페이지 안)만으로는 부족해서, 수집 전체 기준으로 한 번 더 거른다.
        Set<Long> seen = new HashSet<>();

        try {
            projectCollector.collectEachPage(keyword, maxPages, (page, projects) -> {
                List<Project> fresh = projects.stream()
                        .filter(p -> seen.add(p.projIdx())) // Set.add는 처음 넣는 값일 때만 true
                        .toList();
                SaveResult saved = projectSaveService.saveAll(runId, fresh);
                totals.add(page, projects.size(), saved);
                crawlRunService.progress(runId, totals.pages, totals.fetched,
                        totals.inserted, totals.updated, totals.failed);
                if (page % 50 == 0) {
                    log.info("[run={}] {}페이지 진행 중 (누적 {}건)", runId, page, totals.fetched);
                }

                // [6단계] 증분 수집의 멈춤 조건
                totals.idlePages = saved.inserted() > 0 ? 0 : totals.idlePages + 1;
                if (stopAfterIdlePages > 0 && totals.idlePages >= stopAfterIdlePages) {
                    log.info("[run={}] 새 공고 없는 페이지 {}번 연속 -> {}페이지에서 멈춤", runId, totals.idlePages, page);
                    return false; // collectEachPage에 "그만"이라고 알린다
                }
                return true;
            });

            CrawlStatus status = decideStatus(totals);
            crawlRunService.finish(runId, status, totals.pages, totals.fetched,
                    totals.inserted, totals.updated, totals.failed, totals.firstError);

            long elapsed = System.currentTimeMillis() - start;
            log.info("[run={}] 수집 종료: {} ({}페이지, {}ms)", runId, status, totals.pages, elapsed);
            return new CrawlResult(runId, status, keyword, maxPages, totals.fetched,
                    totals.inserted, totals.updated, totals.failed, elapsed);

        } catch (RuntimeException e) {
            // 수집 자체가 실패(프리모아 장애 등)해도 RUNNING으로 남겨두면 안 된다.
            // RUNNING이 남으면 유니크 인덱스 때문에 다음 수집이 영원히 막힌다.
            // 페이지 단위로 저장했으니, 이미 저장된 게 있으면 FAIL이 아니라 PARTIAL이다.
            CrawlStatus status = totals.savedAny() ? CrawlStatus.PARTIAL : CrawlStatus.FAIL;
            crawlRunService.finish(runId, status, totals.pages, totals.fetched,
                    totals.inserted, totals.updated, totals.failed,
                    (totals.pages + 1) + "페이지에서 중단: " + e.getMessage());
            log.error("[run={}] 수집 중단 ({}페이지까지 저장됨)", runId, totals.pages, e);
            throw e;
        }
    }

    private static CrawlStatus decideStatus(Totals totals) {
        if (totals.failed == 0) {
            return CrawlStatus.SUCCESS;
        }
        return totals.savedAny() ? CrawlStatus.PARTIAL : CrawlStatus.FAIL;
    }

    /** 페이지마다 누적되는 건수. execute() 안에서만 쓰는 값 묶음이라 private 중첩 클래스로 둔다 */
    private static final class Totals {
        int pages;
        int fetched;
        int inserted;
        int updated;
        int failed;
        String firstError;
        /** 새 공고(inserted)가 0건인 페이지가 몇 번 연속됐는지 */
        int idlePages;

        void add(int page, int fetchedOnPage, SaveResult saved) {
            pages = page;
            fetched += fetchedOnPage;
            inserted += saved.inserted();
            updated += saved.updated();
            failed += saved.failed();
            if (firstError == null) {
                firstError = saved.firstError();
            }
        }

        boolean savedAny() {
            return inserted + updated > 0;
        }
    }
}
