package com.lhk.freemoa.stats;

import java.util.List;

/** 묶음 하나 (예: DB)와 그 안의 기술 순위 */
public record TechGroupStat(String group, String label, List<TechStat> items) {
}
