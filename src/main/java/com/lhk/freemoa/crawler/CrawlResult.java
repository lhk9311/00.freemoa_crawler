package com.lhk.freemoa.crawler;

import com.lhk.freemoa.crawler.run.CrawlStatus;

/** 수집 1회 결과. Controller가 JSON으로 그대로 응답한다 (record -> JSON 자동 변환) */
public record CrawlResult(
        long runId,
        CrawlStatus status,
        String keyword,
        int maxPages,
        int fetched,
        int inserted,
        int updated,
        int failed,
        long elapsedMs
) {
}
