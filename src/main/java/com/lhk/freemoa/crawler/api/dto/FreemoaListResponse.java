package com.lhk.freemoa.crawler.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 프리모아 목록 API(POST /m4a/s41a)의 응답을 "있는 그대로" 받는 DTO.
 *
 * 응답 구조:
 * {
 *   "ERROR": { "NO": 0, "MSG": "" },
 *   "DATA": { "PROJECT": { "LIST": [ ... ], "PAGINATION": { ... } } }
 * }
 *
 * - record: 필드 + 생성자 + getter + equals/hashCode/toString을 자동으로 만들어주는 불변 클래스 (Java 16+)
 * - 값은 대부분 String 그대로 받는다. 프리모아는 숫자도 "8000"처럼 문자열로 주기 때문이다.
 *   타입 변환(String -> Integer, LocalDate)은 ProjectConverter에서 한 곳에 모아서 한다.
 * - @JsonIgnoreProperties(ignoreUnknown = true): 우리가 안 쓰는 필드(client_id 등)는 무시
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FreemoaListResponse(
        @JsonProperty("ERROR") ApiError error,
        @JsonProperty("DATA") Data data
) {

    /** 프리모아 자체 에러 정보. NO가 0이 아니면 실패 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiError(
            @JsonProperty("NO") int no,
            @JsonProperty("MSG") String msg
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(
            @JsonProperty("PROJECT") ProjectSection project
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProjectSection(
            @JsonProperty("LIST") List<Item> list,
            @JsonProperty("PAGINATION") Pagination pagination
    ) {
    }

    /** totalRows는 "4359"처럼 문자열로 오지만 Jackson이 int로 자동 변환해준다 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pagination(
            int currentPage,
            int totalRows,
            Integer nextPage
    ) {
    }

    /** 공고 1건. 개인정보(client_id 등)는 아예 필드로 선언하지 않는다 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            @JsonProperty("proj_idx") String projIdx,
            @JsonProperty("title") String title,
            @JsonProperty("fld") String category,
            @JsonProperty("fld_nm_2nd") String subCategory,
            @JsonProperty("pv_smallnm") String region,
            @JsonProperty("plan_nm") String planStatus,
            @JsonProperty("workType") String workType,
            @JsonProperty("during") String during,
            @JsonProperty("cost_min") String costMin,
            @JsonProperty("cost_max") String costMax,
            @JsonProperty("ALL_APPLY_COUNT") String applyCount,
            @JsonProperty("proj_language") String skillRaw,
            @JsonProperty("txt") String summary,
            @JsonProperty("INS_TIME") String postedAt,
            @JsonProperty("edate") String deadline
    ) {
    }
}
