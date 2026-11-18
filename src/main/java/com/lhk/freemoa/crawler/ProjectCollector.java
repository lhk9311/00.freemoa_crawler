package com.lhk.freemoa.crawler;

import com.lhk.freemoa.crawler.api.FreemoaApiClient;
import com.lhk.freemoa.crawler.api.FreemoaApiException;
import com.lhk.freemoa.crawler.api.dto.FreemoaListResponse;
import com.lhk.freemoa.crawler.convert.ProjectConverter;
import com.lhk.freemoa.crawler.domain.Project;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 여러 페이지를 돌면서 공고를 모은다.
 * "어떻게 가져오는지"(ApiClient)와 "어떻게 변환하는지"(Converter)는 모르고, 순서만 조율한다.
 */
public class ProjectCollector {

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
     * @param maxPages 최대 몇 페이지까지 가져올지. 전체가 400페이지가 넘으므로 반드시 상한을 둔다.
     */
    public List<Project> collect(String keyword, int maxPages) {
        if (maxPages < 1) {
            throw new IllegalArgumentException("maxPages는 1 이상이어야 함: " + maxPages);
        }

        List<Project> result = new ArrayList<>();
        for (int page = 1; page <= maxPages; page++) {
            if (page > 1) {
                sleep(); // 사이트에 부담 주지 않도록 페이지 사이에 쉰다
            }

            FreemoaListResponse.ProjectSection section = apiClient.fetchList(keyword, page).data().project();
            List<FreemoaListResponse.Item> items = section.list();
            if (items == null || items.isEmpty()) {
                break; // 더 이상 데이터 없음
            }

            for (FreemoaListResponse.Item item : items) {
                result.add(converter.convert(item));
            }

            if (isLastPage(page, items.size(), section.pagination())) {
                break;
            }
        }
        return result;
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
