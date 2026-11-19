package com.lhk.freemoa.project.query;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProjectQueryMapper {

    List<ProjectRow> selectProjects(ProjectSearch search);

    int countProjects(ProjectSearch search);

    List<SkillCount> selectTopSkills(@Param("limit") int limit);
}
