package com.lhk.freemoa.crawler.convert;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillParserTest {

    private final SkillParser parser = new SkillParser();

    @Test
    @DisplayName("콤마로 나누고, 공백을 정리하고, 대소문자만 다른 중복은 첫 표기만 남긴다")
    void parse_dedupeIgnoringCase() {
        assertThat(parser.parse("PHP, php , MySQL,,Apache"))
                .containsExactly("PHP", "MySQL", "Apache");
    }

    @Test
    @DisplayName("null이나 빈 문자열이면 빈 리스트 (null을 돌려주지 않는다)")
    void parse_blank() {
        assertThat(parser.parse(null)).isEmpty();
        assertThat(parser.parse("   ")).isEmpty();
    }

    @Test
    @DisplayName("정규화 키는 공백 제거 + 대문자")
    void toKey() {
        assertThat(SkillParser.toKey("My SQL")).isEqualTo("MYSQL");
        assertThat(SkillParser.toKey("spring boot")).isEqualTo("SPRINGBOOT");
    }
}
