package com.ailab.system.dto;

/** Immediate HTTP receipt for a durable asynchronous report step. */
public final class ReportQueueReceipt {
    private final Long reportId;
    private final Long jobId;
    private final String step;
    private final String status;

    public ReportQueueReceipt(Long reportId, Long jobId, String step, String status) {
        this.reportId = reportId; this.jobId = jobId; this.step = step; this.status = status;
    }
    public Long getReportId() { return reportId; }
    public Long getJobId() { return jobId; }
    public String getStep() { return step; }
    public String getStatus() { return status; }
}
