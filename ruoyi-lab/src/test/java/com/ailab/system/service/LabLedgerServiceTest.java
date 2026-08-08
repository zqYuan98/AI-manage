package com.ailab.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailab.system.domain.LabAsset;
import com.ailab.system.domain.LabIpr;
import com.ailab.system.domain.LabMember;
import com.ailab.system.domain.LabOne2One;
import com.ailab.system.controller.LabOne2OneController;
import com.ailab.system.dto.LabAccessContext;
import com.ailab.system.mapper.LabLedgerMapper;
import com.ailab.system.mapper.LabMemberMapper;
import com.ailab.system.service.impl.LabAccessServiceImpl;
import com.ailab.system.service.impl.LabLedgerServiceImpl;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class LabLedgerServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-08T08:00:00Z"), ZoneOffset.UTC);
    @Mock private LabLedgerMapper ledgerMapper;
    @Mock private LabMemberMapper memberMapper;
    @Mock private LabAccessService accessService;
    private LabLedgerService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new LabLedgerServiceImpl(ledgerMapper, memberMapper, accessService, CLOCK);
    }

    @Test
    void assetRejectsSameOwnerAndBackupAndLocksOwnersInStableOrder() {
        manager(1L);
        LabAsset invalid = asset(null, 20L, 20L, "ACTIVE", 0);
        assertThrows(ServiceException.class, () -> service.createAsset(invalid, 1L));
        verify(memberMapper, never()).lockMembersForUpdate(any());

        LabAsset valid = asset(null, 30L, 20L, "ACTIVE", 0);
        when(memberMapper.lockMembersForUpdate(Arrays.asList(20L, 30L)))
                .thenReturn(Arrays.asList(member(20L, "algorithm", "ACTIVE"), member(30L, "algorithm", "ACTIVE")));
        when(ledgerMapper.insertAsset(valid)).thenReturn(1);
        assertEquals(1, service.createAsset(valid, 1L));
        verify(memberMapper).lockMembersForUpdate(Arrays.asList(20L, 30L));
    }

    @Test
    void createDefaultsAreAppliedBeforeExplicitInsertColumns() {
        manager(1L);
        LabAsset asset = new LabAsset();
        asset.setAssetNo("A-DEFAULT"); asset.setAssetName("Defaulted asset"); asset.setAssetType("algorithm"); asset.setPrimaryOwnerId(20L);
        when(memberMapper.lockMembersForUpdate(Collections.singletonList(20L)))
                .thenReturn(Collections.singletonList(member(20L, "algorithm", "ACTIVE")));
        when(ledgerMapper.insertAsset(asset)).thenReturn(1);

        assertEquals(1, service.createAsset(asset, 1L));
        assertEquals("ACTIVE", asset.getStatus());
        assertEquals("VERIFYING", asset.getAssetStage());
        assertEquals("", asset.getAssetVersion());
        assertEquals("0", asset.getCriticalFlag());
        assertEquals(Integer.valueOf(0), asset.getReuseCount());

        LabOne2One record = one2one(null, 20L, 21L, null);
        when(memberMapper.lockMembersForUpdate(Arrays.asList(20L, 21L)))
                .thenReturn(Arrays.asList(member(20L, "algorithm", "ACTIVE"), member(21L, "algorithm", "ACTIVE")));
        when(ledgerMapper.insertOne2One(record)).thenReturn(1);
        assertEquals(1, service.createOne2One(record, 1L));
        assertEquals("OPEN", record.getStatus());
    }

    @Test
    void inactiveBackupIsRejectedAndSinglePointRiskIsServerDerived() {
        manager(1L);
        LabAsset value = asset(null, 20L, 30L, "ACTIVE", 0);
        value.setCriticalFlag("1"); value.setSinglePointRisk(false);
        when(memberMapper.lockMembersForUpdate(Arrays.asList(20L, 30L)))
                .thenReturn(Arrays.asList(member(20L, "algorithm", "ACTIVE"), member(30L, "algorithm", "INACTIVE")));
        assertThrows(ServiceException.class, () -> service.createAsset(value, 1L));

        LabAsset noBackup = asset(1L, 20L, null, "ACTIVE", 0); noBackup.setCriticalFlag("1");
        LabAsset inactiveBackup = asset(2L, 20L, 30L, "ACTIVE", 0); inactiveBackup.setCriticalFlag("1"); inactiveBackup.setBackupOwnerStatus("INACTIVE");
        LabAsset missingBackup = asset(4L, 20L, 40L, "ACTIVE", 0); missingBackup.setCriticalFlag("1"); missingBackup.setBackupOwnerStatus(null);
        LabAsset nonCritical = asset(3L, 20L, null, "INACTIVE", 0); nonCritical.setCriticalFlag("0");
        LabAsset inactiveCritical = asset(5L, 20L, null, "INACTIVE", 0); inactiveCritical.setCriticalFlag("1");
        when(ledgerMapper.selectAssetList(any(LabAsset.class), any())).thenReturn(Arrays.asList(noBackup, inactiveBackup, missingBackup, nonCritical, inactiveCritical));
        assertEquals(4, service.listAssetRisks(new LabAsset(), 1L).size());
        assertTrue(noBackup.isSinglePointRisk());
        assertTrue(inactiveBackup.isSinglePointRisk());
        assertTrue(missingBackup.isSinglePointRisk());
        assertFalse(nonCritical.isSinglePointRisk());
        assertTrue(inactiveCritical.isSinglePointRisk(), "critical inventory stays in the shared risk population regardless of deployment state");
    }

    @Test
    void dashboardSinglePointRiskDrillIsExactlyFilteredToTrustedActorScope() {
        lead(2L, 21L, "algorithm");
        LabAsset sameLine = asset(1L, 20L, null, "ACTIVE", 0);
        sameLine.setCriticalFlag("1"); sameLine.setPrimaryOwnerBizLine("algorithm");
        LabAsset crossLine = asset(2L, 30L, null, "ACTIVE", 0);
        crossLine.setCriticalFlag("1"); crossLine.setPrimaryOwnerBizLine("platform");
        LabAsset irrelevant = asset(3L, 20L, null, "ACTIVE", 0);
        irrelevant.setAssetStage("VERIFYING"); irrelevant.setCriticalFlag("0");
        irrelevant.setPrimaryOwnerBizLine("algorithm");
        when(ledgerMapper.selectAssetList(any(LabAsset.class), any()))
                .thenReturn(Arrays.asList(sameLine, crossLine, irrelevant));
        LabAsset query = new LabAsset(); query.setSinglePointRisk(true);

        List<LabAsset> result = service.listAssets(query, 2L);

        assertEquals(Collections.singletonList(sameLine), result,
                "risk drill must match the scoped KPI and the shared risk policy exactly");
    }

    @Test
    void memberCanWriteOnlyOwnedAssetAndLeadOnlySameLineOrOwned() {
        memberActor(3L, 20L, "algorithm");
        when(memberMapper.lockMembersForUpdate(Collections.singletonList(30L)))
                .thenReturn(Collections.singletonList(member(30L, "algorithm", "ACTIVE")));
        assertThrows(ServiceException.class, () -> service.createAsset(asset(null, 30L, null, "ACTIVE", 0), 3L));

        lead(2L, 21L, "algorithm");
        when(memberMapper.lockMembersForUpdate(Collections.singletonList(30L)))
                .thenReturn(Collections.singletonList(member(30L, "platform", "ACTIVE")));
        assertThrows(ServiceException.class, () -> service.createAsset(asset(null, 30L, null, "ACTIVE", 0), 2L));
    }

    @Test
    void assetUpdateRequiresMatchingVersionAndDoesNotTrustClientRisk() {
        manager(1L);
        LabAsset stored = asset(1L, 20L, null, "ACTIVE", 4);
        when(ledgerMapper.selectAssetForUpdate(1L)).thenReturn(stored);
        when(memberMapper.selectMemberForUpdate(20L)).thenReturn(member(20L, "algorithm", "ACTIVE"));
        when(memberMapper.lockMembersForUpdate(Collections.singletonList(20L)))
                .thenReturn(Collections.singletonList(member(20L, "algorithm", "ACTIVE")));
        when(ledgerMapper.updateAsset(any(LabAsset.class))).thenReturn(0);
        LabAsset update = asset(1L, 20L, null, "ACTIVE", 4); update.setSinglePointRisk(false); update.setCriticalFlag("1");
        assertThrows(ServiceException.class, () -> service.updateAsset(update, 1L));
        assertTrue(update.isSinglePointRisk());
    }

    @Test
    void assetOwnerTransferLocksCurrentAndNewReferencesInOneStableOrder() {
        manager(1L);
        LabAsset stored = asset(1L, 30L, null, "ACTIVE", 4);
        when(ledgerMapper.selectAssetForUpdate(1L)).thenReturn(stored);
        when(memberMapper.lockMembersForUpdate(Arrays.asList(20L, 30L, 40L))).thenReturn(Arrays.asList(
                member(20L, "algorithm", "ACTIVE"), member(30L, "algorithm", "ACTIVE"), member(40L, "algorithm", "ACTIVE")));
        when(ledgerMapper.updateAsset(any(LabAsset.class))).thenReturn(1);
        LabAsset transfer = asset(1L, 20L, 40L, "ACTIVE", 4);

        assertEquals(1, service.updateAsset(transfer, 1L));

        verify(memberMapper).lockMembersForUpdate(Arrays.asList(20L, 30L, 40L));
        verify(memberMapper, never()).selectMemberForUpdate(30L);
    }

    @Test
    void oneToOneIsManagerWritableAndVisibleOnlyToManagerOrTalkSubject() {
        memberActor(3L, 20L, "algorithm");
        LabOne2One record = one2one(1L, 30L, 21L, 0);
        when(ledgerMapper.selectOne2OneById(1L)).thenReturn(record);
        assertThrows(ServiceException.class, () -> service.getOne2One(1L, 3L));
        assertThrows(ServiceException.class, () -> service.createOne2One(record, 3L));

        memberActor(4L, 30L, "algorithm");
        assertEquals(record, service.getOne2One(1L, 4L));
        assertThrows(ServiceException.class, () -> service.updateOne2One(record, 4L));
    }

    @Test
    void oneToOneUpdateLocksOldAndNewParticipantsInStableOrder() {
        manager(1L);
        LabOne2One stored = one2one(1L, 40L, 30L, 2);
        when(ledgerMapper.selectOne2OneForUpdate(1L)).thenReturn(stored);
        when(memberMapper.lockMembersForUpdate(Arrays.asList(10L, 20L, 30L, 40L))).thenReturn(Arrays.asList(
                member(10L, "algorithm", "ACTIVE"), member(20L, "algorithm", "ACTIVE"),
                member(30L, "platform", "INACTIVE"), member(40L, "platform", "INACTIVE")));
        LabOne2One update = one2one(1L, 20L, 10L, 2);
        when(ledgerMapper.updateOne2One(update)).thenReturn(1);

        assertEquals(1, service.updateOne2One(update, 1L));

        verify(memberMapper).lockMembersForUpdate(Arrays.asList(10L, 20L, 30L, 40L));
    }

    @Test
    void ordinaryLedgerListsNeverContainOneToOneSensitiveContent() {
        memberActor(3L, 20L, "algorithm");
        when(ledgerMapper.selectAssetList(any(LabAsset.class), any())).thenReturn(Collections.<LabAsset>emptyList());
        when(ledgerMapper.selectIprList(any(LabIpr.class))).thenReturn(Collections.<LabIpr>emptyList());
        service.listAssets(new LabAsset(), 3L);
        service.listIprs(new LabIpr(), 3L);
        verify(ledgerMapper, never()).selectOne2OneByMember(any());
    }

    @Test
    void oneToOneWriteLogsNeverPersistSensitiveRequestOrResponseBodies() throws Exception {
        Method create = LabOne2OneController.class.getMethod("create", LabOne2One.class);
        Method update = LabOne2OneController.class.getMethod("update", LabOne2One.class);
        Log createLog = create.getAnnotation(Log.class);
        Log updateLog = update.getAnnotation(Log.class);

        assertEquals(BusinessType.INSERT, createLog.businessType());
        assertEquals(BusinessType.UPDATE, updateLog.businessType());
        assertFalse(createLog.isSaveRequestData());
        assertFalse(createLog.isSaveResponseData());
        assertFalse(updateLog.isSaveRequestData());
        assertFalse(updateLog.isSaveResponseData());
    }

    @Test
    void iprValidatesStageConditionalFieldsAndFutureFacts() {
        manager(1L);
        when(memberMapper.lockMembersForUpdate(Collections.singletonList(20L)))
                .thenReturn(Collections.singletonList(member(20L, "algorithm", "ACTIVE")));
        LabIpr accepted = ipr(null, 20L, "ACCEPTED", 0);
        accepted.setActualSubmitDate(date("2026-07-01"));
        assertThrows(ServiceException.class, () -> service.createIpr(accepted, 1L));
        accepted.setAcceptanceNo("CN-ACCEPT-1");
        accepted.setActualSubmitDate(date("2026-08-09"));
        assertThrows(ServiceException.class, () -> service.createIpr(accepted, 1L));

        LabIpr authorized = ipr(null, 20L, "AUTHORIZED", 0);
        authorized.setActualSubmitDate(date("2026-07-01")); authorized.setAcceptanceNo("A-1");
        assertThrows(ServiceException.class, () -> service.createIpr(authorized, 1L));
    }

    @Test
    void iprAcceptsDraftAndPreparingButRejectsUnconfiguredAliases() {
        manager(1L);
        when(memberMapper.lockMembersForUpdate(Collections.singletonList(20L)))
                .thenReturn(Collections.singletonList(member(20L, "algorithm", "ACTIVE")));
        when(ledgerMapper.insertIpr(any(LabIpr.class))).thenReturn(1);

        assertEquals(1, service.createIpr(ipr(null, 20L, "DRAFT", 0), 1L));
        assertEquals(1, service.createIpr(ipr(null, 20L, "PREPARING", 0), 1L));
        assertThrows(ServiceException.class, () -> service.createIpr(ipr(null, 20L, "DRAFTING", 0), 1L));

        LabIpr completed = ipr(null, 20L, "COMPLETED", 0);
        completed.setActualSubmitDate(date("2026-07-01")); completed.setAcceptanceNo("A-1"); completed.setCertificateNo("C-1");
        assertThrows(ServiceException.class, () -> service.createIpr(completed, 1L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void iprStageOrderUsesEachConfiguredDictionaryStageExactlyOnce() throws Exception {
        Field field = LabLedgerServiceImpl.class.getDeclaredField("IPR_STAGE_ORDER");
        field.setAccessible(true);
        Map<String, Integer> order = (Map<String, Integer>) field.get(null);

        assertEquals(new HashSet<String>(Arrays.asList("DRAFT", "PREPARING", "SUBMITTED", "ACCEPTED", "AUTHORIZED")),
                order.keySet());
        assertEquals(new HashSet<Integer>(Arrays.asList(0, 1, 2, 3, 4)), new HashSet<Integer>(order.values()));
        assertEquals(Integer.valueOf(0), order.get("DRAFT"));
        assertEquals(Integer.valueOf(1), order.get("PREPARING"));
        assertEquals(Integer.valueOf(2), order.get("SUBMITTED"));
        assertEquals(Integer.valueOf(3), order.get("ACCEPTED"));
        assertEquals(Integer.valueOf(4), order.get("AUTHORIZED"));
    }

    @Test
    void iprStageMayAdvanceButRollbackRequiresManagerReason() {
        lead(2L, 21L, "algorithm");
        LabIpr stored = ipr(1L, 20L, "ACCEPTED", 2);
        when(ledgerMapper.selectIprForUpdate(1L)).thenReturn(stored);
        when(memberMapper.selectMemberForUpdate(20L)).thenReturn(member(20L, "algorithm", "ACTIVE"));
        when(memberMapper.lockMembersForUpdate(Collections.singletonList(20L)))
                .thenReturn(Collections.singletonList(member(20L, "algorithm", "ACTIVE")));
        LabIpr rollback = ipr(1L, 20L, "DRAFT", 2);
        assertThrows(ServiceException.class, () -> service.updateIpr(rollback, "correction", 2L));

        manager(1L);
        assertThrows(ServiceException.class, () -> service.updateIpr(rollback, " ", 1L));
        when(ledgerMapper.updateIpr(any(LabIpr.class))).thenReturn(1);
        assertEquals(1, service.updateIpr(rollback, "filing office returned application", 1L));
        assertEquals("filing office returned application", rollback.getStageChangeReason());
    }

    @Test
    void iprRollbackAuditReasonCannotBeForgedOrErasedByOrdinaryWrites() {
        manager(1L);
        when(memberMapper.lockMembersForUpdate(Collections.singletonList(20L)))
                .thenReturn(Collections.singletonList(member(20L, "algorithm", "ACTIVE")));
        LabIpr created = ipr(null, 20L, "DRAFT", null);
        created.setStageChangeReason("client-forged rollback");
        when(ledgerMapper.insertIpr(created)).thenReturn(1);
        assertEquals(1, service.createIpr(created, 1L));
        assertNull(created.getStageChangeReason());

        LabIpr stored = ipr(1L, 20L, "DRAFT", 3);
        stored.setStageChangeReason("filing office returned application");
        when(ledgerMapper.selectIprForUpdate(1L)).thenReturn(stored);
        when(memberMapper.selectMemberForUpdate(20L)).thenReturn(member(20L, "algorithm", "ACTIVE"));
        LabIpr ordinaryUpdate = ipr(1L, 20L, "DRAFT", 3);
        ordinaryUpdate.setStageChangeReason("client-overwrite");
        when(ledgerMapper.updateIpr(ordinaryUpdate)).thenReturn(1);
        assertEquals(1, service.updateIpr(ordinaryUpdate, null, 1L));
        assertEquals("filing office returned application", ordinaryUpdate.getStageChangeReason());
    }

    @Test
    void memberAndLeadCanEditOnlyOwnedOrSameLineIprAndStaleWritesFail() {
        memberActor(3L, 20L, "algorithm");
        LabIpr stored = ipr(1L, 30L, "DRAFT", 0);
        when(ledgerMapper.selectIprForUpdate(1L)).thenReturn(stored);
        assertThrows(ServiceException.class, () -> service.updateIpr(stored, null, 3L));

        manager(1L);
        stored.setOwnerId(20L);
        when(memberMapper.lockMembersForUpdate(Collections.singletonList(20L)))
                .thenReturn(Collections.singletonList(member(20L, "algorithm", "ACTIVE")));
        when(ledgerMapper.updateIpr(any(LabIpr.class))).thenReturn(0);
        assertThrows(ServiceException.class, () -> service.updateIpr(stored, null, 1L));
    }

    @Test
    void iprOwnerTransferLocksOldAndNewOwnersInStableOrder() {
        manager(1L);
        LabIpr stored = ipr(1L, 30L, "DRAFT", 2);
        when(ledgerMapper.selectIprForUpdate(1L)).thenReturn(stored);
        when(memberMapper.lockMembersForUpdate(Arrays.asList(20L, 30L))).thenReturn(Arrays.asList(
                member(20L, "algorithm", "ACTIVE"), member(30L, "platform", "INACTIVE")));
        when(ledgerMapper.updateIpr(any(LabIpr.class))).thenReturn(1);
        LabIpr transfer = ipr(1L, 20L, "DRAFT", 2);

        assertEquals(1, service.updateIpr(transfer, null, 1L));

        verify(memberMapper).lockMembersForUpdate(Arrays.asList(20L, 30L));
        verify(memberMapper, never()).selectMemberForUpdate(30L);
    }

    private void manager(Long userId) { when(accessService.context(userId)).thenReturn(context(userId, 10L, LabAccessServiceImpl.MANAGER, "manage")); }
    private void lead(Long userId, Long memberId, String line) { when(accessService.context(userId)).thenReturn(context(userId, memberId, LabAccessServiceImpl.LEAD, line)); }
    private void memberActor(Long userId, Long memberId, String line) { when(accessService.context(userId)).thenReturn(context(userId, memberId, LabAccessServiceImpl.MEMBER, line)); }
    private LabAccessContext context(Long userId, Long memberId, String role, String line) { LabAccessContext c=new LabAccessContext(); c.setUserId(userId); c.setMemberId(memberId); c.setRoleKey(role); c.setBizLine(line); return c; }
    private LabMember member(Long id, String line, String status) { LabMember m=new LabMember(); m.setId(id); m.setBizLine(line); m.setMemberStatus(status); return m; }
    private LabAsset asset(Long id, Long owner, Long backup, String status, Integer version) { LabAsset a=new LabAsset(); a.setId(id); a.setAssetNo("A-"+(id==null?"NEW":id)); a.setAssetName("Asset"); a.setAssetType("algorithm"); a.setAssetStage("DEPLOYED"); a.setPrimaryOwnerId(owner); a.setBackupOwnerId(backup); a.setStatus(status); a.setVersion(version); return a; }
    private LabOne2One one2one(Long id, Long memberId, Long leaderId, Integer version) { LabOne2One o=new LabOne2One(); o.setId(id); o.setMemberId(memberId); o.setLeaderId(leaderId); o.setMeetingDate(date("2026-08-01")); o.setFactsEvidence("facts"); o.setDifficulties("difficulty"); o.setNextAction("next"); o.setManagerComment("comment"); o.setVersion(version); return o; }
    private LabIpr ipr(Long id, Long owner, String stage, Integer version) { LabIpr i=new LabIpr(); i.setId(id); i.setIprNo("IPR-"+(id==null?"NEW":id)); i.setIprName("IPR"); i.setIprType("PATENT"); i.setIprStage(stage); i.setOwnerId(owner); i.setPlannedSubmitDate(date("2026-08-15")); i.setStatus("ACTIVE"); i.setVersion(version); return i; }
    private Date date(String iso) { return Date.from(java.time.LocalDate.parse(iso).atStartOfDay(ZoneOffset.UTC).toInstant()); }
}
