package com.lhk.freemoa.project.query;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 조회 전용 서비스.
 *
 * readOnly = true
 * - "이 메서드는 조회만 한다"는 의도를 표시한다.
 * - 실수로 INSERT/UPDATE를 넣으면 DB나 드라이버에 따라 막아주기도 한다.
 * - 목록과 건수를 같은 트랜잭션에서 읽어서, 그 사이에 수집이 끼어들어도 두 결과가 덜 어긋난다.
 */
@Service
@Transactional(readOnly = true)
public class ProjectQueryService {

    public static final int MAX_LIMIT = 200;

    private final ProjectQueryMapper mapper;

    public ProjectQueryService(ProjectQueryMapper mapper) {
        this.mapper = mapper;
    }

    public record Page(List<ProjectRow> rows, int totalCount) {
    }

    public Page search(ProjectSearch search) {
        if (search.getLimit() < 1 || search.getLimit() > MAX_LIMIT) {
            throw new IllegalArgumentException("한 번에 조회할 수 있는 건수는 1~" + MAX_LIMIT + "건입니다");
        }
        if (search.getOffset() < 0) {
            throw new IllegalArgumentException("offset은 0 이상이어야 합니다");
        }
        return new Page(mapper.selectProjects(search), mapper.countProjects(search));
    }

    public List<SkillCount> topSkills(int limit) {
        return mapper.selectTopSkills(limit);
    }
}
