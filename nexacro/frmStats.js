/**
 * 백엔드 기술 수요 통계 화면
 *  - 서버 API: /nx/stats (화면 열 때 한 번), /nx/stats/together (기술 고를 때)
 *  - 공고 목록 화면(frmProjectList)의 [통계 보기] 버튼으로 팝업처럼 열린다
 *
 * 막대: Grid 셀 displaytype="progressbarcontrol"이 0~100 값을 막대로 그린다.
 *       서버가 목록 안 1등을 100으로 맞춘 값(bar)을 보내 준다.
 * 묶음 그리드 4개는 같은 이벤트 함수(grdGroup_oncellclick)를 같이 쓴다.
 */

this.Form_onload = function(obj, e)
{
    // 인자가 없으면 넥사크로는 GET으로 보낸다 (서버 /nx/stats는 GET도 받는다)
    this.transaction("stats", "svc::nx/stats", "",
        "ds_kpi=ds_kpi ds_lang=ds_lang ds_fw=ds_fw ds_db=ds_db ds_infra=ds_infra ds_region=ds_region ds_tech=ds_tech",
        "", "fnCallback");
};

/* 같이 요구되는 기술 불러오기 */
this.fnLoadTogether = function(tech)
{
    if (tech == null || tech === "") return;
    this.cbo_tech.set_value(tech);
    this.sta_togetherDesc.set_text("불러오는 중");
    this.transaction("together", "svc::nx/stats/together", "",
        "ds_base=ds_base ds_together=ds_together",
        "tech=" + nexacro.wrapQuote(String(tech)), "fnCallback");
};

/* 묶음 그리드에서 기술을 누르면 그 기술 기준으로 바꾼다 */
this.grdGroup_oncellclick = function(obj, e)
{
    var ds = obj.getBindDataset();
    if (e.row < 0 || ds.rowcount == 0) return;
    this.fnLoadTogether(ds.getColumn(e.row, "name"));
};

/* 콤보에서 기술을 고르면 */
this.cbo_tech_onitemchanged = function(obj, e)
{
    this.fnLoadTogether(e.postvalue);
};

this.btn_close_onclick = function(obj, e)
{
    this.close();
};

/* 1234 -> "1,234" */
this.fnComma = function(n)
{
    if (n == null || n === "") return "-";
    return String(n).replace(/\B(?=(\d{3})+(?!\d))/g, ",");
};

this.fnCallback = function(svcId, errorCode, errorMsg)
{
    if (errorCode < 0) {
        this.alert("[" + svcId + "] " + errorMsg);
        return;
    }

    switch (svcId) {
        case "stats":
            var k = this.ds_kpi;
            this.sta_kTotal.set_text(this.fnComma(k.getColumn(0, "totalProjects")) + "건");
            this.sta_kBackend.set_text(this.fnComma(k.getColumn(0, "backendProjects")) + "건 (" + k.getColumn(0, "backendPct") + "%)");
            this.sta_kLang.set_text((k.getColumn(0, "topLang") || "-") + "  " + (k.getColumn(0, "topLangPct") || 0) + "%");
            this.sta_kDb.set_text((k.getColumn(0, "topDb") || "-") + "  " + (k.getColumn(0, "topDbPct") || 0) + "%");

            // 처음에는 1위 언어 기준으로 "같이 요구되는 기술"을 보여 준다
            this.fnLoadTogether(k.getColumn(0, "topLang"));
            break;

        case "together":
            var b = this.ds_base;
            this.sta_togetherDesc.set_text(b.getColumn(0, "tech") + " 공고 " + this.fnComma(b.getColumn(0, "baseCount"))
                + "건 중에서 같이 요구된 비율 (묶음이 기타인 건 분류 목록에 없는 기술)");
            break;
    }
};
