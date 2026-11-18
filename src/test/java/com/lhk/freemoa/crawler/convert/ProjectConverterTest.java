package com.lhk.freemoa.crawler.convert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhk.freemoa.crawler.api.dto.FreemoaListResponse;
import com.lhk.freemoa.crawler.domain.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 네트워크 없이 저장해둔 JSON 샘플(fixture)로 파싱 + 변환을 검증한다.
 * 실제 사이트에 의존하는 테스트는 사이트가 느리거나 바뀌면 깨지기 때문에, 변환 로직은 이렇게 분리해서 테스트한다.
 */
class ProjectConverterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProjectConverter converter = new ProjectConverter(new SkillParser());

    private FreemoaListResponse response;

    @BeforeEach
    void setUp() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/freemoa-list-sample.json")) {
            response = objectMapper.readValue(in, FreemoaListResponse.class);
        }
    }

    @Test
    @DisplayName("응답 JSON이 DTO로 파싱된다 (문자열 totalRows -> int)")
    void parseResponse() {
        assertThat(response.error().no()).isZero();
        assertThat(response.data().project().list()).hasSize(2);
        assertThat(response.data().project().pagination().totalRows()).isEqualTo(4359);
    }

    @Test
    @DisplayName("예산 0은 null(협의), 날짜는 LocalDate/LocalDateTime으로, 제목은 trim")
    void convert_budgetUnknown() {
        Project p = converter.convert(items().get(0));

        assertThat(p.projIdx()).isEqualTo(48588L);
        assertThat(p.title()).isEqualTo("기 운영중인 카페24기반 반응형웹의 PHP 버전 업데이트");
        assertThat(p.costMin()).isNull();
        assertThat(p.costMax()).isNull();
        assertThat(p.hasBudget()).isFalse();
        assertThat(p.durationDays()).isEqualTo(30);
        assertThat(p.applyCount()).isEqualTo(48);
        assertThat(p.deadline()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(p.postedAt()).isEqualTo(LocalDateTime.of(2026, 11, 17, 13, 52, 38));
        assertThat(p.skills()).containsExactly("PHP", "php 8.5", "Linux 8.1", "MySQL", "Apache");
    }

    @Test
    @DisplayName("예산이 있으면 숫자로, 빈 기술스택/본문은 빈 리스트/null")
    void convert_withBudget() {
        Project p = converter.convert(items().get(1));

        assertThat(p.costMin()).isEqualTo(8000);
        assertThat(p.costMax()).isEqualTo(10000);
        assertThat(p.skills()).isEmpty();
        assertThat(p.skillRaw()).isNull();
        assertThat(p.summary()).isNull();
    }

    private List<FreemoaListResponse.Item> items() {
        return response.data().project().list();
    }
}
