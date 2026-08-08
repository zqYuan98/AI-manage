package com.ailab.system.dto;

/** Safe manual-summary schema exposed without granting access to template configuration. */
public final class ReportSummarySectionView {
    private final String sectionCode;
    private final String sectionName;
    private final boolean required;

    public ReportSummarySectionView(String sectionCode, String sectionName, boolean required) {
        this.sectionCode = sectionCode;
        this.sectionName = sectionName;
        this.required = required;
    }

    public String getSectionCode() { return sectionCode; }
    public String getSectionName() { return sectionName; }
    public boolean isRequired() { return required; }
}
