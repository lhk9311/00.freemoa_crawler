/**
 * 프리모아 공고 조회 화면
 *  - 서버 주소: TypeDefinition > Services 의 svc (http://localhost:8080/)
 *  - 서버 API : /nx/skills, /nx/projects/search, /nx/crawl, /nx/crawl/incremental, /nx/crawl/full, /nx/crawl/run
 *  - [통계 보기] 버튼 -> frmStats.xfdl (백엔드 기술 수요)
 */

this.page = 1;
this.pageSize = 50;

/* [6단계] 전체 수집 폴링 */
this.POLL_TIMER_ID = 1;
this.POLL_INTERVAL_MS = 3000;   // 3초마다 진행 상황을 물어본다
this.pollRunId = null;          // 지금 지켜보고 있는 수집 번호
this.polling = false;           // 앞의 폴링 응답이 오기 전에 다음 폴링을 보내지 않기 위한 표시

/* 화면 로드: 기술스택 콤보 목록 받기 */
this.Form_onload = function(obj, e)
{
    this.fnLoadSkills();
};

this.fnLoadSkills = function()
{
    // transaction(서비스ID, URL, 보낼Dataset, 받을Dataset(화면=서버), 인자, 콜백)
    // 인자가 없으면 넥사크로는 GET으로 보낸다 (그래서 서버 /nx/skills는 GET도 받는다)
    this.transaction("skills", "svc::nx/skills", "", "ds_skill=ds_skill", "", "fnCallback");
};

/* 목록 더블클릭: 프리모아 원문 열기 (로그인은 브라우저에서 직접)
 *  - 주소 모양은 상세 API 응답의 queryString(page, pno, first_pno)을 보고 맞춘 것
 *  - QuickView/NRE(Runtime)는 system.execBrowser, 웹 브라우저로 띄웠을 땐 window.open */
this.FREEMOA_DETAIL_URL = "https://www.freemoa.net/m4/s41?page=1&pno=";

this.grd_list_oncelldblclick = function(obj, e)
{
    var projIdx = this.ds_list.getColumn(e.row, "projIdx");
    if (projIdx == null) return;

    var url = this.FREEMOA_DETAIL_URL + projIdx + "&first_pno=" + projIdx;
    if (nexacro._Browser == "Runtime") {
        system.execBrowser(url);
    } else {
        window.open(url, "_blank");
    }
};

/* [통계 보기] 백엔드 기술 수요 화면을 새 창(팝업)으로 연다
 *  - "Base::"는 Studio TypeDefinition > Services 에 등록된 폼 폴더의 prefix다. 프로젝트마다 다를 수 있다
 *  - nexacro.open(창이름, 폼URL, 부모프레임, 넘길값, 창옵션, left, top, width, height, 여는폼) */
this.STATS_FORM_URL = "Base::frmStats.xfdl";

this.btn_stats_onclick = function(obj, e)
{
    nexacro.open("popStats", this.STATS_FORM_URL, this.getOwnerFrame(), {},
                 "showtitlebar=true showstatusbar=false resizable=true", 40, 40, 1280, 720, this);
};

/* 조회 */
this.btn_search_onclick = function(obj, e)
{
    this.page = 1;
    this.fnSearch();
};

this.btn_prev_onclick = function(obj, e)
{
    if (this.page <= 1) return;
    this.page--;
    this.fnSearch();
};

this.btn_next_onclick = function(obj, e)
{
    var total = this.ds_page.getColumn(0, "totalCount") || 0;
    if (this.page * this.pageSize >= total) return;
    this.page++;
    this.fnSearch();
};

this.fnSearch = function()
{
    var args = "";
    args += this.fnArg("keyword",  this.edt_keyword.value);
    args += this.fnArg("skillKey", this.cbo_skill.value);
    args += this.fnArg("region",   this.edt_region.value);
    args += this.fnArg("minCost",  this.edt_minCost.value);
    args += this.fnArg("openOnly", this.chk_openOnly.value);
    args += this.fnArg("page",     this.page);
    args += this.fnArg("pageSize", this.pageSize);

    this.transaction("search", "svc::nx/projects/search", "", "ds_list=ds_list ds_page=ds_page", args, "fnCallback");
};

/* 인자 문자열: "keyword='java spring' page=1 " (값이 비면 보내지 않음 -> 서버에서 조건 없음) */
this.fnArg = function(name, value)
{
    if (value == null || value === "") return "";
    return name + "=" + nexacro.wrapQuote(String(value)) + " ";
};

/* 수집 버튼 3개를 한꺼번에 켜고 끈다 (수집 중 중복 클릭 방지, 서버도 409로 막음) */
this.fnSetCrawlEnable = function(enable)
{
    this.btn_crawl.set_enable(enable);
    this.btn_crawlNew.set_enable(enable);
    this.btn_crawlFull.set_enable(enable);
};

/* 수집 실행 (페이지 수 지정) */
this.btn_crawl_onclick = function(obj, e)
{
    var pages = this.edt_crawlPages.value || 1;
    if (!this.confirm(pages + "페이지(약 " + (pages * 10) + "건)를 수집합니다. 진행할까요?")) return;

    var args = "";
    args += this.fnArg("keyword",  this.edt_crawlKeyword.value);
    args += this.fnArg("maxPages", pages);

    this.fnSetCrawlEnable(false);
    this.transaction("crawl", "svc::nx/crawl", "", "ds_result=ds_result", args, "fnCallback");
};

/* [6단계] 새 공고만 (증분 수집). 새 공고가 끊기면 서버가 알아서 멈춘다 */
this.btn_crawlNew_onclick = function(obj, e)
{
    this.fnSetCrawlEnable(false);
    this.sta_runStatus.set_text("새 공고 수집 중...");
    // 인자를 하나는 꼭 보낸다. 인자가 비면 넥사크로가 GET으로 보내는데, 데이터를 바꾸는 요청은 POST여야 한다.
    this.transaction("crawlNew", "svc::nx/crawl/incremental", "", "ds_result=ds_result", "type=INCREMENTAL", "fnCallback");
};

/* [6단계] 전체 수집: 서버는 접수만 하고 runId를 바로 준다 -> 3초마다 진행 상황을 물어본다 */
this.btn_crawlFull_onclick = function(obj, e)
{
    if (!this.confirm("전체 공고(수천 건)를 수집합니다.\n7~10분 정도 걸리고, 그동안 다른 수집은 할 수 없습니다.\n진행할까요?")) return;

    var args = "type=FULL ";   // 위와 같은 이유로 인자를 항상 하나 이상 보낸다
    args += this.fnArg("keyword", this.edt_crawlKeyword.value);

    this.fnSetCrawlEnable(false);
    this.transaction("crawlFull", "svc::nx/crawl/full", "", "ds_run=ds_run", args, "fnCallback");
};

/* 타이머가 울릴 때마다 (setTimer로 등록한 간격) */
this.Form_ontimer = function(obj, e)
{
    if (e.timerid != this.POLL_TIMER_ID) return;
    if (this.polling) return;   // 앞의 응답이 아직 안 왔으면 이번 회차는 건너뜀

    this.polling = true;
    this.transaction("runStatus", "svc::nx/crawl/run", "", "ds_run=ds_run",
                     "runId=" + this.pollRunId, "fnCallback");
};

this.fnStartPolling = function(runId)
{
    this.pollRunId = runId;
    this.polling = false;
    this.setTimer(this.POLL_TIMER_ID, this.POLL_INTERVAL_MS);
};

this.fnStopPolling = function()
{
    this.killTimer(this.POLL_TIMER_ID);
    this.pollRunId = null;
    this.polling = false;
    this.fnSetCrawlEnable(true);
};

/* ds_run 1행을 사람이 읽는 문장으로 */
this.fnRunText = function()
{
    var r = this.ds_run;
    return "[" + r.getColumn(0, "runType") + " #" + r.getColumn(0, "runId") + "] "
        + r.getColumn(0, "status") + " - " + r.getColumn(0, "pageCount") + "페이지, "
        + r.getColumn(0, "fetchedCnt") + "건 (신규 " + r.getColumn(0, "insertedCnt")
        + " / 갱신 " + r.getColumn(0, "updatedCnt") + " / 실패 " + r.getColumn(0, "failedCnt") + ")";
};

/* 수집이 끝난 뒤 목록과 기술 콤보를 새로 받는다 */
this.fnRefreshAfterCrawl = function()
{
    this.fnLoadSkills();
    this.page = 1;
    this.fnSearch();
};

/* 공통 콜백: errorCode < 0 이면 서버 에러(ErrorCode=-1) 또는 통신 실패 */
this.fnCallback = function(svcId, errorCode, errorMsg)
{
    if (svcId == "runStatus") {
        this.polling = false;
    }

    if (errorCode < 0) {
        if (svcId == "runStatus") {
            // 폴링이 실패하면 계속 alert가 뜨지 않게 타이머부터 멈춘다. 수집 자체는 서버에서 계속 돌고 있을 수 있다.
            this.fnStopPolling();
            this.sta_runStatus.set_text("진행 상황 조회 실패 (수집은 서버에서 계속 진행 중일 수 있음)");
        } else if (svcId == "crawl" || svcId == "crawlNew" || svcId == "crawlFull") {
            this.fnSetCrawlEnable(true);
            this.sta_runStatus.set_text("");
        }
        this.alert("[" + svcId + "] " + errorMsg);
        return;
    }

    switch (svcId) {
        case "skills":
            this.cbo_skill.set_index(0); // "전체"
            break;

        case "search":
            var total = this.ds_page.getColumn(0, "totalCount") || 0;
            var lastPage = Math.max(1, Math.ceil(total / this.pageSize));
            this.sta_total.set_text("총 " + total + "건");
            this.sta_page.set_text(this.page + " / " + lastPage);
            break;

        case "crawl":
        case "crawlNew":
            this.fnSetCrawlEnable(true);
            var r = this.ds_result;
            var msg = "수집 " + r.getColumn(0, "status")
                + " - 가져온 공고 " + r.getColumn(0, "fetched") + "건"
                + " (신규 " + r.getColumn(0, "inserted") + " / 갱신 " + r.getColumn(0, "updated")
                + " / 실패 " + r.getColumn(0, "failed") + ")";
            this.sta_runStatus.set_text(msg);
            this.fnRefreshAfterCrawl();
            break;

        case "crawlFull":
            // 접수됨: 버튼은 꺼둔 채로 폴링 시작
            this.sta_runStatus.set_text(this.fnRunText());
            this.fnStartPolling(this.ds_run.getColumn(0, "runId"));
            break;

        case "runStatus":
            this.sta_runStatus.set_text(this.fnRunText());
            if (this.ds_run.getColumn(0, "status") != "RUNNING") {
                this.fnStopPolling();
                var err = this.ds_run.getColumn(0, "errorMsg");
                this.alert("전체 수집 끝: " + this.fnRunText() + (err ? "\n" + err : ""));
                this.fnRefreshAfterCrawl();
            }
            break;
    }
};
