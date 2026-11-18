package com.lhk.freemoa.crawler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhk.freemoa.crawler.api.FreemoaApiClient;
import com.lhk.freemoa.crawler.convert.ProjectConverter;
import com.lhk.freemoa.crawler.convert.SkillParser;
import com.lhk.freemoa.crawler.domain.Project;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * [2단계] 스프링 없이 순수 Java로 수집기를 실행한다.
 *
 * 실행: 이 파일의 main 옆 ▶ 버튼. (DB를 쓰지 않으므로 DB_PASSWORD 없어도 됨)
 * 인자: Run Configuration > Program arguments 에  "java 3"  (검색어, 최대 페이지)
 *
 * main 앞부분의 "조립" 코드를 잘 봐둘 것.
 * 객체가 늘어날수록 누가 누구를 필요로 하는지 직접 순서대로 new 해서 넣어줘야 한다.
 * 3단계에서는 이 조립을 스프링 컨테이너가 대신한다. 그게 IoC/DI다.
 */
public class PlainCrawlerMain {

    public static void main(String[] args) {
        String keyword = args.length > 0 ? args[0] : "java";
        int maxPages = args.length > 1 ? Integer.parseInt(args[1]) : 3;

        // ===== 조립 (Composition Root) : 3단계에서 스프링이 대신 해줄 부분 =====
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        ObjectMapper objectMapper = new ObjectMapper();
        FreemoaApiClient apiClient = new FreemoaApiClient(httpClient, objectMapper, "https://www.freemoa.net");
        SkillParser skillParser = new SkillParser();
        ProjectConverter converter = new ProjectConverter(skillParser);
        ProjectCollector collector = new ProjectCollector(apiClient, converter, Duration.ofSeconds(1));
        // ====================================================================

        System.out.printf("검색어='%s', 최대 %d페이지 수집 시작%n", keyword, maxPages);
        long start = System.currentTimeMillis();
        List<Project> projects = collector.collect(keyword, maxPages);
        System.out.printf("수집 완료: %d건 (%dms)%n%n", projects.size(), System.currentTimeMillis() - start);

        printProjects(projects);
        printBudgetSummary(projects);
        printTopSkills(projects, 10);
    }

    private static void printProjects(List<Project> projects) {
        System.out.println("=== 공고 목록 (최대 5건) ===");
        projects.stream()
                .limit(5)
                .forEach(p -> System.out.printf("[%d] %s | %s | 예산 %s | 마감 %s | 지원 %d명%n",
                        p.projIdx(),
                        p.title(),
                        p.region() == null ? "-" : p.region(),
                        formatBudget(p),
                        p.deadline(),
                        p.applyCount()));
        System.out.println();
    }

    private static void printBudgetSummary(List<Project> projects) {
        // mapToInt -> IntStream: average()가 OptionalDouble을 돌려준다 (데이터가 없을 수도 있으니까)
        OptionalDouble avgMax = projects.stream()
                .filter(Project::hasBudget)
                .mapToInt(Project::costMax)
                .average();

        long withBudget = projects.stream().filter(Project::hasBudget).count();

        System.out.println("=== 예산 ===");
        System.out.printf("예산 공개: %d건 / %d건%n", withBudget, projects.size());
        System.out.println("최대예산 평균: "
                + (avgMax.isPresent() ? String.format("%,.0f만원", avgMax.getAsDouble()) : "데이터 없음"));
        System.out.println();
    }

    private static void printTopSkills(List<Project> projects, int limit) {
        // 1) 공고마다 가진 기술 리스트를 펼쳐서(flatMap) 하나의 기술 스트림으로 만들고
        // 2) 정규화 키로 묶어서(groupingBy) 개수를 센다(counting)
        Map<String, Long> countBySkill = projects.stream()
                .flatMap(p -> p.skills().stream())
                .collect(Collectors.groupingBy(SkillParser::toKey, Collectors.counting()));

        System.out.println("=== 기술스택 TOP " + limit + " ===");
        countBySkill.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.<String, Long>comparingByKey()))
                .limit(limit)
                .forEach(e -> System.out.printf("%-15s %d건%n", e.getKey(), e.getValue()));
    }

    private static String formatBudget(Project p) {
        if (!p.hasBudget()) {
            return "협의";
        }
        Function<Integer, String> won = v -> String.format("%,d만원", v);
        return p.costMin() == null ? "~" + won.apply(p.costMax())
                : won.apply(p.costMin()) + "~" + won.apply(p.costMax());
    }
}
