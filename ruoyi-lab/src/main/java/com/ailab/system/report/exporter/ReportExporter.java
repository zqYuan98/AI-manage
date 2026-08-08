package com.ailab.system.report.exporter;

import com.ailab.system.report.model.ReportData;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

/** Extension point for a stable artifact format. */
public interface ReportExporter {
    String getId();
    boolean supports(String format);
    default Set<String> getSupportedIds() { return Collections.singleton(getId()); }
    byte[] export(ReportData data) throws IOException;
}
