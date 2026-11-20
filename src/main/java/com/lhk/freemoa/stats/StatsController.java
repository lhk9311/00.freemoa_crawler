package com.lhk.freemoa.stats;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 통계 API. 화면은 src/main/resources/static/stats.html (서버 실행 후 http://localhost:8080/stats.html)
 * 같은 서버(8080)에서 나가는 페이지라 CORS 설정이 필요 없다.
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping
    public StatsResponse stats() {
        return statsService.getStats();
    }
}
