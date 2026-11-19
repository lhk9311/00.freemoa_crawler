/**
 * 화면: 프리모아 공고 조회 (frmProjectList)
 * 넥사크로 Studio > Form 열기 > 아래쪽 Script 탭에 그대로 붙여넣는다.
 *
 * 화면에 있어야 하는 컴포넌트 id (docs/05-nexacro.md 참고)
 *   edt_keyword, cbo_skill, edt_region, edt_minCost, chk_openOnly, btn_search
 *   grd_list, sta_total, btn_prev, btn_next, sta_page
 *   edt_crawlKeyword, edt_crawlPages, btn_crawl
 *
 * 이벤트 연결: 각 컴포넌트 Properties 창 > Events 탭(번개 아이콘)에서
 *   Form.onload       -> Form_onload
 *   btn_search.onclick -> btn_search_onclick  ... (함수 이름과 동일하게)
 */

this.page = 1;
this.pageSize = 50;

/* ---------------------------------------------------------------
 * 화면 로드: 기술스택 콤보 목록을 먼저 받아온다
 * --------------------------------------------------------------- */
this.Form_onload = function(obj, e)
{
    this.fnLoadSkills();
};

this.fnLoadSkills = function()
{
    // transaction(서비스ID, URL, 보낼Dataset, 받을Dataset, 인자, 콜백)
    //   "svc::nx/skills" -> TypeDefinition에 등록한 svc(http://localhost:8080/) + nx/skills
    //   "ds_skill=ds_skill" -> 화면 ds_skill = 서버가 보낸 ds_skill
    this.transaction("skills", "svc::nx/skills", "", "ds_skill=ds_skill", "", "fnCallback");
};

/* ---------------------------------------------------------------
 * 조회
 * --------------------------------------------------------------- */
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

/**
 * 인자 문자열 만들기: "keyword='java spring' page=1 "
 * 값에 공백이 있으면 넥사크로가 인자를 잘못 나누기 때문에 wrapQuote로 감싼다.
 * 값이 비어 있으면 아예 보내지 않는다 -> 서버에서는 null (조건 없음)
 */
this.fnArg = function(name, value)
{
    if (value == null || value === "") return "";
    return name + "=" + nexacro.wrapQuote(String(value)) + " ";
};

/* ---------------------------------------------------------------
 * 수집 실행
 * --------------------------------------------------------------- */
this.btn_crawl_onclick = function(obj, e)
{
    var pages = this.edt_crawlPages.value || 1;
    if (!this.confirm(pages + "페이지(약 " + (pages * 10) + "건)를 수집합니다. 진행할까요?")) return;

    var args = "";
    args += this.fnArg("keyword",  this.edt_crawlKeyword.value);
    args += this.fnArg("maxPages", pages);

    this.btn_crawl.set_enable(false); // 수집 중 중복 클릭 방지 (서버에서도 409로 막지만 화면에서 먼저)
    this.transaction("crawl", "svc::nx/crawl", "", "ds_result=ds_result", args, "fnCallback");
};

/* ---------------------------------------------------------------
 * 공통 콜백
 *   errorCode < 0 : 서버가 ErrorCode=-1로 응답한 경우 (NexacroExceptionHandler)
 *                   또는 통신 실패 (서버 꺼짐, CORS 차단 등)
 * --------------------------------------------------------------- */
this.fnCallback = function(svcId, errorCode, errorMsg)
{
    if (svcId == "crawl") {
        this.btn_crawl.set_enable(true);
    }

    if (errorCode < 0) {
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
            var r = this.ds_result;
            this.alert("수집 " + r.getColumn(0, "status")
                + "\n가져온 공고 " + r.getColumn(0, "fetched") + "건"
                + "\n신규 " + r.getColumn(0, "inserted") + " / 갱신 " + r.getColumn(0, "updated")
                + " / 실패 " + r.getColumn(0, "failed"));
            this.fnLoadSkills();
            this.page = 1;
            this.fnSearch();
            break;
    }
};
