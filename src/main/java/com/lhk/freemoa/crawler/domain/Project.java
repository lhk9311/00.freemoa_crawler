package com.lhk.freemoa.crawler.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 우리 프로그램 안에서 쓰는 공고 객체.
 * FreemoaListResponse.Item(외부 형식, 전부 String)과 달리 타입이 정리된 상태다.
 * 외부 API 형식이 바뀌어도 ProjectConverter만 고치면 되고, 이 클래스를 쓰는 나머지 코드는 그대로다.
 *
 * 숫자 필드가 int가 아니라 Integer인 이유: "값 없음"(null)을 표현해야 해서.
 * int는 기본값이 0이라 "예산 0원"과 "예산 미입력"을 구분할 수 없다.
 */
public record Project(
        long projIdx,
        String title,
        String category,
        String subCategory,
        String region,
        String planStatus,
        String workType,
        Integer durationDays,
        Integer costMin,
        Integer costMax,
        int applyCount,
        String skillRaw,
        List<String> skills,
        String summary,
        LocalDateTime postedAt,
        LocalDate deadline
) {

    /**
     * 컴팩트 생성자: record의 생성자에 검증/보정 로직을 넣는 문법.
     * 잘못된 객체가 아예 만들어지지 않도록 생성 시점에 막는다.
     */
    public Project {
        Objects.requireNonNull(title, "title은 필수");
        // List.copyOf: 불변 리스트로 복사. 밖에서 원본 리스트를 바꿔도 이 객체는 영향받지 않는다.
        skills = (skills == null) ? List.of() : List.copyOf(skills);
    }

    public boolean hasBudget() {
        return costMax != null;
    }
}
