package com.lhk.freemoa.project.query;

import java.time.LocalDate;

/**
 * 목록 조회 결과 1행.
 * map-underscore-to-camel-case 설정 덕분에 COST_MAX 컬럼 -> setCostMax()로 자동 매핑된다.
 */
public class ProjectRow {

    private Long projIdx;
    private String title;
    private String region;
    private Integer costMin;
    private Integer costMax;
    private Integer applyCount;
    private LocalDate deadline;
    private String skills; // LISTAGG로 합친 "Java, Spring, Oracle"

    public Long getProjIdx() { return projIdx; }
    public void setProjIdx(Long projIdx) { this.projIdx = projIdx; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public Integer getCostMin() { return costMin; }
    public void setCostMin(Integer costMin) { this.costMin = costMin; }

    public Integer getCostMax() { return costMax; }
    public void setCostMax(Integer costMax) { this.costMax = costMax; }

    public Integer getApplyCount() { return applyCount; }
    public void setApplyCount(Integer applyCount) { this.applyCount = applyCount; }

    public LocalDate getDeadline() { return deadline; }
    public void setDeadline(LocalDate deadline) { this.deadline = deadline; }

    public String getSkills() { return skills; }
    public void setSkills(String skills) { this.skills = skills; }
}
