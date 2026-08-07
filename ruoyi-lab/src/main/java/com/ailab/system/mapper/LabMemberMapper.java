package com.ailab.system.mapper;

import com.ailab.system.domain.LabMember;
import com.ailab.system.domain.LabMemberSkill;
import com.ailab.system.domain.LabSkill;
import com.ailab.system.domain.LabTask;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface LabMemberMapper {
    List<LabMember> selectMemberList(LabMember query);
    List<LabMember> selectAvailableSystemUsers();
    LabMember lockActiveSystemUser(Long userId);
    LabMember selectMemberById(Long id);
    LabMember selectMemberForUpdate(Long id);
    LabMember selectMemberByUserId(Long userId);
    List<LabMember> lockMembersForUpdate(@Param("ids") List<Long> ids);
    int insertMember(LabMember member);
    int updateMember(LabMember member);
    int deactivateMember(@Param("id") Long id, @Param("version") Integer version, @Param("updateBy") String updateBy);
    int reactivateMember(@Param("id") Long id, @Param("version") Integer version, @Param("updateBy") String updateBy);

    List<LabSkill> selectSkillList(LabSkill query);
    LabSkill selectSkillById(Long id);
    LabSkill selectSkillForUpdate(Long id);
    LabSkill selectSkillByNameForUpdate(@Param("skillName") String skillName, @Param("excludeId") Long excludeId);
    List<LabSkill> lockSkillsForUpdate(@Param("ids") List<Long> ids);
    int insertSkill(LabSkill skill);
    int updateSkill(LabSkill skill);

    List<LabMemberSkill> selectMemberSkills(@Param("memberId") Long memberId, @Param("activeSkillsOnly") boolean activeSkillsOnly);
    List<LabMemberSkill> selectMemberSkillsForUpdate(Long memberId);
    int insertMemberSkill(LabMemberSkill skill);
    int updateMemberSkill(LabMemberSkill skill);
    int deleteMemberSkill(@Param("id") Long id, @Param("version") Integer version, @Param("updateBy") String updateBy);
    List<LabTask> selectRecentTasks(@Param("memberId") Long memberId, @Param("limit") int limit);
}
