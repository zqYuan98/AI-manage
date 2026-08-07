package com.ailab.system.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;
import java.util.Date;

/** Task evidence persisted in {@code lab_task_evidence}. */
public class LabTaskEvidence extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long taskId;
    private String evidenceType;
    private String evidenceTitle;
    private String evidenceUrl;
    private String evidenceJson;
    private Long submitterId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date submitTime;
    private String auditStatus;
    private Long auditorId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date auditTime;
    private String auditComment;
    private String delFlag;

    public LabTaskEvidence() {
    }

    public LabTaskEvidence(LabTaskEvidence source) {
        this.id = source.id;
        this.taskId = source.taskId;
        this.evidenceType = source.evidenceType;
        this.evidenceTitle = source.evidenceTitle;
        this.evidenceUrl = source.evidenceUrl;
        this.evidenceJson = source.evidenceJson;
        this.submitterId = source.submitterId;
        this.submitTime = copyDate(source.submitTime);
        this.auditStatus = source.auditStatus;
        this.auditorId = source.auditorId;
        this.auditTime = copyDate(source.auditTime);
        this.auditComment = source.auditComment;
        this.delFlag = source.delFlag;
        setCreateBy(source.getCreateBy());
        setCreateTime(copyDate(source.getCreateTime()));
        setUpdateBy(source.getUpdateBy());
        setUpdateTime(copyDate(source.getUpdateTime()));
        setRemark(source.getRemark());
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getEvidenceType() { return evidenceType; }
    public void setEvidenceType(String evidenceType) { this.evidenceType = evidenceType; }
    public String getEvidenceTitle() { return evidenceTitle; }
    public void setEvidenceTitle(String evidenceTitle) { this.evidenceTitle = evidenceTitle; }
    public String getEvidenceUrl() { return evidenceUrl; }
    public void setEvidenceUrl(String evidenceUrl) { this.evidenceUrl = evidenceUrl; }
    public String getEvidenceJson() { return evidenceJson; }
    public void setEvidenceJson(String evidenceJson) { this.evidenceJson = evidenceJson; }
    public Long getSubmitterId() { return submitterId; }
    public void setSubmitterId(Long submitterId) { this.submitterId = submitterId; }
    public Date getSubmitTime() { return copyDate(submitTime); }
    public void setSubmitTime(Date submitTime) { this.submitTime = copyDate(submitTime); }
    public String getAuditStatus() { return auditStatus; }
    public void setAuditStatus(String auditStatus) { this.auditStatus = auditStatus; }
    public Long getAuditorId() { return auditorId; }
    public void setAuditorId(Long auditorId) { this.auditorId = auditorId; }
    public Date getAuditTime() { return copyDate(auditTime); }
    public void setAuditTime(Date auditTime) { this.auditTime = copyDate(auditTime); }
    public String getAuditComment() { return auditComment; }
    public void setAuditComment(String auditComment) { this.auditComment = auditComment; }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }

    private Date copyDate(Date value) { return value == null ? null : new Date(value.getTime()); }
}
