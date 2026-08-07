package com.ailab.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class TaskWorkflowServiceTest {
    private final TaskWorkflowService service = new TaskWorkflowServiceImpl();

    @Test
    void activatesAValidDraftPlan() {
        LabTask task = validDraft();

        List<FieldValidationError> errors = service.activatePlan(task);

        assertTrue(errors.isEmpty());
        assertEquals(LabConstants.WORKFLOW_ACTIVE, task.getWorkflowStatus());
        assertEquals(LabConstants.RESULT_DOING, task.getResultStatus());
    }

    @Test
    void returnsNamedErrorsForInvalidPlanFields() {
        LabTask task = new LabTask();
        task.setWorkflowStatus(LabConstants.WORKFLOW_DRAFT);
        task.setPerfWeight(new BigDecimal("101"));
        task.setGoalWeight(new BigDecimal("-1"));
        task.setCoordinationRequired(LabConstants.YES);

        List<String> fields = fields(service.activatePlan(task));

        assertTrue(fields.containsAll(Arrays.asList("title", "ownerId", "period", "planDate", "deliverable", "taskLevel", "bizLine", "taskType", "perfWeight", "goalWeight", "coordinationOwnerId", "coordinationDesc")));
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

        List<FieldValidationError> invalidRequest = service.submitResult(onTime, onTimeCommand);
        assertEquals(Collections.singletonList("requestedResultStatus"), fields(invalidRequest));
        assertEquals(LabConstants.WORKFLOW_ACTIVE, onTime.getWorkflowStatus());

        onTimeCommand.setRequestedResultStatus(null);
        assertTrue(service.submitResult(onTime, onTimeCommand).isEmpty());
        assertEquals(LabConstants.RESULT_ONTIME, onTime.getResultStatus());
        assertEquals(LabConstants.WORKFLOW_PENDING_REVIEW, onTime.getWorkflowStatus());

        LabTask delayed = activeTask(LocalDate.of(2026, 8, 10));
        TaskSubmitCommand delayedCommand = completionCommand(LocalDate.of(2026, 8, 11));
        assertTrue(service.submitResult(delayed, delayedCommand).isEmpty());
        assertEquals(LabConstants.RESULT_DELAYED, delayed.getResultStatus());
    }

    @Test
    void validatesCompletionFieldsWithPreciseNames() {
        LabTask task = activeTask(LocalDate.of(2026, 8, 10));
        TaskSubmitCommand command = new TaskSubmitCommand();
        command.setRequestedResultStatus(LabConstants.RESULT_EXCEEDED);

        List<String> fields = fields(service.submitResult(task, command));

        assertTrue(fields.containsAll(Arrays.asList("resultDesc", "actualFinishTime", "evidenceList")));
        assertEquals(LabConstants.WORKFLOW_ACTIVE, task.getWorkflowStatus());
    }

    @Test
    void acceptsUndoneWithoutFinishOrEvidenceButRequiresReasonAndAction() {
        LabTask task = activeTask(LocalDate.of(2026, 8, 10));
        TaskSubmitCommand invalid = new TaskSubmitCommand();
        invalid.setRequestedResultStatus(LabConstants.RESULT_UNDONE);
        assertTrue(fields(service.submitResult(task, invalid)).containsAll(Arrays.asList("failReason", "nextAction")));

        TaskSubmitCommand command = new TaskSubmitCommand();
        command.setRequestedResultStatus(LabConstants.RESULT_UNDONE);
        command.setFailReason("依赖未按期交付");
        command.setNextAction("下周重新排期");
        assertTrue(service.submitResult(task, command).isEmpty());
        assertEquals(LabConstants.RESULT_UNDONE, task.getResultStatus());
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

        List<String> fields = fields(service.submitResult(task, command));

        assertTrue(fields.contains("evidenceList"));
    }

    @Test
    void sanitizesClientControlledEvidenceAuditFieldsOnSubmission() {
        LabTask task = activeTask(LocalDate.of(2026, 8, 10));
        TaskSubmitCommand command = completionCommand(LocalDate.of(2026, 8, 9));
        LabTaskEvidence clientEvidence = command.getEvidenceList().get(0);
        Date clientAuditTime = dateAtStartOfDay(LocalDate.of(2026, 8, 8));
        clientEvidence.setAuditStatus(LabConstants.EVIDENCE_AUDIT_VERIFIED);
        clientEvidence.setAuditorId(77L);
        clientEvidence.setAuditTime(clientAuditTime);
        clientEvidence.setAuditComment("client preapproval");

        assertTrue(service.submitResult(task, command).isEmpty());

        LabTaskEvidence attached = task.getEvidenceList().get(0);
        assertEquals(LabConstants.EVIDENCE_AUDIT_PENDING, attached.getAuditStatus());
        assertEquals(null, attached.getAuditorId());
        assertEquals(null, attached.getAuditTime());
        assertEquals(null, attached.getAuditComment());
        assertEquals(LabConstants.EVIDENCE_AUDIT_VERIFIED, clientEvidence.getAuditStatus());
        assertEquals(Long.valueOf(77L), clientEvidence.getAuditorId());
        assertEquals(clientAuditTime, clientEvidence.getAuditTime());
    }

    @Test
    void reviewPassVerifiesAttachedEvidenceUsingReviewerMetadata() {
        LabTask task = pendingOnTimeTask();
        TaskSubmitCommand review = reviewCommand(99L, false);
        Date reviewTime = dateAtStartOfDay(LocalDate.of(2026, 8, 12));
        review.setReviewTime(reviewTime);
        review.setEvidenceAuditComment("evidence verified");

        assertTrue(service.reviewPass(task, review).isEmpty());

        LabTaskEvidence evidence = task.getEvidenceList().get(0);
        assertEquals(LabConstants.WORKFLOW_CONFIRMED, task.getWorkflowStatus());
        assertEquals(LabConstants.EVIDENCE_AUDIT_VERIFIED, evidence.getAuditStatus());
        assertEquals(Long.valueOf(99L), evidence.getAuditorId());
        assertEquals(reviewTime, evidence.getAuditTime());
        assertEquals("evidence verified", evidence.getAuditComment());
    }

    @Test
    void invalidEvidenceCannotYieldConfirmedTask() {
        LabTask task = pendingOnTimeTask();
        LabTaskEvidence invalid = new LabTaskEvidence();
        invalid.setEvidenceTitle(" ");
        invalid.setEvidenceUrl("https://example.invalid/evidence/invalid");
        task.getEvidenceList().add(invalid);

        assertEquals(Collections.singletonList("evidenceList"), fields(service.reviewPass(task, reviewCommand(99L, false))));
        assertEquals(LabConstants.WORKFLOW_PENDING_REVIEW, task.getWorkflowStatus());
    }

    @Test
    void revalidatesCoordinationWhenAnActiveTaskIsSubmitted() {
        LabTask task = activeTask(LocalDate.of(2026, 8, 10));
        task.setCoordinationRequired(LabConstants.YES);
        task.setCoordinationOwnerId(null);
        task.setCoordinationDesc(" ");

        assertEquals(Arrays.asList("coordinationOwnerId", "coordinationDesc"), fields(service.submitResult(task, completionCommand(LocalDate.of(2026, 8, 9)))));
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
        assertThrows(ServiceException.class, () -> service.reviewPass(task, selfReview));

        TaskSubmitCommand noConfirmation = reviewCommand(99L, false);
        assertEquals(Collections.singletonList("exceededConfirmed"), fields(service.reviewPass(task, noConfirmation)));
        assertEquals(LabConstants.WORKFLOW_PENDING_REVIEW, task.getWorkflowStatus());

        assertTrue(service.reviewPass(task, reviewCommand(99L, true)).isEmpty());
        assertEquals(LabConstants.WORKFLOW_CONFIRMED, task.getWorkflowStatus());
    }

    @Test
    void requiresEvidenceAtReviewForCompletionResults() {
        LabTask task = activeTask(LocalDate.of(2026, 8, 10));
        TaskSubmitCommand submission = completionCommand(LocalDate.of(2026, 8, 9));
        assertTrue(service.submitResult(task, submission).isEmpty());
        task.setEvidenceList(Collections.<LabTaskEvidence>emptyList());

        assertEquals(Collections.singletonList("evidenceList"), fields(service.reviewPass(task, reviewCommand(99L, false))));
    }

    @Test
    void supportsWithdrawReturnAndManagerReopenWithoutDeletingEvidence() {
        LabTask withdrawn = pendingOnTimeTask();
        service.withdraw(withdrawn);
        assertEquals(LabConstants.WORKFLOW_ACTIVE, withdrawn.getWorkflowStatus());

        LabTask returned = pendingOnTimeTask();
        service.reviewReturn(returned, reviewCommand(99L, false));
        assertEquals(LabConstants.WORKFLOW_ACTIVE, returned.getWorkflowStatus());

        LabTask confirmed = pendingOnTimeTask();
        assertTrue(service.reviewPass(confirmed, reviewCommand(99L, false)).isEmpty());
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
        assertTrue(service.reviewPass(confirmed, reviewCommand(99L, false)).isEmpty());

        assertThrows(ServiceException.class, () -> service.submitResult(confirmed, completionCommand(LocalDate.of(2026, 8, 9))));
        assertThrows(ServiceException.class, () -> service.withdraw(confirmed));
        assertThrows(ServiceException.class, () -> service.reviewReturn(confirmed, reviewCommand(99L, false)));
    }

    private LabTask pendingExceededTask() {
        LabTask task = activeTask(LocalDate.of(2026, 8, 10));
        TaskSubmitCommand command = completionCommand(LocalDate.of(2026, 8, 9));
        command.setRequestedResultStatus(LabConstants.RESULT_EXCEEDED);
        assertTrue(service.submitResult(task, command).isEmpty());
        return task;
    }

    private LabTask pendingOnTimeTask() {
        LabTask task = activeTask(LocalDate.of(2026, 8, 10));
        assertTrue(service.submitResult(task, completionCommand(LocalDate.of(2026, 8, 9))).isEmpty());
        return task;
    }

    private LabTask validDraft() {
        LabTask task = new LabTask();
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
        evidence.setEvidenceTitle("验收报告");
        evidence.setEvidenceUrl("https://example.invalid/evidence/report");
        command.setEvidenceList(Collections.singletonList(evidence));
        return command;
    }

    private TaskSubmitCommand reviewCommand(Long reviewerId, boolean exceededConfirmed) {
        TaskSubmitCommand command = new TaskSubmitCommand();
        command.setReviewerId(reviewerId);
        command.setReviewerComment("核验通过");
        command.setReviewTime(dateAtStartOfDay(LocalDate.of(2026, 8, 12)));
        command.setExceededConfirmed(exceededConfirmed);
        return command;
    }

    private Date dateAtStartOfDay(LocalDate date) {
        return Date.from(date.atStartOfDay().toInstant(ZoneOffset.UTC));
    }

    private List<String> fields(List<FieldValidationError> errors) {
        return errors.stream().map(FieldValidationError::getField).collect(Collectors.toList());
    }
}
