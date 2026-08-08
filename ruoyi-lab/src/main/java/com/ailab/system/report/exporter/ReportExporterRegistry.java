package com.ailab.system.report.exporter;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Fails fast if artifact exporters are ambiguous. */
public final class ReportExporterRegistry {
    private static final String[] FORMATS = { "JSON", "MARKDOWN", "WORD", "PDF" };
    private final Map<String, ReportExporter> exporters;
    private final Map<String, ReportExporter> capabilities;
    public ReportExporterRegistry(Collection<? extends ReportExporter> values) {
        Map<String, ReportExporter> result = new LinkedHashMap<String, ReportExporter>();
        for (ReportExporter value : values) {
            if (value == null || value.getId() == null || value.getId().trim().isEmpty()) throw new IllegalStateException("Report exporter id is required");
            if (result.put(value.getId(), value) != null) throw new IllegalStateException("Duplicate report exporter id: " + value.getId());
        }
        Map<String, ReportExporter> capabilityMap = new LinkedHashMap<String, ReportExporter>();
        for (String format : FORMATS) { ReportExporter owner = null; for (ReportExporter exporter : result.values()) if (exporter.supports(format)) { if (owner != null) throw new IllegalStateException("Conflicting report exporters for: " + format); owner = exporter; } if (owner != null) capabilityMap.put(format, owner); }
        exporters = Collections.unmodifiableMap(result);
        capabilities = Collections.unmodifiableMap(capabilityMap);
    }
    public ReportExporter require(String id) { ReportExporter value = exporters.get(id); if (value == null) value = capabilities.get(id); if (value == null) throw new IllegalArgumentException("Unknown report exporter: " + id); return value; }
    public Map<String, ReportExporter> asMap() { return exporters; }
}
