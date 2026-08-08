package com.ailab.system.report;

import com.ailab.system.domain.LabReportJob;
import com.ailab.system.dto.ReportQueueReceipt;
import com.ailab.system.mapper.LabReportMapper;
import com.ailab.system.service.LabReportRecoveryWorker;
import com.ruoyi.common.exception.ServiceException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
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
    private static final int RECOVERY_PAGE_SIZE=100;
    private static final int MAX_RECOVERY_DISPATCH_PER_SCAN=100;
    private static final int MAX_LOCAL_OUTSTANDING=200;
    private final LabReportMapper mapper; private final ReportJobQueuePersistence persistence; private final ScheduledExecutorService executor; private final ReportGenerationWorker worker; private final Clock clock;
    private final Set<Long> outstanding = Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());
    private final Semaphore dispatchSlots = new Semaphore(MAX_LOCAL_OUTSTANDING);
    @Autowired
    public ReportJobDispatcher(LabReportMapper mapper, @Qualifier("scheduledExecutorService") ScheduledExecutorService executor,
            ReportGenerationWorker worker, ReportJobQueuePersistence persistence) { this(mapper, persistence, executor, worker, Clock.systemDefaultZone()); }
    public ReportJobDispatcher(LabReportMapper mapper, ScheduledExecutorService executor, ReportGenerationWorker worker, Clock clock) {
        this(mapper, new ReportJobQueuePersistence(mapper), executor, worker, clock);
    }
    ReportJobDispatcher(LabReportMapper mapper, ReportJobQueuePersistence persistence, ScheduledExecutorService executor,
            ReportGenerationWorker worker, Clock clock) {
        this.mapper = mapper; this.persistence = persistence; this.executor = executor; this.worker = worker; this.clock = clock;
    }

    public ReportQueueReceipt queue(Long reportId, String step, String actor) {
        requireStep(step); if (reportId == null || actor == null || actor.trim().isEmpty()) throw new ServiceException("Report queue identity is required");
        ReportJobQueuePersistence.Created created;
        try {
            created = persistence.createOrGet(reportId, step, actor);
        } catch (DuplicateKeyException race) {
            LabReportJob winner = persistence.findActiveAfterConflict(reportId, step);
            if (winner != null) return receipt(winner);
            throw new ServiceException("A concurrent report job changed before it could be queued");
        }
        if (created.isCreated()) submitAfterCommit(created.getJob().getId());
        return receipt(created.getJob());
    }

    /** Atomically completes the current durable job and creates its downstream job. */
    @Transactional
    public void advance(Long jobId, Long reportId, String nextStep, String runToken, String actor, Date finishedTime) {
        int activated = 1;
        if ("WORD".equals(nextStep)) activated = mapper.activateWordAfterData(reportId, jobId, runToken, actor);
        else if ("PDF".equals(nextStep)) activated = mapper.activatePdfAfterWord(reportId, jobId, runToken, actor);
        if (activated != 1) throw new ServiceException("Report artifact state changed before progression");
        if (nextStep != null) queue(reportId, nextStep, actor);
        if (mapper.completeReportJob(jobId, runToken, actor, finishedTime) != 1) {
            throw new ServiceException("Report job state changed before progression");
        }
    }

    @Override public int recoverInterruptedJobs() {
        mapper.failInvalidActiveReportJobs("report-recovery");
        int capacity=Math.min(MAX_RECOVERY_DISPATCH_PER_SCAN,dispatchSlots.availablePermits());if(capacity==0)return 0;
        Date cutoff = Date.from(clock.instant().minus(10, ChronoUnit.MINUTES)); int count = 0; int scanned=0; long afterId = 0L;
        while (count<capacity&&scanned<MAX_RECOVERY_DISPATCH_PER_SCAN&&dispatchSlots.availablePermits()>0) {
            int pageSize=Math.min(RECOVERY_PAGE_SIZE,Math.min(capacity-count,MAX_RECOVERY_DISPATCH_PER_SCAN-scanned));List<LabReportJob> jobs = mapper.selectRecoverableReportJobs(cutoff, afterId, pageSize);
            if (jobs == null || jobs.isEmpty()) break;
            long before=afterId;
            for (LabReportJob job : jobs) {
                if(scanned>=MAX_RECOVERY_DISPATCH_PER_SCAN||dispatchSlots.availablePermits()==0)break;
                scanned++;
                afterId = Math.max(afterId, job.getId());
                if ("RUNNING".equals(job.getJobStatus()) && mapper.resetStaleReportJob(job.getId(), job.getVersion(), job.getRunToken(), cutoff, "report-recovery") != 1) continue;
                if (!("QUEUED".equals(job.getJobStatus()) || "RUNNING".equals(job.getJobStatus()))) continue;
                if (submit(job.getId())) count++;
                if(count>=capacity)break;
            }
            if (jobs.size() < pageSize||afterId==before||scanned>=MAX_RECOVERY_DISPATCH_PER_SCAN||dispatchSlots.availablePermits()==0) break;
        }
        return count;
    }

    private void submitAfterCommit(final Long jobId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void afterCommit() { submit(jobId); } });
        } else submit(jobId);
    }
    private boolean submit(final Long jobId) {
        if (!dispatchSlots.tryAcquire()) return false;
        if (!outstanding.add(jobId)) { dispatchSlots.release(); return false; }
        try {
            executor.execute(new Runnable() { @Override public void run() { try { worker.execute(jobId); } finally { outstanding.remove(jobId); dispatchSlots.release(); } } });
            return true;
        } catch (RejectedExecutionException ex) {
            outstanding.remove(jobId); dispatchSlots.release(); LOG.error("AI Lab report job {} remains queued because executor rejected submission", jobId, ex); return false;
        } catch (RuntimeException ex) {
            outstanding.remove(jobId); dispatchSlots.release(); throw ex;
        }
    }
    private ReportQueueReceipt receipt(LabReportJob job) { return new ReportQueueReceipt(job.getReportId(), job.getId(), job.getJobType(), job.getJobStatus()); }
    private void requireStep(String value) { if (!("DATA".equals(value) || "WORD".equals(value) || "PDF".equals(value))) throw new ServiceException("Unsupported report job step"); }
}
