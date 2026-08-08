package com.ailab.system.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailab.system.domain.LabReportJob;
import com.ailab.system.dto.ReportQueueReceipt;
import com.ailab.system.mapper.LabReportMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

class ReportJobDispatcherTest {
    private LabReportMapper mapper;
    private ScheduledExecutorService executor;
    private ReportGenerationWorker worker;
    private ReportJobDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        mapper = mock(LabReportMapper.class); executor = mock(ScheduledExecutorService.class);
        worker = mock(ReportGenerationWorker.class);
        dispatcher = new ReportJobDispatcher(mapper, executor, worker,
                Clock.fixed(Instant.parse("2026-08-08T01:00:00Z"), ZoneOffset.UTC));
        doAnswer(call -> { ((LabReportJob) call.getArgument(0)).setId(51L); return 1; })
                .when(mapper).insertReportJob(any(LabReportJob.class));
    }

    @Test
    void queuePersistsBeforeSubmittingAndReturnsWithoutRunningWorkerInline() {
        ReportQueueReceipt receipt = dispatcher.queue(31L, "DATA", "1001");
        assertEquals(31L, receipt.getReportId()); assertEquals(51L, receipt.getJobId());
        verify(mapper).insertReportJob(any(LabReportJob.class));
        ArgumentCaptor<Runnable> submitted = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(submitted.capture());
        verify(worker, never()).execute(any());
        submitted.getValue().run();
        verify(worker).execute(51L);
    }

    @Test
    void databaseActiveStateSuppressesDuplicateStepEvenWithoutRedis() {
        LabReportJob active = job(61L, 31L, "WORD", "RUNNING", 2);
        when(mapper.selectActiveReportJob(31L, "WORD")).thenReturn(active);
        ReportQueueReceipt receipt = dispatcher.queue(31L, "WORD", "1001");
        assertEquals(61L, receipt.getJobId());
        verify(mapper, never()).insertReportJob(any()); verify(executor, never()).execute(any(Runnable.class));
    }

    @Test
    void databaseUniqueConstraintRaceReturnsTheWinningActiveJob() {
        LabReportJob winner = job(61L, 31L, "DATA", "QUEUED", 0);
        when(mapper.selectActiveReportJob(31L, "DATA")).thenReturn(null, winner);
        org.mockito.Mockito.doThrow(new DuplicateKeyException("active_step_unique"))
                .when(mapper).insertReportJob(any(LabReportJob.class));

        ReportQueueReceipt receipt = dispatcher.queue(31L, "DATA", "1001");

        assertEquals(61L, receipt.getJobId());
        verify(executor, never()).execute(any(Runnable.class));
    }

    @Test
    void recoveryResetsStaleRunningAndRequeuesQueuedJobsOnRuoYiExecutor() {
        LabReportJob queued = job(71L, 41L, "DATA", "QUEUED", 1);
        LabReportJob stale = job(72L, 42L, "PDF", "RUNNING", 3);
        when(mapper.selectRecoverableReportJobs(any(Date.class))).thenReturn(Arrays.asList(queued, stale));
        when(mapper.resetStaleReportJob(72L, 3, "report-recovery")).thenReturn(1);

        assertEquals(2, dispatcher.recoverInterruptedJobs());
        verify(mapper).resetStaleReportJob(72L, 3, "report-recovery");
        verify(executor, org.mockito.Mockito.times(2)).execute(any(Runnable.class));
    }

    @Test
    void progressionCreatesDownstreamBeforeCompletingCurrentJob() {
        when(mapper.completeReportJob(any(),any(),any())).thenReturn(1);

        dispatcher.advance(50L,31L,"WORD","1001",new Date());

        org.mockito.InOrder ordered=org.mockito.Mockito.inOrder(mapper);
        ordered.verify(mapper).insertReportJob(any(LabReportJob.class));
        ordered.verify(mapper).completeReportJob(org.mockito.ArgumentMatchers.eq(50L),org.mockito.ArgumentMatchers.eq("1001"),any(Date.class));
    }

    private LabReportJob job(Long id, Long reportId, String type, String status, int version) {
        LabReportJob value = new LabReportJob(); value.setId(id); value.setReportId(reportId); value.setJobType(type);
        value.setJobStatus(status); value.setVersion(version); return value;
    }
}
