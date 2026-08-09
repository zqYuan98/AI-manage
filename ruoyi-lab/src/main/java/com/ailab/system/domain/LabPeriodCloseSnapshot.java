package com.ailab.system.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import java.util.Date;

/** 月结时固化的修订头。 */
public class LabPeriodCloseSnapshot extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String period;
    private Integer revisionNo;
    private Integer periodVersion;
    private Long formalRevisionId;
    private Integer performanceRevision;
    private Long closedBy;
    private Date closedTime;
    private String calculationVersion;
    private String delFlag;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    public Integer getRevisionNo() { return revisionNo; }
    public void setRevisionNo(Integer revisionNo) { this.revisionNo = revisionNo; }
    public Integer getPeriodVersion() { return periodVersion; }
    public void setPeriodVersion(Integer periodVersion) { this.periodVersion = periodVersion; }
    public Long getFormalRevisionId() { return formalRevisionId; }
    public void setFormalRevisionId(Long formalRevisionId) { this.formalRevisionId = formalRevisionId; }
    public Integer getPerformanceRevision() { return performanceRevision; }
    public void setPerformanceRevision(Integer performanceRevision) { this.performanceRevision = performanceRevision; }
    public Long getClosedBy() { return closedBy; }
    public void setClosedBy(Long closedBy) { this.closedBy = closedBy; }
    public Date getClosedTime() { return copy(closedTime); }
    public void setClosedTime(Date closedTime) { this.closedTime = copy(closedTime); }
    public String getCalculationVersion() { return calculationVersion; }
    public void setCalculationVersion(String calculationVersion) { this.calculationVersion = calculationVersion; }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
    private Date copy(Date value) { return value == null ? null : new Date(value.getTime()); }
}
