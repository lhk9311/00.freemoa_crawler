package com.lhk.freemoa.stats;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TechStatsCalculatorTest {

    /**
     * 공고 3건 (전체 공고는 4건이라고 가정, 1건은 태그 없음)
     *  1번: Java, Spring Boot, Oracle            지원 10명, 예산 3000
     *  2번: PHP, php 8.5, MySQL, 제안요청         지원 4명,  예산 없음
     *  3번: java 17, Oracle 19c, React           지원 6명,  예산 1000
     */
    private final List<ProjectSkillRow> rows = List.of(
            row(1, "Java", 10, 3000), row(1, "Spring Boot", 10, 3000), row(1, "Oracle", 10, 3000),
            row(2, "PHP", 4, null), row(2, "php 8.5", 4, null), row(2, "MySQL", 4, null), row(2, "제안요청", 4, null),
            row(3, "java 17", 6, 1000), row(3, "Oracle 19c", 6, 1000), row(3, "React", 6, 1000));

    @Test
    @DisplayName("같은 공고의 PHP, php 8.5는 1건으로 센다")
    void groupStats_sameProjectCountedOnce() {
        TechGroupStat language = find(TechStatsCalculator.groupStats(rows, 4, 10), "LANGUAGE");

        assertThat(language.items()).extracting(TechStat::name).containsExactly("Java", "PHP");
        assertThat(language.items().get(1).cnt()).isEqualTo(1);
    }

    @Test
    @DisplayName("비율은 전체 공고 기준, 평균은 값이 있는 공고만으로")
    void groupStats_pctAndAverages() {
        TechStat java = find(TechStatsCalculator.groupStats(rows, 4, 10), "LANGUAGE").items().get(0);

        assertThat(java.cnt()).isEqualTo(2);           // 1번, 3번
        assertThat(java.pct()).isEqualTo(50.0);        // 2 / 4
        assertThat(java.avgApply()).isEqualTo(8.0);    // (10 + 6) / 2
        assertThat(java.avgBudget()).isEqualTo(2000);  // (3000 + 1000) / 2

        TechStat php = find(TechStatsCalculator.groupStats(rows, 4, 10), "LANGUAGE").items().get(1);
        assertThat(php.avgBudget()).isNull();          // 예산 입력된 공고가 없음
    }

    @Test
    @DisplayName("백엔드 공고 = 언어, 프레임워크, DB 중 하나라도 있는 공고")
    void countBackendProjects() {
        assertThat(TechStatsCalculator.countBackendProjects(rows)).isEqualTo(3);
    }

    @Test
    @DisplayName("같이 요구되는 기술: 기준 기술은 빼고, 기준 공고 수 대비 비율. 한글 태그는 뺀다")
    void together() {
        TogetherResponse r = TechStatsCalculator.together(rows, "java", 15);

        assertThat(r.tech()).isEqualTo("Java");
        assertThat(r.baseCount()).isEqualTo(2);
        assertThat(r.items()).extracting(TechStat::name).containsExactly("Oracle", "React", "Spring Boot");
        assertThat(r.items().get(0).pct()).isEqualTo(100.0);   // Java 공고 2건 모두 Oracle
        assertThat(r.items().get(1).group()).isEqualTo("기타");
    }

    @Test
    @DisplayName("분류표에 없는 기술로 물어보면 IllegalArgumentException (컨트롤러가 400으로 바꿈)")
    void together_unknownTech() {
        assertThatThrownBy(() -> TechStatsCalculator.together(rows, "React", 15))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ProjectSkillRow row(long projIdx, String skillName, Integer applyCount, Integer costMax) {
        String key = skillName.replaceAll("\\s+", "").toUpperCase();
        return new ProjectSkillRow(projIdx, key, skillName, applyCount, costMax);
    }

    private static TechGroupStat find(List<TechGroupStat> groups, String group) {
        return groups.stream().filter(g -> g.group().equals(group)).findFirst().orElseThrow();
    }
}
