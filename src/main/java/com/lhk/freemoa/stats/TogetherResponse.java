package com.lhk.freemoa.stats;

import java.util.List;

/**
 * GET /api/stats/together?tech=Java 응답.
 * "Java를 요구한 공고 baseCount건 중에서, 같이 요구된 기술"의 순위. pct의 기준은 baseCount다.
 */
public record TogetherResponse(String tech, String group, int baseCount, List<TechStat> items) {
}
