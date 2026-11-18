package com.lhk.freemoa.project;

/** 저장 결과: 신규 INSERT 건수, 기존 UPDATE 건수 */
public record SaveResult(int inserted, int updated) {

    public static SaveResult empty() {
        return new SaveResult(0, 0);
    }
}
