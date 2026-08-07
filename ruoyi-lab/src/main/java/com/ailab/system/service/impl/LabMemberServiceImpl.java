package com.ailab.system.service.impl;

import com.ailab.system.domain.LabAsset;
import com.ailab.system.domain.LabMember;
import com.ailab.system.domain.LabMemberSkill;
import com.ailab.system.domain.LabSkill;
import com.ailab.system.dto.LabAccessContext;
import com.ailab.system.dto.LabMemberDetail;
import com.ailab.system.mapper.LabLedgerMapper;
import com.ailab.system.mapper.LabMemberMapper;
import com.ailab.system.service.LabAccessService;
import com.ailab.system.service.LabAssetRiskPolicy;
import com.ailab.system.service.LabMemberService;
import com.ruoyi.common.exception.ServiceException;
import com.github.pagehelper.Page;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LabMemberServiceImpl implements LabMemberService {
    private final LabMemberMapper memberMapper;
    private final LabLedgerMapper ledgerMapper;
    private final LabAccessService accessService;
    private final LabAssetRiskPolicy assetRiskPolicy;

    public LabMemberServiceImpl(LabMemberMapper memberMapper, LabLedgerMapper ledgerMapper,
            LabAccessService accessService) {
        this(memberMapper, ledgerMapper, accessService, new LabAssetRiskPolicy());
    }

    @Autowired
    public LabMemberServiceImpl(LabMemberMapper memberMapper, LabLedgerMapper ledgerMapper,
            LabAccessService accessService, LabAssetRiskPolicy assetRiskPolicy) {
        this.memberMapper = memberMapper; this.ledgerMapper = ledgerMapper; this.accessService = accessService;
        this.assetRiskPolicy = assetRiskPolicy;
    }

    @Override
    public List<LabMember> listMembers(LabMember query, Long actorId) {
        LabAccessContext actor = accessService.context(actorId);
        List<LabMember> mapped = memberMapper.selectMemberList(query == null ? new LabMember() : query);
        if (mapped instanceof Page) {
            for (int i = 0; i < mapped.size(); i++) mapped.set(i, visibleMember(mapped.get(i), actor));
            return mapped;
        }
        List<LabMember> visible = new ArrayList<LabMember>();
        for (LabMember row : mapped) visible.add(visibleMember(row, actor));
        return visible;
    }

    @Override
    public List<LabMember> listAvailableSystemUsers(Long actorId) {
        requireManager(actorId);
        return memberMapper.selectAvailableSystemUsers();
    }

    @Override
    public LabMemberDetail getMemberDetail(Long memberId, Long actorId) {
        LabAccessContext actor = accessService.context(actorId);
        LabMember found = requireMember(memberMapper.selectMemberById(memberId));
        boolean sensitive = canReadSensitiveProfile(actor, found);
        LabMember visible = visibleMember(found, actor);
        LabMemberDetail detail = new LabMemberDetail();
        detail.setMember(visible);
        detail.setSkillMatrix(sensitive
                ? memberMapper.selectMemberSkills(memberId, false) : Collections.<LabMemberSkill>emptyList());
        List<LabAsset> assets = sensitive
                ? ledgerMapper.selectAssetsByMember(memberId) : Collections.emptyList();
        assetRiskPolicy.applyAll(assets);
        detail.setAssets(assets);
        detail.setRecentTasks(sensitive ? memberMapper.selectRecentTasks(memberId, 10) : Collections.emptyList());
        detail.setOneToOnes(isManager(actor) || same(actor.getMemberId(), memberId)
                ? ledgerMapper.selectOne2OneByMember(memberId) : Collections.emptyList());
        return detail;
    }

    @Override
    @Transactional
    public int createMember(LabMember member, Long actorId) {
        requireManager(actorId); requireMemberInput(member);
        if (memberMapper.lockActiveSystemUser(member.getUserId()) == null) {
            throw new ServiceException("Only active system users may become lab members");
        }
        if (memberMapper.selectMemberByUserId(member.getUserId()) != null) {
            throw new ServiceException("The system user already has a lab profile; reactivate it instead");
        }
        validateLeader(member);
        clearJoinedIdentity(member);
        member.setMemberStatus("ACTIVE"); member.setVersion(0); member.setDelFlag("0");
        member.setCreateBy(actor(actorId));
        return requireAffected(memberMapper.insertMember(member), "Member profile was not created");
    }

    @Override
    @Transactional
    public int updateMember(LabMember member, Long actorId) {
        requireManager(actorId); requireMemberInput(member);
        if (member.getId() == null || member.getVersion() == null) throw new ServiceException("Member id and version are required");
        requireMember(memberMapper.selectMemberById(member.getId()));
        LabMember current = lockMemberUpdateReferences(member);
        if (!same(current.getUserId(), member.getUserId())) throw new ServiceException("A member profile cannot be reassigned to another system user");
        clearJoinedIdentity(member); member.setUpdateBy(actor(actorId));
        return requireAffected(memberMapper.updateMember(member), "Member profile changed concurrently");
    }

    @Override
    @Transactional
    public int deactivateMember(Long memberId, Integer version, Long actorId) {
        requireManager(actorId); requireVersion(memberId, version);
        requireMember(memberMapper.selectMemberForUpdate(memberId));
        return requireAffected(memberMapper.deactivateMember(memberId, version, actor(actorId)), "Member profile changed concurrently");
    }

    @Override
    @Transactional
    public int reactivateMember(Long memberId, Integer version, Long actorId) {
        requireManager(actorId); requireVersion(memberId, version);
        LabMember member = requireMember(memberMapper.selectMemberForUpdate(memberId));
        if (memberMapper.lockActiveSystemUser(member.getUserId()) == null) {
            throw new ServiceException("The linked system user is not active");
        }
        return requireAffected(memberMapper.reactivateMember(memberId, version, actor(actorId)), "Member profile changed concurrently");
    }

    @Override
    public List<LabSkill> listSkills(LabSkill query, Long actorId) {
        LabAccessContext actor = accessService.context(actorId);
        LabSkill safe = query == null ? new LabSkill() : query;
        if (!isManager(actor)) safe.setStatus("ACTIVE");
        return memberMapper.selectSkillList(safe);
    }

    @Override
    @Transactional
    public int createSkill(LabSkill skill, Long actorId) {
        requireManager(actorId); requireSkillInput(skill);
        if (memberMapper.selectActiveSkillByName(skill.getSkillName(), null) != null) throw new ServiceException("Active skill name already exists");
        skill.setStatus(blank(skill.getStatus()) ? "ACTIVE" : skill.getStatus());
        skill.setVersion(0); skill.setDelFlag("0"); skill.setCreateBy(actor(actorId));
        return requireAffected(memberMapper.insertSkill(skill), "Skill was not created");
    }

    @Override
    @Transactional
    public int updateSkill(LabSkill skill, Long actorId) {
        requireManager(actorId); requireSkillInput(skill);
        if (skill.getId() == null || skill.getVersion() == null) throw new ServiceException("Skill id and version are required");
        requireSkill(memberMapper.selectSkillForUpdate(skill.getId()));
        if ("ACTIVE".equals(skill.getStatus()) && memberMapper.selectActiveSkillByName(skill.getSkillName(), skill.getId()) != null) {
            throw new ServiceException("Active skill name already exists");
        }
        skill.setUpdateBy(actor(actorId));
        return requireAffected(memberMapper.updateSkill(skill), "Skill changed concurrently");
    }

    @Override
    public List<LabMemberSkill> getSkillMatrix(Long memberId, Long actorId) {
        LabAccessContext actor = accessService.context(actorId);
        LabMember member = requireMember(memberMapper.selectMemberById(memberId));
        if (!canReadMatrix(actor, member)) throw new ServiceException("Skill matrix is outside the authenticated actor's scope");
        return memberMapper.selectMemberSkills(memberId, false);
    }

    @Override
    @Transactional
    public int saveSkillMatrix(Long memberId, List<LabMemberSkill> requested, Long actorId) {
        LabAccessContext actorContext = accessService.context(actorId);
        if (requested == null) throw new ServiceException("Skill matrix is required");
        validateMatrixRequest(memberId, requested);
        LabMember member = requireMember(memberMapper.selectMemberForUpdate(memberId));
        if (!isManager(actorContext) && !same(actorContext.getMemberId(), memberId)) {
            throw new ServiceException("Only managers or the member may edit this skill matrix");
        }
        if (!"ACTIVE".equals(member.getMemberStatus())) throw new ServiceException("Inactive member skill matrix cannot be edited");

        List<LabMemberSkill> existing = memberMapper.selectMemberSkillsForUpdate(memberId);
        Set<Long> allSkillIds = new HashSet<Long>();
        for (LabMemberSkill item : requested) allSkillIds.add(item.getSkillId());
        for (LabMemberSkill item : existing) allSkillIds.add(item.getSkillId());
        List<Long> skillIds = new ArrayList<Long>(allSkillIds);
        Collections.sort(skillIds);
        List<LabSkill> lockedSkills = skillIds.isEmpty() ? Collections.<LabSkill>emptyList() : memberMapper.lockSkillsForUpdate(skillIds);
        if (lockedSkills.size() != skillIds.size()) throw new ServiceException("All selected skills must exist");
        Map<Long, LabMemberSkill> bySkill = new HashMap<Long, LabMemberSkill>();
        for (LabMemberSkill item : existing) bySkill.put(item.getSkillId(), item);
        for (LabSkill skill : lockedSkills) {
            if (!"ACTIVE".equals(skill.getStatus()) && !bySkill.containsKey(skill.getId())) {
                throw new ServiceException("Inactive skills cannot be newly assigned");
            }
        }
        Set<Long> retained = new HashSet<Long>();
        int affected = 0;
        for (LabMemberSkill item : requested) {
            retained.add(item.getSkillId()); item.setMemberId(memberId);
            LabMemberSkill current = bySkill.get(item.getSkillId());
            if (current == null) {
                if (item.getId() != null) throw new ServiceException("Skill matrix id does not belong to this member");
                item.setVersion(0); item.setDelFlag("0"); item.setCreateBy(actor(actorId));
                affected += requireAffected(memberMapper.insertMemberSkill(item), "Skill matrix row was not created");
            } else {
                if (!same(current.getId(), item.getId()) || !same(current.getVersion(), item.getVersion())) {
                    throw new ServiceException("Skill matrix row changed concurrently");
                }
                item.setUpdateBy(actor(actorId));
                affected += requireAffected(memberMapper.updateMemberSkill(item), "Skill matrix row changed concurrently");
            }
        }
        for (LabMemberSkill item : existing) {
            if (!retained.contains(item.getSkillId())) {
                affected += requireAffected(memberMapper.deleteMemberSkill(item.getId(), item.getVersion(), actor(actorId)),
                        "Skill matrix row changed concurrently");
            }
        }
        return affected;
    }

    private void validateMatrixRequest(Long memberId, List<LabMemberSkill> requested) {
        if (memberId == null) throw new ServiceException("Member id is required");
        Set<Long> seen = new HashSet<Long>();
        for (LabMemberSkill item : requested) {
            if (item == null || item.getSkillId() == null) throw new ServiceException("Skill id is required");
            if (!seen.add(item.getSkillId())) throw new ServiceException("Duplicate skill id is not allowed");
            if (item.getLevel() == null || item.getLevel() < 1 || item.getLevel() > 5) throw new ServiceException("Skill level must be between 1 and 5");
            if (item.getMemberId() != null && !same(memberId, item.getMemberId())) throw new ServiceException("Skill row member cannot be changed");
        }
    }

    private boolean canReadMatrix(LabAccessContext actor, LabMember target) {
        return canReadSensitiveProfile(actor, target);
    }
    private boolean canReadSensitiveProfile(LabAccessContext actor, LabMember target) {
        return isManager(actor) || same(actor.getMemberId(), target.getId())
                || (isLead(actor) && same(actor.getBizLine(), target.getBizLine()));
    }
    private void requireManager(Long actorId) { if (!isManager(accessService.context(actorId))) throw new ServiceException("Manager role is required"); }
    private void validateLeader(LabMember member) {
        if (member.getLeaderId() == null) return;
        if (same(member.getId(), member.getLeaderId())) throw new ServiceException("A member cannot be their own leader");
        List<LabMember> leaders = memberMapper.lockMembersForUpdate(Collections.singletonList(member.getLeaderId()));
        if (leaders.size() != 1 || !"ACTIVE".equals(leaders.get(0).getMemberStatus())) throw new ServiceException("Leader must be an active lab member");
    }
    private LabMember lockMemberUpdateReferences(LabMember member) {
        if (same(member.getId(), member.getLeaderId())) throw new ServiceException("A member cannot be their own leader");
        List<Long> ids = new ArrayList<Long>(); ids.add(member.getId());
        if (member.getLeaderId() != null) ids.add(member.getLeaderId());
        Collections.sort(ids);
        List<LabMember> rows = memberMapper.lockMembersForUpdate(ids);
        if (rows == null || rows.size() != ids.size()) throw new ServiceException("Member or leader does not exist");
        LabMember current = null; LabMember leader = null;
        for (LabMember row : rows) {
            if (same(row.getId(), member.getId())) current = row;
            if (same(row.getId(), member.getLeaderId())) leader = row;
        }
        if (current == null) throw new ServiceException("Member does not exist");
        if (member.getLeaderId() != null && (leader == null || !"ACTIVE".equals(leader.getMemberStatus()))) {
            throw new ServiceException("Leader must be an active lab member");
        }
        return current;
    }
    private boolean isManager(LabAccessContext c){return LabAccessServiceImpl.MANAGER.equals(c.getRoleKey());}
    private boolean isLead(LabAccessContext c){return LabAccessServiceImpl.LEAD.equals(c.getRoleKey());}
    private LabMember requireMember(LabMember v){if(v==null)throw new ServiceException("Member does not exist");return v;}
    private LabSkill requireSkill(LabSkill v){if(v==null)throw new ServiceException("Skill does not exist");return v;}
    private int requireAffected(int count,String message){if(count!=1)throw new ServiceException(message);return count;}
    private void requireVersion(Long id,Integer version){if(id==null||version==null)throw new ServiceException("Id and version are required");}
    private void requireMemberInput(LabMember m){if(m==null||m.getUserId()==null||blank(m.getMemberNo())||blank(m.getBizLine())||blank(m.getPosition())||blank(m.getRoleType()))throw new ServiceException("User, member number, position, business line and role are required");}
    private void requireSkillInput(LabSkill s){if(s==null||blank(s.getSkillCode())||blank(s.getSkillName()))throw new ServiceException("Skill code and name are required");}
    private void clearJoinedIdentity(LabMember m){m.setUserName(null);m.setNickName(null);m.setLeaderName(null);}
    private String actor(Long actorId){return String.valueOf(actorId);}
    private boolean same(Object a,Object b){return a==null?b==null:a.equals(b);}
    private boolean blank(String v){return v==null||v.trim().isEmpty();}
    private LabMember copyMember(LabMember source) {
        LabMember out=new LabMember(); out.setId(source.getId()); out.setUserId(source.getUserId()); out.setMemberNo(source.getMemberNo());
        out.setPosition(source.getPosition()); out.setBizLine(source.getBizLine()); out.setRoleType(source.getRoleType()); out.setLeaderId(source.getLeaderId());
        out.setPrimaryResponsibilities(source.getPrimaryResponsibilities()); out.setBackupResponsibilities(source.getBackupResponsibilities()); out.setJoinDate(source.getJoinDate());
        out.setMemberStatus(source.getMemberStatus()); out.setVersion(source.getVersion()); out.setDelFlag(source.getDelFlag()); out.setUserName(source.getUserName());
        out.setNickName(source.getNickName()); out.setLeaderName(source.getLeaderName()); out.setRemark(source.getRemark()); return out;
    }
    private LabMember visibleMember(LabMember source, LabAccessContext actor) {
        LabMember out = copyMember(source);
        if (canReadSensitiveProfile(actor, source)) return out;
        out.setUserId(null); out.setUserName(null); out.setMemberNo(null); out.setRoleType(null); out.setLeaderId(null);
        out.setJoinDate(null); out.setMemberStatus(null); out.setVersion(null); out.setDelFlag(null); out.setLeaderName(null);
        out.setPrimaryResponsibilities(null); out.setBackupResponsibilities(null); out.setRemark(null);
        return out;
    }
}
