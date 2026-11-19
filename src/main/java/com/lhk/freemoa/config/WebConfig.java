package com.lhk.freemoa.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS 설정.
 *
 * 왜 필요한가
 * - 넥사크로 화면을 브라우저로 실행하면 화면은 http://localhost:4098 (Studio 로컬 웹서버)에서 뜨고,
 *   API는 http://localhost:8080 에 있다. 포트가 다르면 "다른 출처(origin)"다.
 * - 브라우저는 보안상 다른 출처로 보내는 요청의 응답을, 서버가 허락하지 않으면 스크립트에 넘겨주지 않는다.
 *   서버는 Access-Control-Allow-Origin 헤더로 "이 출처는 허락한다"고 답한다.
 *
 * Preflight(사전 요청)
 * - 넥사크로는 Content-Type: text/xml 로 POST 한다. 이건 "단순 요청"이 아니라서
 *   브라우저가 진짜 요청 전에 OPTIONS 요청을 먼저 보내 허락을 묻는다. 그래서 OPTIONS도 허용해야 한다.
 *
 * 넥사크로 런처(NRE, 브라우저가 아닌 전용 실행기)로 실행하면 CORS 검사 자체가 없다. CORS는 "브라우저" 규칙이다.
 *
 * 주의: allowedOrigins("*")로 전부 열어버리면 아무 사이트에서나 이 API를 호출해 결과를 읽을 수 있다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public WebConfig(@Value("${nexacro.allowed-origins}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/nx/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "OPTIONS") // GET: 인자 없는 넥사크로 transaction (/nx/skills)
                .allowedHeaders("*")
                .maxAge(3600); // preflight 결과를 브라우저가 1시간 캐시 -> 매번 OPTIONS를 보내지 않음
    }
}
