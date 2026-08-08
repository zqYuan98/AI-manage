package com.ailab.system.domain;

import com.ruoyi.common.core.domain.BaseEntity;

/** Configured report section; JSON is validated before either save or import. */
public class LabReportSection extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long id; private Long templateId; private String sectionCode; private String sectionName;
    private String sectionType; private Integer sortNo; private String dataSource; private String queryConfigJson;
    private String renderConfigJson; private String styleConfigJson; private String manualFlag; private String visibleFlag;
    private String sensitiveFlag; private String sensitivePermission; private Integer version; private String delFlag;
    public Long getId() { return id; } public void setId(Long value) { id = value; }
    public Long getTemplateId() { return templateId; } public void setTemplateId(Long value) { templateId = value; }
    public String getSectionCode() { return sectionCode; } public void setSectionCode(String value) { sectionCode = value; }
    public String getSectionName() { return sectionName; } public void setSectionName(String value) { sectionName = value; }
    public String getSectionType() { return sectionType; } public void setSectionType(String value) { sectionType = value; }
    public Integer getSortNo() { return sortNo; } public void setSortNo(Integer value) { sortNo = value; }
    public String getDataSource() { return dataSource; } public void setDataSource(String value) { dataSource = value; }
    public String getQueryConfigJson() { return queryConfigJson; } public void setQueryConfigJson(String value) { queryConfigJson = value; }
    public String getRenderConfigJson() { return renderConfigJson; } public void setRenderConfigJson(String value) { renderConfigJson = value; }
    public String getStyleConfigJson() { return styleConfigJson; } public void setStyleConfigJson(String value) { styleConfigJson = value; }
    public String getManualFlag() { return manualFlag; } public void setManualFlag(String value) { manualFlag = value; }
    public String getVisibleFlag() { return visibleFlag; } public void setVisibleFlag(String value) { visibleFlag = value; }
    public String getSensitiveFlag() { return sensitiveFlag; } public void setSensitiveFlag(String value) { sensitiveFlag = value; }
    public String getSensitivePermission() { return sensitivePermission; } public void setSensitivePermission(String value) { sensitivePermission = value; }
    public Integer getVersion() { return version; } public void setVersion(Integer value) { version = value; }
    public String getDelFlag() { return delFlag; } public void setDelFlag(String value) { delFlag = value; }
    public boolean isSensitive() { return "1".equals(sensitiveFlag); }
}
