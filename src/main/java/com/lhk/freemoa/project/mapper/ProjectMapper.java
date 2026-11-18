package com.lhk.freemoa.project.mapper;

import com.lhk.freemoa.crawler.domain.Project;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProjectMapper {

    /** 주어진 공고번호 중 이미 DB에 있는 것만 돌려준다 (신규/갱신 건수 계산용) */
    List<Long> selectExistingProjIdx(@Param("projIdxList") List<Long> projIdxList);

    /** 있으면 UPDATE, 없으면 INSERT (Oracle MERGE INTO) */
    int merge(Project project);
}
