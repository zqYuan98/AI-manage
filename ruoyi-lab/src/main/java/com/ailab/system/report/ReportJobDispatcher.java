package com.ailab.system.report;

import com.ailab.system.domain.LabReportJob;
import com.ailab.system.dto.ReportQueueReceipt;
import com.ailab.system.mapper.LabReportMapper;
import com.ailab.system.service.LabReportRecoveryWorker;
import com.ruoyi.common.exception.ServiceException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Persists the durable queue record before handing a tiny runnable to RuoYi's executor. */
@Component
public class ReportJobDispatcher implements LabReportRecoveryWorker {
    private static final Logger LOG = LoggerFactory.getLogger(ReportJobDispatcher.class);
    private final LabReportMapper mapper; private final ScheduledExecutorService executor; private final ReportGenerationWorker worker; private final Clock clock;
    @Autowired
    public ReportJobDispatcher(LabReportMapper mapper, @Qualifier("scheduledExecutorService") ScheduledExecutorService executor,
            ReportGenerationWorker worker) { this(mapper, executor, worker, Clock.systemDefaultZone()); }
    public ReportJobDispatcher(LabReportMapper mapper, ScheduledExecutorService executor, ReportGenerationWorker worker, Clock clock) {
        this.mapper = mapper; this.executor = executor; this.worker = worker; this.clock = clock;
    }

    @Transactional
    public ReportQueueReceipt queue(Long reportId, String step, String actor) {
        requireStep(step); if (reportId == null || actor == null || actor.trim().isEmpty()) throw new ServiceException("Report queue identity is required");
        LabReportJob active = mapper.selectActiveReportJob(reportId, step);
        if (active != null) return receipt(active);
        LabReportJob job = new LabReportJob(); String suffix = UUID.randomUUID().toString().replace("-", "");
        job.setJobNo("RPJ-" + reportId + "-" + step + "-" + suffix.substring(0, 12)); job.setReportId(reportId); job.setJobType(step);
        job.setJobStatus("QUEUED"); job.setProgressRate(BigDecimal.ZERO); job.setAttemptCount(nextAttempt(reportId, step));
        job.setIdempotencyKey("report:" + reportId + ":" + step + ":" + suffix); job.setVersion(0); job.setDelFlag("0"); job.setCreateBy(actor);
        try {
            if (mapper.insertReportJob(job) != 1 || job.getId() == null) throw new ServiceException("Report job was not queued");
        } catch (DuplicateKeyException race) {
            LabReportJob winner = mapper.selectActiveReportJob(reportId, step);
            if (winner != null) return receipt(winner);
            throw new ServiceException("A concurrent report job changed before it could be queued");
        }
        submitAfterCommit(job.getId()); return receipt(job);
    }

    /** Atomically completes the current durable job and creates its downstream job. */
    @Transactional
    public void advance(Long jobId, Long reportId, String nextStep, String actor, Date finishedTime) {
        if (nextStep != null) queue(reportId, nextStep, actor);
        if (mapper.completeReportJob(jobId, actor, finishedTime) != 1) {
            throw new ServiceException("Report job state changed before progression");
        }
    }

    @Override public int recoverInterruptedJobs() {
        Date cutoff = Date.from(clock.instant().minus(10, ChronoUnit.MINUTES)); List<LabReportJob> jobs = mapper.selectRecoverableReportJobs(cutoff);
        if (jobs == null) jobs = Collections.emptyList(); int count = 0;
        for (LabReportJob job : jobs) {
            if ("RUNNING".equals(job.getJobStatus()) && mapper.resetStaleReportJob(job.getId(), job.getVersion(), "report-recovery") != 1) continue;
            if (!("QUEUED".equals(job.getJobStatus()) || "RUNNING".equals(job.getJobStatus()))) continue;
            submit(job.getId()); count++;
        }
        return count;
    }

    private int nextAttempt(Long reportId, String step) { int attempt = 1; List<LabReportJob> jobs = mapper.selectReportJobs(reportId); if (jobs != null) for (LabReportJob value : jobs) if (step.equals(value.getJobType()) && value.getAttemptCount() != null) attempt = Math.max(attempt, value.getAttemptCount() + 1); return attempt; }
    private void submitAfterCommit(final Long jobId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void afterCommit() { submit(jobId); } });
        } else submit(jobId);
    }
    private void submit(final Long jobId) { try { executor.execute(new Runnable() { @Override public void run() { worker.execute(jobId); } }); } catch (RejectedExecutionException ex) { LOG.error("AI Lab report job {} remains queued because executor rejected submission", jobId, ex); } }
    private ReportQueueReceipt receipt(LabReportJob job) { return new ReportQueueReceipt(job.getReportId(), job.getId(), job.getJobType(), job.getJobStatus()); }
    private void requireStep(String value) { if (!("DATA".equals(value) || "WORD".equals(value) || "PDF".equals(value))) throw new ServiceException("Unsupported report job step"); }
}
