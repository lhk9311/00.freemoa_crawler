package com.lhk.freemoa.screen;

import com.lhk.freemoa.stats.StatRow;
import com.lhk.freemoa.stats.StatsResponse;
import com.lhk.freemoa.stats.StatsService;
import com.lhk.freemoa.stats.TechGroupStat;
import com.lhk.freemoa.stats.TechStat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 넥사크로 통계 API가 화면이 기대하는 Dataset id와 막대값(bar)을 내보내는지 확인.
 * StatsService는 Mockito로 가짜를 만들어서 DB 없이 돈다.
 */
class StatsScreenControllerTest {

    @Test
    @DisplayName("Dataset 7개를 내보내고, 목록 안 1등의 막대값은 100이다")
    void stats() {
        StatsService service = mock(StatsService.class);
        StatRow seoul = new StatRow();
        seoul.setLabel("서울");
        seoul.setCnt(50);
        seoul.setPct(50.0);
        when(service.getStats()).thenReturn(new StatsResponse(100, 60, List.of(
                new TechGroupStat("LANGUAGE", "언어", List.of(
                        new TechStat("Java", "언어", 30, 30.0, 6.0, 2500),
                        new TechStat("PHP", "언어", 15, 15.0, 5.0, null))),
                new TechGroupStat("FRAMEWORK", "프레임워크", List.of()),
                new TechGroupStat("DATABASE", "DB", List.of()),
                new TechGroupStat("INFRA", "인프라·서버", List.of())),
                List.of(seoul)));

        String xml = new StatsScreenController(service).stats().getBody();

        assertThat(xml).contains("id=\"ds_kpi\"", "id=\"ds_lang\"", "id=\"ds_fw\"", "id=\"ds_db\"",
                "id=\"ds_infra\"", "id=\"ds_region\"", "id=\"ds_tech\"");
        assertThat(xml).contains("<Col id=\"bar\">100</Col>");   // Java (1등)
        assertThat(xml).contains("<Col id=\"bar\">50</Col>");    // PHP (1등의 절반)
        assertThat(xml).contains("<Col id=\"topLang\">Java</Col>");
    }
}
