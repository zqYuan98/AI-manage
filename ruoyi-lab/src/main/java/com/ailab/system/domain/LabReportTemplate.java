package com.ailab.system.domain;

import com.ruoyi.common.core.domain.BaseEntity;

/** A single immutable-in-use revision in a report-template family. */
public class LabReportTemplate extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String templateCode;
    private String templateName;
    private String periodType;
    private Integer revisionNo;
    private String latestFlag;
    private String defaultFlag;
    private String status;
    private String headerJson;
    private String styleJson;
    private Integer version;
    private String delFlag;
    public Long getId() { return id; } public void setId(Long value) { id = value; }
    public String getTemplateCode() { return templateCode; } public void setTemplateCode(String value) { templateCode = value; }
    public String getTemplateName() { return templateName; } public void setTemplateName(String value) { templateName = value; }
    public String getReportType() { return periodType; } public void setReportType(String value) { periodType = value; }
    public String getPeriodType() { return periodType; } public void setPeriodType(String value) { periodType = value; }
    public Integer getRevisionNo() { return revisionNo; } public void setRevisionNo(Integer value) { revisionNo = value; }
    public String getLatestFlag() { return latestFlag; } public void setLatestFlag(String value) { latestFlag = value; }
    public String getDefaultFlag() { return defaultFlag; } public void setDefaultFlag(String value) { defaultFlag = value; }
    public String getStatus() { return status; } public void setStatus(String value) { status = value; }
    public String getHeaderJson() { return headerJson; } public void setHeaderJson(String value) { headerJson = value; }
    public String getStyleJson() { return styleJson; } public void setStyleJson(String value) { styleJson = value; }
    public Integer getVersion() { return version; } public void setVersion(Integer value) { version = value; }
    public String getDelFlag() { return delFlag; } public void setDelFlag(String value) { delFlag = value; }
    public boolean isLatest() { return "1".equals(latestFlag); }
    public boolean isDefaultTemplate() { return "1".equals(defaultFlag); }
}
