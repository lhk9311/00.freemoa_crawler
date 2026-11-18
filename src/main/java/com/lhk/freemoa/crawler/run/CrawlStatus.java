package com.lhk.freemoa.crawler.run;

/**
 * TB_CRAWL_RUN.STATUS 값.
 * 문자열 "SUCCESS"를 코드 곳곳에 직접 쓰면 오타("SUCESS")가 나도 컴파일러가 못 잡는다.
 * enum으로 두면 정해진 값만 쓸 수 있다. MyBatis는 enum을 이름 그대로("SUCCESS") 바인딩한다.
 */
public enum CrawlStatus {
    RUNNING,
    SUCCESS,
    PARTIAL,
    FAIL
}
