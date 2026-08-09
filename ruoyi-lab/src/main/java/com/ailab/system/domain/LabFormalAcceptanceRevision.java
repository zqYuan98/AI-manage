package com.ailab.system.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import java.util.Date;

/** 月度正式验收修订头。 */
public class LabFormalAcceptanceRevision extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String period;
    private String bizLine;
    private Integer revisionNo;
    private Long acceptedBy;
    private Date acceptedTime;
    private String calculationVersion;
    private String delFlag;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    public String getBizLine() { return bizLine; }
    public void setBizLine(String bizLine) { this.bizLine = bizLine; }
    public Integer getRevisionNo() { return revisionNo; }
    public void setRevisionNo(Integer revisionNo) { this.revisionNo = revisionNo; }
    public Long getAcceptedBy() { return acceptedBy; }
    public void setAcceptedBy(Long acceptedBy) { this.acceptedBy = acceptedBy; }
    public Date getAcceptedTime() { return copy(acceptedTime); }
    public void setAcceptedTime(Date acceptedTime) { this.acceptedTime = copy(acceptedTime); }
    public String getCalculationVersion() { return calculationVersion; }
    public void setCalculationVersion(String calculationVersion) { this.calculationVersion = calculationVersion; }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
    private Date copy(Date value) { return value == null ? null : new Date(value.getTime()); }
}
