package com.ailab.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailab.system.constant.LabConstants;
import com.ailab.system.domain.LabTask;
import com.ailab.system.domain.LabTaskEvidence;
import com.ailab.system.dto.FieldValidationError;
import com.ailab.system.dto.TaskSubmitCommand;
import com.ailab.system.service.impl.TaskWorkflowServiceImpl;
import com.ruoyi.common.exception.ServiceException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class TaskWorkflowServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-12T01:02:03Z"), ZoneId.of("Asia/Shanghai"));
    private static final Long SUBMITTER_ID = 66L;
    private final TaskWorkflowService service = new TaskWorkflowServiceImpl(CLOCK);

    @Test
    void activatesAValidDraftPlan() {
        LabTask task = validDraft();

        List<FieldValidationError> errors = service.activatePlan(task);

        assertTrue(errors.isEmpty());
        assertEquals(LabConstants.WORKFLOW_ACTIVE, task.getWorkflowStatus());
        assertEquals(LabConstants.RESULT_DOING, task.getResultStatus());
    }

    @Test
    void commandCannotCarryTrustedActorOrReviewTimeFields() {
        assertThrows(NoSuchFieldException.class, () -> TaskSubmitCommand.class.getDeclaredField("actionUserId"));
        assertThrows(NoSuchFieldException.class, () -> TaskSubmitCommand.class.getDeclaredField("reviewerId"));
        assertThrows(NoSuchFieldException.class, () -> TaskSubmitCommand.class.getDeclaredField("reviewTime"));
    }

    @Test
    void commandDefensivelyCopiesApprovedEvidenceIds() {
        TaskSubmitCommand command = new TaskSubmitCommand();
        List<Long> ids = new java.util.ArrayList<Long>(Collections.singletonList(1L));
        command.setApprovedEvidenceIds(ids);
        ids.clear();
        command.getApprovedEvidenceIds().clear();

        assertEquals(Collections.singletonList(1L), command.getApprovedEvidenceIds());
    }

    @Test
    void returnsNamedErrorsForInvalidPlanFields() {
        LabTask task = new LabTask();
        task.setWorkflowStatus(LabConstants.WORKFLOW_DRAFT);
        task.setPerfWeight(new BigDecimal("101"));
        task.setGoalWeight(new BigDecimal("-1"));
        task.setCoordinationRequired(LabConstants.YES);

        List<String> fields = fields(service.activatePlan(task));

        assertTrue(fields.containsAll(Arrays.asList("title", "ownerId", "period", "planDate", "deliverable", "taskLevel", "bizLine", "taskType", "perfWeight", "goalWeight", "coordinationOwnerId", "coordinationDeptId", "coordinationContent", "coordinationSupport")));
    }

    @Test
    void reportsInvalidPlanDictionaryValuesAgainstTheirOwnFields() {
        LabTask task = validDraft();
        task.setTaskLevel("quarter");
        task.setTaskType("temporary");

        List<String> fields = fields(service.activatePlan(task));

        assertTrue(fields.contains("taskLevel"));
        assertTrue(fields.contains("taskType"));
    }

    @Test
    void rejectsActivationFromWrongSourceOrLockedPeriod() {
        LabTask active = validDraft();
        active.setWorkflowStatus(LabConstants.WORKFLOW_ACTIVE);
        assertThrows(ServiceException.class, () -> service.activatePlan(active));

        LabTask locked = validDraft();
        locked.setPeriodLockFlag(LabConstants.YES);
        assertThrows(ServiceException.class, () -> service.activatePlan(locked));
    }

    @Test
    void derivesOnTimeAndDelayedRatherThanTrustingClientCompletionLabel() {
        LabTask onTime = activeTask(LocalDate.of(2026, 8, 10));
        TaskSubmitCommand onTimeCommand = completionCommand(LocalDate.of(2026, 8, 10));
        onTimeCommand.setRequestedResultStatus(LabConstants.RESULT_ONTIME);

        List<FieldValidationError> invalidRequest = service.submitResult(onTime, onTimeCommand, SUBMITTER_ID);
        assertEquals(Collections.singletonList("requestedResultStatus"), fields(invalidRequest));
        assertEquals(LabConstants.WORKFLOW_ACTIVE, onTime.getWorkflowStatus());

        onTimeCommand.setRequestedResultStatus(null);
        assertTrue(service.submitResult(onTime, onTimeCommand, SUBMITTER_ID).isEmpty());
        assertEquals(LabConstants.RESULT_ONTIME, onTime.getResultStatus());
        assertEquals(LabConstants.WORKFLOW_PENDING_REVIEW, onTime.getWorkflowStatus());

        LabTask delayed = activeTask(LocalDate.of(2026, 8, 10));
        TaskSubmitCommand delayedCommand = completionCommand(LocalDate.of(2026, 8, 11));
        assertTrue(service.submitResult(delayed, delayedCommand, SUBMITTER_ID).isEmpty());
        assertEquals(LabConstants.RESULT_DELAYED, delayed.getResultStatus());
    }

    @Test
    void derivesCompletionDateUsingClockBusinessZoneAtShanghaiBoundary() {
        TaskWorkflowService shanghaiService = new TaskWorkflowServiceImpl(CLOCK);
        LabTask task = validDraft();
        task.setPlanDate(Date.from(Instant.parse("2026-08-10T15:30:00Z")));
        assertTrue(shanghaiService.activatePlan(task).isEmpty());
        TaskSubmitCommand command = completionCommand(LocalDate.of(2026, 8, 10));
        command.setActualFinishTime(Date.from(Instant.parse("2026-08-10T16:30:00Z")));

        assertTrue(shanghaiService.submitResult(task, command, SUBMITTER_ID).isEmpty());

        assertEquals(LabConstants.RESULT_DELAYED, task.getResultStatus());
    }

    @Test
    void validatesCompletionFieldsWithPreciseNames() {
        LabTask task = activeTask(LocalDate.of(2026, 8, 10));
        TaskSubmitCommand command = new TaskSubmitCommand();
        command.setRequestedResultStatus(LabConstants.RESULT_EXCEEDED);

        List<String> fields = fields(service.submitResult(task, command, SUBMITTER_ID));

        assertTrue(fields.containsAll(Arrays.asList("resultDesc", "actualFinishTime", "evidenceList")));
        assertEquals(LabConstants.WORKFLOW_ACTIVE, task.getWorkflowStatus());
    }

    @Test
    void acceptsUndoneWithoutFinishOrEvidenceButRequiresReasonAndAction() {
        LabTask task = activeTask(LocalDate.of(2026, 8, 10));
        TaskSubmitCommand invalid = new TaskSubmitCommand();
        invalid.setRequestedResultStatus(LabConstants.RESULT_UNDONE);
        assertTrue(fields(service.submitResult(task, invalid, SUBMITTER_ID)).containsAll(Arrays.asList("failReason", "nextAction")));

        TaskSubmitCommand command = new TaskSubmitCommand();
        command.setRequestedResultStatus(LabConstants.RESULT_UNDONE);
        command.setFailReason("依赖未按期交付");
        command.setNextAction("下周重新排期");
        assertTrue(service.submitResult(task, command, SUBMITTER_ID).isEmpty());
        assertEquals(LabConstants.RESULT_UNDONE, task.getResultStatus());
        assertEquals(LabConstants.WORKFLOW_PENDING_REVIEW, task.getWorkflowStatus());
    }

    @Test
    void reviewRevalidatesUndoneReasonAndNextAction() {
        LabTask task = activeTask(LocalDate.of(2026, 8, 10));
        TaskSubmitCommand command = new TaskSubmitCommand();
        command.setRequestedResultStatus(LabConstants.RESULT_UNDONE);
        command.setFailReason("blocked");
        command.setNextAction("reschedule");
        assertTrue(service.submitResult(task, command, SUBMITTER_ID).isEmpty());
        task.setFailReason(" ");
        task.setNextAction(null);

        assertEquals(Arrays.asList("failReason", "nextAction"), fields(service.reviewPass(task, reviewCommand(99L, false), 99L)));
        assertEquals(LabConstants.WORKFLOW_PENDING_REVIEW, task.getWorkflowStatus());
    }

    @Test
    void requiresEvidenceWithNonblankTitleAndUrlForCompletion() {
        LabTask task = activeTask(LocalDate.of(2026, 8, 10));
        TaskSubmitCommand command = completionCommand(LocalDate.of(2026, 8, 9));
        LabTaskEvidence evidence = new LabTaskEvidence();
        evidence.setEvidenceTitle(" ");
        evidence.setEvidenceUrl(" ");
        command.setEvidenceList(Collections.singletonList(evidence));

        List<String> fields = fields(service.submitResult(task, command, SUBMITTER_ID));

        assertEquals(Arrays.asList("evidenceList[0].evidenceType", "evidenceList[0].evidenceTitle", "evidenceList[0].evidenceUrl"), fields);
    }

    @Test
    void sanitizesClientControlledEvidenceAuditFieldsOnSubmission() {
        LabTask task = activeTask(LocalDate.of(2026, 8, 10));
        TaskSubmitCommand command = completionCommand(LocalDate.of(2026, 8, 9));
        LabTaskEvidence clientEvidence = new LabTaskEvidence();
        clientEvidence.setId(1L);
        clientEvidence.setEvidenceType("DOCUMENT");
        clientEvidence.setEvidenceTitle("验收报告");
        clientEvidence.setEvidenceUrl("https://example.invalid/evidence/report");
        Date clientAuditTime = dateAtStartOfDay(LocalDate.of(2026, 8, 8));
        clientEvidence.setAuditStatus(LabConstants.EVIDENCE_AUDIT_APPROVED);
        clientEvidence.setAuditorId(77L);
        clientEvidence.setAuditTime(clientAuditTime);
        clientEvidence.setAuditComment("client preapproval");
        command.setEvidenceList(Collections.singletonList(clientEvidence));

        assertTrue(service.submitResult(task, command, SUBMITTER_ID).isEmpty());

        LabTaskEvidence attached = task.getEvidenceList().get(0);
        assertEquals(LabConstants.EVIDENCE_AUDIT_PENDING, attached.getAuditStatus());
        assertEquals(null, attached.getAuditorId());
        assertEquals(null, attached.getAuditTime());
        assertEquals(null, attached.getAuditComment());
        assertEquals(LabConstants.EVIDENCE_AUDIT_APPROVED, clientEvidence.getAuditStatus());
        assertEquals(Long.valueOf(77L), clientEvidence.getAuditorId());
        assertEquals(clientAuditTime, clientEvidence.getAuditTime());
    }

    @Test
    void reviewPassVerifiesAttachedEvidenceUsingReviewerMetadata() {
        LabTask task = pendingOnTimeTask();
        TaskSubmitCommand review = reviewCommand(99L, false);
        Date reviewTime = Date.from(CLOCK.instant());
        review.setEvidenceAuditComment("evidence verified");

        assertTrue(service.reviewPass(task, review, 99L).isEmpty());

        LabTaskEvidence evidence = task.getEvidenceList().get(0);
        assertEquals(LabConstants.WORKFLOW_CONFIRMED, task.getWorkflowStatus());
        assertEquals(LabConstants.EVIDENCE_AUDIT_APPROVED, evidence.getAuditStatus());
        assertEquals(Long.valueOf(99L), evidence.getAuditorId());
        assertEquals(reviewTime, evidence.getAuditTime());
        assertEquals("evidence verified", evidence.getAuditComment());
    }

    @Test
    void reviewRequiresAtLeastOneValidEvidence() {
        LabTask task = pendingOnTimeTask();
        LabTaskEvidence invalid = new LabTaskEvidence();
        invalid.setEvidenceTitle(" ");
        invalid.setEvidenceUrl("https://example.invalid/evidence/invalid");
        task.setEvidenceList(Collections.singletonList(invalid));

        assertEquals(Collections.singletonList("approvedEvidenceIds"), fields(service.reviewPass(task, reviewCommand(99L, false), 99L)));
        assertEquals(LabConstants.WORKFLOW_PENDING_REVIEW, task.getWorkflowStatus());
    }

    @Test
    void rejectsMixedEvidenceBatchWithPreciseIndexedErrorsAndNoTaskMutation() {
        LabTask task = activeTask(LocalDate.of(2026, 8, 10));
        TaskSubmitCommand command = completionCommand(LocalDate.of(2026, 8, 9));
        LabTaskEvidence valid = command.getEvidenceList().get(0);
        LabTaskEvidence missingType = new LabTaskEvidence();
        missingType.setEvidenceTitle("missing type");
        missingType.setEvidenceUrl("https://example.invalid/evidence/missing-type");
        LabTaskEvidence missingTitle = new LabTaskEvidence();
        missingTitle.setEvidenceType("DOCUMENT");
        missingTitle.setEvidenceUrl("https://example.invalid/evidence/missing-title");
        LabTaskEvidence missingUrl = new LabTaskEvidence();
        missingUrl.setEvidenceType("DOCUMENT");
        missingUrl.setEvidenceTitle("missing url");
        command.setEvidenceList(Arrays.asList(valid, missingType, missingTitle, missingUrl, null));

        List<FieldValidationError> errors = service.submitResult(task, command, SUBMITTER_ID);

        assertEquals(Arrays.asList("evidenceList[1].evidenceType", "evidenceList[2].evidenceTitle", "evidenceList[3].evidenceUrl", "evidenceList[4]"), fields(errors));
        assertEquals(LabConstants.WORKFLOW_ACTIVE, task.getWorkflowStatus());
        assertEquals(LabConstants.RESULT_DOING, task.getResultStatus());
        assertNull(task.getActualFinishTime());
        assertTrue(task.getEvidenceList().isEmpty());
    }

    @Test
    void preservesApprovedEvidenceHistoryAcrossReopenAndSecondReview() {
        LabTask task = pendingOnTimeTask();
        TaskSubmitCommand firstReview = reviewCommand(99L, false);
        Date firstReviewTime = Date.from(CLOCK.instant());
        firstReview.setEvidenceAuditComment("first approval");
        assertTrue(service.reviewPass(task, firstReview, 99L).isEmpty());
        assertEquals(LabConstants.WORKFLOW_CONFIRMED, task.getWorkflowStatus());
        LabTaskEvidence historical = task.getEvidenceList().get(0);
        assertEquals(LabConstants.EVIDENCE_AUDIT_APPROVED, historical.getAuditStatus());
        String historicalStatus = historical.getAuditStatus();
        Long historicalAuditor = historical.getAuditorId();
        Date historicalTime = historical.getAuditTime();
        String historicalComment = historical.getAuditComment();

        service.managerReopen(task, 90L, "recheck");
        TaskSubmitCommand secondSubmission = completionCommand(LocalDate.of(2026, 8, 13));
        assertTrue(service.submitResult(task, secondSubmission, SUBMITTER_ID).isEmpty());
        assignEvidenceId(task, 1, 2L);
        assertEquals(LabConstants.EVIDENCE_AUDIT_PENDING, task.getEvidenceList().get(1).getAuditStatus());
        TaskSubmitCommand secondReview = reviewCommand(88L, false);
        secondReview.setApprovedEvidenceIds(Collections.singletonList(2L));
        Date secondReviewTime = Date.from(CLOCK.instant());
        secondReview.setEvidenceAuditComment("second approval");

        assertTrue(service.reviewPass(task, secondReview, 88L).isEmpty());

        LabTaskEvidence retained = task.getEvidenceList().get(0);
        LabTaskEvidence newlyApproved = task.getEvidenceList().get(1);
        assertEquals(historicalStatus, retained.getAuditStatus());
        assertEquals(historicalAuditor, retained.getAuditorId());
        assertEquals(historicalTime, retained.getAuditTime());
        assertEquals(historicalComment, retained.getAuditComment());
        assertEquals(LabConstants.EVIDENCE_AUDIT_APPROVED, newlyApproved.getAuditStatus());
        assertEquals(Long.valueOf(88L), newlyApproved.getAuditorId());
        assertEquals(secondReviewTime, newlyApproved.getAuditTime());
        assertEquals("second approval", newlyApproved.getAuditComment());
    }

    @Test
    void reopenedRoundRequiresExplicitApprovalOfNewPendingEvidence() {
        LabTask task = pendingOnTimeTask();
        assertTrue(service.reviewPass(task, reviewCommand(99L, false), 99L).isEmpty());
        LabTaskEvidence historical = task.getEvidenceList().get(0);
        String historicalStatus = historical.getAuditStatus();
        Long historicalAuditor = historical.getAuditorId();
        Date historicalTime = historical.getAuditTime();
        String historicalComment = historical.getAuditComment();

        service.managerReopen(task, 90L, "new evidence needed");
        TaskSubmitCommand resubmission = completionCommand(LocalDate.of(2026, 8, 9));
        assertTrue(service.submitResult(task, resubmission, SUBMITTER_ID).isEmpty());
        assignEvidenceId(task, 1, 2L);

        TaskSubmitCommand noSelection = reviewCommand(88L, false);
        noSelection.setApprovedEvidenceIds(Collections.<Long>emptyList());
        assertEquals(Collections.singletonList("approvedEvidenceIds"), fields(service.reviewPass(task, noSelection, 88L)));
        assertEquals(LabConstants.WORKFLOW_PENDING_REVIEW, task.getWorkflowStatus());

        TaskSubmitCommand approveNew = reviewCommand(88L, false);
        approveNew.setApprovedEvidenceIds(Collections.singletonList(2L));
        assertTrue(service.reviewPass(task, approveNew, 88L).isEmpty());
        LabTaskEvidence retained = task.getEvidenceList().get(0);
        assertEquals(historicalStatus, retained.getAuditStatus());
        assertEquals(historicalAuditor, retained.getAuditorId());
        assertEquals(historicalTime, retained.getAuditTime());
        assertEquals(historicalComment, retained.getAuditComment());
    }

    @Test
    void submissionBuildsEvidenceServerFieldsAndIgnoresForgedClientValues() {
        LabTask task = activeTask(LocalDate.of(2026, 8, 10));
        task.setId(500L);
        TaskSubmitCommand command = completionCommand(LocalDate.of(2026, 8, 9));
        LabTaskEvidence client = command.getEvidenceList().get(0);
        client.setId(91L);
        client.setTaskId(92L);
        client.setSubmitterId(93L);
        client.setSubmitTime(new Date(0L));
        client.setDelFlag("1");
        client.setAuditStatus(LabConstants.EVIDENCE_AUDIT_APPROVED);
        client.setAuditorId(94L);
        client.setAuditTime(new Date(1L));
        client.setAuditComment("forged audit");
        command.setEvidenceList(Collections.singletonList(client));

        assertTrue(service.submitResult(task, command, SUBMITTER_ID).isEmpty());

        LabTaskEvidence attached = task.getEvidenceList().get(0);
        assertNull(attached.getId());
        assertEquals(Long.valueOf(500L), attached.getTaskId());
        assertEquals(Long.valueOf(66L), attached.getSubmitterId());
        assertEquals(Date.from(CLOCK.instant()), attached.getSubmitTime());
        assertEquals(LabConstants.NO, attached.getDelFlag());
        assertEquals(LabConstants.EVIDENCE_AUDIT_PENDING, attached.getAuditStatus());
        assertNull(attached.getAuditorId());
        assertNull(attached.getAuditTime());
        assertNull(attached.getAuditComment());
    }

    @Test
    void submissionRejectsEvidenceWithoutTypeBeforeChangingTask() {
        LabTask task = activeTask(LocalDate.of(2026, 8, 10));
        task.setId(500L);
        TaskSubmitCommand command = completionCommand(LocalDate.of(2026, 8, 9));
        LabTaskEvidence evidence = command.getEvidenceList().get(0);
        evidence.setEvidenceType(null);
        command.setEvidenceList(Collections.singletonList(evidence));

        assertEquals(Collections.singletonList("evidenceList[0].evidenceType"), fields(service.submitResult(task, command, SUBMITTER_ID)));
        assertEquals(LabConstants.WORKFLOW_ACTIVE, task.getWorkflowStatus());
        assertEquals(0, task.getEvidenceList().size());
    }

    @Test
    void submissionRequiresTrustedActorAndTaskIdentityForEvidence() {
        LabTask task = activeTask(LocalDate.of(2026, 8, 10));
        task.setId(null);
        TaskSubmitCommand command = completionCommand(LocalDate.of(2026, 8, 9));

        assertEquals(Arrays.asList("actorId", "taskId"), fields(service.submitResult(task, command, null)));
        assertEquals(LabConstants.WORKFLOW_ACTIVE, task.getWorkflowStatus());
    }

    @Test
    void onlyExplicitlySelectedPendingEvidenceIsApproved() {
        LabTask task = pendingOnTimeTask();
        TaskSubmitCommand secondSubmission = completionCommand(LocalDate.of(2026, 8, 9));
        service.withdraw(task);
        assertTrue(service.submitResult(task, secondSubmission, SUBMITTER_ID).isEmpty());
        assignEvidenceId(task, 1, 2L);

        TaskSubmitCommand review = reviewCommand(99L, false);
        review.setApprovedEvidenceIds(Collections.singletonList(1L));
        assertTrue(service.reviewPass(task, review, 99L).isEmpty());

        assertEquals(LabConstants.EVIDENCE_AUDIT_APPROVED, task.getEvidenceList().get(0).getAuditStatus());
        assertEquals(LabConstants.EVIDENCE_AUDIT_PENDING, task.getEvidenceList().get(1).getAuditStatus());
    }

    @Test
    void reviewRejectsInvalidEvidenceSelectionsWithoutPartialAuditMutation() {
        LabTask task = pendingOnTimeTask();
        TaskSubmitCommand review = reviewCommand(99L, false);
        review.setApprovedEvidenceIds(Arrays.asList(1L, 999L));

        assertEquals(Collections.singletonList("approvedEvidenceIds"), fields(service.reviewPass(task, review, 99L)));
        assertEquals(LabConstants.EVIDENCE_AUDIT_PENDING, task.getEvidenceList().get(0).getAuditStatus());
        assertEquals(LabConstants.WORKFLOW_PENDING_REVIEW, task.getWorkflowStatus());
    }

    @Test
    void evidenceHistoryDoesNotExposeMutableListItemsOrDates() {
        LabTask task = pendingOnTimeTask();
        assertTrue(service.reviewPass(task, reviewCommand(99L, false), 99L).isEmpty());
        List<LabTaskEvidence> external = task.getEvidenceList();
        LabTaskEvidence externalEvidence = external.get(0);
        Date externalAuditTime = externalEvidence.getAuditTime();
        external.clear();
        externalEvidence.setAuditStatus(LabConstants.EVIDENCE_AUDIT_PENDING);
        externalAuditTime.setTime(0L);

        LabTaskEvidence retained = task.getEvidenceList().get(0);
        assertEquals(1, task.getEvidenceList().size());
        assertEquals(LabConstants.EVIDENCE_AUDIT_APPROVED, retained.getAuditStatus());
        assertEquals(Date.from(CLOCK.instant()), retained.getAuditTime());
    }

    @Test
    void revalidatesCoordinationWhenAnActiveTaskIsSubmitted() {
        LabTask task = activeTask(LocalDate.of(2026, 8, 10));
        task.setCoordinationRequired(LabConstants.YES);
        task.setCoordinationOwnerId(null);
        task.setCoordinationDeptId(null);
        task.setCoordinationContent(" ");
        task.setCoordinationSupport(" ");
        task.setCoordinationDesc("legacy description cannot satisfy the new contract");

        assertEquals(Arrays.asList("coordinationOwnerId", "coordinationDeptId", "coordinationContent", "coordinationSupport"), fields(service.submitResult(task, completionCommand(LocalDate.of(2026, 8, 9)), SUBMITTER_ID)));
        assertEquals(LabConstants.WORKFLOW_ACTIVE, task.getWorkflowStatus());
    }

    @Test
    void attributesOnlyNonzeroPerformanceWeightForNonKeyMonthTasks() {
        LabTask task = validDraft();
        task.setTaskType(LabConstants.TASK_TYPE_DAILY);
        task.setPerfWeight(new BigDecimal("1"));
        task.setGoalWeight(BigDecimal.ZERO);

        assertEquals(Collections.singletonList("perfWeight"), fields(service.activatePlan(task)));
    }

    @Test
    void attributesOnlyNonzeroGoalWeightForNonKeyMonthTasks() {
        LabTask task = validDraft();
        task.setTaskType(LabConstants.TASK_TYPE_DAILY);
        task.setPerfWeight(BigDecimal.ZERO);
        task.setGoalWeight(new BigDecimal("1"));

        assertEquals(Collections.singletonList("goalWeight"), fields(service.activatePlan(task)));
    }

    @Test
    void rejectsSelfReviewAndRequiresExplicitExceededConfirmation() {
        LabTask task = pendingExceededTask();
        TaskSubmitCommand selfReview = reviewCommand(task.getOwnerId(), false);
        assertThrows(ServiceException.class, () -> service.reviewPass(task, selfReview, task.getOwnerId()));

        TaskSubmitCommand noConfirmation = reviewCommand(99L, false);
        assertEquals(Collections.singletonList("exceededConfirmed"), fields(service.reviewPass(task, noConfirmation, 99L)));
        assertEquals(LabConstants.WORKFLOW_PENDING_REVIEW, task.getWorkflowStatus());

        assertTrue(service.reviewPass(task, reviewCommand(99L, true), 99L).isEmpty());
        assertEquals(LabConstants.WORKFLOW_CONFIRMED, task.getWorkflowStatus());
    }

    @Test
    void requiresEvidenceAtReviewForCompletionResults() {
        LabTask task = activeTask(LocalDate.of(2026, 8, 10));
        TaskSubmitCommand submission = completionCommand(LocalDate.of(2026, 8, 9));
        assertTrue(service.submitResult(task, submission, SUBMITTER_ID).isEmpty());
        task.setEvidenceList(Collections.<LabTaskEvidence>emptyList());

        assertEquals(Collections.singletonList("approvedEvidenceIds"), fields(service.reviewPass(task, reviewCommand(99L, false), 99L)));
    }

    @Test
    void supportsWithdrawReturnAndManagerReopenWithoutDeletingEvidence() {
        LabTask withdrawn = pendingOnTimeTask();
        service.withdraw(withdrawn);
        assertEquals(LabConstants.WORKFLOW_ACTIVE, withdrawn.getWorkflowStatus());

        LabTask returned = pendingOnTimeTask();
        service.reviewReturn(returned, reviewCommand(99L, false), 99L);
        assertEquals(LabConstants.WORKFLOW_ACTIVE, returned.getWorkflowStatus());

        LabTask confirmed = pendingOnTimeTask();
        assertTrue(service.reviewPass(confirmed, reviewCommand(99L, false), 99L).isEmpty());
        int evidenceCount = confirmed.getEvidenceList().size();
        assertThrows(ServiceException.class, () -> service.managerReopen(confirmed, 99L, " "));
        service.managerReopen(confirmed, 99L, "补充核验材料");
        assertEquals(LabConstants.WORKFLOW_ACTIVE, confirmed.getWorkflowStatus());
        assertEquals(LabConstants.RESULT_DOING, confirmed.getResultStatus());
        assertEquals(evidenceCount, confirmed.getEvidenceList().size());
    }

    @Test
    void keepsConfirmedTasksImmutableExceptForManagerReopen() {
        LabTask confirmed = pendingOnTimeTask();
        assertTrue(service.reviewPass(confirmed, reviewCommand(99L, false), 99L).isEmpty());

        assertThrows(ServiceException.class, () -> service.submitResult(confirmed, completionCommand(LocalDate.of(2026, 8, 9)), SUBMITTER_ID));
        assertThrows(ServiceException.class, () -> service.withdraw(confirmed));
        assertThrows(ServiceException.class, () -> service.reviewReturn(confirmed, reviewCommand(99L, false), 99L));
    }

    private LabTask pendingExceededTask() {
        LabTask task = activeTask(LocalDate.of(2026, 8, 10));
        TaskSubmitCommand command = completionCommand(LocalDate.of(2026, 8, 9));
        command.setRequestedResultStatus(LabConstants.RESULT_EXCEEDED);
        assertTrue(service.submitResult(task, command, SUBMITTER_ID).isEmpty());
        assignEvidenceId(task, 0, 1L);
        return task;
    }

    private LabTask pendingOnTimeTask() {
        LabTask task = activeTask(LocalDate.of(2026, 8, 10));
        assertTrue(service.submitResult(task, completionCommand(LocalDate.of(2026, 8, 9)), SUBMITTER_ID).isEmpty());
        assignEvidenceId(task, 0, 1L);
        return task;
    }

    private LabTask validDraft() {
        LabTask task = new LabTask();
        task.setId(100L);
        task.setWorkflowStatus(LabConstants.WORKFLOW_DRAFT);
        task.setTaskLevel(LabConstants.TASK_LEVEL_MONTH);
        task.setPeriod("2026-08");
        task.setBizLine("algorithm");
        task.setTaskType(LabConstants.TASK_TYPE_KEY);
        task.setTitle("完成基准评测");
        task.setOwnerId(10L);
        task.setPlanDate(dateAtStartOfDay(LocalDate.of(2026, 8, 10)));
        task.setDeliverable("评测报告");
        task.setPerfWeight(new BigDecimal("50"));
        task.setGoalWeight(new BigDecimal("30"));
        task.setCoordinationRequired(LabConstants.NO);
        task.setPeriodLockFlag(LabConstants.NO);
        return task;
    }

    private LabTask activeTask(LocalDate planDate) {
        LabTask task = validDraft();
        task.setPlanDate(dateAtStartOfDay(planDate));
        assertTrue(service.activatePlan(task).isEmpty());
        return task;
    }

    private TaskSubmitCommand completionCommand(LocalDate finishDate) {
        TaskSubmitCommand command = new TaskSubmitCommand();
        command.setActualFinishTime(dateAtStartOfDay(finishDate));
        command.setResultDesc("完成验收并形成报告");
        LabTaskEvidence evidence = new LabTaskEvidence();
        evidence.setId(1L);
        evidence.setEvidenceType("DOCUMENT");
        evidence.setEvidenceTitle("验收报告");
        evidence.setEvidenceUrl("https://example.invalid/evidence/report");
        command.setEvidenceList(Collections.singletonList(evidence));
        return command;
    }

    private TaskSubmitCommand reviewCommand(Long reviewerId, boolean exceededConfirmed) {
        TaskSubmitCommand command = new TaskSubmitCommand();
        command.setReviewerComment("核验通过");
        command.setApprovedEvidenceIds(Collections.singletonList(1L));
        command.setExceededConfirmed(exceededConfirmed);
        return command;
    }

    private Date dateAtStartOfDay(LocalDate date) {
        return Date.from(date.atStartOfDay().toInstant(ZoneOffset.UTC));
    }

    private void assignEvidenceId(LabTask task, int index, Long id) {
        List<LabTaskEvidence> evidenceList = task.getEvidenceList();
        evidenceList.get(index).setId(id);
        task.setEvidenceList(evidenceList);
    }

    private List<String> fields(List<FieldValidationError> errors) {
        return errors.stream().map(FieldValidationError::getField).collect(Collectors.toList());
    }
}
