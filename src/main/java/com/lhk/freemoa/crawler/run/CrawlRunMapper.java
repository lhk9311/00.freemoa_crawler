package com.lhk.freemoa.crawler.run;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CrawlRunMapper {

    long selectNextRunId();

    int insertRunning(@Param("runId") long runId,
                      @Param("keyword") String keyword,
                      @Param("runType") CrawlRunType runType);

    int updateFinish(@Param("runId") long runId,
                     @Param("status") CrawlStatus status,
                     @Param("pageCount") int pageCount,
                     @Param("fetchedCnt") int fetchedCnt,
                     @Param("insertedCnt") int insertedCnt,
                     @Param("updatedCnt") int updatedCnt,
                     @Param("failedCnt") int failedCnt,
                     @Param("errorMsg") String errorMsg);

    /** [6단계] 진행 중 건수 갱신. 전체 수집(수 분)을 기다리는 동안 얼마나 됐는지 보기 위함 */
    int updateProgress(@Param("runId") long runId,
                       @Param("pageCount") int pageCount,
                       @Param("fetchedCnt") int fetchedCnt,
                       @Param("insertedCnt") int insertedCnt,
                       @Param("updatedCnt") int updatedCnt,
                       @Param("failedCnt") int failedCnt);

    /** [6단계] 서버가 수집 도중 꺼져서 남은 RUNNING 행을 FAIL로 정리 */
    int failStaleRunning(@Param("errorMsg") String errorMsg);

    /** [6단계] 수집 1건 상태 조회. 없으면 null */
    CrawlRunView selectRun(@Param("runId") long runId);
}
