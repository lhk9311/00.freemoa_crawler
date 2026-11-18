package com.lhk.freemoa.project;

import com.lhk.freemoa.crawler.domain.Project;
import com.lhk.freemoa.project.mapper.ProjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 수집한 공고 목록을 저장한다.
 *
 * [3단계] saveAll 전체가 트랜잭션 1개 -> 1건만 실패해도 300건 전부 롤백
 * [4단계] 공고 1건 = 트랜잭션 1개 (ProjectWriter.saveOne)
 *         -> 실패한 건만 롤백되고 나머지는 저장된다. 실패 건수와 원인은 이력에 남긴다.
 *
 * 어느 쪽이 맞는지는 업무에 따라 다르다.
 *  - 계좌 이체처럼 "전부 아니면 전무"여야 하는 일 -> 전체 1트랜잭션
 *  - 공고 수집처럼 건마다 독립적인 일 -> 건별 트랜잭션 (1건 때문에 299건을 버릴 이유가 없다)
 *
 * 그래서 이 클래스에는 @Transactional이 없다. 붙이면 saveOne의 트랜잭션들이
 * 바깥 트랜잭션에 합류(REQUIRED)해서 다시 "1건 실패 = 전체 롤백"이 된다.
 */
@Service
public class ProjectSaveService {

    private static final Logger log = LoggerFactory.getLogger(ProjectSaveService.class);

    private final ProjectMapper projectMapper;
    private final ProjectWriter projectWriter;

    public ProjectSaveService(ProjectMapper projectMapper, ProjectWriter projectWriter) {
        this.projectMapper = projectMapper;
        this.projectWriter = projectWriter;
    }

    public SaveResult saveAll(long runId, List<Project> projects) {
        List<Project> unique = dedupe(projects);
        if (unique.isEmpty()) {
            return SaveResult.empty(); // 빈 리스트로 IN () 를 만들면 SQL 문법 에러
        }

        List<Long> projIdxList = unique.stream().map(Project::projIdx).toList();
        Set<Long> existing = new HashSet<>(projectMapper.selectExistingProjIdx(projIdxList));
        Map<String, Long> skillIdCache = new HashMap<>();

        int inserted = 0;
        int updated = 0;
        int failed = 0;
        String firstError = null;

        for (Project project : unique) {
            try {
                projectWriter.saveOne(runId, project, skillIdCache);
                if (existing.contains(project.projIdx())) {
                    updated++;
                } else {
                    inserted++;
                }
            } catch (RuntimeException e) {
                // 이 공고의 트랜잭션은 이미 롤백됐다. 기록만 하고 다음 공고로 넘어간다.
                failed++;
                if (firstError == null) {
                    firstError = "projIdx=" + project.projIdx() + " : " + e.getMessage();
                }
                log.warn("공고 저장 실패 projIdx={}", project.projIdx(), e);
            }
        }

        log.info("공고 저장 완료: 신규 {}건, 갱신 {}건, 실패 {}건", inserted, updated, failed);
        return new SaveResult(inserted, updated, failed, firstError);
    }

    /**
     * 페이지를 넘기는 사이 새 공고가 올라오면, 이전 페이지 마지막 공고가 다음 페이지 첫 번째로 밀려서 중복으로 온다.
     * 스냅샷 PK(RUN_ID, PROJ_IDX)에 걸리지 않도록 공고번호 기준으로 중복을 제거한다. (나중 것 유지, 순서는 처음 위치)
     */
    private static List<Project> dedupe(List<Project> projects) {
        Map<Long, Project> byId = new LinkedHashMap<>();
        for (Project p : projects) {
            byId.put(p.projIdx(), p);
        }
        return new ArrayList<>(byId.values());
    }
}
