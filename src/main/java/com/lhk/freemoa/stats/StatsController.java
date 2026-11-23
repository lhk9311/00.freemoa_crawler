package com.lhk.freemoa.stats;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

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

    /** 백엔드 기술 묶음별 순위 + 지역 */
    @GetMapping
    public StatsResponse stats() {
        return statsService.getStats();
    }

    /** GET /api/stats/together?tech=Java : Java 공고에서 같이 요구된 기술 */
    @GetMapping("/together")
    public TogetherResponse together(@RequestParam(defaultValue = "Java") String tech) {
        return statsService.together(tech);
    }

    /** 분류표에 없는 기술 이름 -> 400 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
}
