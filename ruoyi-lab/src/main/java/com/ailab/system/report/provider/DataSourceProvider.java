package com.ailab.system.report.provider;

import com.ailab.system.report.config.ReportSectionConfig;
import com.ailab.system.report.model.ReportContext;
import com.ailab.system.report.model.ReportSectionData;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Extension point for a stable, named report data source. */
public interface DataSourceProvider {
    String getId();
    boolean supports(String providerId);
    default Set<String> getSupportedIds() { return Collections.singleton(getId()); }
    /** Extensions are contextual by default; built-in providers override this with their exact formal boundary. */
    default ReportFactClassification getFactClassification() { return ReportFactClassification.CONTEXT_SNAPSHOT; }
    default List<ReportFieldSpec> getFieldSpecs() { return Collections.emptyList(); }
    default Set<String> getSupportedMetrics() { return Collections.emptySet(); }
    ReportSectionData load(ReportContext context, ReportSectionConfig section);
}
