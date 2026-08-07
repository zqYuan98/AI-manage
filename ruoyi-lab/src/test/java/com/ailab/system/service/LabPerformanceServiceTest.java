package com.ailab.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailab.system.constant.LabConstants;
import com.ailab.system.domain.LabCollaborationRecord;
import com.ailab.system.domain.LabMember;
import com.ailab.system.domain.LabPerfScore;
import com.ailab.system.domain.LabPeriodClose;
import com.ailab.system.domain.LabTask;
import com.ailab.system.domain.LabTaskEvidence;
import com.ailab.system.domain.LabTaskQualityGate;
import com.ailab.system.dto.CalibrationCommand;
import com.ailab.system.dto.CollaborationReviewCommand;
import com.ailab.system.dto.LabAccessContext;
import com.ailab.system.dto.PerformanceAssetFact;
import com.ailab.system.dto.PerformanceCalculationInput;
import com.ailab.system.dto.PerformanceCalculationResult;
import com.ailab.system.dto.RedLineRevokeCommand;
import com.ailab.system.mapper.LabPerformanceMapper;
import com.ailab.system.service.impl.LabAccessServiceImpl;
import com.ailab.system.service.impl.LabPerformanceServiceImpl;
import com.ruoyi.common.exception.ServiceException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LabPerformanceServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-01T02:00:00Z"), ZoneOffset.UTC);

    @Mock private LabPerformanceMapper mapper;
    @Mock private LabAccessService access;
    private LabPerformanceCalculator calculator;
    private LabPerformanceService service;

    @BeforeEach
    void setUp() {
        calculator = new LabPerformanceCalculator();
        service = new LabPerformanceServiceImpl(mapper, access, calculator, CLOCK);
    }

    @Test
    void calculatorAppliesDeliveryCoefficientsAndSixtyPointCap() {
        PerformanceCalculationInput input = input(task(1L, 40, "CONFIRMED", "EXCEEDED"),
                task(2L, 30, "CONFIRMED", "ONTIME"), task(3L, 30, "CONFIRMED", "DELAYED"));
        PerformanceCalculationResult result = calculator.calculate(input);
        assertEquals(new BigDecimal("59.40"), result.getDeliveryScore());

        PerformanceCalculationInput capped = input(task(4L, 100, "CONFIRMED", "EXCEEDED"));
        assertEquals(new BigDecimal("60.00"), calculator.calculate(capped).getDeliveryScore());
    }

    @Test
    void qualityRequiresPassedGateBoundToApprovedEvidenceAndExplainsNoGate() {
        LabTask first = task(1L, 50, "CONFIRMED", "ONTIME");
        LabTask second = task(2L, 50, "CONFIRMED", "ONTIME");
        PerformanceCalculationInput input = input(first, second);
        input.getEvidenceByTask().put(1L, Arrays.asList(evidence(11L, "APPROVED"), evidence(12L, "PENDING")));
        input.getQualityGatesByTask().put(1L, Arrays.asList(gate(1L, "PASSED", 11L), gate(2L, "PASSED", 12L)));

        PerformanceCalculationResult result = calculator.calculate(input);

        assertEquals(new BigDecimal("6.25"), result.getQualityScore());
        assertTrue(result.getDetailJson().contains("NO_APPLICABLE_GATE"));
        assertTrue(result.getDetailJson().contains("approvedEvidence"));
    }

    @Test
    void collaborationUsesOnlyReviewedEvidenceBackedFactsAndCapsBeforeDeductions() {
        PerformanceCalculationInput input = input(task(1L, 100, "CONFIRMED", "ONTIME"));
        input.setCollaborationRecords(Arrays.asList(
                collab(1L, "CROSS_DEPT", "10", "APPROVED", "https://e/1"),
                collab(2L, "KNOWLEDGE", "8", "APPROVED", "https://e/2"),
                collab(3L, "BACKUP", "8", "APPROVED", "https://e/3"),
                collab(4L, "OVERDUE", "-2", "APPROVED", null),
                collab(5L, "CROSS_DEPT", "99", "PENDING", "https://e/5"),
                collab(6L, "KNOWLEDGE", "99", "APPROVED", null)));

        PerformanceCalculationResult result = calculator.calculate(input);

        assertEquals(new BigDecimal("13.00"), result.getCollaborationScore());
        assertEquals(new BigDecimal("73.00"), result.getTotalScore());
        assertTrue(result.getDetailJson().contains("positiveBeforeDeduction"));
    }

    @Test
    void closeTreatsUnconfirmedKeyTaskAsUndoneWithoutMutatingWorkflowAndSnapshotsSources() {
        LabTask active = task(9L, 100, "ACTIVE", "DOING");
        PerformanceCalculationInput input = input(active);
        input.setCloseMode(true);

        PerformanceCalculationResult result = calculator.calculate(input);

        assertEquals("ACTIVE", active.getWorkflowStatus());
        assertEquals("DOING", active.getResultStatus());
        assertEquals(new BigDecimal("0.00"), result.getDeliveryScore());
        assertTrue(result.getDetailJson().contains("MONTH_CLOSE_UNCONFIRMED_AS_UNDONE"));
        assertTrue(result.getDetailJson().contains("2026-09-01T02:00:00Z"));
        assertTrue(result.getDetailJson().contains("AILAB_PERF_V1"));
    }

    @Test
    void redLineKeepsNumericScoreAndSnapshotsEvidenceAndAssetTriggers() {
        LabTask noEvidence = task(1L, 100, "CONFIRMED", "ONTIME");
        PerformanceCalculationInput input = input(noEvidence);
        input.setCloseMode(true);
        input.setAssetFacts(Collections.singletonList(assetFact(21L, "Critical model", false, false)));

        PerformanceCalculationResult result = calculator.calculate(input);

        assertTrue(result.isRedLine());
        assertEquals("RED_LINE", result.getResultStatus());
        assertEquals(new BigDecimal("60.00"), result.getDeliveryScore());
        assertTrue(result.getDetailJson().contains("MISSING_APPROVED_DELIVERY_EVIDENCE"));
        assertTrue(result.getDetailJson().contains("CRITICAL_ASSET_WITHOUT_BACKUP"));
        assertTrue(result.getDetailJson().contains("Critical model"));
    }

    @Test
    void closeLocksInStableOrderCreatesOneOverdueAndIncrementsRevisions() {
        manager(100L, 900L);
        LabPeriodClose open = period("2026-08", "OPEN", 3);
        when(mapper.selectPeriodForUpdate("2026-08")).thenReturn(open);
        LabTask unsubmitted = task(1L, 100, "ACTIVE", "DOING");
        when(mapper.selectPeriodTasksForUpdate("2026-08")).thenReturn(Collections.singletonList(unsubmitted));
        when(mapper.selectActiveMembersForUpdate()).thenReturn(Collections.singletonList(member(7L)));
        when(mapper.selectEvidenceForTaskIds(Collections.singletonList(1L))).thenReturn(Collections.<LabTaskEvidence>emptyList());
        when(mapper.selectQualityGatesForTaskIds(Collections.singletonList(1L))).thenReturn(Collections.<LabTaskQualityGate>emptyList());
        when(mapper.selectCollaborationForPeriod("2026-08")).thenReturn(Collections.<LabCollaborationRecord>emptyList());
        when(mapper.selectCriticalAssetFactsForUpdate("2026-07", "2026-09")).thenReturn(Collections.<PerformanceAssetFact>emptyList());
        when(mapper.selectMaxRevision(7L, "2026-08")).thenReturn(2);
        when(mapper.insertOverdueRecord(any(LabCollaborationRecord.class))).thenReturn(1);
        when(mapper.insertPerfScore(any(LabPerfScore.class))).thenReturn(1);
        when(mapper.lockTasksForPeriod(eq("2026-08"), eq("1"))).thenReturn(1);
        when(mapper.closePeriod(eq(open.getId()), eq(3), eq("100"), any(Date.class), eq("close"))).thenReturn(1);

        List<LabPerfScore> scores = service.closePeriod("2026-08", "close", 100L);

        assertEquals(1, scores.size());
        assertEquals(3, scores.get(0).getRevisionNo());
        assertEquals("ACTIVE", unsubmitted.getWorkflowStatus());
        assertEquals("DOING", unsubmitted.getResultStatus());
        ArgumentCaptor<LabCollaborationRecord> overdue = ArgumentCaptor.forClass(LabCollaborationRecord.class);
        verify(mapper).insertOverdueRecord(overdue.capture());
        assertEquals("PERIOD_OVERDUE:2026-08:1", overdue.getValue().getIdempotencyKey());
        assertEquals("APPROVED", overdue.getValue().getReviewStatus());
        verify(mapper).markCurrentScoresHistorical("2026-08", 7L, "100");
    }

    @Test
    void secondCloseIsIdempotentAndDoesNotCreateScoresOrDeductions() {
        manager(100L, 900L);
        LabPeriodClose closed = period("2026-08", "CLOSED", 4);
        when(mapper.selectPeriodForUpdate("2026-08")).thenReturn(closed);
        when(mapper.selectCurrentScores("2026-08")).thenReturn(Collections.singletonList(score(1L, 7L, 3)));

        List<LabPerfScore> result = service.closePeriod("2026-08", "again", 100L);

        assertEquals(1, result.size());
        verify(mapper, never()).selectPeriodTasksForUpdate(any(String.class));
        verify(mapper, never()).insertOverdueRecord(any(LabCollaborationRecord.class));
        verify(mapper, never()).insertPerfScore(any(LabPerfScore.class));
    }

    @Test
    void reopenRequiresManagerAndReasonPreservesRevisionsAndUnlocksTasks() {
        manager(100L, 900L);
        LabPeriodClose closed = period("2026-08", "CLOSED", 4);
        closed.setReopenHistoryJson("[{\"version\":1}]");
        when(mapper.selectPeriodForUpdate("2026-08")).thenReturn(closed);
        List<LabTask> lockedTasks = Arrays.asList(task(1L, 25, "CONFIRMED", "ONTIME"),
                task(2L, 25, "CONFIRMED", "ONTIME"), task(3L, 25, "CONFIRMED", "ONTIME"),
                task(4L, 25, "CONFIRMED", "ONTIME"));
        when(mapper.selectPeriodTasksForUpdate("2026-08")).thenReturn(lockedTasks);
        when(mapper.selectCurrentScoresForUpdate("2026-08")).thenReturn(Arrays.asList(score(1L, 7L, 1), score(2L, 8L, 1)));
        when(mapper.markPeriodScoresHistorical("2026-08", "100")).thenReturn(2);
        when(mapper.lockTasksForPeriod("2026-08", "0")).thenReturn(4);
        when(mapper.reopenPeriod(eq(closed.getId()), eq(4), eq("100"), any(Date.class), eq("late correction"))).thenReturn(1);

        service.reopenPeriod("2026-08", "late correction", 100L);

        verify(mapper).selectPeriodTasksForUpdate("2026-08");
        verify(mapper).selectCurrentScoresForUpdate("2026-08");
        verify(mapper).markPeriodScoresHistorical("2026-08", "100");
        verify(mapper).lockTasksForPeriod("2026-08", "0");
        verify(mapper).reopenPeriod(eq(closed.getId()), eq(4), eq("100"), any(Date.class), eq("late correction"));
        assertThrows(ServiceException.class, () -> service.reopenPeriod("2026-08", " ", 100L));
    }

    @Test
    void reopenStopsBeforeUnlockWhenOnlySomeCurrentScoresBecomeHistorical() {
        manager(100L, 900L);
        LabPeriodClose closed = period("2026-08", "CLOSED", 4);
        when(mapper.selectPeriodForUpdate("2026-08")).thenReturn(closed);
        when(mapper.selectPeriodTasksForUpdate("2026-08")).thenReturn(Collections.<LabTask>emptyList());
        when(mapper.selectCurrentScoresForUpdate("2026-08")).thenReturn(Arrays.asList(score(1L, 7L, 1), score(2L, 8L, 1)));
        when(mapper.markPeriodScoresHistorical("2026-08", "100")).thenReturn(1);

        assertThrows(ServiceException.class, () -> service.reopenPeriod("2026-08", "correction", 100L));

        verify(mapper, never()).lockTasksForPeriod(any(String.class), any(String.class));
        verify(mapper, never()).reopenPeriod(any(Long.class), any(Integer.class), any(String.class), any(Date.class), any(String.class));
    }

    @Test
    void collaborationReviewRejectsSelfReviewAndUsesServerActorAndClock() {
        manager(100L, 9L);
        LabCollaborationRecord own = collab(1L, "CROSS_DEPT", "3", "PENDING", "https://e/1");
        own.setFromMemberId(9L);
        when(mapper.selectCollaborationForUpdate(1L)).thenReturn(own);
        assertThrows(ServiceException.class, () -> service.reviewCollaboration(1L,
                new CollaborationReviewCommand(new BigDecimal("3"), "ok"), 100L));

        own.setFromMemberId(8L);
        when(mapper.reviewCollaboration(eq(1L), eq(new BigDecimal("3.00")), eq(9L),
                eq(Date.from(CLOCK.instant())), eq("ok"), eq("100"))).thenReturn(1);
        service.reviewCollaboration(1L, new CollaborationReviewCommand(new BigDecimal("3"), "ok"), 100L);
        verify(mapper).reviewCollaboration(eq(1L), eq(new BigDecimal("3.00")), eq(9L),
                eq(Date.from(CLOCK.instant())), eq("ok"), eq("100"));
    }

    @Test
    void redLineRevocationRequiresManagerEvidenceAndReasonAndDoesNotRewriteDetail() {
        manager(100L, 9L);
        LabPerfScore score = score(1L, 7L, 2);
        score.setRedLineFlag("1"); score.setRevokedFlag("0");
        score.setDetailJson("{\"redLineTriggers\":[{\"code\":\"MISSING\"}]}"); score.setVersion(5);
        when(mapper.selectScoreForUpdate(1L)).thenReturn(score);
        when(mapper.revokeRedLine(eq(1L), eq(5), eq("https://fix/evidence"), eq("corrected"),
                eq(9L), eq(Date.from(CLOCK.instant())), eq("100"))).thenReturn(1);

        service.revokeRedLine(1L, new RedLineRevokeCommand("https://fix/evidence", "corrected"), 100L);

        verify(mapper).revokeRedLine(eq(1L), eq(5), eq("https://fix/evidence"), eq("corrected"),
                eq(9L), eq(Date.from(CLOCK.instant())), eq("100"));
        assertEquals("{\"redLineTriggers\":[{\"code\":\"MISSING\"}]}", score.getDetailJson());
        assertThrows(ServiceException.class, () -> service.revokeRedLine(1L, new RedLineRevokeCommand("", "why"), 100L));
    }

    @Test
    void calibrationIsManagerOnlyReadsThreeMonthsAndCannotMaskActiveRedLine() {
        manager(100L, 9L);
        LabPerfScore july=score(1L,7L,1); july.setPeriod("2026-07");
        LabPerfScore august=score(2L,7L,1); august.setPeriod("2026-08");
        LabPerfScore september=score(3L,7L,1); september.setPeriod("2026-09");
        when(mapper.selectCurrentMonthlyScoresForUpdate(7L, "2026-07", "2026-09"))
                .thenReturn(Arrays.asList(july, august, september));
        when(mapper.selectMaxRevision(7L, "2026-Q3")).thenReturn(0);
        when(mapper.insertPerfScore(any(LabPerfScore.class))).thenReturn(1);

        LabPerfScore result = service.calibrateQuarter("2026-Q3", 7L,
                new CalibrationCommand(new BigDecimal("88.5"), "three-month review", "NORMAL"), 100L);

        assertEquals(new BigDecimal("88.50"), result.getCalibrateScore());
        assertTrue(result.getDetailJson().contains("2026-07"));
        assertTrue(result.getDetailJson().contains("2026-09"));

        LabPerfScore red = score(4L, 7L, 1); red.setRedLineFlag("1"); red.setRevokedFlag("0");
        when(mapper.selectCurrentMonthlyScoresForUpdate(7L, "2026-07", "2026-09"))
                .thenReturn(Arrays.asList(red, august, september));
        assertThrows(ServiceException.class, () -> service.calibrateQuarter("2026-Q3", 7L,
                new CalibrationCommand(new BigDecimal("88"), "comment", "NORMAL"), 100L));
    }

    @Test
    void calibrationRejectsUnknownStatusAndCarriesActiveRedLineIntoSnapshot() {
        manager(100L, 9L);
        LabPerfScore july=score(1L,7L,1); july.setPeriod("2026-07"); july.setRedLineFlag("1"); july.setRevokedFlag("0");
        LabPerfScore august=score(2L,7L,1); august.setPeriod("2026-08");
        LabPerfScore september=score(3L,7L,1); september.setPeriod("2026-09");
        when(mapper.selectCurrentMonthlyScoresForUpdate(7L, "2026-07", "2026-09"))
                .thenReturn(Arrays.asList(july, august, september));
        when(mapper.selectMaxRevision(7L, "2026-Q3")).thenReturn(0);
        when(mapper.insertPerfScore(any(LabPerfScore.class))).thenReturn(1);

        assertThrows(ServiceException.class, () -> service.calibrateQuarter("2026-Q3", 7L,
                new CalibrationCommand(new BigDecimal("90"), "review", "EXCELLENT"), 100L));

        LabPerfScore result = service.calibrateQuarter("2026-Q3", 7L,
                new CalibrationCommand(new BigDecimal("90"), "review", "RED_LINE"), 100L);
        assertEquals("RED_LINE", result.getResultStatus());
        assertEquals("1", result.getRedLineFlag());
        assertTrue(result.getDetailJson().contains("activeMonthlyRedLine"));
    }

    @Test
    void personalReadIgnoresClientMemberIdAndUsesAuthenticatedMapping() {
        memberActor(200L, 77L, "vision");
        when(mapper.selectScoresForMember(77L, "2026-08")).thenReturn(Collections.singletonList(score(1L, 77L, 1)));
        assertEquals(1, service.listMyScores("2026-08", 200L).size());
        verify(mapper).selectScoresForMember(77L, "2026-08");
    }

    @Test
    void previewUsesNonLockingReadsAndNeverRunsForUpdateInReadOnlyTransaction() {
        LabTask confirmed = task(1L, 100, "CONFIRMED", "ONTIME");
        when(mapper.selectPeriodTasks("2026-08")).thenReturn(Collections.singletonList(confirmed));
        when(mapper.selectEvidenceForTaskIds(Collections.singletonList(1L))).thenReturn(Collections.singletonList(evidence(1L, "APPROVED")));
        when(mapper.selectQualityGatesForTaskIds(Collections.singletonList(1L))).thenReturn(Collections.<LabTaskQualityGate>emptyList());
        when(mapper.selectCollaborationForPeriod("2026-08")).thenReturn(Collections.<LabCollaborationRecord>emptyList());
        when(mapper.selectCriticalAssetFacts("2026-07", "2026-09")).thenReturn(Collections.<PerformanceAssetFact>emptyList());

        assertEquals(new BigDecimal("60.00"), service.preview(7L, "2026-08", 100L).getDeliveryScore());

        verify(mapper).selectPeriodTasks("2026-08");
        verify(mapper, never()).selectPeriodTasksForUpdate(any(String.class));
        verify(mapper, never()).selectCriticalAssetFactsForUpdate(any(String.class), any(String.class));
    }

    @Test
    void affectedRowFailureAbortsCloseBeforePeriodIsMarkedClosed() {
        manager(100L, 900L);
        LabPeriodClose open = period("2026-08", "OPEN", 3);
        when(mapper.selectPeriodForUpdate("2026-08")).thenReturn(open);
        when(mapper.selectPeriodTasksForUpdate("2026-08")).thenReturn(Collections.<LabTask>emptyList());
        when(mapper.selectActiveMembersForUpdate()).thenReturn(Collections.singletonList(member(7L)));
        when(mapper.selectEvidenceForTaskIds(Collections.<Long>emptyList())).thenReturn(Collections.<LabTaskEvidence>emptyList());
        when(mapper.selectQualityGatesForTaskIds(Collections.<Long>emptyList())).thenReturn(Collections.<LabTaskQualityGate>emptyList());
        when(mapper.selectCollaborationForPeriod("2026-08")).thenReturn(Collections.<LabCollaborationRecord>emptyList());
        when(mapper.selectCriticalAssetFactsForUpdate("2026-07", "2026-09")).thenReturn(Collections.<PerformanceAssetFact>emptyList());
        when(mapper.selectMaxRevision(7L, "2026-08")).thenReturn(0);
        when(mapper.insertPerfScore(any(LabPerfScore.class))).thenReturn(0);

        assertThrows(ServiceException.class, () -> service.closePeriod("2026-08", "close", 100L));
        verify(mapper, never()).closePeriod(any(Long.class), any(Integer.class), any(String.class), any(Date.class), any(String.class));
    }

    @Test
    void clientCannotForgeCollaborationCreatorOrReviewMetadata() {
        memberActor(200L, 77L, "vision");
        LabCollaborationRecord submitted = collab(null, "KNOWLEDGE", "3", "APPROVED", "https://e/client");
        submitted.setFromMemberId(999L); submitted.setReviewerId(999L); submitted.setReviewTime(new Date(0)); submitted.setReviewComment("forged");
        when(mapper.insertCollaboration(submitted)).thenReturn(1);

        service.createCollaboration(submitted, 200L);

        assertEquals(Long.valueOf(77L), submitted.getFromMemberId());
        assertEquals("PENDING", submitted.getReviewStatus());
        assertEquals(null, submitted.getReviewerId());
        assertEquals(null, submitted.getReviewTime());
        assertEquals(null, submitted.getReviewComment());
        verify(mapper).insertCollaboration(submitted);
    }

    @Test
    void leadAndMemberCannotCloseReopenReviewRevokeOrCalibrate() {
        memberActor(200L, 77L, "vision");
        assertThrows(ServiceException.class, () -> service.closePeriod("2026-08", "close", 200L));
        assertThrows(ServiceException.class, () -> service.reopenPeriod("2026-08", "reason", 200L));
        assertThrows(ServiceException.class, () -> service.reviewCollaboration(1L, new CollaborationReviewCommand(BigDecimal.ONE, "review"), 200L));
        assertThrows(ServiceException.class, () -> service.revokeRedLine(1L, new RedLineRevokeCommand("https://e/fix", "reason"), 200L));
        assertThrows(ServiceException.class, () -> service.calibrateQuarter("2026-Q3", 77L, new CalibrationCommand(new BigDecimal("80"), "comment", "NORMAL"), 200L));
        verify(mapper, never()).selectPeriodForUpdate(any(String.class));
        verify(mapper, never()).selectCollaborationForUpdate(any(Long.class));
        verify(mapper, never()).selectScoreForUpdate(any(Long.class));
    }

    @Test
    void monthlyConfirmationAllowsSelfButRejectsAnotherMembersScore() {
        memberActor(200L, 77L, "vision");
        LabPerfScore own = score(1L, 77L, 1); own.setVersion(3); own.setConfirmationStatus("PENDING");
        when(mapper.selectScoreForUpdate(1L)).thenReturn(own);
        when(mapper.confirmScore(1L, 3, 77L, Date.from(CLOCK.instant()), "200")).thenReturn(1);
        service.confirmMonthlyScore(1L, 3, 200L);

        LabPerfScore other = score(2L, 88L, 1); other.setVersion(0);
        when(mapper.selectScoreForUpdate(2L)).thenReturn(other);
        assertThrows(ServiceException.class, () -> service.confirmMonthlyScore(2L, 0, 200L));
        verify(mapper, never()).confirmScore(eq(2L), eq(0), any(Long.class), any(Date.class), any(String.class));
    }

    private PerformanceCalculationInput input(LabTask... tasks) {
        PerformanceCalculationInput value = new PerformanceCalculationInput();
        value.setMemberId(7L); value.setPeriod("2026-08"); value.setCutoffTime(Date.from(CLOCK.instant()));
        value.setTasks(new ArrayList<LabTask>(Arrays.asList(tasks)));
        value.setEvidenceByTask(new HashMap<Long, List<LabTaskEvidence>>());
        value.setQualityGatesByTask(new HashMap<Long, List<LabTaskQualityGate>>());
        value.setCollaborationRecords(new ArrayList<LabCollaborationRecord>());
        value.setAssetFacts(new ArrayList<PerformanceAssetFact>());
        return value;
    }

    private LabTask task(Long id, int weight, String workflow, String result) {
        LabTask task = new LabTask(); task.setId(id); task.setOwnerId(7L); task.setPeriod("2026-08");
        task.setTaskLevel("month"); task.setTaskType("key"); task.setPerfWeight(new BigDecimal(weight));
        task.setWorkflowStatus(workflow); task.setResultStatus(result); task.setPeriodLockFlag("0"); task.setDelFlag("0");
        return task;
    }

    private LabTaskEvidence evidence(Long id, String status) {
        LabTaskEvidence e = new LabTaskEvidence(); e.setId(id); e.setEvidenceUrl("https://e/" + id); e.setAuditStatus(status); e.setDelFlag("0"); return e;
    }

    private LabTaskQualityGate gate(Long id, String status, Long evidenceId) {
        LabTaskQualityGate g = new LabTaskQualityGate(); g.setId(id); g.setGateStatus(status); g.setEvidenceId(evidenceId); g.setDelFlag("0"); return g;
    }

    private LabCollaborationRecord collab(Long id, String category, String points, String reviewStatus, String evidenceUrl) {
        LabCollaborationRecord c = new LabCollaborationRecord(); c.setId(id); c.setPeriod("2026-08"); c.setToMemberId(7L);
        c.setCategory(category); c.setSignedScore(new BigDecimal(points)); c.setReviewStatus(reviewStatus); c.setEvidenceUrl(evidenceUrl); c.setDelFlag("0"); return c;
    }

    private PerformanceAssetFact assetFact(Long id, String name, boolean activeBackup, boolean quarterTraining) {
        PerformanceAssetFact f = new PerformanceAssetFact(); f.setAssetId(id); f.setAssetName(name); f.setPrimaryOwnerId(7L);
        f.setActiveBackup(activeBackup); f.setQuarterBackupTraining(quarterTraining); return f;
    }

    private LabMember member(Long id) { LabMember m = new LabMember(); m.setId(id); m.setMemberStatus("ACTIVE"); return m; }
    private LabPeriodClose period(String period, String status, int version) { LabPeriodClose p = new LabPeriodClose(); p.setId(31L); p.setPeriod(period); p.setCloseStatus(status); p.setVersion(version); return p; }
    private LabPerfScore score(Long id, Long memberId, int revision) { LabPerfScore s = new LabPerfScore(); s.setId(id); s.setMemberId(memberId); s.setPeriod("2026-08"); s.setRevisionNo(revision); s.setCurrentFlag("1"); s.setVersion(0); return s; }

    private void manager(Long userId, Long memberId) { when(access.context(userId)).thenReturn(context(userId, memberId, LabAccessServiceImpl.MANAGER, "manage")); }
    private void memberActor(Long userId, Long memberId, String line) { when(access.context(userId)).thenReturn(context(userId, memberId, LabAccessServiceImpl.MEMBER, line)); }
    private LabAccessContext context(Long userId, Long memberId, String role, String line) { LabAccessContext c = new LabAccessContext(); c.setUserId(userId); c.setMemberId(memberId); c.setRoleKey(role); c.setBizLine(line); return c; }
}
