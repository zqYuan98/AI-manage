package com.ailab.system.report.provider;

import com.ailab.system.report.config.ReportSectionConfig;
import com.ailab.system.report.model.ReportContext;
import com.ailab.system.report.model.ReportSectionData;

/** Extension point for a stable, named report data source. */
public interface DataSourceProvider {
    String getId();
    boolean supports(String providerId);
    ReportSectionData load(ReportContext context, ReportSectionConfig section);
}
