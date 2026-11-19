package com.lhk.freemoa.crawler;

import com.lhk.freemoa.crawler.api.FreemoaApiClient;
import com.lhk.freemoa.crawler.api.FreemoaApiException;
import com.lhk.freemoa.crawler.api.dto.FreemoaListResponse;
import com.lhk.freemoa.crawler.convert.ProjectConverter;
import com.lhk.freemoa.crawler.domain.Project;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 여러 페이지를 돌면서 공고를 모은다.
 * "어떻게 가져오는지"(ApiClient)와 "어떻게 변환하는지"(Converter)는 모르고, 순서만 조율한다.
 */
public class ProjectCollector {

    private static final Logger log = LoggerFactory.getLogger(ProjectCollector.class);

    /** 프리모아 목록 API는 한 페이지에 10건씩 준다 */
    private static final int PAGE_SIZE = 10;

    private final FreemoaApiClient apiClient;
    private final ProjectConverter converter;
    private final Duration delayBetweenPages;

    public ProjectCollector(FreemoaApiClient apiClient, ProjectConverter converter, Duration delayBetweenPages) {
        this.apiClient = apiClient;
        this.converter = converter;
        this.delayBetweenPages = delayBetweenPages;
    }

    /**
     * 한 페이지를 받을 때마다 호출되는 콜백.
     * 메서드가 하나뿐인 인터페이스(함수형 인터페이스)라서 람다로 넘길 수 있다.
     *
     * @return true면 다음 페이지 계속, false면 여기서 멈춤 (6단계 증분 수집의 멈춤 조건에 쓴다)
     */
    @FunctionalInterface
    public interface PageHandler {
        boolean onPage(int page, List<Project> projects);
    }

    /**
     * 모두 모아서 한 번에 돌려준다. (2단계 PlainCrawlerMain용, 페이지 수가 적을 때)
     * 내부적으로는 collectEachPage에 "리스트에 담기만 하는 콜백"을 넘기는 것과 같다.
     */
    public List<Project> collect(String keyword, int maxPages) {
        List<Project> result = new ArrayList<>();
        collectEachPage(keyword, maxPages, (page, projects) -> {
            result.addAll(projects);
            return true;
        });
        return result;
    }

    /**
     * 페이지를 받을 때마다 handler에게 넘긴다. (6단계: 전체 수집, 증분 수집)
     * 전부 모은 뒤 저장하면 400페이지 중 마지막에 실패했을 때 앞의 399페이지도 날아간다.
     * 페이지 단위로 넘기면 받은 만큼은 바로 저장할 수 있다.
     *
     * @param maxPages 최대 몇 페이지까지 볼지. 전체가 400페이지가 넘으므로 반드시 상한을 둔다.
     * @return 실제로 받은 페이지 수
     */
    public int collectEachPage(String keyword, int maxPages, PageHandler handler) {
        if (maxPages < 1) {
            throw new IllegalArgumentException("maxPages는 1 이상이어야 함: " + maxPages);
        }

        int page = 1;
        for (; page <= maxPages; page++) {
            if (page > 1) {
                sleep(); // 사이트에 부담 주지 않도록 페이지 사이에 쉰다
            }

            FreemoaListResponse.ProjectSection section = apiClient.fetchList(keyword, page).data().project();
            List<FreemoaListResponse.Item> items = section.list();
            if (items == null || items.isEmpty()) {
                return page - 1; // 더 이상 데이터 없음
            }

            List<Project> projects = convertEach(page, items);
            boolean keepGoing = handler.onPage(page, projects);

            if (!keepGoing || isLastPage(page, items.size(), section.pagination())) {
                return page;
            }
        }
        return maxPages;
    }

    /**
     * 한 건씩 변환하고, 실패한 공고는 로그만 남기고 건너뛴다.
     * items.stream().map(converter::convert).toList()로 한 번에 하면, 1건이 예외를 던지는 순간
     * 그 페이지 나머지 9건도 버려지고 수집 전체가 멈춘다. (실제로 마감일 "0000-00-00" 1건 때문에 전체 수집이 멈췄다)
     * 외부 데이터는 언제든 예상 못 한 값이 올 수 있으니, 1건의 이상이 전체를 막지 않게 한다.
     */
    private List<Project> convertEach(int page, List<FreemoaListResponse.Item> items) {
        List<Project> projects = new ArrayList<>(items.size());
        for (FreemoaListResponse.Item item : items) {
            try {
                projects.add(converter.convert(item));
            } catch (RuntimeException e) {
                log.warn("변환 실패로 건너뜀 page={}, projIdx={}: {}", page, item.projIdx(), e.getMessage());
            }
        }
        return projects;
    }

    /** 받은 건수가 한 페이지 분량보다 적거나, 지금까지 본 건수가 전체 건수에 도달하면 마지막 페이지 */
    private static boolean isLastPage(int page, int itemCount, FreemoaListResponse.Pagination pagination) {
        if (itemCount < PAGE_SIZE) {
            return true;
        }
        return pagination != null && (long) page * PAGE_SIZE >= pagination.totalRows();
    }

    private void sleep() {
        try {
            Thread.sleep(delayBetweenPages.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FreemoaApiException("수집 대기 중 인터럽트", e);
        }
    }
}
