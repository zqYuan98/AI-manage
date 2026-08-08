package com.ailab.system.report.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable intermediate report representation; JSON is a first-class export format. */
public final class ReportData {
    private final ReportContext context; private final String templateCode; private final int templateRevision;
    private final List<ReportSectionData> sections; private final Map<String, Object> metadata;
    public ReportData(ReportContext context, String templateCode, int templateRevision, List<ReportSectionData> sections, Map<String, Object> metadata) {
        if (context == null) throw new IllegalArgumentException("context is required"); if (templateCode == null || templateCode.trim().isEmpty()) throw new IllegalArgumentException("templateCode is required"); if (templateRevision < 1) throw new IllegalArgumentException("templateRevision must be positive");
        ReportDataBudget.validate(context, sections, metadata);
        this.context = context; this.templateCode = templateCode; this.templateRevision = templateRevision;
        this.sections = Collections.unmodifiableList(new ArrayList<ReportSectionData>(sections == null ? Collections.<ReportSectionData>emptyList() : sections));
        this.metadata = ImmutableReportValue.map(metadata);
    }
    public ReportContext getContext() { return context; } public String getTemplateCode() { return templateCode; } public int getTemplateRevision() { return templateRevision; }
    public List<ReportSectionData> getSections() { return sections; } public Map<String, Object> getMetadata() { return metadata; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ReportData)) return false;
        ReportData that = (ReportData) other;
        return templateRevision == that.templateRevision && context.equals(that.context)
                && templateCode.equals(that.templateCode) && sections.equals(that.sections)
                && metadata.equals(that.metadata);
    }
    @Override public int hashCode() { return Objects.hash(context, templateCode, templateRevision, sections, metadata); }
}
