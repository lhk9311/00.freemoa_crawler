package com.lhk.freemoa.crawler.run;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CrawlRunMapper {

    long selectNextRunId();

    int insertRunning(@Param("runId") long runId,
                      @Param("keyword") String keyword);

    int updateFinish(@Param("runId") long runId,
                     @Param("status") CrawlStatus status,
                     @Param("pageCount") int pageCount,
                     @Param("fetchedCnt") int fetchedCnt,
                     @Param("insertedCnt") int insertedCnt,
                     @Param("updatedCnt") int updatedCnt,
                     @Param("failedCnt") int failedCnt,
                     @Param("errorMsg") String errorMsg);
}
