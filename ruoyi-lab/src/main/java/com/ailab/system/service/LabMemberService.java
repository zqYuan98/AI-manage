package com.ailab.system.service;

import com.ailab.system.domain.LabMember;
import com.ailab.system.domain.LabMemberSkill;
import com.ailab.system.domain.LabSkill;
import com.ailab.system.dto.LabMemberDetail;
import java.util.List;

public interface LabMemberService {
    List<LabMember> listMembers(LabMember query, Long actorId);
    List<LabMember> listAvailableSystemUsers(Long actorId);
    LabMemberDetail getMemberDetail(Long memberId, Long actorId);
    int createMember(LabMember member, Long actorId);
    int updateMember(LabMember member, Long actorId);
    int deactivateMember(Long memberId, Integer version, Long actorId);
    int reactivateMember(Long memberId, Integer version, Long actorId);
    List<LabSkill> listSkills(LabSkill query, Long actorId);
    int createSkill(LabSkill skill, Long actorId);
    int updateSkill(LabSkill skill, Long actorId);
    List<LabMemberSkill> getSkillMatrix(Long memberId, Long actorId);
    int saveSkillMatrix(Long memberId, List<LabMemberSkill> skills, Long actorId);
}
