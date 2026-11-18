package com.lhk.freemoa.project;

import com.lhk.freemoa.project.mapper.SkillMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기술스택 마스터(TB_SKILL) 등록/조회.
 *
 * REQUIRES_NEW를 쓰는 이유 (실제로 터지는 버그를 막기 위해서)
 *
 * 공고는 1건씩 각자의 트랜잭션으로 저장한다(ProjectWriter). 그리고 SKILL_ID는 캐시에 담아 재사용한다.
 * 만약 기술 등록이 "공고 트랜잭션 안"(REQUIRED)에서 일어난다면:
 *   1. 공고 A 저장 중 새 기술 "Rust" 등록 -> SKILL_ID 7번, 캐시에 RUST=7 저장
 *   2. 공고 A의 다른 SQL이 실패 -> 공고 A 트랜잭션 롤백 -> "Rust" 행도 같이 사라짐
 *   3. 공고 B 저장 시 캐시에서 RUST=7을 꺼내 매핑 INSERT -> 7번 기술이 없으니 FK 위반(ORA-02291)
 *
 * 기술 마스터는 어떤 공고의 저장 성공 여부와 상관없이 남아도 되는 데이터다.
 * 그래서 별도 트랜잭션으로 즉시 커밋해서, 캐시에 들어간 SKILL_ID가 항상 DB에 실제로 존재하게 만든다.
 */
@Service
public class SkillService {

    private final SkillMapper skillMapper;

    public SkillService(SkillMapper skillMapper) {
        this.skillMapper = skillMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long findOrCreateSkillId(String skillKey, String skillName) {
        skillMapper.mergeSkill(skillKey, skillName);
        return skillMapper.selectSkillIdByKey(skillKey);
    }
}
