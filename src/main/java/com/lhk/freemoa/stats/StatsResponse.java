package com.lhk.freemoa.stats;

import java.util.List;

/**
 * GET /api/stats 응답. 화면 하나에 필요한 통계를 한 번에 돌려준다.
 *
 * @param totalProjects   전체 공고 수
 * @param backendProjects 언어, 프레임워크, DB 중 하나라도 요구한 공고 수
 * @param groups          언어 / 프레임워크 / DB / 인프라·서버 순위
 * @param regions         지역별 공고 수와 비율
 */
public record StatsResponse(
        int totalProjects,
        int backendProjects,
        List<TechGroupStat> groups,
        List<StatRow> regions
) {
}
