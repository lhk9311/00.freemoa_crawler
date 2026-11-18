package com.lhk.freemoa.crawler;

import com.lhk.freemoa.crawler.domain.Project;
import com.lhk.freemoa.project.ProjectSaveService;
import com.lhk.freemoa.project.SaveResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 수집(HTTP) -> 저장(DB) 흐름을 조율한다.
 *
 * 이 클래스에는 일부러 @Transactional을 붙이지 않았다.
 * 붙이면 프리모아 수집(페이지당 1초 딜레이 x N페이지) 내내 DB 커넥션을 붙잡고 있게 된다.
 * 커넥션 풀(기본 10개)이 금방 바닥나서, 다른 요청들이 커넥션을 기다리며 멈춘다.
 * -> 느린 외부 호출은 트랜잭션 밖에서, DB 작업만 트랜잭션 안(ProjectSaveService)에서.
 */
@Service
public class CrawlService {

    private static final Logger log = LoggerFactory.getLogger(CrawlService.class);

    private final ProjectCollector projectCollector;
    private final ProjectSaveService projectSaveService;
    private final CrawlerProperties properties;

    public CrawlService(ProjectCollector projectCollector,
                        ProjectSaveService projectSaveService,
                        CrawlerProperties properties) {
        this.projectCollector = projectCollector;
        this.projectSaveService = projectSaveService;
        this.properties = properties;
    }

    public CrawlResult crawl(String keyword, int maxPages) {
        if (maxPages < 1 || maxPages > properties.maxPagesLimit()) {
            throw new IllegalArgumentException(
                    "maxPages는 1~" + properties.maxPagesLimit() + " 사이여야 합니다: " + maxPages);
        }

        long start = System.currentTimeMillis();
        log.info("수집 시작: keyword='{}', maxPages={}", keyword, maxPages);

        List<Project> projects = projectCollector.collect(keyword, maxPages); // 트랜잭션 밖 (HTTP)
        SaveResult saved = projectSaveService.saveAll(projects);             // 트랜잭션 안 (DB)

        long elapsed = System.currentTimeMillis() - start;
        log.info("수집 종료: {}건 수집, {}ms", projects.size(), elapsed);

        return new CrawlResult(keyword, maxPages, projects.size(), saved.inserted(), saved.updated(), elapsed);
    }
}
