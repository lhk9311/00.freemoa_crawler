package com.lhk.freemoa.stats;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface StatsMapper {

    int countProjects();

    /** 공고 x 기술 태그 (공고 1건에 태그가 4개면 4줄). 기술 묶기는 TechStatsCalculator가 한다 */
    List<ProjectSkillRow> selectProjectSkills();

    /** 지역별 공고 수와 비율 */
    List<StatRow> selectRegionStats();
}
