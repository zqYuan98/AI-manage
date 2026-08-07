package com.ailab.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailab.system.domain.LabAsset;
import com.ailab.system.domain.LabMember;
import com.ailab.system.domain.LabMemberSkill;
import com.ailab.system.domain.LabSkill;
import com.ailab.system.dto.LabAccessContext;
import com.ailab.system.dto.LabMemberDetail;
import com.ailab.system.mapper.LabLedgerMapper;
import com.ailab.system.mapper.LabMemberMapper;
import com.ailab.system.service.impl.LabAccessServiceImpl;
import com.ailab.system.service.impl.LabMemberServiceImpl;
import com.ruoyi.common.exception.ServiceException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.annotation.Transactional;

class LabMemberServiceTest {
    @Mock private LabMemberMapper memberMapper;
    @Mock private LabLedgerMapper ledgerMapper;
    @Mock private LabAccessService accessService;
    private LabMemberService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new LabMemberServiceImpl(memberMapper, ledgerMapper, accessService);
    }

    @Test
    void createsProfileOnlyForAnActiveUnmappedSystemUserAndNeverCopiesIdentity() {
        manager(1L);
        when(memberMapper.lockActiveSystemUser(90L)).thenReturn(systemUser(90L, "account", "Display Name"));
        when(memberMapper.selectMemberByUserId(90L)).thenReturn(null);
        when(memberMapper.insertMember(any(LabMember.class))).thenReturn(1);
        LabMember input = member(10L, 90L, "algorithm");
        input.setUserName("forged-account"); input.setNickName("forged-name");

        assertEquals(1, service.createMember(input, 1L));

        assertNull(input.getUserName());
        assertNull(input.getNickName());
        assertEquals(Integer.valueOf(0), input.getVersion());
        verify(memberMapper).insertMember(input);
    }

    @Test
    void rejectsDuplicateActiveSystemUserProfileAndInactiveSystemUser() {
        manager(1L);
        LabMember input = member(null, 90L, "algorithm");
        when(memberMapper.lockActiveSystemUser(90L)).thenReturn(systemUser(90L, "a", "A"));
        when(memberMapper.selectMemberByUserId(90L)).thenReturn(member(10L, 90L, "algorithm"));
        assertThrows(ServiceException.class, () -> service.createMember(input, 1L));
        when(memberMapper.lockActiveSystemUser(91L)).thenReturn(null);
        input.setUserId(91L);
        assertThrows(ServiceException.class, () -> service.createMember(input, 1L));
    }

    @Test
    void memberWriteRejectsMissingLeaderBeforeInsert() {
        manager(1L);
        when(memberMapper.lockActiveSystemUser(90L)).thenReturn(systemUser(90L, "a", "A"));
        when(memberMapper.selectMemberByUserId(90L)).thenReturn(null);
        when(memberMapper.lockMembersForUpdate(Collections.singletonList(99L))).thenReturn(Collections.<LabMember>emptyList());
        LabMember input = member(null, 90L, "algorithm"); input.setLeaderId(99L);

        assertThrows(ServiceException.class, () -> service.createMember(input, 1L));

        verify(memberMapper, never()).insertMember(input);
    }

    @Test
    void memberUpdateLocksSelfAndLeaderInStableIdOrder() {
        manager(1L);
        LabMember current = member(30L, 90L, "algorithm"); current.setVersion(2);
        LabMember leader = member(20L, 80L, "algorithm"); leader.setMemberStatus("ACTIVE");
        when(memberMapper.selectMemberById(30L)).thenReturn(current);
        when(memberMapper.lockMembersForUpdate(Arrays.asList(20L, 30L))).thenReturn(Arrays.asList(leader, current));
        when(memberMapper.updateMember(any(LabMember.class))).thenReturn(1);
        LabMember update = member(30L, 90L, "algorithm"); update.setVersion(2); update.setLeaderId(20L);

        assertEquals(1, service.updateMember(update, 1L));

        verify(memberMapper).lockMembersForUpdate(Arrays.asList(20L, 30L));
        verify(memberMapper, never()).selectMemberForUpdate(30L);
    }

    @Test
    void onlyManagerMayMaintainProfilesAndReactivationRechecksSystemUser() {
        memberActor(2L, 20L, "algorithm");
        assertThrows(ServiceException.class, () -> service.deactivateMember(20L, 0, 2L));
        manager(1L);
        LabMember stored = member(20L, 90L, "algorithm"); stored.setVersion(3);
        when(memberMapper.selectMemberForUpdate(20L)).thenReturn(stored);
        when(memberMapper.lockActiveSystemUser(90L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.reactivateMember(20L, 3, 1L));
        when(memberMapper.lockActiveSystemUser(90L)).thenReturn(systemUser(90L, "a", "A"));
        when(memberMapper.reactivateMember(20L, 3, "1")).thenReturn(0);
        assertThrows(ServiceException.class, () -> service.reactivateMember(20L, 3, 1L));
    }

    @Test
    void memberDetailMasksResponsibilitiesAndOneToOnesOutsideObjectScope() {
        memberActor(3L, 30L, "platform");
        LabMember target = member(20L, 90L, "algorithm");
        target.setPrimaryResponsibilities("sensitive primary");
        target.setBackupResponsibilities("sensitive backup");
        when(memberMapper.selectMemberById(20L)).thenReturn(target);
        when(memberMapper.selectMemberSkills(20L, false)).thenReturn(Collections.<LabMemberSkill>emptyList());
        when(ledgerMapper.selectAssetsByMember(20L)).thenReturn(Collections.<LabAsset>emptyList());
        when(memberMapper.selectRecentTasks(20L, 10)).thenReturn(Collections.emptyList());

        LabMemberDetail detail = service.getMemberDetail(20L, 3L);

        assertNull(detail.getMember().getPrimaryResponsibilities());
        assertNull(detail.getMember().getBackupResponsibilities());
        assertTrue(detail.getOneToOnes().isEmpty());
        verify(ledgerMapper, never()).selectOne2OneByMember(20L);
        verify(memberMapper, never()).selectRecentTasks(20L, 10);
    }

    @Test
    void ordinaryMemberListContainsOnlyBasicFieldsOutsideOwnProfile() {
        memberActor(3L, 30L, "platform");
        LabMember other = member(20L, 90L, "algorithm");
        other.setPrimaryResponsibilities("sensitive primary");
        other.setBackupResponsibilities("sensitive backup");
        other.setRemark("sensitive manager note");
        when(memberMapper.selectMemberList(any(LabMember.class))).thenReturn(Collections.singletonList(other));

        LabMember visible = service.listMembers(new LabMember(), 3L).get(0);

        assertNull(visible.getPrimaryResponsibilities());
        assertNull(visible.getBackupResponsibilities());
        assertNull(visible.getRemark());
    }

    @Test
    void leadSeesSameLineResponsibilitiesButOneToOneOnlyWhenTheLeadIsTalkSubject() {
        lead(2L, 21L, "algorithm");
        LabMember target = member(20L, 90L, "algorithm");
        target.setPrimaryResponsibilities("model delivery");
        when(memberMapper.selectMemberById(20L)).thenReturn(target);
        when(memberMapper.selectMemberSkills(20L, false)).thenReturn(Collections.<LabMemberSkill>emptyList());
        when(ledgerMapper.selectAssetsByMember(20L)).thenReturn(Collections.<LabAsset>emptyList());
        when(memberMapper.selectRecentTasks(20L, 10)).thenReturn(Collections.emptyList());

        LabMemberDetail detail = service.getMemberDetail(20L, 2L);

        assertEquals("model delivery", detail.getMember().getPrimaryResponsibilities());
        assertTrue(detail.getOneToOnes().isEmpty());
    }

    @Test
    void managerConfiguresSkillsAndActiveNameMustBeUnique() {
        manager(1L);
        LabSkill skill = skill(50L, "MLOps", "ACTIVE", 0);
        when(memberMapper.selectActiveSkillByName("MLOps", null)).thenReturn(skill(51L, "MLOps", "ACTIVE", 0));
        assertThrows(ServiceException.class, () -> service.createSkill(skill, 1L));
        verify(memberMapper, never()).insertSkill(any(LabSkill.class));
    }

    @Test
    void batchMatrixRejectsDuplicateSkillAndInvalidLevelsBeforeWriting() {
        memberActor(3L, 20L, "algorithm");
        LabMemberSkill first = memberSkill(null, 20L, 50L, 3, 0);
        LabMemberSkill duplicate = memberSkill(null, 20L, 50L, 6, 0);

        assertThrows(ServiceException.class,
                () -> service.saveSkillMatrix(20L, Arrays.asList(first, duplicate), 3L));

        verify(memberMapper, never()).insertMemberSkill(any(LabMemberSkill.class));
    }

    @Test
    void batchMatrixLocksInStableSkillOrderAndLogicallyDeletesMissingRows() {
        memberActor(3L, 20L, "algorithm");
        when(memberMapper.selectMemberForUpdate(20L)).thenReturn(member(20L, 90L, "algorithm"));
        when(memberMapper.lockSkillsForUpdate(Arrays.asList(40L, 50L)))
                .thenReturn(Arrays.asList(skill(40L, "Java", "ACTIVE", 0), skill(50L, "MLOps", "ACTIVE", 0)));
        LabMemberSkill retained = memberSkill(100L, 20L, 40L, 2, 1);
        LabMemberSkill removed = memberSkill(101L, 20L, 60L, 4, 2);
        when(memberMapper.selectMemberSkillsForUpdate(20L)).thenReturn(Arrays.asList(retained, removed));
        when(memberMapper.updateMemberSkill(any(LabMemberSkill.class))).thenReturn(1);
        when(memberMapper.deleteMemberSkill(101L, 2, "3")).thenReturn(1);
        when(memberMapper.insertMemberSkill(any(LabMemberSkill.class))).thenReturn(1);

        LabMemberSkill update = memberSkill(100L, 20L, 40L, 4, 1);
        LabMemberSkill add = memberSkill(null, 20L, 50L, 5, null);
        assertEquals(3, service.saveSkillMatrix(20L, Arrays.asList(add, update), 3L));

        verify(memberMapper).lockSkillsForUpdate(Arrays.asList(40L, 50L));
        verify(memberMapper).deleteMemberSkill(101L, 2, "3");
    }

    @Test
    void matrixUpdateFailsOnStaleVersionAndMethodIsTransactional() throws Exception {
        assertTrue(LabMemberServiceImpl.class
                .getMethod("saveSkillMatrix", Long.class, java.util.List.class, Long.class)
                .isAnnotationPresent(Transactional.class));
        manager(1L);
        when(memberMapper.selectMemberForUpdate(20L)).thenReturn(member(20L, 90L, "algorithm"));
        when(memberMapper.lockSkillsForUpdate(Collections.singletonList(40L)))
                .thenReturn(Collections.singletonList(skill(40L, "Java", "ACTIVE", 0)));
        LabMemberSkill stored = memberSkill(100L, 20L, 40L, 2, 3);
        when(memberMapper.selectMemberSkillsForUpdate(20L)).thenReturn(Collections.singletonList(stored));
        when(memberMapper.updateMemberSkill(any(LabMemberSkill.class))).thenReturn(0);

        assertThrows(ServiceException.class, () -> service.saveSkillMatrix(20L,
                Collections.singletonList(memberSkill(100L, 20L, 40L, 5, 3)), 1L));
    }

    private void manager(Long userId) { when(accessService.context(userId)).thenReturn(context(userId, 10L, LabAccessServiceImpl.MANAGER, "manage")); }
    private void lead(Long userId, Long memberId, String line) { when(accessService.context(userId)).thenReturn(context(userId, memberId, LabAccessServiceImpl.LEAD, line)); }
    private void memberActor(Long userId, Long memberId, String line) { when(accessService.context(userId)).thenReturn(context(userId, memberId, LabAccessServiceImpl.MEMBER, line)); }
    private LabAccessContext context(Long userId, Long memberId, String role, String line) { LabAccessContext c=new LabAccessContext(); c.setUserId(userId); c.setMemberId(memberId); c.setRoleKey(role); c.setBizLine(line); return c; }
    private LabMember member(Long id, Long userId, String line) { LabMember m=new LabMember(); m.setId(id); m.setUserId(userId); m.setMemberNo("M-"+userId); m.setBizLine(line); m.setPosition("Engineer"); m.setRoleType("MEMBER"); m.setMemberStatus("ACTIVE"); m.setVersion(0); return m; }
    private LabMember systemUser(Long id, String userName, String nickName) { LabMember m=new LabMember(); m.setUserId(id); m.setUserName(userName); m.setNickName(nickName); return m; }
    private LabSkill skill(Long id, String name, String status, Integer version) { LabSkill s=new LabSkill(); s.setId(id); s.setSkillCode(name.toUpperCase()); s.setSkillName(name); s.setStatus(status); s.setVersion(version); return s; }
    private LabMemberSkill memberSkill(Long id, Long memberId, Long skillId, Integer level, Integer version) { LabMemberSkill s=new LabMemberSkill(); s.setId(id); s.setMemberId(memberId); s.setSkillId(skillId); s.setLevel(level); s.setVersion(version); s.setLastVerifiedDate(new Date()); s.setEvidenceUrl("https://example.invalid/evidence"); return s; }
}
