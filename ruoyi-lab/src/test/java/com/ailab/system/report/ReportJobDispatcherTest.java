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
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.RejectedExecutionException;
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
        when(mapper.lockReportJobScope(any(Long.class))).thenAnswer(call->call.getArgument(0));
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
    void queueRejectsAMissingOrDeletedReportScope() {
        when(mapper.lockReportJobScope(31L)).thenReturn(null);

        org.junit.jupiter.api.Assertions.assertThrows(com.ruoyi.common.exception.ServiceException.class,()->dispatcher.queue(31L,"DATA","1001"));

        verify(mapper,never()).insertReportJob(any());
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
        stale.setRunToken("stale-run-token-1234");
        when(mapper.selectRecoverableReportJobs(any(Date.class),any(Long.class),any(Integer.class))).thenReturn(Arrays.asList(queued, stale));
        when(mapper.resetStaleReportJob(org.mockito.ArgumentMatchers.eq(72L), org.mockito.ArgumentMatchers.eq(3), org.mockito.ArgumentMatchers.eq("stale-run-token-1234"), any(Date.class), org.mockito.ArgumentMatchers.eq("report-recovery"))).thenReturn(1);

        assertEquals(2, dispatcher.recoverInterruptedJobs());
        verify(mapper).resetStaleReportJob(org.mockito.ArgumentMatchers.eq(72L), org.mockito.ArgumentMatchers.eq(3), org.mockito.ArgumentMatchers.eq("stale-run-token-1234"), any(Date.class), org.mockito.ArgumentMatchers.eq("report-recovery"));
        verify(executor, org.mockito.Mockito.times(2)).execute(any(Runnable.class));
        verify(mapper).failInvalidActiveReportJobs("report-recovery");
    }

    @Test
    void repeatedRecoveryScansDoNotSubmitTheSameLocalJobTwiceWhileItIsOutstanding() {
        LabReportJob queued=job(81L,51L,"DATA","QUEUED",1);when(mapper.selectRecoverableReportJobs(any(Date.class),any(Long.class),any(Integer.class))).thenReturn(Arrays.asList(queued));
        assertEquals(1,dispatcher.recoverInterruptedJobs());assertEquals(0,dispatcher.recoverInterruptedJobs());
        verify(executor).execute(any(Runnable.class));
    }

    @Test
    void recoveryQueryUsesKeysetPagesSoTheFirstHundredQueuedRowsCannotStarveLaterJobs() throws Exception {
        String xml=new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/main/resources/mapper/lab/LabReportMapper.xml")),java.nio.charset.StandardCharsets.UTF_8).toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+"," ");
        int from=xml.indexOf("<select id=\"selectrecoverablereportjobs\"");int to=xml.indexOf("</select>",from);String query=xml.substring(from,to);
        org.junit.jupiter.api.Assertions.assertTrue(query.contains("id&gt;#{afterid}")&&query.contains("limit #{pagesize}"),"recovery must page past locally outstanding rows");
    }

    @Test
    void oneRecoveryScanAppliesBackpressureToTheUnboundedSharedExecutor() {
        java.util.List<LabReportJob> first=new java.util.ArrayList<LabReportJob>();java.util.List<LabReportJob> second=new java.util.ArrayList<LabReportJob>();for(long id=1;id<=100;id++)first.add(job(id,1000L+id,"DATA","QUEUED",0));for(long id=101;id<=200;id++)second.add(job(id,1000L+id,"DATA","QUEUED",0));when(mapper.selectRecoverableReportJobs(any(Date.class),any(Long.class),any(Integer.class))).thenReturn(first,second,java.util.Collections.<LabReportJob>emptyList());

        assertEquals(100,dispatcher.recoverInterruptedJobs());

        verify(executor,org.mockito.Mockito.times(100)).execute(any(Runnable.class));
    }

    @Test
    void httpCreatedJobsUseTheSameLocalCapacityAsRecovery() {
        java.util.concurrent.atomic.AtomicLong ids=new java.util.concurrent.atomic.AtomicLong(1000L);
        doAnswer(call->{((LabReportJob)call.getArgument(0)).setId(ids.incrementAndGet());return 1;}).when(mapper).insertReportJob(any(LabReportJob.class));

        for(long reportId=1;reportId<=201;reportId++)dispatcher.queue(reportId,"DATA","1001");

        verify(executor,org.mockito.Mockito.times(200)).execute(any(Runnable.class));
    }

    @Test
    void recoveryScansAtMostOneBoundedPageWhenASubmissionIsRejected() {
        java.util.List<LabReportJob> first=new java.util.ArrayList<LabReportJob>();for(long id=1;id<=100;id++)first.add(job(id,2000L+id,"DATA","QUEUED",0));
        when(mapper.selectRecoverableReportJobs(any(Date.class),any(Long.class),any(Integer.class))).thenReturn(first,Collections.singletonList(job(101L,2101L,"DATA","QUEUED",0)));
        org.mockito.Mockito.doThrow(new RejectedExecutionException("saturated")).doNothing().when(executor).execute(any(Runnable.class));

        assertEquals(99,dispatcher.recoverInterruptedJobs());

        verify(mapper).selectRecoverableReportJobs(any(Date.class),any(Long.class),any(Integer.class));
    }

    @Test
    void progressionCreatesDownstreamBeforeCompletingCurrentJob() {
        when(mapper.activateWordAfterData(31L,50L,"run-token-1234567890","1001")).thenReturn(1);
        when(mapper.completeReportJob(any(),any(),any(),any())).thenReturn(1);

        dispatcher.advance(50L,31L,"WORD","run-token-1234567890","1001",new Date());

        org.mockito.InOrder ordered=org.mockito.Mockito.inOrder(mapper);
        ordered.verify(mapper).activateWordAfterData(31L,50L,"run-token-1234567890","1001");
        ordered.verify(mapper).insertReportJob(any(LabReportJob.class));
        ordered.verify(mapper).completeReportJob(org.mockito.ArgumentMatchers.eq(50L),org.mockito.ArgumentMatchers.eq("run-token-1234567890"),org.mockito.ArgumentMatchers.eq("1001"),any(Date.class));
    }

    private LabReportJob job(Long id, Long reportId, String type, String status, int version) {
        LabReportJob value = new LabReportJob(); value.setId(id); value.setReportId(reportId); value.setJobType(type);
        value.setJobStatus(status); value.setVersion(version); return value;
    }
}
