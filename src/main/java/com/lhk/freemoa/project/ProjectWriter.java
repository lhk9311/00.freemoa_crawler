package com.lhk.freemoa.project;

import com.lhk.freemoa.crawler.convert.SkillParser;
import com.lhk.freemoa.crawler.domain.Project;
import com.lhk.freemoa.project.mapper.ProjectMapper;
import com.lhk.freemoa.project.mapper.ProjectSkillMapper;
import com.lhk.freemoa.project.mapper.SnapshotMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 공고 "1건"을 저장하는 트랜잭션 단위.
 *
 * 공고 마스터 MERGE + 기술 매핑 재등록 + 스냅샷 INSERT 는 한 묶음이다.
 * 이 중 하나라도 실패하면 이 공고에 대한 변경은 전부 롤백된다. (매핑만 지워지고 공고는 남는 식의 반쪽 상태 방지)
 * 하지만 다른 공고의 저장 결과에는 영향을 주지 않는다.
 *
 * ProjectSaveService 안의 private 메서드로 두지 않고 별도 빈으로 분리한 이유:
 * 같은 클래스에서 this.saveOne()을 부르면 프록시를 안 거쳐서 @Transactional이 동작하지 않는다.
 */
@Component
public class ProjectWriter {

    private final ProjectMapper projectMapper;
    private final ProjectSkillMapper projectSkillMapper;
    private final SnapshotMapper snapshotMapper;
    private final SkillService skillService;

    public ProjectWriter(ProjectMapper projectMapper,
                         ProjectSkillMapper projectSkillMapper,
                         SnapshotMapper snapshotMapper,
                         SkillService skillService) {
        this.projectMapper = projectMapper;
        this.projectSkillMapper = projectSkillMapper;
        this.snapshotMapper = snapshotMapper;
        this.skillService = skillService;
    }

    @Transactional
    public void saveOne(long runId, Project project, Map<String, Long> skillIdCache) {
        projectMapper.merge(project);

        projectSkillMapper.deleteByProjIdx(project.projIdx());
        for (String skillName : project.skills()) {
            String skillKey = SkillParser.toKey(skillName);
            long skillId = skillIdCache.computeIfAbsent(skillKey,
                    key -> skillService.findOrCreateSkillId(key, skillName));
            projectSkillMapper.insert(project.projIdx(), skillId);
        }

        snapshotMapper.insert(runId, project);
    }
}
