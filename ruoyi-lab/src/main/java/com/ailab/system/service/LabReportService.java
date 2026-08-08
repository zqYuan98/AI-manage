package com.ailab.system.service;

import com.ailab.system.domain.LabReportInstance;
import com.ailab.system.domain.LabReportJob;
import com.ailab.system.domain.LabReportSummary;
import com.ailab.system.dto.ReportArtifact;
import com.ailab.system.dto.ReportQueueReceipt;
import java.util.List;

public interface LabReportService {
    ReportQueueReceipt generate(Long templateId, String period, String bizLine, Long actorUserId);
    LabReportInstance status(Long reportId, Long actorUserId);
    List<LabReportInstance> history(String period, String bizLine, Long actorUserId);
    List<LabReportJob> jobs(Long reportId, Long actorUserId);
    ReportQueueReceipt retry(Long reportId, String artifact, Long actorUserId);
    ReportQueueReceipt importMarkdown(Long sourceReportId, String fileName, byte[] bytes, Long actorUserId);
    LabReportInstance finalizeReport(Long reportId, int expectedVersion, Long actorUserId);
    ReportArtifact artifact(Long reportId, String format, Long actorUserId);
    List<LabReportSummary> summaries(String period, String bizLine, Long actorUserId);
    LabReportSummary saveSummary(LabReportSummary summary, Long actorUserId);
}
