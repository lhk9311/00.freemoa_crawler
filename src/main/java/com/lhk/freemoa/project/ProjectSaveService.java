package com.lhk.freemoa.project;

import com.lhk.freemoa.crawler.convert.SkillParser;
import com.lhk.freemoa.crawler.domain.Project;
import com.lhk.freemoa.project.mapper.ProjectMapper;
import com.lhk.freemoa.project.mapper.ProjectSkillMapper;
import com.lhk.freemoa.project.mapper.SkillMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 수집한 공고를 DB에 저장한다.
 *
 * @Transactional: 이 메서드 안의 모든 SQL이 하나의 트랜잭션으로 묶인다.
 *  - 중간에 RuntimeException이 나면 이미 실행한 MERGE/INSERT까지 전부 롤백된다.
 *  - 정상 종료되면 메서드가 끝나는 시점에 한 번에 COMMIT 된다.
 *  - 4단계에서 "300건 중 1건만 실패하면 전부 롤백하는 게 맞나?"를 다룬다.
 */
@Service
public class ProjectSaveService {

    private static final Logger log = LoggerFactory.getLogger(ProjectSaveService.class);

    private final ProjectMapper projectMapper;
    private final SkillMapper skillMapper;
    private final ProjectSkillMapper projectSkillMapper;

    public ProjectSaveService(ProjectMapper projectMapper,
                              SkillMapper skillMapper,
                              ProjectSkillMapper projectSkillMapper) {
        this.projectMapper = projectMapper;
        this.skillMapper = skillMapper;
        this.projectSkillMapper = projectSkillMapper;
    }

    @Transactional
    public SaveResult saveAll(List<Project> projects) {
        if (projects.isEmpty()) {
            return SaveResult.empty(); // 빈 리스트로 IN () 를 만들면 SQL 문법 에러가 난다
        }

        List<Long> projIdxList = projects.stream()
                .map(Project::projIdx)
                .distinct()
                .toList();
        // List.contains는 처음부터 끝까지 훑지만(O(n)), HashSet.contains는 거의 바로 찾는다(O(1))
        Set<Long> existing = new HashSet<>(projectMapper.selectExistingProjIdx(projIdxList));

        // 같은 기술(JAVA 등)을 공고마다 다시 조회하지 않도록, 이번 저장 동안만 쓰는 캐시
        Map<String, Long> skillIdCache = new HashMap<>();

        int inserted = 0;
        int updated = 0;
        for (Project project : projects) {
            projectMapper.merge(project);

            // Set.add는 새로 추가되면 true, 이미 있으면 false를 돌려준다.
            // 페이지를 넘기는 사이 새 공고가 올라오면 같은 공고가 두 페이지에 걸쳐 중복으로 올 수 있는데,
            // 이 방식이면 중복 공고를 신규로 두 번 세지 않는다.
            if (existing.add(project.projIdx())) {
                inserted++;
            } else {
                updated++;
            }

            replaceSkills(project, skillIdCache);
        }

        log.info("공고 저장 완료: 신규 {}건, 갱신 {}건, 사용 기술 {}종", inserted, updated, skillIdCache.size());
        return new SaveResult(inserted, updated);
    }

    private void replaceSkills(Project project, Map<String, Long> skillIdCache) {
        projectSkillMapper.deleteByProjIdx(project.projIdx());

        for (String skillName : project.skills()) {
            String skillKey = SkillParser.toKey(skillName);
            // computeIfAbsent: 캐시에 없을 때만 람다를 실행해서 값을 구하고 캐시에 넣는다
            long skillId = skillIdCache.computeIfAbsent(skillKey, key -> findOrCreateSkillId(key, skillName));
            projectSkillMapper.insert(project.projIdx(), skillId);
        }
    }

    private Long findOrCreateSkillId(String skillKey, String skillName) {
        skillMapper.mergeSkill(skillKey, skillName);
        return skillMapper.selectSkillIdByKey(skillKey);
    }
}
