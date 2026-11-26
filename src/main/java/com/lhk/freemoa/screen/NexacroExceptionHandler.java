package com.lhk.freemoa.screen;

import com.lhk.freemoa.crawler.api.FreemoaApiException;
import com.lhk.freemoa.crawler.run.CrawlAlreadyRunningException;
import com.lhk.freemoa.nexacro.NxResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 넥사크로 화면 API(/nx/**)에서 난 예외를 넥사크로 형식(ErrorCode = -1)으로 바꿔준다.
 * 화면 스크립트의 콜백 함수에서 errorCode < 0 이면 errorMsg를 alert로 띄우면 된다.
 *
 * assignableTypes로 범위를 화면 컨트롤러(/nx/**)로 제한했다.
 * 범위를 안 주면 /api/** (JSON) 에러까지 XML로 바뀌어버린다.
 */
@RestControllerAdvice(assignableTypes = {ProjectScreenController.class, StatsScreenController.class})
public class NexacroExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(NexacroExceptionHandler.class);

    /** 사용자가 고칠 수 있는 문제: 메시지를 그대로 보여준다 */
    @ExceptionHandler({IllegalArgumentException.class, CrawlAlreadyRunningException.class, FreemoaApiException.class})
    public ResponseEntity<String> handleKnown(RuntimeException e) {
        log.warn("넥사크로 요청 실패: {}", e.getMessage());
        return NxResponses.error(e.getMessage());
    }

    /**
     * 예상하지 못한 에러: 상세 내용(SQL, 스택트레이스)은 로그에만 남기고 화면에는 일반 메시지만.
     * ORA-00942 같은 DB 에러 메시지를 그대로 내보내면 테이블 구조 같은 내부 정보가 노출된다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleUnknown(Exception e) {
        log.error("넥사크로 요청 처리 중 오류", e);
        return NxResponses.error("처리 중 오류가 발생했습니다. 관리자에게 문의하세요.");
    }
}
