package com.lhk.freemoa.project.query;

/** 기술스택별 공고 수 (콤보박스용) */
public class SkillCount {

    private String skillKey;
    private String skillName;
    private Integer cnt;

    public String getSkillKey() { return skillKey; }
    public void setSkillKey(String skillKey) { this.skillKey = skillKey; }

    public String getSkillName() { return skillName; }
    public void setSkillName(String skillName) { this.skillName = skillName; }

    public Integer getCnt() { return cnt; }
    public void setCnt(Integer cnt) { this.cnt = cnt; }
}
