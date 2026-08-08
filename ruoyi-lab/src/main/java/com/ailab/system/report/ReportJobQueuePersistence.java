package com.ailab.system.report;

import com.ailab.system.domain.LabReportJob;
import com.ailab.system.mapper.LabReportMapper;
import com.ruoyi.common.exception.ServiceException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Transactional database boundary for durable report-job creation and race recovery. */
@Component
public class ReportJobQueuePersistence {
    private final LabReportMapper mapper;

    public ReportJobQueuePersistence(LabReportMapper mapper) { this.mapper = mapper; }

    @Transactional
    public Created createOrGet(Long reportId, String step, String actor) {
        if(mapper.lockReportJobScope(reportId)==null)throw new ServiceException("Only an existing draft report can be queued");
        LabReportJob active = mapper.selectActiveReportJob(reportId, step);
        if (active != null) return new Created(active, false);
        LabReportJob job = new LabReportJob();
        String suffix = UUID.randomUUID().toString().replace("-", "");
        job.setJobNo("RPJ-" + reportId + "-" + step + "-" + suffix.substring(0, 12));
        job.setReportId(reportId); job.setJobType(step); job.setJobStatus("QUEUED");
        job.setProgressRate(BigDecimal.ZERO); job.setAttemptCount(nextAttempt(reportId, step));
        job.setIdempotencyKey("report:" + reportId + ":" + step + ":" + suffix);
        job.setVersion(0); job.setDelFlag("0"); job.setCreateBy(actor);
        if (mapper.insertReportJob(job) != 1 || job.getId() == null) {
            throw new ServiceException("Report job was not queued");
        }
        return new Created(job, true);
    }

    /** A unique-key loser must re-read outside the rolled-back transaction under MySQL RR. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public LabReportJob findActiveAfterConflict(Long reportId, String step) {
        return mapper.selectActiveReportJob(reportId, step);
    }

    private int nextAttempt(Long reportId, String step) {
        int attempt = 1; List<LabReportJob> jobs = mapper.selectReportJobs(reportId);
        if (jobs != null) for (LabReportJob value : jobs) {
            if (step.equals(value.getJobType()) && value.getAttemptCount() != null) {
                attempt = Math.max(attempt, value.getAttemptCount() + 1);
            }
        }
        return attempt;
    }

    public static final class Created {
        private final LabReportJob job; private final boolean created;
        Created(LabReportJob job, boolean created) { this.job = job; this.created = created; }
        public LabReportJob getJob() { return job; }
        public boolean isCreated() { return created; }
    }
}
