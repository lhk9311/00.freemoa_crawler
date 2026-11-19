package com.lhk.freemoa.crawler.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhk.freemoa.crawler.api.dto.FreemoaListResponse;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 프리모아 목록 API를 호출하는 클라이언트.
 * HTTP 통신과 JSON 파싱"만" 책임진다. 데이터 가공은 ProjectConverter가 한다. (단일 책임 원칙)
 *
 * 의존 객체(HttpClient, ObjectMapper)를 내부에서 new 하지 않고 생성자로 받는다.
 * -> 테스트할 때 가짜 객체로 바꿔 끼울 수 있고, 3단계에서 스프링이 대신 넣어줄 수 있다. (이게 DI)
 */
public class FreemoaApiClient {

    private static final String LIST_PATH = "/m4a/s41a";
    /** 목록 API를 실제로 호출하는 프리모아 화면 주소. Referer 헤더에 쓴다 */
    private static final String LIST_PAGE_PATH = "/m4/s41";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    // 어떤 프로그램이 요청하는지 사이트 운영자가 알 수 있도록 정직하게 밝힌다
    private static final String USER_AGENT = "freemoa-crawler-study/0.1 (personal learning project)";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public FreemoaApiClient(HttpClient httpClient, ObjectMapper objectMapper, String baseUrl) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
    }

    /**
     * 공고 목록 한 페이지(10건)를 가져온다.
     *
     * @param keyword 검색어 (빈 문자열이면 전체)
     * @param page    1부터 시작
     */
    public FreemoaListResponse fetchList(String keyword, int page) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + LIST_PATH))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("User-Agent", USER_AGENT)
                // 이 API는 프리모아 목록 화면의 Ajax 요청에만 응답한다. 두 헤더가 없으면 HTTP 404.
                // 브라우저가 화면에서 보내는 헤더를 그대로 재현한 것 (curl로 하나씩 빼보며 확인함)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", baseUrl + LIST_PAGE_PATH)
                .POST(HttpRequest.BodyPublishers.ofString(buildForm(keyword, page)))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new FreemoaApiException("프리모아 요청 실패 (page=" + page + ")", e);
        } catch (InterruptedException e) {
            // 인터럽트 신호를 삼키면 안 된다. 상태를 복구해서 상위 코드도 "중단 요청"을 알 수 있게 한다.
            Thread.currentThread().interrupt();
            throw new FreemoaApiException("프리모아 요청 중 인터럽트 (page=" + page + ")", e);
        }

        if (response.statusCode() != 200) {
            throw new FreemoaApiException("프리모아 응답 코드 이상: HTTP " + response.statusCode() + " (page=" + page + ")");
        }

        FreemoaListResponse body;
        try {
            // 프리모아는 Content-Type을 text/html로 주지만 실제 내용은 JSON이다
            body = objectMapper.readValue(response.body(), FreemoaListResponse.class);
        } catch (JsonProcessingException e) {
            throw new FreemoaApiException("프리모아 응답 JSON 파싱 실패 (page=" + page + ")", e);
        }

        if (body.error() != null && body.error().no() != 0) {
            throw new FreemoaApiException("프리모아 에러 응답: " + body.error().msg());
        }
        return body;
    }

    /** 브라우저가 실제로 보내는 폼 파라미터를 그대로 재현한다: sS=java&page=1&mp=0&lp=0&st=stayAll&st2=&st3= */
    private String buildForm(String keyword, int page) {
        Map<String, String> params = new LinkedHashMap<>(); // 입력 순서 유지
        params.put("sS", keyword == null ? "" : keyword);
        params.put("page", String.valueOf(page));
        params.put("mp", "0");
        params.put("lp", "0");
        params.put("st", "stayAll");
        params.put("st2", "");
        params.put("st3", "");

        return params.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    /** 한글 검색어는 URL 인코딩해야 한다 ("자바" -> %EC%9E%90%EB%B0%94) */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
