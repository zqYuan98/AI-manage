package com.ailab.system.report.exporter;

import com.ailab.system.report.model.ReportData;
import java.io.IOException;

/** Extension point for a stable artifact format. */
public interface ReportExporter {
    String getId();
    boolean supports(String format);
    byte[] export(ReportData data) throws IOException;
}
