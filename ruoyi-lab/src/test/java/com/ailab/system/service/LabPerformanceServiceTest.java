package com.ailab.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
    void qualityRequiresPassedGateBoundToApprovedEvidence() {
        LabTask first = task(1L, 100, "CONFIRMED", "ONTIME");
        PerformanceCalculationInput input = input(first);
        input.getEvidenceByTask().put(1L, Arrays.asList(evidence(11L, "APPROVED"), evidence(12L, "PENDING")));
        input.getQualityGatesByTask().put(1L, Arrays.asList(gate(1L, "PASSED", 11L), gate(2L, "PASSED", 12L)));

        PerformanceCalculationResult result = calculator.calculate(input);

        assertEquals(new BigDecimal("12.50"), result.getQualityScore());
        assertTrue(result.getDetailJson().contains("approvedEvidence"));
    }

    @Test
    void presentButUnpassedQualityGateIsValidAndScoresZeroQuality() {
        PerformanceCalculationInput input = input(task(1L, 100, "CONFIRMED", "ONTIME"));
        input.getQualityGatesByTask().put(1L, Collections.singletonList(gate(1L, "PENDING", null)));

        PerformanceCalculationResult result = calculator.calculate(input);

        assertEquals(new BigDecimal("0.00"), result.getQualityScore());
        assertFalse(result.getDetailJson().contains("NO_APPLICABLE_GATE"));
    }

    @Test
    void previewRejectsMonthlyKeyTaskWithoutQualityGateWithTaskSpecificError() {
        LabTask missingGate = task(41L, 100, "CONFIRMED", "ONTIME"); missingGate.setTitle("Gate contract task");
        when(mapper.selectPeriodTasks("2026-08")).thenReturn(Collections.singletonList(missingGate));
        when(mapper.selectQualityGatesForTaskIds(Collections.singletonList(41L))).thenReturn(Collections.<LabTaskQualityGate>emptyList());

        ServiceException error = assertThrows(ServiceException.class, () -> service.preview(7L, "2026-08", 100L));

        assertTrue(error.getMessage().contains("41"));
        assertTrue(error.getMessage().contains("Gate contract task"));
        assertTrue(error.getMessage().toLowerCase().contains("quality gate"));
    }

    @Test
    void previewRejectsWhenAnyMonthlyKeyTaskInPeriodLacksGate() {
        LabTask own = task(44L, 100, "CONFIRMED", "ONTIME");
        LabTask other = task(45L, 100, "CONFIRMED", "ONTIME"); other.setOwnerId(88L); other.setTitle("Other member missing gate");
        when(mapper.selectPeriodTasks("2026-08")).thenReturn(Arrays.asList(own, other));
        when(mapper.selectQualityGatesForTaskIds(Arrays.asList(44L, 45L))).thenReturn(Collections.singletonList(gateForTask(44L, 144L, "PENDING", null)));

        ServiceException error = assertThrows(ServiceException.class, () -> service.preview(7L, "2026-08", 100L));

        assertTrue(error.getMessage().contains("45") && error.getMessage().contains("Other member missing gate"));
        verify(mapper, never()).selectEvidenceForTaskIds(anyList());
    }

    @Test
    void closeRejectsMissingGateBeforeBusinessAuditWritesAndReliesOnTransactionRollbackForPeriod() {
        manager(100L, 900L);
        LabTask missingGate = task(42L, 100, "ACTIVE", "DOING"); missingGate.setTitle("No gate close task");
        when(mapper.selectPeriodForUpdate("2026-08")).thenReturn(period("2026-08", "OPEN", 0));
        when(mapper.selectPeriodTasksForUpdate("2026-08")).thenReturn(Collections.singletonList(missingGate));
        when(mapper.selectQualityGatesForTaskIds(Collections.singletonList(42L))).thenReturn(Collections.<LabTaskQualityGate>emptyList());

        ServiceException error = assertThrows(ServiceException.class, () -> service.closePeriod("2026-08", "close", 100L));

        assertTrue(error.getMessage().contains("42"));
        InOrder periodOrder = inOrder(mapper);
        periodOrder.verify(mapper).ensureOpenPeriod("2026-08", "100");
        periodOrder.verify(mapper).selectPeriodForUpdate("2026-08");
        verify(mapper, never()).insertOverdueRecord(any(LabCollaborationRecord.class));
        verify(mapper, never()).insertPerfScore(any(LabPerfScore.class));
        verify(mapper, never()).lockTasksForPeriod(any(String.class), any(String.class));
        verify(mapper, never()).closePeriod(any(Long.class), any(Integer.class), any(String.class), any(Date.class), any(String.class));
    }

    @Test
    void closeWithExistingOpenPeriodRejectsMissingGateBeforeDeductionsOrScores() {
        manager(100L, 900L);
        LabPeriodClose open = period("2026-08", "OPEN", 3);
        LabTask missingGate = task(43L, 100, "ACTIVE", "DOING"); missingGate.setTitle("Existing period missing gate");
        when(mapper.selectPeriodForUpdate("2026-08")).thenReturn(open);
        when(mapper.selectPeriodTasksForUpdate("2026-08")).thenReturn(Collections.singletonList(missingGate));
        when(mapper.selectActiveMembersForUpdate()).thenReturn(Collections.singletonList(member(7L)));
        when(mapper.selectEvidenceForTaskIds(Collections.singletonList(43L))).thenReturn(Collections.<LabTaskEvidence>emptyList());
        when(mapper.selectQualityGatesForTaskIds(Collections.singletonList(43L))).thenReturn(Collections.<LabTaskQualityGate>emptyList());

        ServiceException error = assertThrows(ServiceException.class, () -> service.closePeriod("2026-08", "close", 100L));

        assertTrue(error.getMessage().contains("43"));
        verify(mapper, never()).insertOverdueRecord(any(LabCollaborationRecord.class));
        verify(mapper, never()).insertPerfScore(any(LabPerfScore.class));
        verify(mapper, never()).lockTasksForPeriod(any(String.class), any(String.class));
        verify(mapper, never()).closePeriod(any(Long.class), any(Integer.class), any(String.class), any(Date.class), any(String.class));
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
    void detailSnapshotIncludesEveryMemberFactWithDeterministicMetadataAndExclusions() {
        PerformanceCalculationInput input = input(task(1L, 100, "CONFIRMED", "ONTIME"));
        input.setCloseMode(true);
        LabTaskEvidence approved = evidence(10L, "APPROVED"); approved.setEvidenceType("REPORT"); approved.setEvidenceTitle("Accepted report"); approved.setSubmitterId(900L);
        approved.setSubmitTime(Date.from(Instant.parse("2026-08-29T01:02:03Z"))); approved.setAuditComment("verified against acceptance");
        approved.setAuditorId(901L); approved.setAuditTime(Date.from(Instant.parse("2026-08-30T01:02:03Z")));
        LabTaskEvidence pending = evidence(20L, "PENDING"); pending.setEvidenceType("LINK"); pending.setEvidenceTitle("Awaiting review");
        LabTaskEvidence missingUrl = evidence(30L, "APPROVED"); missingUrl.setEvidenceUrl(null); missingUrl.setEvidenceType("FILE"); missingUrl.setEvidenceTitle("No URL");
        input.getEvidenceByTask().put(1L, Arrays.asList(missingUrl, pending, approved));

        LabTaskQualityGate passed = gate(101L, "PASSED", 10L); passed.setGateNo("G-01"); passed.setGateName("Acceptance");
        passed.setCheckerId(902L); passed.setCheckTime(Date.from(Instant.parse("2026-08-30T02:03:04Z"))); passed.setCheckResult("ok");
        LabTaskQualityGate notPassed = gate(102L, "PENDING", 20L); notPassed.setGateNo("G-02"); notPassed.setGateName("Security");
        input.getQualityGatesByTask().put(1L, Arrays.asList(notPassed, passed));

        LabCollaborationRecord first = collab(1L, "CROSS_DEPT", "10", "APPROVED", "https://e/cross");
        first.setTaskId(1L); first.setFromMemberId(8L); first.setReviewerId(9L); first.setReviewTime(Date.from(Instant.parse("2026-08-30T03:04:05Z")));
        first.setRelatedAssetId(21L); first.setReviewComment("accepted collaboration"); first.setIdempotencyKey("MANUAL:COLLAB:1"); first.setVersion(3);
        first.setCreateBy("200"); first.setCreateTime(Date.from(Instant.parse("2026-08-28T01:02:03Z"))); first.setUpdateBy("100"); first.setUpdateTime(Date.from(Instant.parse("2026-08-30T03:04:06Z"))); first.setRemark("source remark");
        LabCollaborationRecord capReached = collab(2L, "CROSS_DEPT", "1", "APPROVED", "https://e/capped");
        LabCollaborationRecord pendingReview = collab(3L, "KNOWLEDGE", "2", "PENDING", "https://e/pending");
        LabCollaborationRecord rejected = collab(4L, "BACKUP", "2", "REJECTED", "https://e/rejected");
        LabCollaborationRecord missingEvidence = collab(5L, "KNOWLEDGE", "2", "APPROVED", null);
        LabCollaborationRecord deduction = collab(6L, "OVERDUE", "-2", "APPROVED", null);
        deduction.setIdempotencyKey("PERIOD_OVERDUE:2026-08:6"); deduction.setReviewComment("system overdue at cutoff"); deduction.setVersion(0); deduction.setCreateBy("100");
        LabCollaborationRecord unsupported = collab(7L, "UNKNOWN", "2", "APPROVED", "https://e/unknown");
        LabCollaborationRecord unrelated = collab(8L, "KNOWLEDGE", "5", "APPROVED", "https://secret/unrelated"); unrelated.setToMemberId(99L);
        input.setCollaborationRecords(Arrays.asList(unrelated, unsupported, deduction, missingEvidence, rejected, pendingReview, capReached, first));

        PerformanceCalculationResult result = calculator.calculate(input);
        String detail = result.getDetailJson();

        assertEquals(new BigDecimal("4.00"), result.getCollaborationScore());
        assertTrue(detail.contains("\"evidence\":["));
        assertTrue(detail.contains("\"evidenceId\":10") && detail.contains("\"type\":\"REPORT\"") && detail.contains("\"title\":\"Accepted report\""));
        assertTrue(detail.contains("\"auditorId\":901") && detail.contains("\"auditTime\":\"2026-08-30T01:02:03Z\""));
        assertTrue(detail.contains("\"submitterId\":900") && detail.contains("\"submitTime\":\"2026-08-29T01:02:03Z\"") && detail.contains("\"auditComment\":\"verified against acceptance\""));
        assertTrue(detail.contains("\"exclusionReason\":\"AUDIT_NOT_APPROVED\"") && detail.contains("\"exclusionReason\":\"MISSING_EVIDENCE_URL\""));
        assertTrue(detail.contains("\"gateNo\":\"G-01\"") && detail.contains("\"gateName\":\"Acceptance\""));
        assertTrue(detail.contains("\"checkerId\":902") && detail.contains("\"checkTime\":\"2026-08-30T02:03:04Z\"") && detail.contains("\"checkResult\":\"ok\""));
        assertTrue(detail.contains("\"recordId\":1") && detail.contains("\"reviewerId\":9") && detail.contains("\"reviewTime\":\"2026-08-30T03:04:05Z\""));
        for (long recordId = 1; recordId <= 7; recordId++) assertTrue(detail.contains("\"recordId\":" + recordId));
        assertTrue(detail.contains("https://e/pending") && detail.contains("https://e/rejected") && detail.contains("https://e/capped"));
        assertTrue(detail.contains("CATEGORY_CAP_REACHED") && detail.contains("REVIEW_NOT_APPROVED") && detail.contains("MISSING_EVIDENCE") && detail.contains("UNSUPPORTED_CATEGORY"));
        assertFalse(detail.contains("https://secret/unrelated"));
        assertTrue(detail.indexOf("\"evidenceId\":10") < detail.indexOf("\"evidenceId\":20"));
        assertTrue(detail.indexOf("\"recordId\":1") < detail.indexOf("\"recordId\":7"));
        com.alibaba.fastjson2.JSONArray collaborationItems = com.alibaba.fastjson2.JSON.parseObject(detail).getJSONObject("collaboration").getJSONArray("items");
        com.alibaba.fastjson2.JSONObject firstSnapshot = collaborationItems.getJSONObject(0);
        assertEquals(Long.valueOf(1L), firstSnapshot.getLong("id"));
        assertEquals(Long.valueOf(1L), firstSnapshot.getLong("recordId"));
        assertEquals(Long.valueOf(1L), firstSnapshot.getLong("taskId"));
        assertEquals("2026-08", firstSnapshot.getString("period"));
        assertEquals(Long.valueOf(21L), firstSnapshot.getLong("relatedAssetId"));
        assertEquals(Long.valueOf(8L), firstSnapshot.getLong("fromMemberId"));
        assertEquals(Long.valueOf(7L), firstSnapshot.getLong("toMemberId"));
        assertEquals("CROSS_DEPT", firstSnapshot.getString("category"));
        assertEquals(new BigDecimal("10.00"), firstSnapshot.getBigDecimal("signedScore"));
        assertEquals("https://e/cross", firstSnapshot.getString("evidenceUrl"));
        assertEquals(Long.valueOf(9L), firstSnapshot.getLong("reviewerId"));
        assertEquals("APPROVED", firstSnapshot.getString("reviewStatus"));
        assertEquals("2026-08-30T03:04:05Z", firstSnapshot.getString("reviewTime"));
        assertEquals("accepted collaboration", firstSnapshot.getString("reviewComment"));
        assertEquals("MANUAL:COLLAB:1", firstSnapshot.getString("idempotencyKey"));
        assertEquals(Integer.valueOf(3), firstSnapshot.getInteger("version"));
        assertEquals("200", firstSnapshot.getString("createBy"));
        assertEquals("2026-08-28T01:02:03Z", firstSnapshot.getString("createTime"));
        assertEquals("100", firstSnapshot.getString("updateBy"));
        assertEquals("2026-08-30T03:04:06Z", firstSnapshot.getString("updateTime"));
        assertEquals("0", firstSnapshot.getString("delFlag"));
        assertEquals("source remark", firstSnapshot.getString("remark"));
        com.alibaba.fastjson2.JSONObject overdueSnapshot = null; for (int i = 0; i < collaborationItems.size(); i++) if (collaborationItems.getJSONObject(i).getLongValue("recordId") == 6L) overdueSnapshot = collaborationItems.getJSONObject(i);
        assertTrue(overdueSnapshot != null && "PERIOD_OVERDUE:2026-08:6".equals(overdueSnapshot.getString("idempotencyKey")));
        assertEquals("system overdue at cutoff", overdueSnapshot.getString("reviewComment"));
        assertEquals(detail, calculator.calculate(input).getDetailJson());
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
    void quarterBackupSnapshotTracesQualifiedFactsAndScopedDecoysWithoutLeakingOtherMembers() {
        PerformanceCalculationInput input = backupInput();
        LabCollaborationRecord qualified = backupFact(71L, "2026-07", 21L, 7L, "BACKUP", "APPROVED", "https://e/july-training");
        qualified.setTaskId(701L); qualified.setFromMemberId(8L); qualified.setSignedScore(new BigDecimal("4.50"));
        qualified.setReviewerId(9L); qualified.setReviewTime(Date.from(Instant.parse("2026-07-20T01:02:03Z"))); qualified.setReviewComment("qualified backup training");
        qualified.setIdempotencyKey("BACKUP:2026-07:71"); qualified.setVersion(2); qualified.setCreateBy("200"); qualified.setCreateTime(Date.from(Instant.parse("2026-07-18T01:02:03Z")));
        qualified.setUpdateBy("100"); qualified.setUpdateTime(Date.from(Instant.parse("2026-07-20T01:02:04Z"))); qualified.setRemark("quarterly backup evidence");
        LabCollaborationRecord pending = backupFact(82L, "2026-07", 21L, 7L, "BACKUP", "PENDING", "https://e/pending");
        LabCollaborationRecord rejected = backupFact(83L, "2026-07", 21L, 7L, "BACKUP", "REJECTED", "https://e/rejected");
        LabCollaborationRecord missingEvidence = backupFact(84L, "2026-07", 21L, 7L, "BACKUP", "APPROVED", " ");
        LabCollaborationRecord wrongCategory = backupFact(85L, "2026-07", 21L, 7L, "KNOWLEDGE", "APPROVED", "https://e/wrong-category");
        LabCollaborationRecord wrongAsset = backupFact(86L, "2026-07", 22L, 7L, "BACKUP", "APPROVED", "https://e/wrong-asset");
        LabCollaborationRecord secretOtherMember = backupFact(87L, "2026-07", 21L, 99L, "BACKUP", "APPROVED", "https://secret/other-member");
        LabCollaborationRecord future = backupFact(88L, "2026-09", 21L, 7L, "BACKUP", "APPROVED", "https://e/future");
        LabCollaborationRecord orphanedTask = backupFact(89L, "2026-07", null, 7L, "BACKUP", "APPROVED", "https://e/orphaned-task");
        input.setQuarterCollaborationFacts(Arrays.asList(future, orphanedTask, secretOtherMember, wrongAsset, wrongCategory, missingEvidence, rejected, pending, qualified));

        PerformanceCalculationResult result = calculator.calculate(input);
        String detail = result.getDetailJson();
        com.alibaba.fastjson2.JSONObject snapshot = com.alibaba.fastjson2.JSON.parseObject(detail);
        com.alibaba.fastjson2.JSONArray facts = snapshot.getJSONArray("quarterBackupFacts");

        assertFalse(detail.contains("CRITICAL_ASSET_WITHOUT_BACKUP"));
        assertFalse(detail.contains("https://secret/other-member"));
        assertTrue(facts != null, "quarter backup facts must be snapshotted");
        assertEquals(8, facts.size());
        assertEquals(Arrays.asList(71L, 82L, 83L, 84L, 85L, 86L, 89L, 88L), Arrays.asList(
                facts.getJSONObject(0).getLongValue("recordId"), facts.getJSONObject(1).getLongValue("recordId"), facts.getJSONObject(2).getLongValue("recordId"),
                facts.getJSONObject(3).getLongValue("recordId"), facts.getJSONObject(4).getLongValue("recordId"), facts.getJSONObject(5).getLongValue("recordId"),
                facts.getJSONObject(6).getLongValue("recordId"), facts.getJSONObject(7).getLongValue("recordId")));
        com.alibaba.fastjson2.JSONObject qualifiedSnapshot = facts.getJSONObject(0);
        assertEquals("2026-07", qualifiedSnapshot.getString("period"));
        assertEquals("BACKUP", qualifiedSnapshot.getString("category"));
        assertEquals(Long.valueOf(21L), qualifiedSnapshot.getLong("relatedAssetId"));
        assertEquals(Long.valueOf(7L), qualifiedSnapshot.getLong("toMemberId"));
        assertEquals(new BigDecimal("4.50"), qualifiedSnapshot.getBigDecimal("score"));
        assertEquals("quarterly backup evidence", qualifiedSnapshot.getString("description"));
        assertEquals("https://e/july-training", qualifiedSnapshot.getString("evidenceUrl"));
        assertEquals("APPROVED", qualifiedSnapshot.getString("reviewStatus"));
        assertEquals(Long.valueOf(9L), qualifiedSnapshot.getLong("reviewerId"));
        assertEquals("2026-07-20T01:02:03Z", qualifiedSnapshot.getString("reviewTime"));
        assertEquals("qualified backup training", qualifiedSnapshot.getString("reviewComment"));
        assertEquals("BACKUP:2026-07:71", qualifiedSnapshot.getString("idempotencyKey"));
        assertEquals(Integer.valueOf(2), qualifiedSnapshot.getInteger("version"));
        assertEquals("200", qualifiedSnapshot.getString("createBy"));
        assertEquals("2026-07-18T01:02:03Z", qualifiedSnapshot.getString("createTime"));
        assertEquals("100", qualifiedSnapshot.getString("updateBy"));
        assertEquals("2026-07-20T01:02:04Z", qualifiedSnapshot.getString("updateTime"));
        assertEquals("0", qualifiedSnapshot.getString("delFlag"));
        assertEquals("quarterly backup evidence", qualifiedSnapshot.getString("remark"));
        assertTrue(qualifiedSnapshot.getBooleanValue("qualified"));
        assertTrue(qualifiedSnapshot.getBooleanValue("included"));
        assertEquals(Collections.singletonList(21L), qualifiedSnapshot.getList("matchedAssetIds", Long.class));
        assertEquals("REVIEW_NOT_APPROVED", facts.getJSONObject(1).getString("exclusionReason"));
        assertEquals("REVIEW_NOT_APPROVED", facts.getJSONObject(2).getString("exclusionReason"));
        assertEquals("MISSING_EVIDENCE", facts.getJSONObject(3).getString("exclusionReason"));
        assertEquals("CATEGORY_NOT_BACKUP", facts.getJSONObject(4).getString("exclusionReason"));
        assertEquals("NO_MATCHING_MEMBER_ASSET", facts.getJSONObject(5).getString("exclusionReason"));
        assertEquals("NO_MATCHING_MEMBER_ASSET", facts.getJSONObject(6).getString("exclusionReason"));
        assertEquals("OUTSIDE_CUTOFF_QUARTER", facts.getJSONObject(7).getString("exclusionReason"));
        com.alibaba.fastjson2.JSONObject asset = snapshot.getJSONArray("assetFacts").getJSONObject(0);
        assertTrue(asset.getBooleanValue("quarterBackupTraining"));
        assertEquals(Collections.singletonList(71L), asset.getList("qualifyingCollaborationIds", Long.class));
        assertEquals(detail, calculator.calculate(input).getDetailJson());
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
        when(mapper.selectQualityGatesForTaskIds(Collections.singletonList(1L))).thenReturn(Collections.singletonList(gateForTask(1L, 101L, "PENDING", null)));
        when(mapper.selectQuarterCollaborationFactsForUpdate("2026-07", "2026-08")).thenReturn(Collections.<LabCollaborationRecord>emptyList());
        when(mapper.selectCollaborationsForPeriodForUpdate("2026-08")).thenReturn(Collections.<LabCollaborationRecord>emptyList());
        when(mapper.selectCriticalAssetFactsForUpdate(any(String.class), any(String.class))).thenReturn(Collections.<PerformanceAssetFact>emptyList());
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
        verify(mapper, never()).selectCollaborationForPeriod("2026-08");
        InOrder lockOrder = inOrder(mapper);
        lockOrder.verify(mapper).selectPeriodForUpdate("2026-08");
        lockOrder.verify(mapper).selectQuarterCollaborationFactsForUpdate("2026-07", "2026-08");
        lockOrder.verify(mapper).selectCollaborationsForPeriodForUpdate("2026-08");
        lockOrder.verify(mapper).selectCriticalAssetFactsForUpdate("2026-07", "2026-08");
    }

    @Test
    void closeCapturesCutoffOnlyAfterEverySnapshotSourceIsLocked() {
        manager(100L, 900L);
        AtomicInteger clockCalls = new AtomicInteger(); AtomicInteger callsAtTaskLock = new AtomicInteger(-1); AtomicInteger callsAtAssetLock = new AtomicInteger(-1);
        Clock trackingClock = new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { clockCalls.incrementAndGet(); return CLOCK.instant(); }
        };
        service = new LabPerformanceServiceImpl(mapper, access, calculator, trackingClock);
        LabPeriodClose open = period("2026-08", "OPEN", 3);
        when(mapper.selectPeriodForUpdate("2026-08")).thenReturn(open);
        LabTask confirmed = task(1L, 100, "CONFIRMED", "ONTIME");
        when(mapper.selectPeriodTasksForUpdate("2026-08")).thenAnswer(invocation -> { callsAtTaskLock.set(clockCalls.get()); return Collections.singletonList(confirmed); });
        when(mapper.selectActiveMembersForUpdate()).thenReturn(Collections.singletonList(member(7L)));
        when(mapper.selectEvidenceForTaskIds(Collections.singletonList(1L))).thenReturn(Collections.singletonList(evidence(11L, "APPROVED")));
        when(mapper.selectQualityGatesForTaskIds(Collections.singletonList(1L))).thenReturn(Collections.singletonList(gateForTask(1L, 101L, "PENDING", null)));
        when(mapper.selectCollaborationsForPeriodForUpdate("2026-08")).thenReturn(Collections.<LabCollaborationRecord>emptyList());
        when(mapper.selectCriticalAssetFactsForUpdate("2026-07", "2026-08")).thenAnswer(invocation -> { callsAtAssetLock.set(clockCalls.get()); return Collections.<PerformanceAssetFact>emptyList(); });
        when(mapper.insertPerfScore(any(LabPerfScore.class))).thenReturn(1);
        when(mapper.lockTasksForPeriod("2026-08", "1")).thenReturn(1);
        when(mapper.closePeriod(eq(open.getId()), eq(3), eq("100"), any(Date.class), eq("close"))).thenReturn(1);

        service.closePeriod("2026-08", "close", 100L);

        assertEquals(0, callsAtTaskLock.get());
        assertEquals(0, callsAtAssetLock.get());
        assertEquals(1, clockCalls.get());
    }

    @Test
    void secondCloseIsIdempotentAndDoesNotCreateScoresOrDeductions() {
        manager(100L, 900L);
        LabPeriodClose closed = period("2026-08", "CLOSED", 4);
        when(mapper.selectPeriodForUpdate("2026-08")).thenReturn(closed);
        when(mapper.selectCurrentScoresForUpdate("2026-08")).thenReturn(Collections.singletonList(score(1L, 7L, 3)));

        List<LabPerfScore> result = service.closePeriod("2026-08", "again", 100L);

        assertEquals(1, result.size());
        verify(mapper, never()).selectCurrentScores("2026-08");
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
        when(mapper.selectCollaborationById(1L)).thenReturn(own);
        when(mapper.selectPeriodForUpdate("2026-08")).thenReturn(period("2026-08", "OPEN", 1));
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
    void collaborationCreateLocksOpenPeriodBeforeInsertAndRejectsClosedPeriod() {
        memberActor(200L, 77L, "vision");
        LabCollaborationRecord allowed = collab(null, "KNOWLEDGE", "3", "PENDING", "https://e/open");
        when(mapper.selectPeriodForUpdate("2026-08")).thenReturn(period("2026-08", "OPEN", 1));
        when(mapper.insertCollaboration(allowed)).thenReturn(1);

        service.createCollaboration(allowed, 200L);

        InOrder order = inOrder(mapper);
        order.verify(mapper).ensureOpenPeriod("2026-08", "200");
        order.verify(mapper).selectPeriodForUpdate("2026-08");
        order.verify(mapper).insertCollaboration(allowed);

        LabCollaborationRecord rejected = collab(null, "KNOWLEDGE", "2", "PENDING", "https://e/closed");
        rejected.setPeriod("2026-09");
        when(mapper.selectPeriodForUpdate("2026-09")).thenReturn(period("2026-09", "CLOSED", 2));
        assertThrows(ServiceException.class, () -> service.createCollaboration(rejected, 200L));
        verify(mapper, never()).insertCollaboration(rejected);
    }

    @Test
    void collaborationReviewRejectsClosedPeriodBeforeAuditUpdate() {
        manager(100L, 9L);
        LabCollaborationRecord pending = collab(81L, "CROSS_DEPT", "3", "PENDING", "https://e/81"); pending.setFromMemberId(8L);
        when(mapper.selectCollaborationById(81L)).thenReturn(pending);
        when(mapper.selectPeriodForUpdate("2026-08")).thenReturn(period("2026-08", "CLOSED", 2));

        assertThrows(ServiceException.class, () -> service.reviewCollaboration(81L,
                new CollaborationReviewCommand(new BigDecimal("3"), "review"), 100L));

        verify(mapper, never()).reviewCollaboration(eq(81L), any(BigDecimal.class), any(Long.class), any(Date.class), any(String.class), any(String.class));
    }

    @Test
    void reopenedPeriodAllowsNewCollaborationOnlyAfterSecondPeriodLock() {
        manager(100L, 900L);
        LabPeriodClose closed = period("2026-08", "CLOSED", 4);
        when(mapper.selectPeriodForUpdate("2026-08")).thenReturn(closed, period("2026-08", "OPEN", 5));
        when(mapper.selectPeriodTasksForUpdate("2026-08")).thenReturn(Collections.<LabTask>emptyList());
        when(mapper.selectCurrentScoresForUpdate("2026-08")).thenReturn(Collections.<LabPerfScore>emptyList());
        when(mapper.reopenPeriod(eq(closed.getId()), eq(4), eq("100"), any(Date.class), eq("correction"))).thenReturn(1);
        LabCollaborationRecord submitted = collab(null, "KNOWLEDGE", "2", "PENDING", "https://e/reopened");
        when(mapper.insertCollaboration(submitted)).thenReturn(1);

        service.reopenPeriod("2026-08", "correction", 100L);
        service.createCollaboration(submitted, 100L);

        verify(mapper, times(2)).selectPeriodForUpdate("2026-08");
        verify(mapper).ensureOpenPeriod("2026-08", "100");
        verify(mapper).insertCollaboration(submitted);
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
    void calibrationIgnoresClientStatusAndRejectsEveryActiveRedLine() {
        manager(100L, 9L);
        LabPerfScore july=score(1L,7L,1); july.setPeriod("2026-07"); july.setRedLineFlag("1"); july.setRevokedFlag("0");
        LabPerfScore august=score(2L,7L,1); august.setPeriod("2026-08");
        LabPerfScore september=score(3L,7L,1); september.setPeriod("2026-09");
        when(mapper.selectCurrentMonthlyScoresForUpdate(7L, "2026-07", "2026-09"))
                .thenReturn(Arrays.asList(july, august, september));
        when(mapper.selectMaxRevision(7L, "2026-Q3")).thenReturn(0);
        when(mapper.insertPerfScore(any(LabPerfScore.class))).thenReturn(1);

        assertThrows(ServiceException.class, () -> service.calibrateQuarter("2026-Q3", 7L,
                new CalibrationCommand(new BigDecimal("90"), "review", "RED_LINE"), 100L));
        verify(mapper, never()).insertPerfScore(any(LabPerfScore.class));

        july.setRedLineFlag("0");
        LabPerfScore result = service.calibrateQuarter("2026-Q3", 7L,
                new CalibrationCommand(new BigDecimal("90"), "review", "RED_LINE"), 100L);
        assertEquals("NORMAL", result.getResultStatus());
        assertEquals("0", result.getRedLineFlag());
        assertTrue(result.getDetailJson().contains("\"activeMonthlyRedLine\":false"));
    }

    @Test
    void personalReadIgnoresClientMemberIdAndUsesAuthenticatedMapping() {
        memberActor(200L, 77L, "vision");
        when(mapper.selectScoresForMember(77L, "2026-08")).thenReturn(Collections.singletonList(score(1L, 77L, 1)));
        assertEquals(1, service.listMyScores("2026-08", 200L).size());
        verify(mapper).selectScoresForMember(77L, "2026-08");
    }

    @Test
    void revisionHistoryIsManagerOnlyAndReturnsCurrentAndHistoricalRowsDescending() {
        manager(100L, 9L);
        LabPerfScore current = score(2L, 77L, 2);
        LabPerfScore historical = score(1L, 77L, 1); historical.setCurrentFlag("0");
        when(mapper.selectScoreRevisions(77L, "2026-08")).thenReturn(Arrays.asList(current, historical));

        List<LabPerfScore> revisions = service.listScoreRevisions(77L, "2026-08", 100L);
        assertEquals(Arrays.asList(current, historical), revisions);

        memberActor(200L, 77L, "vision");
        assertThrows(ServiceException.class, () -> service.listScoreRevisions(77L, "2026-08", 200L));
        verify(mapper, times(1)).selectScoreRevisions(77L, "2026-08");

        leadActor(201L, 78L, "vision");
        assertThrows(ServiceException.class, () -> service.listScoreRevisions(77L, "2026-08", 201L));
        verify(mapper, times(1)).selectScoreRevisions(77L, "2026-08");
    }

    @Test
    void previewUsesNonLockingReadsAndNeverRunsForUpdateInReadOnlyTransaction() {
        LabTask confirmed = task(1L, 100, "CONFIRMED", "ONTIME");
        when(mapper.selectPeriodTasks("2026-08")).thenReturn(Collections.singletonList(confirmed));
        when(mapper.selectEvidenceForTaskIds(Collections.singletonList(1L))).thenReturn(Collections.singletonList(evidence(1L, "APPROVED")));
        when(mapper.selectQualityGatesForTaskIds(Collections.singletonList(1L))).thenReturn(Collections.singletonList(gateForTask(1L, 101L, "PENDING", null)));
        when(mapper.selectCollaborationForPeriod("2026-08")).thenReturn(Collections.<LabCollaborationRecord>emptyList());
        when(mapper.selectQuarterCollaborationFacts("2026-07", "2026-08")).thenReturn(Collections.<LabCollaborationRecord>emptyList());
        when(mapper.selectCriticalAssetFacts(any(String.class), any(String.class))).thenReturn(Collections.<PerformanceAssetFact>emptyList());

        assertEquals(new BigDecimal("60.00"), service.preview(7L, "2026-08", 100L).getDeliveryScore());

        verify(mapper).selectPeriodTasks("2026-08");
        verify(mapper).selectQuarterCollaborationFacts("2026-07", "2026-08");
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
        when(mapper.selectCollaborationsForPeriodForUpdate("2026-08")).thenReturn(Collections.<LabCollaborationRecord>emptyList());
        when(mapper.selectCriticalAssetFactsForUpdate("2026-07", "2026-08")).thenReturn(Collections.<PerformanceAssetFact>emptyList());
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
        when(mapper.selectPeriodForUpdate("2026-08")).thenReturn(period("2026-08", "OPEN", 1));
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

    @Test
    void activeRedLineConfirmationIsManagerOnlyButRevokedRedLineReturnsToNormalRoleRules() {
        memberActor(200L, 77L, "vision");
        LabPerfScore activeMemberRedLine = score(11L, 77L, 1); activeMemberRedLine.setRedLineFlag("1"); activeMemberRedLine.setRevokedFlag("0");
        when(mapper.selectScoreForUpdate(11L)).thenReturn(activeMemberRedLine);
        assertThrows(ServiceException.class, () -> service.confirmMonthlyScore(11L, 0, 200L));
        verify(mapper, never()).confirmScore(eq(11L), any(Integer.class), any(Long.class), any(Date.class), any(String.class));

        leadActor(201L, 78L, "vision");
        LabPerfScore activeLeadRedLine = score(12L, 78L, 1); activeLeadRedLine.setRedLineFlag("1"); activeLeadRedLine.setRevokedFlag("0");
        when(mapper.selectScoreForUpdate(12L)).thenReturn(activeLeadRedLine);
        assertThrows(ServiceException.class, () -> service.confirmMonthlyScore(12L, 0, 201L));
        verify(mapper, never()).confirmScore(eq(12L), any(Integer.class), any(Long.class), any(Date.class), any(String.class));

        manager(100L, 9L);
        when(mapper.selectScoreForUpdate(13L)).thenReturn(activeMemberRedLine);
        when(mapper.confirmScore(13L, 0, 9L, Date.from(CLOCK.instant()), "100")).thenReturn(1);
        service.confirmMonthlyScore(13L, 0, 100L);

        LabPerfScore revoked = score(14L, 77L, 2); revoked.setRedLineFlag("1"); revoked.setRevokedFlag("1");
        when(mapper.selectScoreForUpdate(14L)).thenReturn(revoked);
        when(mapper.confirmScore(14L, 0, 77L, Date.from(CLOCK.instant()), "200")).thenReturn(1);
        service.confirmMonthlyScore(14L, 0, 200L);
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

    private LabTaskQualityGate gateForTask(Long taskId, Long id, String status, Long evidenceId) {
        LabTaskQualityGate gate = gate(id, status, evidenceId); gate.setTaskId(taskId); return gate;
    }

    private LabCollaborationRecord collab(Long id, String category, String points, String reviewStatus, String evidenceUrl) {
        LabCollaborationRecord c = new LabCollaborationRecord(); c.setId(id); c.setPeriod("2026-08"); c.setToMemberId(7L);
        c.setCategory(category); c.setSignedScore(new BigDecimal(points)); c.setReviewStatus(reviewStatus); c.setEvidenceUrl(evidenceUrl); c.setDelFlag("0"); return c;
    }

    private PerformanceAssetFact assetFact(Long id, String name, boolean activeBackup, boolean quarterTraining) {
        PerformanceAssetFact f = new PerformanceAssetFact(); f.setAssetId(id); f.setAssetName(name); f.setPrimaryOwnerId(7L);
        f.setActiveBackup(activeBackup); f.setQuarterBackupTraining(quarterTraining); return f;
    }

    private PerformanceCalculationInput backupInput() {
        LabTask task = task(1L, 100, "CONFIRMED", "ONTIME");
        PerformanceCalculationInput value = input(task); value.setCloseMode(true);
        value.getEvidenceByTask().put(1L, Collections.singletonList(evidence(11L, "APPROVED")));
        value.setAssetFacts(Collections.singletonList(assetFact(21L, "Critical model", false, false)));
        return value;
    }

    private LabCollaborationRecord backupFact(Long id, String period, Long assetId, Long toMemberId, String category, String status, String url) {
        LabCollaborationRecord value = collab(id, category, "1", status, url); value.setPeriod(period); value.setRelatedAssetId(assetId); value.setToMemberId(toMemberId); return value;
    }

    private LabMember member(Long id) { LabMember m = new LabMember(); m.setId(id); m.setMemberStatus("ACTIVE"); return m; }
    private LabPeriodClose period(String period, String status, int version) { LabPeriodClose p = new LabPeriodClose(); p.setId(31L); p.setPeriod(period); p.setCloseStatus(status); p.setVersion(version); return p; }
    private LabPerfScore score(Long id, Long memberId, int revision) { LabPerfScore s = new LabPerfScore(); s.setId(id); s.setMemberId(memberId); s.setPeriod("2026-08"); s.setRevisionNo(revision); s.setCurrentFlag("1"); s.setVersion(0); return s; }

    private void manager(Long userId, Long memberId) { when(access.context(userId)).thenReturn(context(userId, memberId, LabAccessServiceImpl.MANAGER, "manage")); }
    private void memberActor(Long userId, Long memberId, String line) { when(access.context(userId)).thenReturn(context(userId, memberId, LabAccessServiceImpl.MEMBER, line)); }
    private void leadActor(Long userId, Long memberId, String line) { when(access.context(userId)).thenReturn(context(userId, memberId, LabAccessServiceImpl.LEAD, line)); }
    private LabAccessContext context(Long userId, Long memberId, String role, String line) { LabAccessContext c = new LabAccessContext(); c.setUserId(userId); c.setMemberId(memberId); c.setRoleKey(role); c.setBizLine(line); return c; }
}
