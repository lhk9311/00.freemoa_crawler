package com.lhk.freemoa.stats;

import com.lhk.freemoa.crawler.convert.SkillParser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * "공고 x 기술 태그" 행 목록으로 기술 통계를 계산한다. DB도 스프링도 안 쓰는 순수 자바라서 단위 테스트가 쉽다.
 *
 * 왜 SQL GROUP BY가 아니라 자바에서 묶나
 *  같은 공고에 "PHP", "php 8.5"가 같이 달려 있으면 SQL로 SKILL_KEY별 COUNT를 하면 PHP가 2번 세어진다.
 *  "버전만 다른 태그는 같은 기술"이라는 규칙(BackendTechCatalog)이 자바에 있으니,
 *  공고별로 먼저 묶어서 기술 이름을 Set처럼 중복 없이 만든 다음 센다.
 *
 * 흐름
 *   SQL 결과 (공고 x 태그, 수만 줄)
 *     -> groupByProject(): 공고 1건 = ProjectTechs 1개  {기술이름 -> 묶음}
 *     -> 기술 이름별 Agg에 공고를 하나씩 더함 (공고 수, 지원자 합, 예산 합)
 *     -> TechStat 목록으로 변환, 공고 수 많은 순 정렬
 */
public final class TechStatsCalculator {

    /** 분류표에 없는 태그 중 한글이 들어간 것("제안요청", "유지보수")은 기술이 아니라고 보고 뺀다 */
    private static final Pattern HANGUL = Pattern.compile("[가-힣]");

    /** "백엔드 공고"로 볼 묶음. 인프라만 요구한 공고(예: AWS, Linux만)는 넣지 않는다 */
    private static final Set<TechGroup> BACKEND_GROUPS =
            EnumSet.of(TechGroup.LANGUAGE, TechGroup.FRAMEWORK, TechGroup.DATABASE);

    private static final Comparator<Agg> MOST_FIRST =
            Comparator.comparingInt((Agg a) -> a.cnt).reversed().thenComparing(a -> a.name);

    private TechStatsCalculator() {
    }

    /** 공고 1건: 지원자 수, 예산, 그리고 이 공고가 요구한 기술들 {이름 -> 묶음} */
    record ProjectTechs(Integer applyCount, Integer costMax, Map<String, TechGroup> techs) {
    }

    /** 묶음별 상위 top개 (OTHER 제외). pct 기준은 전체 공고 수 */
    public static List<TechGroupStat> groupStats(List<ProjectSkillRow> rows, int totalProjects, int top) {
        Map<String, Agg> aggs = aggregate(groupByProject(rows).values(), null);

        List<TechGroupStat> result = new ArrayList<>();
        for (TechGroup group : TechGroup.values()) {
            if (group == TechGroup.OTHER) {
                continue;
            }
            List<TechStat> items = aggs.values().stream()
                    .filter(a -> a.group == group)
                    .sorted(MOST_FIRST)
                    .limit(top)
                    .map(a -> a.toStat(totalProjects))
                    .toList();
            result.add(new TechGroupStat(group.name(), group.label(), items));
        }
        return result;
    }

    /** 언어, 프레임워크, DB 중 하나라도 요구한 공고 수 */
    public static int countBackendProjects(List<ProjectSkillRow> rows) {
        int count = 0;
        for (ProjectTechs p : groupByProject(rows).values()) {
            boolean backend = p.techs().values().stream().anyMatch(BACKEND_GROUPS::contains);
            if (backend) {
                count++;
            }
        }
        return count;
    }

    /**
     * tech를 요구한 공고들만 골라서, 거기서 같이 요구된 기술 순위 (OTHER 포함). pct 기준은 그 공고 수.
     *
     * @throws IllegalArgumentException 분류표에 없는 기술 이름
     */
    public static TogetherResponse together(List<ProjectSkillRow> rows, String tech, int top) {
        Tech base = BackendTechCatalog.find(tech)
                .orElseThrow(() -> new IllegalArgumentException("분류표에 없는 기술입니다: " + tech));

        List<ProjectTechs> baseProjects = groupByProject(rows).values().stream()
                .filter(p -> p.techs().containsKey(base.name()))
                .toList();

        // 기준 기술 자신은 빼고 센다 (Java 공고에서 Java가 100%인 건 의미가 없으니까)
        Map<String, Agg> aggs = aggregate(baseProjects, base.name());
        List<TechStat> items = baseProjects.isEmpty() ? List.of() : aggs.values().stream()
                .sorted(MOST_FIRST)
                .limit(top)
                .map(a -> a.toStat(baseProjects.size()))
                .toList();

        return new TogetherResponse(base.name(), base.group().label(), baseProjects.size(), items);
    }

    /**
     * 공고 x 태그 행들을 공고 1건씩으로 묶는다.
     * 같은 공고 안에서 "PHP", "php 8.5"는 같은 이름(PHP)이라 Map에 한 번만 들어간다.
     */
    static Map<Long, ProjectTechs> groupByProject(List<ProjectSkillRow> rows) {
        Map<Long, ProjectTechs> projects = new LinkedHashMap<>();
        // 분류표에 없는 태그는 버전을 뗀 키로 묶고, 처음 본 표기를 이름으로 쓴다 ("React", "react 18" -> "React")
        Map<String, String> otherNames = new HashMap<>();

        for (ProjectSkillRow row : rows) {
            ProjectTechs project = projects.computeIfAbsent(row.getProjIdx(),
                    id -> new ProjectTechs(row.getApplyCount(), row.getCostMax(), new LinkedHashMap<>()));

            Optional<Tech> tech = BackendTechCatalog.find(row.getSkillKey());
            if (tech.isPresent()) {
                project.techs().put(tech.get().name(), tech.get().group());
            } else if (isTechLike(row.getSkillName())) {
                String key = BackendTechCatalog.stripVersion(SkillParser.toKey(row.getSkillName()));
                String name = otherNames.computeIfAbsent(key, k -> row.getSkillName().trim());
                project.techs().putIfAbsent(name, TechGroup.OTHER);
            }
        }
        return projects;
    }

    /** 기술 이름별로 공고 수, 지원자 합계, 예산 합계를 모은다. exclude 이름은 건너뜀 */
    private static Map<String, Agg> aggregate(Iterable<ProjectTechs> projects, String exclude) {
        Map<String, Agg> aggs = new HashMap<>();
        for (ProjectTechs project : projects) {
            for (Map.Entry<String, TechGroup> e : project.techs().entrySet()) {
                if (e.getKey().equals(exclude)) {
                    continue;
                }
                aggs.computeIfAbsent(e.getKey(), name -> new Agg(name, e.getValue())).add(project);
            }
        }
        return aggs;
    }

    private static boolean isTechLike(String skillName) {
        return skillName != null && !skillName.isBlank() && !HANGUL.matcher(skillName).find();
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    /** 기술 1개의 누적값. 평균은 마지막에 합계 / 개수로 계산한다 (null인 값은 개수에서도 뺀다) */
    private static final class Agg {
        final String name;
        final TechGroup group;
        int cnt;
        long applySum;
        int applyN;
        long budgetSum;
        int budgetN;

        Agg(String name, TechGroup group) {
            this.name = name;
            this.group = group;
        }

        void add(ProjectTechs p) {
            cnt++;
            if (p.applyCount() != null) {
                applySum += p.applyCount();
                applyN++;
            }
            if (p.costMax() != null) {
                budgetSum += p.costMax();
                budgetN++;
            }
        }

        TechStat toStat(int base) {
            double pct = base == 0 ? 0 : round1(cnt * 100.0 / base);
            Double avgApply = applyN == 0 ? null : round1((double) applySum / applyN);
            Integer avgBudget = budgetN == 0 ? null : (int) Math.round((double) budgetSum / budgetN);
            return new TechStat(name, group.label(), cnt, pct, avgApply, avgBudget);
        }
    }
}
