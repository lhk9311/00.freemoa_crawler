package com.lhk.freemoa.project.query;

/**
 * 공고 검색 조건.
 * MyBatis 동적 SQL(<if test="keyword != null">)에서 getter로 값을 읽는다.
 * SI 현장에서 흔히 "검색 VO"라고 부르는 형태.
 */
public class ProjectSearch {

    private String keyword;     // 제목 포함 검색
    private String skillKey;    // 정규화된 기술 키 (JAVA, SPRINGBOOT ...)
    private String region;      // 지역 (서울, 경기 ...)
    private Integer minCost;    // 최대예산이 이 값(만원) 이상인 공고
    private boolean openOnly;   // 마감 안 된 공고만
    private int offset;
    private int limit;

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }

    public String getSkillKey() { return skillKey; }
    public void setSkillKey(String skillKey) { this.skillKey = skillKey; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public Integer getMinCost() { return minCost; }
    public void setMinCost(Integer minCost) { this.minCost = minCost; }

    public boolean isOpenOnly() { return openOnly; }
    public void setOpenOnly(boolean openOnly) { this.openOnly = openOnly; }

    public int getOffset() { return offset; }
    public void setOffset(int offset) { this.offset = offset; }

    public int getLimit() { return limit; }
    public void setLimit(int limit) { this.limit = limit; }
}
