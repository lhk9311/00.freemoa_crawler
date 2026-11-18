package com.lhk.freemoa.crawler.convert;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "PHP, php , MySQL,,Apache" 같은 기술스택 원문을 [PHP, MySQL, Apache]로 정리한다.
 */
public class SkillParser {

    public List<String> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        // key: 정규화 키(중복 판단용), value: 처음 등장한 원래 표기(표시용)
        // LinkedHashMap이라 등장 순서가 유지된다. HashMap이면 순서가 뒤죽박죽이 된다.
        Map<String, String> byKey = new LinkedHashMap<>();
        for (String token : raw.split(",")) {
            String name = token.trim();
            if (name.isEmpty()) {
                continue;
            }
            byKey.putIfAbsent(toKey(name), name); // 이미 있는 키면 무시 -> 첫 표기 유지
        }
        return List.copyOf(byKey.values());
    }

    /**
     * 중복 판단용 키: 공백 제거 + 대문자.  "My SQL" / "mysql" -> "MYSQL"
     * DB의 TB_SKILL.SKILL_KEY에도 이 값이 들어간다.
     *
     * Locale.ROOT를 지정하는 이유: 기본 Locale에 따라 대소문자 변환 결과가 달라지는 언어가 있다.
     * (터키어 Locale에서 "i".toUpperCase()는 "I"가 아니라 "İ")
     */
    public static String toKey(String name) {
        return name.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }
}
