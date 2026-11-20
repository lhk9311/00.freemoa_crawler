package com.lhk.freemoa.crawler;

import com.lhk.freemoa.crawler.run.CrawlAlreadyRunningException;
import com.lhk.freemoa.crawler.run.CrawlRunService;
import com.lhk.freemoa.crawler.run.CrawlRunView;
import com.lhk.freemoa.crawler.run.CrawlStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 수집 API 문서화 테스트 (Spring REST Docs).
 *
 * 테스트와 문서가 한 몸이다
 *  - 각 테스트가 실제로 API를 호출(MockMvc)하고, 응답이 기대와 맞아야 통과한다.
 *  - 통과하면 document("이름", ...)이 target/generated-snippets/이름/ 아래에 요청·응답 예시와 필드 표를 만든다.
 *  - 응답에 문서에 없는 필드가 생기거나, 문서에 적은 필드가 사라지면 테스트가 실패한다
 *    -> 문서와 실제 API가 어긋날 수 없다. (Swagger는 어노테이션만 붙어 있으면 틀린 문서도 그대로 나간다)
 *
 * @WebMvcTest
 *  - 웹 계층(CrawlController)만 띄운다. DB, MyBatis, 크롤러는 안 뜬다 -> 빠르고 DB 없이 돈다.
 *  - 컨트롤러가 부르는 서비스 3개는 @MockitoBean(가짜)으로 넣고, given(...)으로 돌려줄 값을 정한다.
 */
@WebMvcTest(CrawlController.class)
@AutoConfigureRestDocs
class CrawlControllerDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CrawlService crawlService;
    @MockitoBean
    private CrawlJobLauncher crawlJobLauncher;
    @MockitoBean
    private CrawlRunService crawlRunService;

    @Test
    @DisplayName("수동 수집: 지정 페이지만 수집하고 결과를 돌려준다")
    void crawl() throws Exception {
        given(crawlService.crawl("java", 2))
                .willReturn(new CrawlResult(21, CrawlStatus.SUCCESS, "java", 2, 20, 3, 17, 0, 4210));

        // 쿼리 문자열로 보낸다. POST에 .param()을 쓰면 REST Docs는 폼 파라미터로 보고 queryParameters 문서화가 실패한다
        mockMvc.perform(post("/api/crawl?keyword=java&maxPages=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andDo(document("crawl-manual",
                        preprocessResponse(prettyPrint()),
                        queryParameters(
                                parameterWithName("keyword").description("검색어 (생략하면 전체)").optional(),
                                parameterWithName("maxPages").description("수집할 페이지 수 1~30 (1페이지 = 10건)").optional()),
                        responseFields(crawlResultFields())));
    }

    @Test
    @DisplayName("전체 수집: 접수만 하고 runId를 바로 돌려준다 (202)")
    void crawlAll() throws Exception {
        given(crawlJobLauncher.submitFull("")).willReturn(25L);

        mockMvc.perform(post("/api/crawl/all"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.runId").value(25))
                .andDo(document("crawl-full",
                        preprocessResponse(prettyPrint()),
                        responseFields(
                                fieldWithPath("runId").type(JsonFieldType.NUMBER).description("수집 번호. 진행 상황 조회에 쓴다"),
                                fieldWithPath("status").type(JsonFieldType.STRING).description("항상 RUNNING (접수됨)"),
                                fieldWithPath("check").type(JsonFieldType.STRING).description("진행 상황 조회 주소"))));
    }

    @Test
    @DisplayName("전체 수집: 다른 수집이 실행 중이면 409")
    void crawlAllConflict() throws Exception {
        given(crawlJobLauncher.submitFull(anyString()))
                .willThrow(new CrawlAlreadyRunningException("이미 실행 중인 수집이 있습니다.", null));

        mockMvc.perform(post("/api/crawl/all"))
                .andExpect(status().isConflict())
                .andDo(document("crawl-full-conflict",
                        preprocessResponse(prettyPrint()),
                        responseFields(
                                fieldWithPath("error").type(JsonFieldType.STRING).description("오류 메시지"))));
    }

    @Test
    @DisplayName("진행 상황: runId로 수집 이력 1건을 조회한다")
    void run() throws Exception {
        given(crawlRunService.find(25L)).willReturn(new CrawlRunView(
                25L, "FULL", "RUNNING", "",
                LocalDateTime.of(2026, 11, 19, 16, 5, 12), null,
                137, 1370, 120, 1250, 0, null));

        mockMvc.perform(get("/api/crawl/runs/{runId}", 25))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageCount").value(137))
                .andDo(document("crawl-run",
                        preprocessResponse(prettyPrint()),
                        pathParameters(parameterWithName("runId").description("수집 번호")),
                        responseFields(
                                fieldWithPath("runId").type(JsonFieldType.NUMBER).description("수집 번호"),
                                fieldWithPath("runType").type(JsonFieldType.STRING).description("MANUAL / FULL / INCREMENTAL"),
                                fieldWithPath("status").type(JsonFieldType.STRING).description("RUNNING / SUCCESS / PARTIAL / FAIL"),
                                fieldWithPath("keyword").type(JsonFieldType.STRING).description("검색어 (빈 값이면 전체)").optional(),
                                fieldWithPath("startedAt").type(JsonFieldType.STRING).description("시작 시각"),
                                fieldWithPath("endedAt").type(JsonFieldType.STRING).description("종료 시각 (진행 중이면 null)").optional(),
                                fieldWithPath("pageCount").type(JsonFieldType.NUMBER).description("지금까지 받은 페이지 수"),
                                fieldWithPath("fetchedCnt").type(JsonFieldType.NUMBER).description("받은 공고 수"),
                                fieldWithPath("insertedCnt").type(JsonFieldType.NUMBER).description("신규 저장"),
                                fieldWithPath("updatedCnt").type(JsonFieldType.NUMBER).description("기존 공고 갱신"),
                                fieldWithPath("failedCnt").type(JsonFieldType.NUMBER).description("저장 실패"),
                                fieldWithPath("errorMsg").type(JsonFieldType.STRING).description("실패 사유").optional())));
    }

    @Test
    @DisplayName("진행 상황: 없는 runId면 404")
    void runNotFound() throws Exception {
        given(crawlRunService.find(999L)).willReturn(null);

        mockMvc.perform(get("/api/crawl/runs/{runId}", 999))
                .andExpect(status().isNotFound())
                .andDo(document("crawl-run-not-found"));
    }

    @Test
    @DisplayName("증분 수집: 새 공고가 끊길 때까지 수집하고 결과를 돌려준다")
    void crawlIncremental() throws Exception {
        given(crawlService.crawlIncremental())
                .willReturn(new CrawlResult(26, CrawlStatus.SUCCESS, "", 10, 20, 2, 18, 0, 3214));

        mockMvc.perform(post("/api/crawl/incremental"))
                .andExpect(status().isOk())
                .andDo(document("crawl-incremental",
                        preprocessResponse(prettyPrint()),
                        responseFields(crawlResultFields())));
    }

    private static org.springframework.restdocs.payload.FieldDescriptor[] crawlResultFields() {
        return new org.springframework.restdocs.payload.FieldDescriptor[]{
                fieldWithPath("runId").type(JsonFieldType.NUMBER).description("수집 번호"),
                fieldWithPath("status").type(JsonFieldType.STRING).description("SUCCESS / PARTIAL / FAIL"),
                fieldWithPath("keyword").type(JsonFieldType.STRING).description("검색어"),
                fieldWithPath("maxPages").type(JsonFieldType.NUMBER).description("최대 페이지 수"),
                fieldWithPath("fetched").type(JsonFieldType.NUMBER).description("받은 공고 수"),
                fieldWithPath("inserted").type(JsonFieldType.NUMBER).description("신규 저장"),
                fieldWithPath("updated").type(JsonFieldType.NUMBER).description("기존 공고 갱신"),
                fieldWithPath("failed").type(JsonFieldType.NUMBER).description("저장 실패"),
                fieldWithPath("elapsedMs").type(JsonFieldType.NUMBER).description("걸린 시간 (ms)")};
    }
}
