package com.lhk.freemoa.history;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class CrawlHistoryService {

    private static final int MAX_SIZE = 100;

    private final CrawlRunRepository crawlRunRepository;

    public CrawlHistoryService(CrawlRunRepository crawlRunRepository) {
        this.crawlRunRepository = crawlRunRepository;
    }

    /**
     * 수집 이력 목록 + 종류별 요약.
     * @param runType 빈 값이면 전체, MANUAL / FULL / INCREMENTAL 이면 그 종류만
     */
    @Transactional(readOnly = true)
    public Map<String, Object> history(String runType, int size) {
        Pageable top = PageRequest.of(0, Math.min(Math.max(size, 1), MAX_SIZE));
        List<CrawlRunEntity> runs = (runType == null || runType.isBlank())
                ? crawlRunRepository.findAllByOrderByRunIdDesc(top)
                : crawlRunRepository.findByRunTypeOrderByRunIdDesc(runType, top);

        // 프로젝션(스프링이 만든 프록시 객체)을 그대로 JSON으로 내보내지 않고, 평범한 record로 옮겨 담는다.
        List<SummaryRow> summary = crawlRunRepository.summarizeByRunType().stream()
                .map(s -> new SummaryRow(s.getRunType(), s.getRunCount(), s.getTotalInserted(), s.getLastStartedAt()))
                .toList();

        return Map.of("summary", summary, "runs", runs);
    }

    public record SummaryRow(String runType, Long runCount, Long totalInserted, LocalDateTime lastStartedAt) {
    }
}
