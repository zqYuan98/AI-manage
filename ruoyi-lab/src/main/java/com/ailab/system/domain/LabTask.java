package com.ailab.system.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Task aggregate matching the {@code lab_task} table. */
public class LabTask extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long parentId;
    private Long goalId;
    private Long milestoneId;
    private String taskLevel;
    private String period;
    /** Query-only inclusive month upper bound used by cumulative dashboard drill-downs. */
    private String periodTo;
    private String bizLine;
    private String taskType;
    private String title;
    private Long ownerId;
    private Long deptId;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date planDate;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date actualFinishTime;
    private String deliverable;
    private BigDecimal perfWeight;
    private BigDecimal goalWeight;
    private String workflowStatus;
    /** Query-only workflow collection used by dashboard drill-downs. */
    private List<String> workflowStatuses = new ArrayList<String>();
    private String resultStatus;
    private String executionStatus;
    /** Query-only first ACTIVE event at or before a trusted calculation cutoff. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date executionActivatedAt;
    /** Query-only blocking state at the same trusted calculation cutoff. */
    private Boolean blockedAtAsOf;
    private Long carriedFromId;
    private Integer executionVersion;
    private String resultDesc;
    private String failReason;
    private String nextAction;
    private Long assetId;
    private String coordinationRequired;
    private Long coordinationOwnerId;
    private Long coordinationDeptId;
    private String coordinationContent;
    private String coordinationSupport;
    private String coordinationDesc;
    private String blockFlag;
    /** Query-only current block flag; kept distinct from the persisted mutation field. */
    private String currentBlockFlag;
    /** Query-only inclusive block start cutoff. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date blockStartBefore;
    /** Query-only KPI predicate and trusted calculation timestamp. */
    private Boolean overdueOrPending;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date asOf;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date blockStartTime;
    private String periodLockFlag;
    private Integer version;
    private String delFlag;
    private List<LabTaskEvidence> evidenceList = new ArrayList<LabTaskEvidence>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Long getGoalId() { return goalId; }
    public void setGoalId(Long goalId) { this.goalId = goalId; }
    public Long getMilestoneId() { return milestoneId; }
    public void setMilestoneId(Long milestoneId) { this.milestoneId = milestoneId; }
    public String getTaskLevel() { return taskLevel; }
    public void setTaskLevel(String taskLevel) { this.taskLevel = taskLevel; }
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    public String getPeriodTo() { return periodTo; }
    public void setPeriodTo(String periodTo) { this.periodTo = periodTo; }
    public String getBizLine() { return bizLine; }
    public void setBizLine(String bizLine) { this.bizLine = bizLine; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }
    public Date getPlanDate() { return copyDate(planDate); }
    public void setPlanDate(Date planDate) { this.planDate = copyDate(planDate); }
    public Date getActualFinishTime() { return copyDate(actualFinishTime); }
    public void setActualFinishTime(Date actualFinishTime) { this.actualFinishTime = copyDate(actualFinishTime); }
    public String getDeliverable() { return deliverable; }
    public void setDeliverable(String deliverable) { this.deliverable = deliverable; }
    public BigDecimal getPerfWeight() { return perfWeight; }
    public void setPerfWeight(BigDecimal perfWeight) { this.perfWeight = perfWeight; }
    public BigDecimal getGoalWeight() { return goalWeight; }
    public void setGoalWeight(BigDecimal goalWeight) { this.goalWeight = goalWeight; }
    public String getWorkflowStatus() { return workflowStatus; }
    public void setWorkflowStatus(String workflowStatus) { this.workflowStatus = workflowStatus; }
    /** Mutable only so Spring's indexed GET parameter binder can populate this query collection. */
    public List<String> getWorkflowStatuses() { return workflowStatuses; }
    public void setWorkflowStatuses(List<String> workflowStatuses) {
        this.workflowStatuses = workflowStatuses == null ? new ArrayList<String>() : new ArrayList<String>(workflowStatuses);
    }
    public String getResultStatus() { return resultStatus; }
    public void setResultStatus(String resultStatus) { this.resultStatus = resultStatus; }
    public String getExecutionStatus() { return executionStatus; }
    public void setExecutionStatus(String executionStatus) { this.executionStatus = executionStatus; }
    public Date getExecutionActivatedAt() { return copyDate(executionActivatedAt); }
    public void setExecutionActivatedAt(Date executionActivatedAt) { this.executionActivatedAt = copyDate(executionActivatedAt); }
    public Boolean getBlockedAtAsOf() { return blockedAtAsOf; }
    public void setBlockedAtAsOf(Boolean blockedAtAsOf) { this.blockedAtAsOf = blockedAtAsOf; }
    public Long getCarriedFromId() { return carriedFromId; }
    public void setCarriedFromId(Long carriedFromId) { this.carriedFromId = carriedFromId; }
    public Integer getExecutionVersion() { return executionVersion; }
    public void setExecutionVersion(Integer executionVersion) { this.executionVersion = executionVersion; }
    public String getResultDesc() { return resultDesc; }
    public void setResultDesc(String resultDesc) { this.resultDesc = resultDesc; }
    public String getFailReason() { return failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason; }
    public String getNextAction() { return nextAction; }
    public void setNextAction(String nextAction) { this.nextAction = nextAction; }
    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }
    public String getCoordinationRequired() { return coordinationRequired; }
    public void setCoordinationRequired(String coordinationRequired) { this.coordinationRequired = coordinationRequired; }
    public Long getCoordinationOwnerId() { return coordinationOwnerId; }
    public void setCoordinationOwnerId(Long coordinationOwnerId) { this.coordinationOwnerId = coordinationOwnerId; }
    public Long getCoordinationDeptId() { return coordinationDeptId; }
    public void setCoordinationDeptId(Long coordinationDeptId) { this.coordinationDeptId = coordinationDeptId; }
    public String getCoordinationContent() { return coordinationContent; }
    public void setCoordinationContent(String coordinationContent) { this.coordinationContent = coordinationContent; }
    public String getCoordinationSupport() { return coordinationSupport; }
    public void setCoordinationSupport(String coordinationSupport) { this.coordinationSupport = coordinationSupport; }
    public String getCoordinationDesc() { return coordinationDesc; }
    public void setCoordinationDesc(String coordinationDesc) { this.coordinationDesc = coordinationDesc; }
    public String getBlockFlag() { return blockFlag; }
    public void setBlockFlag(String blockFlag) { this.blockFlag = blockFlag; }
    public String getCurrentBlockFlag() { return currentBlockFlag; }
    public void setCurrentBlockFlag(String currentBlockFlag) { this.currentBlockFlag = currentBlockFlag; }
    public Date getBlockStartBefore() { return copyDate(blockStartBefore); }
    public void setBlockStartBefore(Date blockStartBefore) { this.blockStartBefore = copyDate(blockStartBefore); }
    public Boolean getOverdueOrPending() { return overdueOrPending; }
    public void setOverdueOrPending(Boolean overdueOrPending) { this.overdueOrPending = overdueOrPending; }
    public Date getAsOf() { return copyDate(asOf); }
    public void setAsOf(Date asOf) { this.asOf = copyDate(asOf); }
    public Date getBlockStartTime() { return copyDate(blockStartTime); }
    public void setBlockStartTime(Date blockStartTime) { this.blockStartTime = copyDate(blockStartTime); }
    public String getPeriodLockFlag() { return periodLockFlag; }
    public void setPeriodLockFlag(String periodLockFlag) { this.periodLockFlag = periodLockFlag; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
    public List<LabTaskEvidence> getEvidenceList() { return copyEvidenceList(evidenceList); }
    public void setEvidenceList(List<LabTaskEvidence> evidenceList) { this.evidenceList = copyEvidenceList(evidenceList); }

    private Date copyDate(Date value) { return value == null ? null : new Date(value.getTime()); }

    private List<LabTaskEvidence> copyEvidenceList(List<LabTaskEvidence> source) {
        List<LabTaskEvidence> copy = new ArrayList<LabTaskEvidence>();
        if (source != null) {
            for (LabTaskEvidence evidence : source) {
                copy.add(evidence == null ? null : new LabTaskEvidence(evidence));
            }
        }
        return copy;
    }
}
