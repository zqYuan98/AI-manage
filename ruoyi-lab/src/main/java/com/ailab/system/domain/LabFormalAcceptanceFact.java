package com.ailab.system.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import java.util.Date;

/** 正式验收时固化的单任务事实。 */
public class LabFormalAcceptanceFact extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long formalRevisionId;
    private Long taskId;
    private String factJson;
    private Integer evidenceVersion;
    private Long reviewerId;
    private Date reviewTime;
    private String delFlag;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFormalRevisionId() { return formalRevisionId; }
    public void setFormalRevisionId(Long formalRevisionId) { this.formalRevisionId = formalRevisionId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getFactJson() { return factJson; }
    public void setFactJson(String factJson) { this.factJson = factJson; }
    public Integer getEvidenceVersion() { return evidenceVersion; }
    public void setEvidenceVersion(Integer evidenceVersion) { this.evidenceVersion = evidenceVersion; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    public Date getReviewTime() { return copy(reviewTime); }
    public void setReviewTime(Date reviewTime) { this.reviewTime = copy(reviewTime); }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
    private Date copy(Date value) { return value == null ? null : new Date(value.getTime()); }
}
