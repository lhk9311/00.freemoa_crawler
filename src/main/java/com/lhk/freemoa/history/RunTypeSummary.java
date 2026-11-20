package com.lhk.freemoa.history;

import java.time.LocalDateTime;

/**
 * [JPA] 인터페이스 프로젝션: 엔티티 전체가 아니라 필요한 값만 받을 때.
 * JPQL의 별칭(as runType)과 getter 이름(getRunType)이 같으면 스프링이 구현체를 만들어 채워 준다.
 */
public interface RunTypeSummary {
    String getRunType();
    Long getRunCount();
    Long getTotalInserted();
    LocalDateTime getLastStartedAt();
}
