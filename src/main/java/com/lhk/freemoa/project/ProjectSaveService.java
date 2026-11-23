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
 *
 * 흐름에서의 위치 (docs/12-crawling-flow.md 그림의 "6. 저장")
 *   CrawlService ──(한 페이지 10건)──▶ ProjectSaveService.saveAll   <- 이 클래스: 반복 + 집계 담당 (관리자)
 *                                         for 10번 ──▶ ProjectWriter.saveOne  <- 실제 SQL 실행 (1건 = 트랜잭션 1개)
 *                ◀── SaveResult(신규 3, 갱신 7, 실패 0)
 *
 * 왜 저장을 ProjectWriter라는 다른 클래스로 뺐나 (self-invocation 함정, docs/04 참고)
 *  - saveOne을 이 클래스 안의 메서드로 두고 @Transactional을 붙이면 트랜잭션이 걸리지 않는다.
 *  - 스프링 트랜잭션은 "다른 객체가 부를 때" 끼어드는 프록시가 처리하는데,
 *    같은 클래스 안에서 this.saveOne()으로 부르면 프록시를 거치지 않기 때문이다.
 *  - 그래서 반드시 다른 빈(ProjectWriter)으로 분리해서 부른다.
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

    /**
     * 한 페이지(보통 10건)를 저장하고 신규/갱신/실패 건수를 돌려준다.
     *
     * 예) 10건 중 48588, 48601은 이미 DB에 있고, 48700이 두 번 들어온 경우
     *     -> 중복 제거 후 9건, 신규 7 / 갱신 2 / 실패 0
     */
    public SaveResult saveAll(long runId, List<Project> projects) {
        // ① 중복 제거: 같은 공고번호가 두 번 오면 하나만 남긴다 (이유는 dedupe 주석)
        List<Project> unique = dedupe(projects);

        // ② 빈 목록이면 바로 끝낸다
        if (unique.isEmpty()) {
            return SaveResult.empty(); // 빈 리스트로 IN () 를 만들면 SQL 문법 에러
        }

        // ③ 이미 DB에 있는 공고번호를 저장 "전에" 한 번에 조회한다
        //    - 왜 미리? MERGE는 INSERT를 했는지 UPDATE를 했는지 알려주지 않는다 (로그에 Updates: 1로만 나옴).
        //      그래서 신규/갱신을 세려면 저장 전에 "원래 있었나"를 알아둬야 한다.
        //    - 왜 IN으로 한 번에? 10건을 따로 SELECT하면 SQL이 10번 나간다. IN으로 묶으면 1번.
        //      SELECT PROJ_IDX FROM TB_PROJECT WHERE PROJ_IDX IN (48588, 48601, 48700, ...)
        //    - 왜 HashSet? 아래 for문에서 contains("있나?")를 바로 찾으려고. List는 처음부터 하나씩 비교해야 한다.
        List<Long> projIdxList = unique.stream().map(Project::projIdx).toList();
        Set<Long> existing = new HashSet<>(projectMapper.selectExistingProjIdx(projIdxList));

        // ④ 기술 ID 캐시: "JAVA" -> SKILL_ID 1 을 이 페이지 안에서 기억해 둔다.
        //    10건 중 5건이 JAVA를 쓰면, 처음 1번만 DB에서 찾고 나머지 4번은 이 Map에서 꺼내 쓴다.
        Map<String, Long> skillIdCache = new HashMap<>();

        int inserted = 0;
        int updated = 0;
        int failed = 0;
        String firstError = null;

        // ⑤ 1건씩 저장 + 집계
        //    핵심: try-catch가 for문 "안쪽"에 있다. 1건이 터져도 그 건만 실패로 세고 다음 건으로 계속 간다.
        //    try-catch가 for문 바깥에 있었다면 1건 실패로 반복 자체가 거기서 끝난다.
        for (Project project : unique) {
            try {
                projectWriter.saveOne(runId, project, skillIdCache); // 이 1건만 트랜잭션으로 저장 (MERGE, 기술, 연결, 스냅샷)
                if (existing.contains(project.projIdx())) {
                    updated++;  // ③에서 원래 있던 공고 -> 갱신
                } else {
                    inserted++; // 원래 없던 공고 -> 신규
                }
            } catch (RuntimeException e) {
                // 이 공고의 트랜잭션은 이미 롤백됐다. 기록만 하고 다음 공고로 넘어간다.
                failed++;
                if (firstError == null) {
                    // 첫 에러 하나만 기억한다 -> CrawlService가 TB_CRAWL_RUN.ERROR_MSG에 남긴다
                    firstError = "projIdx=" + project.projIdx() + " : " + e.getMessage();
                }
                log.warn("공고 저장 실패 projIdx={}", project.projIdx(), e);
            }
        }

        // ⑥ 결과를 돌려준다. CrawlService가 페이지마다 누적해서 TB_CRAWL_RUN에 기록한다.
        log.info("공고 저장 완료: 신규 {}건, 갱신 {}건, 실패 {}건", inserted, updated, failed);
        return new SaveResult(inserted, updated, failed, firstError);
    }

    /**
     * 페이지를 넘기는 사이 새 공고가 올라오면, 이전 페이지 마지막 공고가 다음 페이지 첫 번째로 밀려서 중복으로 온다.
     * 스냅샷 PK(RUN_ID, PROJ_IDX)에 걸리지 않도록 공고번호 기준으로 중복을 제거한다. (나중 것 유지, 순서는 처음 위치)
     *
     * 동작 원리
     *  - Map에 같은 키(공고번호)로 put하면 값이 덮어써진다 -> 공고번호당 하나만 남고, 나중 것이 유지된다.
     *  - LinkedHashMap은 처음 넣은 순서를 기억한다. 덮어써도 위치는 처음 자리 그대로다.
     *    (HashMap이면 순서가 뒤섞인다)
     */
    private static List<Project> dedupe(List<Project> projects) {
        Map<Long, Project> byId = new LinkedHashMap<>();
        for (Project p : projects) {
            byId.put(p.projIdx(), p);
        }
        return new ArrayList<>(byId.values());
    }
}
