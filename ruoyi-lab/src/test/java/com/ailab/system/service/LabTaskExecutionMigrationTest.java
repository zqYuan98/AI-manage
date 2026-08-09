package com.ailab.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ailab.system.config.LabProperties;
import com.ailab.system.constant.LabConstants;
import com.ailab.system.domain.LabTask;
import com.ailab.system.mapper.LabCommitmentMapper;
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

    @Test
    void taskCarriesIndependentExecutionAndCarryVersionFields() {
        LabTask task = new LabTask();
        task.setExecutionStatus("ACTIVE");
        task.setCarriedFromId(88L);
        task.setExecutionVersion(3);

        assertEquals("ACTIVE", task.getExecutionStatus());
        assertEquals(88L, task.getCarriedFromId());
        assertEquals(3, task.getExecutionVersion());
    }

    @Test
    void cutoverDefaultsRemainOnLegacyReadAndWrite() {
        LabProperties properties = new LabProperties();

        assertFalse(properties.isReadNewModel());
        assertFalse(properties.isWriteSelfClose());
    }

    @Test
    void cutoverRejectsOpenQuarantineAndOldReaderAfterPointOfNoReturn() {
        assertThrows(IllegalStateException.class,
                () -> service.validateCutover(true, false, 1, false));
        assertThrows(IllegalStateException.class,
                () -> service.validateCutover(false, false, 0, true));
        assertThrows(IllegalStateException.class,
                () -> service.validateCutover(false, true, 0, false));

        service.validateCutover(true, true, 0, true);
    }

    @Test
    void onlyMemberActionAfterWriteCutoverAdvancesPointOfNoReturn() {
        assertFalse(service.advancesPointOfNoReturn(false, "SELF_COMPLETE"));
        assertFalse(service.advancesPointOfNoReturn(true, LabConstants.EXECUTION_EVENT_MIGRATED_BASELINE));
        assertTrue(service.advancesPointOfNoReturn(true, "SELF_COMPLETE"));
    }

    @Test
    void firstMemberActionPersistsPointOfNoReturn() {
        LabCommitmentMapper mapper = mock(LabCommitmentMapper.class);
        LabProperties properties = new LabProperties();
        properties.setWriteSelfClose(true);
        LabTaskExecutionMigrationService migration = new LabTaskExecutionMigrationService(mapper, properties);

        migration.recordPointOfNoReturn("SELF_COMPLETE");

        verify(mapper).updateCutoverValue("lab.commitment.pointOfNoReturn", "false", "true");
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
