package com.lhk.freemoa.stats;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatsService {

    private static final int TOP_SKILLS = 15;
    private static final int MONTHS = 12;

    private final StatsMapper statsMapper;

    public StatsService(StatsMapper statsMapper) {
        this.statsMapper = statsMapper;
    }

    /**
     * readOnly = true: 조회만 한다는 표시. 실수로 쓰기 SQL이 섞여도 막히고, DB/드라이버가 조회용으로 최적화할 여지를 준다.
     * 쿼리 5개를 한 트랜잭션에서 실행하므로 같은 시점의 데이터로 계산된다 (중간에 수집이 끼어들어도 숫자가 서로 어긋나지 않음).
     */
    @Transactional(readOnly = true)
    public StatsResponse getStats() {
        return new StatsResponse(
                statsMapper.countProjects(),
                statsMapper.selectSkillStats(TOP_SKILLS),
                statsMapper.selectRegionStats(),
                statsMapper.selectBudgetStats(),
                statsMapper.selectMonthlyStats(MONTHS));
    }
}
