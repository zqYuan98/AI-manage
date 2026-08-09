package com.ailab.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailab.system.constant.LabConstants;
import com.ailab.system.domain.LabTask;
import com.ailab.system.service.impl.LabTaskExecutionMigrationService;
import com.ailab.system.service.impl.LabTaskExecutionMigrationService.MigrationDecision;
import java.util.Date;
import org.junit.jupiter.api.Test;

class LabTaskExecutionMigrationTest {
    private final LabTaskExecutionMigrationService service = new LabTaskExecutionMigrationService();

    @Test
    void pendingCompletedResultBecomesSelfDoneWithBaselineEvent() {
        LabTask task = task(LabConstants.WORKFLOW_PENDING_REVIEW, LabConstants.RESULT_DELAYED, new Date(1L), "0");

        MigrationDecision decision = service.classify(task, false);

        assertFalse(decision.isQuarantined());
        assertEquals("SELF_DONE", decision.getExecutionStatus());
        assertEquals("MIGRATED_BASELINE", decision.getEventType());
        assertEquals("0", decision.getPeriodLockFlag());
    }

    @Test
    void confirmedUndoneBecomesSelfUndone() {
        MigrationDecision decision = service.classify(
                task(LabConstants.WORKFLOW_CONFIRMED, LabConstants.RESULT_UNDONE, null, "1"), false);

        assertFalse(decision.isQuarantined());
        assertEquals("SELF_UNDONE", decision.getExecutionStatus());
        assertEquals("1", decision.getPeriodLockFlag());
    }

    @Test
    void terminalCandidateWithOpenBlockIsQuarantined() {
        MigrationDecision decision = service.classify(
                task(LabConstants.WORKFLOW_CONFIRMED, LabConstants.RESULT_ONTIME, new Date(2L), "0"), true);

        assertTrue(decision.isQuarantined());
        assertEquals("TERMINAL_WITH_OPEN_BLOCK", decision.getIssueCode());
    }

    @Test
    void draftAndActiveDoingRowsPreserveTheirExecutionPhase() {
        assertEquals("PLANNED", service.classify(
                task(LabConstants.WORKFLOW_DRAFT, LabConstants.RESULT_DOING, null, "0"), false).getExecutionStatus());
        assertEquals("ACTIVE", service.classify(
                task(LabConstants.WORKFLOW_ACTIVE, LabConstants.RESULT_DOING, null, "0"), false).getExecutionStatus());
    }

    @Test
    void activeRowCarryingTerminalResultWithoutFinishTimeIsAmbiguous() {
        MigrationDecision decision = service.classify(
                task(LabConstants.WORKFLOW_ACTIVE, LabConstants.RESULT_DELAYED, null, "0"), false);

        assertTrue(decision.isQuarantined());
        assertEquals("AMBIGUOUS_LEGACY_COMBINATION", decision.getIssueCode());
    }

    private static LabTask task(String workflow, String result, Date finish, String lockFlag) {
        LabTask task = new LabTask();
        task.setTaskLevel(LabConstants.TASK_LEVEL_WEEK);
        task.setWorkflowStatus(workflow);
        task.setResultStatus(result);
        task.setActualFinishTime(finish);
        task.setPeriodLockFlag(lockFlag);
        return task;
    }
}
