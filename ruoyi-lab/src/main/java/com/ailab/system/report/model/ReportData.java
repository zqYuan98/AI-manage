package com.ailab.system.report.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Immutable intermediate report representation; JSON is a first-class export format. */
public final class ReportData {
    private final ReportContext context; private final String templateCode; private final int templateRevision;
    private final List<ReportSectionData> sections; private final Map<String, Object> metadata;
    public ReportData(ReportContext context, String templateCode, int templateRevision, List<ReportSectionData> sections, Map<String, Object> metadata) {
        if (context == null) throw new IllegalArgumentException("context is required"); if (templateCode == null || templateCode.trim().isEmpty()) throw new IllegalArgumentException("templateCode is required"); if (templateRevision < 1) throw new IllegalArgumentException("templateRevision must be positive");
        this.context = context; this.templateCode = templateCode; this.templateRevision = templateRevision;
        this.sections = Collections.unmodifiableList(new ArrayList<ReportSectionData>(sections == null ? Collections.<ReportSectionData>emptyList() : sections));
        this.metadata = ImmutableReportValue.map(metadata);
    }
    public ReportContext getContext() { return context; } public String getTemplateCode() { return templateCode; } public int getTemplateRevision() { return templateRevision; }
    public List<ReportSectionData> getSections() { return sections; } public Map<String, Object> getMetadata() { return metadata; }
}
