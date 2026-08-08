package com.ailab.system.report.config;

import com.ailab.system.domain.LabReportSection;

/** Validated immutable view of a persisted section configuration. */
public final class ReportSectionConfig {
    private final String sectionCode; private final String sectionType; private final String dataSource;
    private final String queryConfigJson; private final String renderConfigJson; private final boolean sensitive;
    public ReportSectionConfig(LabReportSection source) {
        if (source == null) throw new IllegalArgumentException("section is required");
        this.sectionCode = source.getSectionCode(); this.sectionType = source.getSectionType(); this.dataSource = source.getDataSource();
        this.queryConfigJson = source.getQueryConfigJson(); this.renderConfigJson = source.getRenderConfigJson(); this.sensitive = source.isSensitive();
    }
    public String getSectionCode() { return sectionCode; } public String getSectionType() { return sectionType; } public String getDataSource() { return dataSource; }
    public String getQueryConfigJson() { return queryConfigJson; } public String getRenderConfigJson() { return renderConfigJson; } public boolean isSensitive() { return sensitive; }
}
