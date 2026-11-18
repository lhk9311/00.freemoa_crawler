package com.lhk.freemoa.crawler;

import com.lhk.freemoa.crawler.domain.Project;
import com.lhk.freemoa.crawler.run.CrawlRunService;
import com.lhk.freemoa.crawler.run.CrawlStatus;
import com.lhk.freemoa.project.ProjectSaveService;
import com.lhk.freemoa.project.SaveResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 수집(HTTP) -> 저장(DB) 흐름을 조율하고, 결과를 수집 이력에 남긴다.
 *
 * 트랜잭션 구조 (4단계)
 *
 *   CrawlService.crawl()                          트랜잭션 없음
 *    ├─ crawlRunService.start()                   [TX-1] REQUIRES_NEW : RUNNING 기록 즉시 커밋
 *    ├─ projectCollector.collect()                트랜잭션 없음 (HTTP, 느림)
 *    ├─ projectSaveService.saveAll()              트랜잭션 없음 (반복만 담당)
 *    │    └─ projectWriter.saveOne() x N          [TX-2..N] 공고 1건씩 각자 커밋/롤백
 *    │         └─ skillService.findOrCreate()     [TX-별도] REQUIRES_NEW : 기술 마스터 즉시 커밋
 *    └─ crawlRunService.finish()                  [TX-마지막] REQUIRES_NEW : SUCCESS/PARTIAL/FAIL 기록
 *
 * 이 클래스에 @Transactional을 붙이지 않는 이유
 *  1. HTTP 수집 동안 DB 커넥션을 붙잡게 된다 (3단계 설명)
 *  2. saveOne들이 바깥 트랜잭션에 합류해서 건별 커밋이 깨진다
 */
@Service
public class CrawlService {

    private static final Logger log = LoggerFactory.getLogger(CrawlService.class);
    private static final int PAGE_SIZE = 10;

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

    public CrawlResult crawl(String keyword, int maxPages) {
        if (maxPages < 1 || maxPages > properties.maxPagesLimit()) {
            throw new IllegalArgumentException(
                    "maxPages는 1~" + properties.maxPagesLimit() + " 사이여야 합니다: " + maxPages);
        }

        long start = System.currentTimeMillis();
        long runId = crawlRunService.start(keyword); // 이미 실행 중이면 여기서 CrawlAlreadyRunningException
        log.info("[run={}] 수집 시작: keyword='{}', maxPages={}", runId, keyword, maxPages);

        List<Project> projects = List.of();
        try {
            projects = projectCollector.collect(keyword, maxPages);
            SaveResult saved = projectSaveService.saveAll(runId, projects);

            CrawlStatus status = decideStatus(saved);
            int pageCount = (projects.size() + PAGE_SIZE - 1) / PAGE_SIZE; // 올림 나눗셈
            crawlRunService.finish(runId, status, pageCount, projects.size(),
                    saved.inserted(), saved.updated(), saved.failed(), saved.firstError());

            long elapsed = System.currentTimeMillis() - start;
            log.info("[run={}] 수집 종료: {} ({}ms)", runId, status, elapsed);
            return new CrawlResult(runId, status, keyword, maxPages, projects.size(),
                    saved.inserted(), saved.updated(), saved.failed(), elapsed);

        } catch (RuntimeException e) {
            // 수집 자체가 실패(프리모아 장애 등)해도 RUNNING으로 남겨두면 안 된다.
            // RUNNING이 남으면 유니크 인덱스 때문에 다음 수집이 영원히 막힌다.
            crawlRunService.finish(runId, CrawlStatus.FAIL, 0, projects.size(), 0, 0, 0, e.getMessage());
            log.error("[run={}] 수집 실패", runId, e);
            throw e;
        }
    }

    private static CrawlStatus decideStatus(SaveResult saved) {
        if (saved.failed() == 0) {
            return CrawlStatus.SUCCESS;
        }
        boolean anySaved = saved.inserted() + saved.updated() > 0;
        return anySaved ? CrawlStatus.PARTIAL : CrawlStatus.FAIL;
    }
}
