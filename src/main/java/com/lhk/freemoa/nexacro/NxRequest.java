package com.lhk.freemoa.nexacro;

import java.util.Collections;
import java.util.Map;

/**
 * 넥사크로 transaction()이 보낸 요청에서 읽어낸 값.
 * transaction의 5번째 인자(strArgument) "keyword=java maxPages=2" 가 여기 parameters로 들어온다.
 *
 * (지금 화면은 조회 조건만 보내므로 Parameters만 읽는다. 저장 화면처럼 Dataset을 보내는 경우는 확장 포인트)
 */
public class NxRequest {

    private final Map<String, String> parameters;

    public NxRequest(Map<String, String> parameters) {
        this.parameters = Collections.unmodifiableMap(parameters);
    }

    public static NxRequest empty() {
        return new NxRequest(Map.of());
    }

    /** 값이 없거나 공백이면 null */
    public String getString(String id) {
        String value = parameters.get(id);
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    public String getString(String id, String defaultValue) {
        String value = getString(id);
        return value == null ? defaultValue : value;
    }

    /** 숫자가 아니면 IllegalArgumentException -> 화면에 "잘못된 값" 메시지로 내려간다 */
    public Integer getInteger(String id) {
        String value = getString(id);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(id + "는 숫자여야 합니다: " + value);
        }
    }

    public int getInt(String id, int defaultValue) {
        Integer value = getInteger(id);
        return value == null ? defaultValue : value;
    }

    /** 넥사크로 CheckBox 값은 보통 "1"/"0" 또는 "true"/"false" */
    public boolean getBoolean(String id) {
        String value = getString(id);
        return "1".equals(value) || "true".equalsIgnoreCase(value) || "Y".equalsIgnoreCase(value);
    }

    public Map<String, String> parameters() {
        return parameters;
    }
}
