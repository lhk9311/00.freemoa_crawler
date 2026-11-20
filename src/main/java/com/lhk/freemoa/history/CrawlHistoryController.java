package com.lhk.freemoa.history;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * [JPA] 수집 이력 조회. 예) GET /api/crawl/history?runType=INCREMENTAL&size=10
 * 같은 테이블의 "1건 조회"(GET /api/crawl/runs/{runId})는 MyBatis로 되어 있어서 둘을 나란히 비교할 수 있다.
 */
@RestController
@RequestMapping("/api/crawl/history")
public class CrawlHistoryController {

    private final CrawlHistoryService crawlHistoryService;

    public CrawlHistoryController(CrawlHistoryService crawlHistoryService) {
        this.crawlHistoryService = crawlHistoryService;
    }

    @GetMapping
    public Map<String, Object> history(@RequestParam(defaultValue = "") String runType,
                                       @RequestParam(defaultValue = "20") int size) {
        return crawlHistoryService.history(runType, size);
    }
}
