package com.lhk.freemoa.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SkillMapper {

    /** SKILL_KEY가 없을 때만 INSERT */
    int mergeSkill(@Param("skillKey") String skillKey, @Param("skillName") String skillName);

    Long selectSkillIdByKey(@Param("skillKey") String skillKey);
}
