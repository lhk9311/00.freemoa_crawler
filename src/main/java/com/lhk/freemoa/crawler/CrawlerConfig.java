package com.lhk.freemoa.crawler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhk.freemoa.crawler.api.FreemoaApiClient;
import com.lhk.freemoa.crawler.convert.ProjectConverter;
import com.lhk.freemoa.crawler.convert.SkillParser;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * [3단계] PlainCrawlerMain의 "조립" 코드를 스프링 설정으로 옮긴 것.
 *
 * PlainCrawlerMain                                  | CrawlerConfig
 * -------------------------------------------------+-------------------------------------------
 * HttpClient httpClient = HttpClient.newBuilder()  | @Bean HttpClient freemoaHttpClient()
 * ObjectMapper objectMapper = new ObjectMapper();  | (스프링 부트가 이미 빈으로 만들어 둠)
 * new FreemoaApiClient(httpClient, objectMapper..) | @Bean FreemoaApiClient freemoaApiClient(HttpClient, ObjectMapper, ...)
 * ...                                              | ...
 *
 * 차이점
 * 1. 생성 "순서"를 내가 정하지 않는다. 메서드 파라미터에 필요한 타입만 적으면 스프링이 의존 관계를 보고 알아서 순서를 정한다.
 * 2. 각 객체는 기본적으로 싱글톤 1개만 만들어져서, 필요한 곳 어디에나 같은 객체가 주입된다.
 * 3. 설정값(baseUrl, delay)이 코드가 아니라 application.yml에 있다.
 *
 * @Bean vs @Component
 * - HttpClient처럼 내가 소스를 고칠 수 없는 클래스는 @Component를 붙일 수 없으니 @Bean으로 등록한다.
 * - 크롤러 클래스들은 @Component를 붙여도 되지만, PlainCrawlerMain과 1:1로 비교하려고 일부러 @Bean으로 모았다.
 * - 반대로 ProjectSaveService, CrawlService는 @Service(= @Component)로 등록했다. 두 방식을 비교해볼 것.
 */
@Configuration
@EnableConfigurationProperties(CrawlerProperties.class)
public class CrawlerConfig {

    @Bean
    public HttpClient freemoaHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)) // timeout 설정 -> 최장 연결 5초 세팅
                .build();
    }

    @Bean
    public FreemoaApiClient freemoaApiClient(HttpClient freemoaHttpClient,
                                             ObjectMapper objectMapper,
                                             CrawlerProperties properties) {
        return new FreemoaApiClient(freemoaHttpClient, objectMapper, properties.baseUrl());
    }

    @Bean
    public SkillParser skillParser() {
        return new SkillParser();
    }

    @Bean
    public ProjectConverter projectConverter(SkillParser skillParser) {
        return new ProjectConverter(skillParser);
    }

    @Bean
    public ProjectCollector projectCollector(FreemoaApiClient freemoaApiClient,
                                             ProjectConverter projectConverter,
                                             CrawlerProperties properties) {
        return new ProjectCollector(freemoaApiClient, projectConverter, properties.delay());
    }
}
