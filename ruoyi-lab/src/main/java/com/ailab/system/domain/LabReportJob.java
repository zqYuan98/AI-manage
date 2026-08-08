package com.ailab.system.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import java.math.BigDecimal;
import java.util.Date;

/** A retryable asynchronous report-generation step. */
public class LabReportJob extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long id; private String jobNo; private Long reportId; private String jobType; private String jobStatus;
    private BigDecimal progressRate; private Integer attemptCount; private String errorMessage; private Date startedTime; private Date finishedTime;
    private String idempotencyKey; private Integer version; private String delFlag;
    public Long getId() { return id; } public void setId(Long value) { id = value; }
    public String getJobNo() { return jobNo; } public void setJobNo(String value) { jobNo = value; }
    public Long getReportId() { return reportId; } public void setReportId(Long value) { reportId = value; }
    public String getJobType() { return jobType; } public void setJobType(String value) { jobType = value; }
    public String getJobStatus() { return jobStatus; } public void setJobStatus(String value) { jobStatus = value; }
    public BigDecimal getProgressRate() { return progressRate; } public void setProgressRate(BigDecimal value) { progressRate = value; }
    public Integer getAttemptCount() { return attemptCount; } public void setAttemptCount(Integer value) { attemptCount = value; }
    public String getErrorMessage() { return errorMessage; } public void setErrorMessage(String value) { errorMessage = value; }
    public Date getStartedTime() { return copy(startedTime); } public void setStartedTime(Date value) { startedTime = copy(value); }
    public Date getFinishedTime() { return copy(finishedTime); } public void setFinishedTime(Date value) { finishedTime = copy(value); }
    public String getIdempotencyKey() { return idempotencyKey; } public void setIdempotencyKey(String value) { idempotencyKey = value; }
    public Integer getVersion() { return version; } public void setVersion(Integer value) { version = value; }
    public String getDelFlag() { return delFlag; } public void setDelFlag(String value) { delFlag = value; }
    private Date copy(Date value) { return value == null ? null : new Date(value.getTime()); }
}
