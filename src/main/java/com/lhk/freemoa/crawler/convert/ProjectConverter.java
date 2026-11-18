package com.lhk.freemoa.crawler.convert;

import com.lhk.freemoa.crawler.api.dto.FreemoaListResponse;
import com.lhk.freemoa.crawler.domain.Project;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 외부 형식(FreemoaListResponse.Item, 전부 String) -> 내부 형식(Project) 변환.
 * 변환 규칙을 한 곳에 모아두면 규칙이 바뀌어도 여기만 고치면 된다.
 */
public class ProjectConverter {

    // DateTimeFormatter는 불변 + 스레드 안전이라 static 상수로 공유해도 된다.
    // (예전 SimpleDateFormat은 스레드 안전하지 않아서 이렇게 쓰면 버그가 났다)
    private static final DateTimeFormatter POSTED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SkillParser skillParser;

    public ProjectConverter(SkillParser skillParser) {
        this.skillParser = skillParser;
    }

    public Project convert(FreemoaListResponse.Item item) {
        return new Project(
                Long.parseLong(item.projIdx().trim()),
                item.title().trim(),
                blankToNull(item.category()),
                blankToNull(item.subCategory()),
                blankToNull(item.region()),
                blankToNull(item.planStatus()),
                blankToNull(item.workType()),
                positiveOrNull(item.during()),
                positiveOrNull(item.costMin()),
                positiveOrNull(item.costMax()),
                zeroIfBlank(item.applyCount()),
                blankToNull(item.skillRaw()),
                skillParser.parse(item.skillRaw()),
                blankToNull(item.summary()),
                parseDateTime(item.postedAt()),
                parseDate(item.deadline())
        );
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /** 프리모아는 예산/기간 미입력을 "0"으로 준다. 0은 "모름"이므로 null로 바꾼다 */
    private static Integer positiveOrNull(String value) {
        String v = blankToNull(value);
        if (v == null) {
            return null;
        }
        int number = Integer.parseInt(v);
        return number > 0 ? number : null;
    }

    private static int zeroIfBlank(String value) {
        String v = blankToNull(value);
        return v == null ? 0 : Integer.parseInt(v);
    }

    private static LocalDateTime parseDateTime(String value) {
        String v = blankToNull(value);
        return v == null ? null : LocalDateTime.parse(v, POSTED_AT_FORMAT);
    }

    private static LocalDate parseDate(String value) {
        String v = blankToNull(value);
        return v == null ? null : LocalDate.parse(v); // 기본 형식 yyyy-MM-dd
    }
}
