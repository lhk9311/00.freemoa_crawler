package com.lhk.freemoa.crawler;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * application.yml의 crawler.* 설정값을 타입 안전하게 받는 객체.
 *
 * crawler:
 *   base-url: https://www.freemoa.net   -> baseUrl  (kebab-case -> camelCase 자동 변환)
 *   delay: 1s                           -> Duration (1s, 500ms 같은 표기를 스프링이 변환)
 *   max-pages-limit: 30                 -> int
 *   full-max-pages: 1000                -> int  [6단계] 전체 수집 안전 상한
 *   incremental:                        -> Incremental (중첩 record)  [6단계] 증분 배치
 *     enabled: true
 *     cron: "0 0 9-18 * * MON-FRI"
 *     max-pages: 10
 *     stop-after-idle-pages: 2
 *
 * @Value("${crawler.base-url}")를 여기저기 흩어 쓰는 것보다, 설정을 한 곳에 모으고 타입 검사도 받을 수 있다.
 */
@ConfigurationProperties(prefix = "crawler")
public record CrawlerProperties(
        String baseUrl,
        Duration delay,
        int maxPagesLimit,
        int fullMaxPages,
        Incremental incremental
) {

    /** 들여쓰기 한 단계 아래 설정은 중첩 record로 받는다 */
    public record Incremental(
            boolean enabled,
            String cron,
            int maxPages,
            int stopAfterIdlePages
    ) {
    }
}
