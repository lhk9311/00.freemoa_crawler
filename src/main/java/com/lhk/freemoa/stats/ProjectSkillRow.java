package com.lhk.freemoa.stats;

/**
 * SQL 결과 한 줄: 공고 1건 x 기술 태그 1개.
 * 공고에 기술이 4개면 같은 PROJ_IDX로 4줄이 온다. 묶는 건 TechStatsCalculator가 Java로 한다.
 *
 * StatRow와 같은 이유로 record가 아니라 setter 클래스다 (MyBatis가 컬럼 이름으로 하나씩 넣음).
 * applyCount, costMax는 null일 수 있어서 int가 아니라 Integer.
 */
public class ProjectSkillRow {

    private long projIdx;
    private String skillKey;
    private String skillName;
    private Integer applyCount;
    /** 최대 예산 (만원). 미입력이면 null */
    private Integer costMax;

    public ProjectSkillRow() {
    }

    /** 테스트에서 쓰기 편하게 */
    public ProjectSkillRow(long projIdx, String skillKey, String skillName, Integer applyCount, Integer costMax) {
        this.projIdx = projIdx;
        this.skillKey = skillKey;
        this.skillName = skillName;
        this.applyCount = applyCount;
        this.costMax = costMax;
    }

    public long getProjIdx() { return projIdx; }
    public void setProjIdx(long projIdx) { this.projIdx = projIdx; }
    public String getSkillKey() { return skillKey; }
    public void setSkillKey(String skillKey) { this.skillKey = skillKey; }
    public String getSkillName() { return skillName; }
    public void setSkillName(String skillName) { this.skillName = skillName; }
    public Integer getApplyCount() { return applyCount; }
    public void setApplyCount(Integer applyCount) { this.applyCount = applyCount; }
    public Integer getCostMax() { return costMax; }
    public void setCostMax(Integer costMax) { this.costMax = costMax; }
}
