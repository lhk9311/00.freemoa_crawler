package com.lhk.freemoa.nexacro;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * 넥사크로 응답을 ResponseEntity로 감싼다.
 * 넥사크로는 HTTP 상태코드가 아니라 ErrorCode 파라미터로 성공/실패를 판단하므로, 실패도 200으로 내려준다.
 */
public final class NxResponses {

    private static final MediaType XML_UTF8 = new MediaType("text", "xml", java.nio.charset.StandardCharsets.UTF_8);

    private NxResponses() {
    }

    public static ResponseEntity<String> ok(NxDataset... datasets) {
        return ResponseEntity.ok().contentType(XML_UTF8).body(NxXmlWriter.success(List.of(datasets)));
    }

    public static ResponseEntity<String> error(String message) {
        return ResponseEntity.ok().contentType(XML_UTF8).body(NxXmlWriter.error(message));
    }
}
