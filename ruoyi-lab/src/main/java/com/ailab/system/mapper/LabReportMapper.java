package com.ailab.system.mapper;

import com.ailab.system.domain.LabReportInstance;
import com.ailab.system.domain.LabReportJob;
import com.ailab.system.domain.LabReportSection;
import com.ailab.system.domain.LabReportSummary;
import com.ailab.system.domain.LabReportTemplate;
import com.ailab.system.report.model.ReportPerformancePin;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** Fixed-SQL persistence boundary for versioned templates, reports and retryable steps. */
public interface LabReportMapper {
    List<LabReportTemplate> selectTemplates();
    LabReportTemplate selectTemplateById(@Param("id") Long id);
    LabReportTemplate selectTemplateForUpdate(@Param("id") Long id);
    List<LabReportTemplate> lockTemplateType(@Param("periodType") String periodType);
    List<LabReportSection> selectSections(@Param("templateId") Long templateId);
    List<LabReportSection> selectDefaultManualSections(@Param("periodType") String periodType);
    Integer selectMaxTemplateRevisionForUpdate(@Param("templateCode") String templateCode);
    int clearLatestTemplate(@Param("templateCode") String templateCode, @Param("actor") String actor);
    int insertTemplate(LabReportTemplate template);
    int insertSections(@Param("templateId") Long templateId, @Param("sections") List<LabReportSection> sections);
    int clearDefaultTemplate(@Param("periodType") String periodType, @Param("excludeId") Long excludeId,
            @Param("actor") String actor);
    int markDefaultTemplate(@Param("id") Long id, @Param("version") Integer version, @Param("actor") String actor);

    List<LabReportSummary> selectSummaries(@Param("period") String period, @Param("bizLine") String bizLine);
    LabReportSummary selectSummary(@Param("period") String period,@Param("bizLine") String bizLine,@Param("sectionCode") String sectionCode);
    LabReportSummary selectSummaryForUpdate(@Param("period") String period,@Param("bizLine") String bizLine,@Param("sectionCode") String sectionCode);
    int upsertSummary(LabReportSummary summary);
    int deleteSummary(@Param("period") String period, @Param("bizLine") String bizLine,
            @Param("sectionCode") String sectionCode, @Param("actor") String actor);
    List<String> selectActiveBizLines();

    Integer selectMaxReportRevisionForUpdate(@Param("templateCode") String templateCode, @Param("period") String period,
            @Param("bizLine") String bizLine);
    List<ReportPerformancePin> selectSourcePerformancePins(@Param("period") String period, @Param("bizLine") String bizLine);
    int insertReportInstance(LabReportInstance instance);
    LabReportInstance selectReportById(@Param("id") Long id);
    LabReportInstance selectReportForUpdate(@Param("id") Long id);
    List<LabReportInstance> lockReportFamily(@Param("templateCode") String templateCode, @Param("period") String period,
            @Param("bizLine") String bizLine);
    List<LabReportInstance> selectReportHistory(@Param("period") String period, @Param("bizLine") String bizLine,
            @Param("manager") boolean manager, @Param("actorBizLine") String actorBizLine,
            @Param("shareAll") boolean shareAll, @Param("sensitive") boolean sensitive);
    int supersedeCurrentReport(@Param("templateCode") String templateCode, @Param("period") String period,
            @Param("bizLine") String bizLine, @Param("excludeId") Long excludeId, @Param("actor") String actor);
    int finalizeReport(@Param("id") Long id, @Param("version") Integer version, @Param("actor") String actor);

    int markDataPending(@Param("id") Long id, @Param("jobId") Long jobId, @Param("runToken") String runToken, @Param("actor") String actor);
    int completeJson(@Param("id") Long id, @Param("jobId") Long jobId, @Param("runToken") String runToken,
            @Param("contentJson") String contentJson, @Param("path") String path, @Param("actor") String actor);
    int completeMarkdown(@Param("id") Long id, @Param("jobId") Long jobId, @Param("runToken") String runToken,
            @Param("contentMarkdown") String contentMarkdown, @Param("path") String path, @Param("actor") String actor);
    int failData(@Param("id") Long id, @Param("jobId") Long jobId, @Param("runToken") String runToken,
            @Param("error") String error, @Param("actor") String actor);
    int markWordPending(@Param("id") Long id, @Param("jobId") Long jobId, @Param("runToken") String runToken, @Param("actor") String actor);
    int completeWord(@Param("id") Long id, @Param("jobId") Long jobId, @Param("runToken") String runToken,
            @Param("path") String path, @Param("actor") String actor);
    int failWord(@Param("id") Long id, @Param("jobId") Long jobId, @Param("runToken") String runToken,
            @Param("error") String error, @Param("actor") String actor);
    int markPdfPending(@Param("id") Long id, @Param("jobId") Long jobId, @Param("runToken") String runToken, @Param("actor") String actor);
    int completePdf(@Param("id") Long id, @Param("jobId") Long jobId, @Param("runToken") String runToken,
            @Param("path") String path, @Param("actor") String actor);
    int failPdf(@Param("id") Long id, @Param("jobId") Long jobId, @Param("runToken") String runToken,
            @Param("error") String error, @Param("actor") String actor);
    int activateWordAfterData(@Param("id") Long id, @Param("jobId") Long jobId, @Param("runToken") String runToken, @Param("actor") String actor);
    int activatePdfAfterWord(@Param("id") Long id, @Param("jobId") Long jobId, @Param("runToken") String runToken, @Param("actor") String actor);

    LabReportJob selectActiveReportJob(@Param("reportId") Long reportId, @Param("jobType") String jobType);
    Long lockReportJobScope(@Param("reportId") Long reportId);
    int insertReportJob(LabReportJob job);
    LabReportJob selectReportJobById(@Param("id") Long id);
    int claimReportJob(@Param("id") Long id, @Param("version") Integer version, @Param("runToken") String runToken,
            @Param("actor") String actor, @Param("startedTime") Date startedTime);
    int heartbeatReportJob(@Param("id") Long id, @Param("runToken") String runToken, @Param("actor") String actor);
    int completeReportJob(@Param("id") Long id, @Param("runToken") String runToken, @Param("actor") String actor, @Param("finishedTime") Date finishedTime);
    int failReportJob(@Param("id") Long id, @Param("runToken") String runToken, @Param("error") String error, @Param("actor") String actor,
            @Param("finishedTime") Date finishedTime);
    List<LabReportJob> selectReportJobs(@Param("reportId") Long reportId);
    List<LabReportJob> selectRecoverableReportJobs(@Param("staleBefore") Date staleBefore, @Param("afterId") Long afterId,
            @Param("pageSize") Integer pageSize);
    int failInvalidActiveReportJobs(@Param("actor") String actor);
    int resetStaleReportJob(@Param("id") Long id, @Param("version") Integer version, @Param("runToken") String runToken,
            @Param("staleBefore") Date staleBefore, @Param("actor") String actor);
    int countActiveReportJobs(@Param("reportId") Long reportId);
    int countTerminalReportJobs(@Param("reportId") Long reportId);
    List<String> selectReferencedReportArtifactPaths();
}
