package com.lhk.freemoa.screen;

import com.lhk.freemoa.crawler.CrawlJobLauncher;
import com.lhk.freemoa.crawler.CrawlResult;
import com.lhk.freemoa.crawler.CrawlService;
import com.lhk.freemoa.crawler.convert.SkillParser;
import com.lhk.freemoa.crawler.run.CrawlRunService;
import com.lhk.freemoa.crawler.run.CrawlRunView;
import com.lhk.freemoa.nexacro.NxDataset;
import com.lhk.freemoa.nexacro.NxRequest;
import com.lhk.freemoa.nexacro.NxResponses;
import com.lhk.freemoa.nexacro.NxType;
import com.lhk.freemoa.nexacro.NxXmlReader;
import com.lhk.freemoa.project.query.ProjectQueryService;
import com.lhk.freemoa.project.query.ProjectRow;
import com.lhk.freemoa.project.query.ProjectSearch;
import com.lhk.freemoa.project.query.SkillCount;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * [5단계] 넥사크로 화면용 API. 요청/응답 모두 넥사크로 XML(Dataset) 형식.
 *
 * 넥사크로 화면 스크립트
 *   this.transaction("search", "svc::nx/projects/search", "", "ds_list=ds_list ds_page=ds_page",
 *                    "keyword=java skillKey=JAVA", "fnCallback");
 *
 *   - "svc::" 는 TypeDefinition > Services 에 등록한 서버 주소(http://localhost:8080/)로 치환된다
 *   - "ds_list=ds_list" : 화면 Dataset(왼쪽) = 서버 응답 Dataset(오른쪽)
 *   - "keyword=java ..." : 서버에서 NxRequest.getString("keyword")로 읽는다
 *
 * 화면용 API(/nx/**)와 일반 REST API(/api/**)를 나눠둔 이유:
 * 같은 Service를 서로 다른 형식으로 노출한다. 넥사크로 체험판이 끝나도 /api/** 와 Service는 그대로 쓴다.
 */
@RestController
@RequestMapping("/nx")
public class ProjectScreenController {

    private static final int DEFAULT_PAGE_SIZE = 50;

    private final ProjectQueryService queryService;
    private final CrawlService crawlService;
    private final CrawlJobLauncher crawlJobLauncher;
    private final CrawlRunService crawlRunService;

    public ProjectScreenController(ProjectQueryService queryService,
                                   CrawlService crawlService,
                                   CrawlJobLauncher crawlJobLauncher,
                                   CrawlRunService crawlRunService) {
        this.queryService = queryService;
        this.crawlService = crawlService;
        this.crawlJobLauncher = crawlJobLauncher;
        this.crawlRunService = crawlRunService;
    }

    /** 공고 목록 조회 */
    @PostMapping("/projects/search")
    public ResponseEntity<String> search(@RequestBody(required = false) String body) {
        NxRequest req = NxXmlReader.read(body);

        int page = Math.max(1, req.getInt("page", 1));
        int pageSize = req.getInt("pageSize", DEFAULT_PAGE_SIZE);

        ProjectSearch search = new ProjectSearch();
        search.setKeyword(req.getString("keyword"));
        String skill = req.getString("skillKey");
        search.setSkillKey(skill == null ? null : SkillParser.toKey(skill)); // "spring boot" -> "SPRINGBOOT"
        search.setRegion(req.getString("region"));
        search.setMinCost(req.getInteger("minCost"));
        search.setOpenOnly(req.getBoolean("openOnly"));
        search.setLimit(pageSize);
        search.setOffset((page - 1) * pageSize);

        ProjectQueryService.Page result = queryService.search(search);

        NxDataset list = new NxDataset("ds_list")
                .column("projIdx", NxType.INT, 10)
                .column("title", NxType.STRING, 500)
                .column("region", NxType.STRING, 50)
                .column("costMin", NxType.INT, 10)
                .column("costMax", NxType.INT, 10)
                .column("applyCount", NxType.INT, 6)
                .column("deadline", NxType.DATE, 8)
                .column("skills", NxType.STRING, 1000);
        for (ProjectRow r : result.rows()) {
            list.addRow(r.getProjIdx(), r.getTitle(), r.getRegion(), r.getCostMin(), r.getCostMax(),
                    r.getApplyCount(), r.getDeadline(), r.getSkills());
        }

        NxDataset pageInfo = new NxDataset("ds_page")
                .column("totalCount", NxType.INT, 10)
                .column("page", NxType.INT, 10)
                .column("pageSize", NxType.INT, 10)
                .addRow(result.totalCount(), page, pageSize);

        return NxResponses.ok(list, pageInfo);
    }

    /**
     * 기술스택 콤보박스 목록 (공고 수 많은 순)
     * 넥사크로는 보낼 Dataset도 인자도 없는 transaction을 GET으로 보낸다. 그래서 GET도 받는다.
     */
    @RequestMapping(value = "/skills", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> skills() {
        NxDataset ds = new NxDataset("ds_skill")
                .column("skillKey", NxType.STRING, 100)
                .column("skillName", NxType.STRING, 100)
                .column("label", NxType.STRING, 120)
                .column("cnt", NxType.INT, 10);
        // 첫 행은 "전체" (선택 안 함)
        ds.addRow("", "", "전체", null);
        for (SkillCount s : queryService.topSkills(50)) {
            ds.addRow(s.getSkillKey(), s.getSkillName(), s.getSkillName() + " (" + s.getCnt() + ")", s.getCnt());
        }
        return NxResponses.ok(ds);
    }

    /** 화면에서 수집 실행 (페이지 수 지정) */
    @PostMapping("/crawl")
    public ResponseEntity<String> crawl(@RequestBody(required = false) String body) {
        NxRequest req = NxXmlReader.read(body);
        CrawlResult r = crawlService.crawl(req.getString("keyword", ""), req.getInt("maxPages", 1));
        return NxResponses.ok(resultDataset(r));
    }

    /** [6단계] 새 공고만 수집 (증분). 보통 1~2페이지라 결과를 기다려서 돌려준다 */
    @PostMapping("/crawl/incremental")
    public ResponseEntity<String> crawlIncremental() {
        return NxResponses.ok(resultDataset(crawlService.crawlIncremental()));
    }

    /**
     * [6단계] 전체 수집 접수. 수 분 걸리므로 기다리지 않고 RUNNING 상태의 이력 1행을 바로 돌려준다.
     * 화면은 받은 runId로 /nx/crawl/run 을 몇 초마다 다시 물어본다 (폴링).
     */
    @PostMapping("/crawl/full")
    public ResponseEntity<String> crawlFull(@RequestBody(required = false) String body) {
        NxRequest req = NxXmlReader.read(body);
        long runId = crawlJobLauncher.submitFull(req.getString("keyword", ""));
        return NxResponses.ok(runDataset(crawlRunService.find(runId)));
    }

    /** [6단계] 수집 진행 상황 1건 (폴링용) */
    @PostMapping("/crawl/run")
    public ResponseEntity<String> crawlRun(@RequestBody(required = false) String body) {
        NxRequest req = NxXmlReader.read(body);
        CrawlRunView run = crawlRunService.find(req.getInt("runId", 0));
        if (run == null) {
            throw new IllegalArgumentException("수집 이력이 없습니다: runId=" + req.getString("runId"));
        }
        return NxResponses.ok(runDataset(run));
    }

    private static NxDataset resultDataset(CrawlResult r) {
        return new NxDataset("ds_result")
                .column("runId", NxType.INT, 10)
                .column("status", NxType.STRING, 10)
                .column("fetched", NxType.INT, 7)
                .column("inserted", NxType.INT, 7)
                .column("updated", NxType.INT, 7)
                .column("failed", NxType.INT, 7)
                .column("elapsedMs", NxType.INT, 10)
                .addRow(r.runId(), r.status().name(), r.fetched(), r.inserted(), r.updated(), r.failed(), r.elapsedMs());
    }

    private static NxDataset runDataset(CrawlRunView v) {
        return new NxDataset("ds_run")
                .column("runId", NxType.INT, 10)
                .column("runType", NxType.STRING, 20)
                .column("status", NxType.STRING, 10)
                .column("pageCount", NxType.INT, 5)
                .column("fetchedCnt", NxType.INT, 7)
                .column("insertedCnt", NxType.INT, 7)
                .column("updatedCnt", NxType.INT, 7)
                .column("failedCnt", NxType.INT, 7)
                .column("errorMsg", NxType.STRING, 1000)
                .addRow(v.runId(), v.runType(), v.status(), v.pageCount(), v.fetchedCnt(),
                        v.insertedCnt(), v.updatedCnt(), v.failedCnt(), v.errorMsg());
    }
}
