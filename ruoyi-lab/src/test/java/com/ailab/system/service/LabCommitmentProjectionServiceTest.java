package com.ailab.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailab.system.constant.LabConstants;
import com.ailab.system.domain.LabFormalAcceptanceFact;
import com.ailab.system.domain.LabPeriodCloseFact;
import com.ailab.system.domain.LabPeriodCloseSnapshot;
import com.ailab.system.domain.LabTask;
import com.ailab.system.dto.CommitmentProgress;
import com.ailab.system.mapper.LabFormalAcceptanceMapper;
import com.ailab.system.mapper.LabPeriodCloseSnapshotMapper;
import com.ailab.system.mapper.LabTaskMapper;
import com.ailab.system.service.impl.LabCommitmentProjectionService;
import com.ruoyi.common.exception.ServiceException;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LabCommitmentProjectionServiceTest {
    private final LabTaskMapper tasks = mock(LabTaskMapper.class);
    private final LabFormalAcceptanceMapper formal = mock(LabFormalAcceptanceMapper.class);
    private final LabPeriodCloseSnapshotMapper closes = mock(LabPeriodCloseSnapshotMapper.class);
    private LabCommitmentProjectionService service;

    @BeforeEach
    void setUp() { service = new LabCommitmentProjectionService(tasks, formal, closes, new LabCommitmentCalculationService()); }

    @Test
    void openPeriodFormalProgressReadsLatestImmutableAcceptanceInsteadOfLiveWorkflow() {
        LabTask month = month("0");
        LabFormalAcceptanceFact fact = new LabFormalAcceptanceFact(); fact.setFormalRevisionId(41L);
        fact.setFactJson("{\"resultStatus\":\"DELAYED\",\"goalWeight\":60}");
        when(formal.selectLatestFactForTask(10L)).thenReturn(fact);
        when(tasks.selectCommitmentsForCalculation(eq(10L), any(Date.class))).thenReturn(Collections.emptyList());

        CommitmentProgress progress = service.projectMonth(month, Date.from(Instant.parse("2026-08-15T23:59:59Z")));

        assertEquals(Long.valueOf(41L), progress.getFormalRevision());
        assertEquals("100.00", progress.getFormalProgress().toPlainString());
        assertEquals("0.00", progress.getOperationalProgress().toPlainString());
        assertEquals("60", progress.getFormalWeight().toPlainString());
        assertEquals("100", progress.getOperationalWeight().toPlainString());
        verify(closes, never()).selectLatestSnapshotForPeriod(any(String.class));
    }

    @Test
    void closedPeriodReadsTheExactCloseRevisionAndNeverFallsBackToLiveAcceptance() {
        LabTask month = month("1");
        LabPeriodCloseSnapshot snapshot = new LabPeriodCloseSnapshot(); snapshot.setId(9L); snapshot.setFormalRevisionId(31L);
        snapshot.setClosedTime(Date.from(Instant.parse("2026-08-31T23:59:59Z")));
        LabPeriodCloseFact fact = new LabPeriodCloseFact(); fact.setFactJson("{\"workflowStatus\":\"ACTIVE\",\"resultStatus\":\"UNDONE\",\"goalWeight\":100}");
        when(closes.selectLatestSnapshotForPeriod("2026-08")).thenReturn(snapshot);
        when(closes.selectFactByTypeAndBusinessId(9L, "MONTH_RESULT", 10L)).thenReturn(fact);
        when(tasks.selectCommitmentsForCalculation(10L, snapshot.getClosedTime())).thenReturn(Collections.emptyList());

        CommitmentProgress progress = service.projectMonth(month, Date.from(Instant.parse("2026-09-15T00:00:00Z")));

        assertEquals(Long.valueOf(9L), progress.getCloseRevision());
        assertEquals(Long.valueOf(31L), progress.getFormalRevision());
        assertEquals("0.00", progress.getFormalProgress().toPlainString());
        verify(formal, never()).selectLatestFactForTask(any(Long.class));
    }

    @Test
    void closedPeriodMissingItsPinnedMonthFactFailsClosed() {
        LabTask month = month("1");
        LabPeriodCloseSnapshot snapshot = new LabPeriodCloseSnapshot(); snapshot.setId(9L); snapshot.setClosedTime(new Date());
        when(closes.selectLatestSnapshotForPeriod("2026-08")).thenReturn(snapshot);

        assertThrows(ServiceException.class, () -> service.projectMonth(month, new Date()));
    }

    private LabTask month(String lock) {
        LabTask task = new LabTask(); task.setId(10L); task.setTaskLevel(LabConstants.TASK_LEVEL_MONTH);
        task.setPeriod("2026-08"); task.setWorkflowStatus(LabConstants.WORKFLOW_ACTIVE);
        task.setResultStatus(LabConstants.RESULT_DOING); task.setPeriodLockFlag(lock);
        task.setGoalWeight(new java.math.BigDecimal("100"));
        task.setPlanDate(Date.from(Instant.parse("2026-08-31T00:00:00Z"))); return task;
    }
}
