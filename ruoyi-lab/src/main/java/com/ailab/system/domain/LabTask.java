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
    private String resultStatus;
    private String resultDesc;
    private String failReason;
    private String nextAction;
    private Long assetId;
    private String coordinationRequired;
    private Long coordinationOwnerId;
    private String coordinationDesc;
    private String blockFlag;
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
    public Date getPlanDate() { return planDate; }
    public void setPlanDate(Date planDate) { this.planDate = planDate; }
    public Date getActualFinishTime() { return actualFinishTime; }
    public void setActualFinishTime(Date actualFinishTime) { this.actualFinishTime = actualFinishTime; }
    public String getDeliverable() { return deliverable; }
    public void setDeliverable(String deliverable) { this.deliverable = deliverable; }
    public BigDecimal getPerfWeight() { return perfWeight; }
    public void setPerfWeight(BigDecimal perfWeight) { this.perfWeight = perfWeight; }
    public BigDecimal getGoalWeight() { return goalWeight; }
    public void setGoalWeight(BigDecimal goalWeight) { this.goalWeight = goalWeight; }
    public String getWorkflowStatus() { return workflowStatus; }
    public void setWorkflowStatus(String workflowStatus) { this.workflowStatus = workflowStatus; }
    public String getResultStatus() { return resultStatus; }
    public void setResultStatus(String resultStatus) { this.resultStatus = resultStatus; }
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
    public String getCoordinationDesc() { return coordinationDesc; }
    public void setCoordinationDesc(String coordinationDesc) { this.coordinationDesc = coordinationDesc; }
    public String getBlockFlag() { return blockFlag; }
    public void setBlockFlag(String blockFlag) { this.blockFlag = blockFlag; }
    public Date getBlockStartTime() { return blockStartTime; }
    public void setBlockStartTime(Date blockStartTime) { this.blockStartTime = blockStartTime; }
    public String getPeriodLockFlag() { return periodLockFlag; }
    public void setPeriodLockFlag(String periodLockFlag) { this.periodLockFlag = periodLockFlag; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
    public List<LabTaskEvidence> getEvidenceList() { return evidenceList; }
    public void setEvidenceList(List<LabTaskEvidence> evidenceList) { this.evidenceList = evidenceList == null ? new ArrayList<LabTaskEvidence>() : new ArrayList<LabTaskEvidence>(evidenceList); }
}
