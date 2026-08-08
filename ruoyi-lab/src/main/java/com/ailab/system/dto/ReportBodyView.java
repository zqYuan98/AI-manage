package com.ailab.system.dto;

import com.ailab.system.domain.LabReportInstance;

/** Separately authorized online report body; never included in status/history payloads. */
public final class ReportBodyView {
    private final Long reportId;private final String contentJson,contentMarkdown;
    private ReportBodyView(LabReportInstance value){reportId=value.getId();contentJson=value.getContentJson();contentMarkdown=value.getContentMarkdown();}
    public static ReportBodyView from(LabReportInstance value){return new ReportBodyView(value);}public Long getReportId(){return reportId;}public String getContentJson(){return contentJson;}public String getContentMarkdown(){return contentMarkdown;}
}
