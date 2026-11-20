package com.lhk.freemoa.crawler;

import com.lhk.freemoa.crawler.domain.Project;
import com.lhk.freemoa.crawler.run.CrawlRunService;
import com.lhk.freemoa.crawler.run.CrawlRunType;
import com.lhk.freemoa.crawler.run.CrawlStatus;
import com.lhk.freemoa.project.ProjectSaveService;
import com.lhk.freemoa.project.SaveResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 증분 수집의 멈춤 조건 단위 테스트.
 *
 * 무엇을 테스트하나
 *  - CrawlService.execute()의 "새 공고(INSERT)가 0건인 페이지가 N번 연속이면 멈춘다" 규칙
 *
 * 어떻게 (Mock = 가짜 객체)
 *  - 진짜 CrawlService를 쓰고, 그 주변 3개(수집기, 저장, 이력)만 Mockito로 가짜를 만든다.
 *  - 가짜 수집기는 프리모아에 요청하지 않고, 페이지마다 공고 10개를 만들어 CrawlService의 콜백에 넘긴다.
 *  - 가짜 저장은 DB에 쓰지 않고, 시나리오대로 "이번 페이지 신규 N건"을 돌려준다.
 *  -> 네트워크와 DB 없이 0.1초 만에, 원하는 상황(신규 3, 0, 0 ...)을 마음대로 만들어 검증할 수 있다.
 *
 * 구조 (given - when - then)
 *  - given: 페이지별 신규 건수 시나리오
 *  - when : 수집 실행
 *  - then : 몇 페이지에서 멈췄나, 저장은 몇 번 불렸나, 이력에 뭐가 남았나
 */
@ExtendWith(MockitoExtension.class)
class CrawlServiceStopConditionTest {

    private static final int PAGE_SIZE = 10;
    private static final long RUN_ID = 1L;

    @Mock
    private ProjectCollector projectCollector;
    @Mock
    private ProjectSaveService projectSaveService;
    @Mock
    private CrawlRunService crawlRunService;

    private CrawlService crawlService;

    /** 가짜 수집기가 실제로 넘겨준 페이지 번호들 (멈춘 위치 확인용) */
    private final List<Integer> visitedPages = new ArrayList<>();

    @BeforeEach
    void setUp() {
        CrawlerProperties properties = new CrawlerProperties(
                "https://example.test", Duration.ZERO, 30, 1000,
                new CrawlerProperties.Incremental(true, "0 0 * * * *", 10, 2));
        crawlService = new CrawlService(projectCollector, projectSaveService, crawlRunService, properties);
    }

    @Test
    @DisplayName("신규 0건 페이지가 2번 연속이면 그 페이지에서 멈춘다")
    void stopsAfterTwoIdlePages() {
        // given: 1페이지 신규 3, 2페이지 0, 3페이지 0, 4페이지 7(여기까지 오면 안 됨)
        givenPages(3, 0, 0, 7);
        when(crawlRunService.start("", CrawlRunType.INCREMENTAL)).thenReturn(RUN_ID);

        // when
        CrawlResult result = crawlService.crawlIncremental();

        // then
        assertThat(visitedPages).containsExactly(1, 2, 3);
        assertThat(result.inserted()).isEqualTo(3);
        assertThat(result.status()).isEqualTo(CrawlStatus.SUCCESS);
        verify(projectSaveService, times(3)).saveAll(eq(RUN_ID), any());
        verify(crawlRunService).finish(eq(RUN_ID), eq(CrawlStatus.SUCCESS), eq(3),
                eq(30), eq(3), eq(27), eq(0), any());
    }

    @Test
    @DisplayName("중간에 신규가 나오면 연속 횟수가 0으로 초기화된다")
    void idleCountResetsWhenNewProjectAppears() {
        // given: 0 -> 4(초기화) -> 0 -> 2(초기화) -> 0 -> 0(멈춤) -> 9
        givenPages(0, 4, 0, 2, 0, 0, 9);

        // when
        CrawlResult result = crawlService.execute(RUN_ID, "", 10, 2);

        // then
        assertThat(visitedPages).containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(result.inserted()).isEqualTo(6);
    }

    @Test
    @DisplayName("첫 페이지부터 신규가 없으면 2페이지에서 멈춘다")
    void stopsAtSecondPageWhenNothingNew() {
        givenPages(0, 0, 0, 0);

        crawlService.execute(RUN_ID, "", 10, 2);

        assertThat(visitedPages).containsExactly(1, 2);
    }

    @Test
    @DisplayName("NO_STOP(전체 수집)은 신규가 없어도 maxPages까지 간다")
    void noStopGoesToMaxPages() {
        givenPages(0, 0, 0, 0);

        CrawlResult result = crawlService.execute(RUN_ID, "", 4, CrawlService.NO_STOP);

        assertThat(visitedPages).containsExactly(1, 2, 3, 4);
        assertThat(result.updated()).isEqualTo(40);
    }

    @Test
    @DisplayName("신규가 계속 나와도 maxPages 상한에서 멈춘다")
    void maxPagesIsUpperBound() {
        givenPages(5, 5, 5, 5, 5, 5);

        crawlService.execute(RUN_ID, "", 3, 2);

        assertThat(visitedPages).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("stopAfterIdlePages=1이면 신규 0건 페이지 1번 만에 멈춘다 (경계값)")
    void stopAfterOneIdlePage() {
        givenPages(2, 0, 3);

        crawlService.execute(RUN_ID, "", 10, 1);

        assertThat(visitedPages).containsExactly(1, 2);
    }

    // ---------------------------------------------------------------------------------

    /**
     * 페이지별 신규 건수 시나리오를 가짜 수집기 + 가짜 저장에 심는다.
     * 예) givenPages(3, 0, 0) : 1페이지 신규 3(갱신 7), 2페이지 신규 0(갱신 10), 3페이지 신규 0(갱신 10)
     */
    private void givenPages(int... insertedPerPage) {
        // 가짜 수집기: 진짜 collectEachPage처럼 페이지마다 handler를 부르고, false를 받으면 멈춘다.
        doAnswer(invocation -> {
            int maxPages = invocation.getArgument(1);
            ProjectCollector.PageHandler handler = invocation.getArgument(2);
            int lastPage = Math.min(maxPages, insertedPerPage.length);
            for (int page = 1; page <= lastPage; page++) {
                visitedPages.add(page);
                if (!handler.onPage(page, projectsOfPage(page))) {
                    return page;
                }
            }
            return lastPage;
        }).when(projectCollector).collectEachPage(anyString(), anyInt(), any());

        // 가짜 저장: 몇 번째 호출인지(=몇 페이지인지)에 따라 시나리오의 신규 건수를 돌려준다.
        int[] call = {0}; // 람다 안에서 바꿔야 해서 배열로 (effectively final 규칙)
        when(projectSaveService.saveAll(anyLong(), any())).thenAnswer(invocation -> {
            List<Project> projects = invocation.getArgument(1);
            int inserted = insertedPerPage[call[0]++];
            return new SaveResult(inserted, projects.size() - inserted, 0, null);
        });
    }

    /** 페이지마다 겹치지 않는 공고번호 10개 (2페이지면 201~210) */
    private static List<Project> projectsOfPage(int page) {
        return IntStream.rangeClosed(1, PAGE_SIZE)
                .mapToObj(i -> project(page * 100L + i))
                .toList();
    }

    private static Project project(long projIdx) {
        return new Project(projIdx, "공고 " + projIdx, null, null, null, null, null,
                null, null, null, 0, null, List.of(), null, null, null);
    }
}
