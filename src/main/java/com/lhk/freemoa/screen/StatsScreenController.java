package com.lhk.freemoa.screen;

import com.lhk.freemoa.nexacro.NxDataset;
import com.lhk.freemoa.nexacro.NxRequest;
import com.lhk.freemoa.nexacro.NxResponses;
import com.lhk.freemoa.nexacro.NxType;
import com.lhk.freemoa.nexacro.NxXmlReader;
import com.lhk.freemoa.stats.StatRow;
import com.lhk.freemoa.stats.StatsResponse;
import com.lhk.freemoa.stats.StatsService;
import com.lhk.freemoa.stats.TechGroupStat;
import com.lhk.freemoa.stats.TechStat;
import com.lhk.freemoa.stats.TogetherResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 넥사크로 통계 화면(frmStats.xfdl)용 API. /api/stats(JSON)와 같은 StatsService를 넥사크로 XML로 내보낸다.
 * ProjectScreenController가 공고 목록 화면용이라면, 이건 통계 화면용이다.
 *
 *   /nx/stats           ds_kpi, ds_lang, ds_fw, ds_db, ds_infra, ds_region, ds_tech
 *   /nx/stats/together  ds_base, ds_together   (인자: tech=Java)
 *
 * 막대 그리기
 *   넥사크로 Grid 셀의 displaytype="progressbarcontrol"은 0~100 값을 막대로 그린다.
 *   비율(pct)을 그대로 넣으면 1등이 30%일 때 막대가 30%까지만 차서 차이가 잘 안 보이므로,
 *   목록 안 1등을 100으로 맞춘 값(bar)을 서버에서 따로 계산해서 보낸다.
 */
@RestController
@RequestMapping("/nx/stats")
public class StatsScreenController {

    /** 서버 묶음 코드(TechGroup) -> 화면 Dataset id */
    private static final Map<String, String> GROUP_DATASET = Map.of(
            "LANGUAGE", "ds_lang",
            "FRAMEWORK", "ds_fw",
            "DATABASE", "ds_db",
            "INFRA", "ds_infra");

    private final StatsService statsService;

    public StatsScreenController(StatsService statsService) {
        this.statsService = statsService;
    }

    /** 화면 처음 열 때 한 번. 인자가 없어서 넥사크로가 GET으로 보낸다 */
    @RequestMapping(method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> stats() {
        StatsResponse s = statsService.getStats();

        TechStat lang = top(s.groups(), "LANGUAGE");
        TechStat db = top(s.groups(), "DATABASE");
        double backendPct = s.totalProjects() == 0 ? 0 : s.backendProjects() * 100.0 / s.totalProjects();

        NxDataset kpi = new NxDataset("ds_kpi")
                .column("totalProjects", NxType.INT, 10)
                .column("backendProjects", NxType.INT, 10)
                .column("backendPct", NxType.BIGDECIMAL, 10)
                .column("topLang", NxType.STRING, 100)
                .column("topLangPct", NxType.BIGDECIMAL, 10)
                .column("topDb", NxType.STRING, 100)
                .column("topDbPct", NxType.BIGDECIMAL, 10)
                .addRow(s.totalProjects(), s.backendProjects(), round1(backendPct),
                        lang == null ? null : lang.name(), lang == null ? null : round1(lang.pct()),
                        db == null ? null : db.name(), db == null ? null : round1(db.pct()));

        // 콤보박스(같이 요구되는 기술 기준 선택)용: 묶음 순서대로 모든 기술
        NxDataset tech = new NxDataset("ds_tech")
                .column("name", NxType.STRING, 100)
                .column("label", NxType.STRING, 130);

        List<NxDataset> datasets = new ArrayList<>();
        datasets.add(kpi);
        for (TechGroupStat g : s.groups()) {
            String dsId = GROUP_DATASET.get(g.group());
            if (dsId == null) {
                continue;
            }
            datasets.add(techDataset(dsId, g.items(), false));
            for (TechStat t : g.items()) {
                tech.addRow(t.name(), g.label() + " · " + t.name());
            }
        }
        datasets.add(regionDataset(s.regions()));
        datasets.add(tech);
        return NxResponses.ok(datasets.toArray(new NxDataset[0]));
    }

    /** 같이 요구되는 기술. 화면에서 기술을 고르면 호출 */
    @PostMapping("/together")
    public ResponseEntity<String> together(@RequestBody(required = false) String body) {
        NxRequest req = NxXmlReader.read(body);
        TogetherResponse t = statsService.together(req.getString("tech", "Java"));

        NxDataset base = new NxDataset("ds_base")
                .column("tech", NxType.STRING, 100)
                .column("groupName", NxType.STRING, 30)
                .column("baseCount", NxType.INT, 10)
                .addRow(t.tech(), t.group(), t.baseCount());

        return NxResponses.ok(base, techDataset("ds_together", t.items(), true));
    }

    /** 순위, 기술, (묶음), 건수, 비율, 막대값, 평균 지원자, 평균 최대예산 */
    private static NxDataset techDataset(String id, List<TechStat> items, boolean withGroup) {
        NxDataset ds = new NxDataset(id)
                .column("rank", NxType.INT, 3)
                .column("name", NxType.STRING, 100)
                .column("groupName", NxType.STRING, 30)
                .column("cnt", NxType.INT, 10)
                .column("pct", NxType.BIGDECIMAL, 10)
                .column("bar", NxType.INT, 3)
                .column("avgApply", NxType.BIGDECIMAL, 10)
                .column("avgBudget", NxType.INT, 10);
        double max = items.stream().mapToDouble(TechStat::pct).max().orElse(0);
        int rank = 1;
        for (TechStat t : items) {
            ds.addRow(rank++, t.name(), withGroup ? t.group() : null, t.cnt(), round1(t.pct()), bar(t.pct(), max),
                      t.avgApply() == null ? null : round1(t.avgApply()), t.avgBudget());
        }
        return ds;
    }

    /** 상위 6개 + 나머지는 기타로 합침 */
    private static NxDataset regionDataset(List<StatRow> regions) {
        NxDataset ds = new NxDataset("ds_region")
                .column("rank", NxType.INT, 3)
                .column("label", NxType.STRING, 50)
                .column("cnt", NxType.INT, 10)
                .column("pct", NxType.BIGDECIMAL, 10)
                .column("bar", NxType.INT, 3);
        int shown = Math.min(6, regions.size());
        double max = regions.stream().mapToDouble(r -> r.getPct() == null ? 0 : r.getPct()).max().orElse(0);
        for (int i = 0; i < shown; i++) {
            StatRow r = regions.get(i);
            double pct = r.getPct() == null ? 0 : r.getPct();
            ds.addRow(i + 1, r.getLabel(), r.getCnt(), round1(pct), bar(pct, max));
        }
        if (regions.size() > shown) {
            int cnt = 0;
            double pct = 0;
            for (StatRow r : regions.subList(shown, regions.size())) {
                cnt += r.getCnt();
                pct += r.getPct() == null ? 0 : r.getPct();
            }
            ds.addRow(null, "기타", cnt, round1(pct), bar(pct, max));
        }
        return ds;
    }

    private static TechStat top(List<TechGroupStat> groups, String group) {
        return groups.stream()
                .filter(g -> g.group().equals(group) && !g.items().isEmpty())
                .map(g -> g.items().get(0))
                .findFirst().orElse(null);
    }

    /** 목록 안 최댓값을 100으로 맞춘 막대 길이 */
    private static int bar(double pct, double max) {
        return max <= 0 ? 0 : (int) Math.round(Math.min(pct / max, 1.0) * 100);
    }

    private static BigDecimal round1(double v) {
        return BigDecimal.valueOf(Math.round(v * 10) / 10.0);
    }
}
