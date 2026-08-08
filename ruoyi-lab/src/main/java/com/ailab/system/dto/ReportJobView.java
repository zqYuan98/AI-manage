package com.ailab.system.dto;

import com.ailab.system.domain.LabReportJob;
import java.math.BigDecimal;
import java.util.Date;

/** Public job projection without run fences, idempotency keys or persistence internals. */
public final class ReportJobView {
    private final Long id,reportId;private final String jobType,jobStatus,errorSummary;private final BigDecimal progressRate;private final Integer attemptCount;private final Date startedTime,finishedTime;
    private ReportJobView(LabReportJob value){id=value.getId();reportId=value.getReportId();jobType=value.getJobType();jobStatus=value.getJobStatus();progressRate=value.getProgressRate();attemptCount=value.getAttemptCount();errorSummary=safe(value.getErrorMessage());startedTime=copy(value.getStartedTime());finishedTime=copy(value.getFinishedTime());}
    public static ReportJobView from(LabReportJob value){return new ReportJobView(value);}public Long getId(){return id;}public Long getReportId(){return reportId;}public String getJobType(){return jobType;}public String getJobStatus(){return jobStatus;}public BigDecimal getProgressRate(){return progressRate;}public Integer getAttemptCount(){return attemptCount;}public String getErrorSummary(){return errorSummary;}public Date getStartedTime(){return copy(startedTime);}public Date getFinishedTime(){return copy(finishedTime);}private static Date copy(Date value){return value==null?null:new Date(value.getTime());}private static String safe(String value){if(value==null)return null;return value.matches("REPORT_[A-Z_]+: .{1,500}")?value:"REPORT_GENERATION_FAILED: Report generation failed; retry the failed step or contact an administrator.";}
}
