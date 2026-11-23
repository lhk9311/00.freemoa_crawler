package com.lhk.freemoa.stats;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StatsService {

    /** 묶음(언어, DB ...)마다 보여 줄 기술 수 */
    private static final int TOP_PER_GROUP = 10;
    /** "같이 요구되는 기술"에서 보여 줄 기술 수 */
    private static final int TOP_TOGETHER = 15;

    private final StatsMapper statsMapper;

    public StatsService(StatsMapper statsMapper) {
        this.statsMapper = statsMapper;
    }

    /**
     * readOnly = true: 조회만 한다는 표시. 실수로 쓰기 SQL이 섞여도 막히고, DB/드라이버가 조회용으로 최적화할 여지를 준다.
     * 쿼리 3개를 한 트랜잭션에서 실행하므로 같은 시점의 데이터로 계산된다 (중간에 수집이 끼어들어도 숫자가 서로 어긋나지 않음).
     *
     * SQL은 "공고 x 태그" 원본만 가져오고, 기술 묶기(버전 합치기, 분류)는 TechStatsCalculator가 자바로 한다.
     */
    @Transactional(readOnly = true)
    public StatsResponse getStats() {
        int total = statsMapper.countProjects();
        List<ProjectSkillRow> rows = statsMapper.selectProjectSkills();
        return new StatsResponse(
                total,
                TechStatsCalculator.countBackendProjects(rows),
                TechStatsCalculator.groupStats(rows, total, TOP_PER_GROUP),
                statsMapper.selectRegionStats());
    }

    /** tech를 요구한 공고에서 같이 요구된 기술 순위 */
    @Transactional(readOnly = true)
    public TogetherResponse together(String tech) {
        return TechStatsCalculator.together(statsMapper.selectProjectSkills(), tech, TOP_TOGETHER);
    }
}
