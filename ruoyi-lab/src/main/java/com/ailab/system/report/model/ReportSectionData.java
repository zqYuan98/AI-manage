package com.ailab.system.report.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable neutral section payload shared by all renderers and exporters. */
public final class ReportSectionData {
    private final String sectionCode; private final String sectionType; private final String title;
    private final List<Map<String, Object>> rows; private final Map<String, Object> summary;
    public ReportSectionData(String sectionCode, String sectionType, String title, List<Map<String, Object>> rows, Map<String, Object> summary) {
        this.sectionCode = required(sectionCode, "sectionCode"); this.sectionType = required(sectionType, "sectionType"); this.title = title == null ? "" : title;
        List<Map<String, Object>> rowCopy = new ArrayList<Map<String, Object>>();
        if (rows != null) for (Map<String, Object> row : rows) rowCopy.add(ImmutableReportValue.map(row));
        this.rows = Collections.unmodifiableList(rowCopy);
        this.summary = ImmutableReportValue.map(summary);
    }
    public String getSectionCode() { return sectionCode; } public String getSectionType() { return sectionType; } public String getTitle() { return title; }
    public List<Map<String, Object>> getRows() { return rows; } public Map<String, Object> getSummary() { return summary; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ReportSectionData)) return false;
        ReportSectionData that = (ReportSectionData) other;
        return sectionCode.equals(that.sectionCode) && sectionType.equals(that.sectionType)
                && title.equals(that.title) && rows.equals(that.rows) && summary.equals(that.summary);
    }
    @Override public int hashCode() { return Objects.hash(sectionCode, sectionType, title, rows, summary); }
    private static String required(String value, String field) { if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(field + " is required"); return value; }
}
