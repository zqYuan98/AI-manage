package com.ailab.system.domain;

import com.ruoyi.common.core.domain.BaseEntity;

/** Persisted manual narrative for a period, business line and section. */
public class LabReportSummary extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long id; private String period; private String bizLine; private String sectionCode; private String summaryJson;
    private String summaryText; private Integer sourceRevision; private String delFlag;
    public Long getId() { return id; } public void setId(Long value) { id = value; }
    public String getPeriod() { return period; } public void setPeriod(String value) { period = value; }
    public String getBizLine() { return bizLine; } public void setBizLine(String value) { bizLine = value; }
    public String getSectionCode() { return sectionCode; } public void setSectionCode(String value) { sectionCode = value; }
    public String getSummaryJson() { return summaryJson; } public void setSummaryJson(String value) { summaryJson = value; }
    public String getSummaryText() { return summaryText; } public void setSummaryText(String value) { summaryText = value; }
    public Integer getSourceRevision() { return sourceRevision; } public void setSourceRevision(Integer value) { sourceRevision = value; }
    public String getDelFlag() { return delFlag; } public void setDelFlag(String value) { delFlag = value; }
}
