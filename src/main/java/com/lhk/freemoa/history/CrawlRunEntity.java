package com.lhk.freemoa.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/**
 * [JPA] TB_CRAWL_RUN 테이블을 객체로 매핑한 엔티티.
 *
 * MyBatis와 비교
 *  - MyBatis: SQL을 XML에 직접 쓰고, 결과 컬럼을 객체에 담는다 (CrawlRunMapper.xml).
 *  - JPA    : 테이블과 클래스를 이렇게 연결해 두면, SQL은 JPA(Hibernate)가 만든다.
 *
 * @Immutable
 *  - 이 엔티티는 조회 전용이다. 실수로 setter를 부르거나 save()를 해도 UPDATE가 나가지 않는다.
 *  - 이 테이블에 쓰는 건 MyBatis(CrawlRunService)만 한다. 같은 테이블을 두 기술이 동시에 쓰면
 *    "누가 언제 바꿨는지" 추적이 어려워서, 역할을 나눴다 (쓰기 = MyBatis, 이력 조회 = JPA).
 *
 * protected 기본 생성자
 *  - JPA는 DB에서 읽은 값으로 객체를 만들 때 기본 생성자를 쓴다 (리플렉션). 밖에서 빈 객체를 만들지 못하게 protected.
 */
@Entity
@Immutable
@Table(name = "TB_CRAWL_RUN")
public class CrawlRunEntity {

    @Id
    @Column(name = "RUN_ID")
    private Long runId;

    @Column(name = "RUN_TYPE")
    private String runType;

    @Column(name = "STATUS")
    private String status;

    @Column(name = "KEYWORD")
    private String keyword;

    @Column(name = "STARTED_AT")
    private LocalDateTime startedAt;

    @Column(name = "ENDED_AT")
    private LocalDateTime endedAt;

    @Column(name = "PAGE_COUNT")
    private int pageCount;

    @Column(name = "FETCHED_CNT")
    private int fetchedCnt;

    @Column(name = "INSERTED_CNT")
    private int insertedCnt;

    @Column(name = "UPDATED_CNT")
    private int updatedCnt;

    @Column(name = "FAILED_CNT")
    private int failedCnt;

    @Column(name = "ERROR_MSG")
    private String errorMsg;

    protected CrawlRunEntity() {
    }

    public Long getRunId() { return runId; }
    public String getRunType() { return runType; }
    public String getStatus() { return status; }
    public String getKeyword() { return keyword; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public int getPageCount() { return pageCount; }
    public int getFetchedCnt() { return fetchedCnt; }
    public int getInsertedCnt() { return insertedCnt; }
    public int getUpdatedCnt() { return updatedCnt; }
    public int getFailedCnt() { return failedCnt; }
    public String getErrorMsg() { return errorMsg; }
}
