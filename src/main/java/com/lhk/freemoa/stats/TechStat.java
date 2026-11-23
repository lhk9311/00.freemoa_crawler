package com.lhk.freemoa.stats;

/**
 * 기술 1개의 통계.
 *
 * @param name       대표 이름 (Java, Spring Boot, Oracle ...)
 * @param group      묶음 이름 (언어, 프레임워크, DB, 인프라·서버, 기타)
 * @param cnt        이 기술을 요구한 공고 수 (같은 공고에 "PHP", "php 8.5"가 같이 있어도 1건)
 * @param pct        기준 공고 수 대비 비율 (%)
 * @param avgApply   이 기술 공고의 평균 지원자 수. 계산할 공고가 없으면 null
 * @param avgBudget  이 기술 공고의 평균 최대 예산 (만원). 예산 입력된 공고가 없으면 null
 */
public record TechStat(String name, String group, int cnt, double pct, Double avgApply, Integer avgBudget) {
}
