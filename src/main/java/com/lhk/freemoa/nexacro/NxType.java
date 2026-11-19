package com.lhk.freemoa.nexacro;

/**
 * 넥사크로 Dataset 컬럼 타입.
 * 넥사크로는 DATE를 yyyyMMdd, DATETIME을 yyyyMMddHHmmssSSS 문자열로 주고받는다.
 */
public enum NxType {
    STRING,
    INT,
    BIGDECIMAL,
    DATE,
    DATETIME
}
