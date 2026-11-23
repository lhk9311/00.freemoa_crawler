package com.lhk.freemoa.stats;

/**
 * 백엔드 개발자 기준 기술 묶음. 통계 화면의 카드 4개가 이 순서대로 나온다.
 * OTHER는 분류 목록(BackendTechCatalog)에 없는 기술이다. "같이 요구되는 기술"에서만 보인다.
 */
public enum TechGroup {
    LANGUAGE("언어"),
    FRAMEWORK("프레임워크"),
    DATABASE("DB"),
    INFRA("인프라·서버"),
    OTHER("기타");

    private final String label;

    TechGroup(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
