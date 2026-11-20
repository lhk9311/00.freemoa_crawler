package com.lhk.freemoa.stats;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StatsMapper {

    int countProjects();

    /** 기술별 공고 수, 전체 공고 중 비율, 평균 지원자 수 (상위 limit개) */
    List<StatRow> selectSkillStats(@Param("limit") int limit);

    /** 지역별 공고 수와 비율 */
    List<StatRow> selectRegionStats();

    /** 최대 예산 구간별 공고 수와 비율 */
    List<StatRow> selectBudgetStats();

    /** 최근 months개월 월별 신규 공고 수와 누적 */
    List<StatRow> selectMonthlyStats(@Param("months") int months);
}
