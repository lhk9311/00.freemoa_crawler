package com.lhk.freemoa.crawler.run;

/** 이미 수집이 진행 중일 때 새 수집 요청이 들어오면 발생 -> 409 Conflict */
public class CrawlAlreadyRunningException extends RuntimeException {

    public CrawlAlreadyRunningException(String message, Throwable cause) {
        super(message, cause);
    }
}
