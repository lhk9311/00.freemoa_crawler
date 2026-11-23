package com.lhk.freemoa.stats;

/**
 * 통계 한 줄 (항목, 건수, 비율). 지금은 지역별 통계가 이 모양으로 나온다.
 *
 * record가 아니라 setter가 있는 클래스로 둔 이유:
 *  MyBatis가 record에 값을 넣으려면 생성자 인자 순서와 타입을 정확히 맞춰야 한다 (트러블슈팅 12번 참고).
 *  Oracle NUMBER는 BigDecimal로 오기 때문에 int/double 생성자와 어긋나기 쉽다.
 *  setter 방식은 컬럼 이름(LABEL -> label)으로 하나씩 넣고 타입 변환도 해 준다.
 */
public class StatRow {

    /** 항목 이름 (지역) */
    private String label;
    /** 공고 수 */
    private int cnt;
    /** 비율 (%) */
    private Double pct;
    /** 항목별 추가 값 (필요한 통계에서만 씀) */
    private Double extra;

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public int getCnt() { return cnt; }
    public void setCnt(int cnt) { this.cnt = cnt; }
    public Double getPct() { return pct; }
    public void setPct(Double pct) { this.pct = pct; }
    public Double getExtra() { return extra; }
    public void setExtra(Double extra) { this.extra = extra; }
}
