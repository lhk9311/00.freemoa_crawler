package com.lhk.freemoa.crawler.run;

import java.time.LocalDateTime;

/**
 * [6단계] 수집 1건의 현재 상태. GET /api/crawl/runs/{runId} 응답.
 * 필드 순서가 CrawlRunMapper.xml의 constructor/arg 순서와 같아야 한다.
 */
public record CrawlRunView(
        long runId,
        String runType,
        String status,
        String keyword,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        int pageCount,
        int fetchedCnt,
        int insertedCnt,
        int updatedCnt,
        int failedCnt,
        String errorMsg
) {
}
