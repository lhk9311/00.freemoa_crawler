package com.lhk.freemoa.stats;

import java.util.List;

/** GET /api/stats 응답. 화면 하나에 필요한 통계를 한 번에 돌려준다 (요청 4번 -> 1번) */
public record StatsResponse(
        int totalProjects,
        List<StatRow> skills,
        List<StatRow> regions,
        List<StatRow> budgets,
        List<StatRow> monthly
) {
}
