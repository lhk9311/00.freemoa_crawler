package com.lhk.freemoa.crawler;

import com.lhk.freemoa.crawler.api.FreemoaApiException;
import com.lhk.freemoa.crawler.run.CrawlAlreadyRunningException;
import com.lhk.freemoa.crawler.run.CrawlRunService;
import com.lhk.freemoa.crawler.run.CrawlRunView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 수집 실행 API.
 * 데이터를 변경(저장)하는 요청이라 GET이 아니라 POST로 받는다.
 * (GET은 "조회"용이라 브라우저 미리보기, 크롤러 봇 등이 마음대로 호출해도 안전해야 한다)
 */
@RestController
@RequestMapping("/api/crawl")
public class CrawlController {

    private final CrawlService crawlService;
    private final CrawlJobLauncher crawlJobLauncher;
    private final CrawlRunService crawlRunService;

    public CrawlController(CrawlService crawlService,
                           CrawlJobLauncher crawlJobLauncher,
                           CrawlRunService crawlRunService) {
        this.crawlService = crawlService;
        this.crawlJobLauncher = crawlJobLauncher;
        this.crawlRunService = crawlRunService;
    }

    /** 예) POST /api/crawl?keyword=java&maxPages=3 */
    @PostMapping
    public CrawlResult crawl(@RequestParam(defaultValue = "") String keyword,
                             @RequestParam(defaultValue = "1") int maxPages) {
        return crawlService.crawl(keyword, maxPages);
    }

    /**
     * [6단계] 전체 수집. 예) POST /api/crawl/all  (검색어 없으면 전체 공고)
     * 수 분 걸리므로 결과를 기다리지 않고 202 Accepted + runId를 바로 돌려준다.
     * 200 OK는 "처리 끝", 202 Accepted는 "접수만 됨, 처리는 나중에"라는 뜻이다.
     */
    @PostMapping("/all")
    public ResponseEntity<Map<String, Object>> crawlAll(@RequestParam(defaultValue = "") String keyword) {
        long runId = crawlJobLauncher.submitFull(keyword);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "runId", runId,
                "status", "RUNNING",
                "check", "/api/crawl/runs/" + runId));
    }

    /**
     * [6단계] 증분 수집을 지금 한 번 실행 (배치를 기다리지 않고 테스트할 때).
     * 보통 1~2페이지라 금방 끝나서 결과를 기다려서 돌려준다.
     */
    @PostMapping("/incremental")
    public CrawlResult crawlIncremental() {
        return crawlService.crawlIncremental();
    }

    /** [6단계] 진행 상황 조회. 예) GET /api/crawl/runs/12 */
    @GetMapping("/runs/{runId}")
    public ResponseEntity<CrawlRunView> run(@PathVariable long runId) {
        CrawlRunView view = crawlRunService.find(runId);
        return view == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(view);
    }

    /** 잘못된 요청값 -> 400 Bad Request */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    /** 이미 수집 중 -> 409 Conflict (요청 자체는 정상이지만 현재 서버 상태와 충돌) */
    @ExceptionHandler(CrawlAlreadyRunningException.class)
    public ResponseEntity<Map<String, String>> handleConflict(CrawlAlreadyRunningException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    /** 프리모아(외부 서버) 쪽 문제 -> 502 Bad Gateway. 우리 서버 잘못(500)과 구분한다 */
    @ExceptionHandler(FreemoaApiException.class)
    public ResponseEntity<Map<String, String>> handleUpstreamError(FreemoaApiException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
    }
}
