package com.lhk.freemoa.project.mapper;

import com.lhk.freemoa.crawler.domain.Project;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SnapshotMapper {

    /** 파라미터가 2개 이상이면 @Param 이름으로 접근한다: #{runId}, #{p.projIdx} */
    int insert(@Param("runId") long runId, @Param("p") Project project);
}
