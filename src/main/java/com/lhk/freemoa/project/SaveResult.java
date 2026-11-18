package com.lhk.freemoa.project;

/**
 * 저장 결과.
 *
 * @param firstError 실패가 있었다면 첫 번째 실패 원인 (이력 테이블 ERROR_MSG에 남김)
 */
public record SaveResult(int inserted, int updated, int failed, String firstError) {

    public static SaveResult empty() {
        return new SaveResult(0, 0, 0, null);
    }
}
