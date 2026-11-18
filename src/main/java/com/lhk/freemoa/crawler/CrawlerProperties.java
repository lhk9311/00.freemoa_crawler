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
 *
 * @Value("${crawler.base-url}")를 여기저기 흩어 쓰는 것보다, 설정을 한 곳에 모으고 타입 검사도 받을 수 있다.
 */
@ConfigurationProperties(prefix = "crawler")
public record CrawlerProperties(
        String baseUrl,
        Duration delay,
        int maxPagesLimit
) {
}
