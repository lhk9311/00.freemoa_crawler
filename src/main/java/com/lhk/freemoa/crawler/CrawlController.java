package com.lhk.freemoa.crawler;

import com.lhk.freemoa.crawler.api.FreemoaApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
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

    public CrawlController(CrawlService crawlService) {
        this.crawlService = crawlService;
    }

    /** 예) POST /api/crawl?keyword=java&maxPages=3 */
    @PostMapping
    public CrawlResult crawl(@RequestParam(defaultValue = "") String keyword,
                             @RequestParam(defaultValue = "1") int maxPages) {
        return crawlService.crawl(keyword, maxPages);
    }

    /** 잘못된 요청값 -> 400 Bad Request */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    /** 프리모아(외부 서버) 쪽 문제 -> 502 Bad Gateway. 우리 서버 잘못(500)과 구분한다 */
    @ExceptionHandler(FreemoaApiException.class)
    public ResponseEntity<Map<String, String>> handleUpstreamError(FreemoaApiException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
    }
}
