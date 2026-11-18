package com.lhk.freemoa.crawler.api;

/**
 * 프리모아 API 호출 실패를 나타내는 예외.
 *
 * RuntimeException(언체크 예외)을 상속했다.
 * - IOException 같은 체크 예외는 호출하는 모든 메서드가 throws를 달거나 try-catch를 해야 한다.
 * - 수집기를 쓰는 쪽은 "실패했다"는 사실만 알면 되고, 네트워크 세부 예외까지 알 필요는 없다.
 * - 그래서 원인 예외(cause)는 안에 담아두고, 바깥에는 이 예외 하나만 보이게 감싼다 (예외 전환).
 * - 4단계에서 볼 내용: 스프링 @Transactional은 기본적으로 언체크 예외에서만 롤백한다.
 */
public class FreemoaApiException extends RuntimeException {

    public FreemoaApiException(String message) {
        super(message);
    }

    public FreemoaApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
