package com.lhk.freemoa.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProjectSkillMapper {

    int deleteByProjIdx(@Param("projIdx") long projIdx);

    int insert(@Param("projIdx") long projIdx, @Param("skillId") long skillId);
}
