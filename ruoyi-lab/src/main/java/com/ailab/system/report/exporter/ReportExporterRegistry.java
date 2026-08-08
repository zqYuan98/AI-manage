package com.ailab.system.report.exporter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Startup-time registry for exact exporter capabilities. */
@Component
public final class ReportExporterRegistry {
    private final Map<String, ReportExporter> exporters; private final Map<String, ReportExporter> capabilities;
    public ReportExporterRegistry(List<ReportExporter> values) {
        Map<String, ReportExporter> ids = new LinkedHashMap<String, ReportExporter>(); Map<String, ReportExporter> claimed = new LinkedHashMap<String, ReportExporter>();
        for (ReportExporter value : values) { if (value == null || blank(value.getId()) || !value.supports(value.getId())) throw new IllegalStateException("Report exporter must support its own id"); if (ids.put(value.getId(), value) != null) throw new IllegalStateException("Duplicate report exporter id: " + value.getId()); }
        for (ReportExporter value : ids.values()) index(ids, claimed, value, value.getSupportedIds());
        exporters = Collections.unmodifiableMap(ids); capabilities = Collections.unmodifiableMap(claimed);
    }
    private static void index(Map<String, ReportExporter> ids, Map<String, ReportExporter> claimed, ReportExporter value, Set<String> declared) { if (declared == null) throw new IllegalStateException("Report exporter capabilities are required"); for (String capability : declared) { if (blank(capability) || !value.supports(capability)) throw new IllegalStateException("Exporter does not support declared capability"); ReportExporter idOwner = ids.get(capability); ReportExporter old = claimed.put(capability, value); if ((idOwner != null && idOwner != value) || (old != null && old != value)) throw new IllegalStateException("Conflicting report exporter capability: " + capability); } }
    public ReportExporter require(String id) { ReportExporter value = exporters.containsKey(id) ? exporters.get(id) : capabilities.get(id); if (value == null || !value.supports(id)) throw new IllegalArgumentException("Unknown report exporter: " + id); return value; }
    public Map<String, ReportExporter> asMap() { return exporters; }
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
