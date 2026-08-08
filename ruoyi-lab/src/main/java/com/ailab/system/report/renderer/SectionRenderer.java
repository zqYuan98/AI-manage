package com.ailab.system.report.renderer;

import com.ailab.system.report.config.ReportSectionConfig;
import com.ailab.system.report.model.ReportContext;
import com.ailab.system.report.model.ReportSectionData;
import java.util.Collections;
import java.util.Set;

/** Extension point for a stable report-section rendering type. */
public interface SectionRenderer {
    String getId();
    boolean supports(String sectionType);
    default Set<String> getSupportedIds() { return Collections.singleton(getId()); }
    ReportSectionData render(ReportContext context, ReportSectionConfig section, ReportSectionData sourceData);
}
