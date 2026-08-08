package com.ailab.system.report;

import com.ailab.system.domain.LabReportInstance;
import com.ailab.system.domain.LabReportSection;
import com.ailab.system.domain.LabReportTemplate;
import com.ailab.system.dto.LabAccessContext;
import com.ailab.system.dto.ReportArtifact;
import com.ailab.system.mapper.LabReportMapper;
import com.ailab.system.report.model.ReportData;
import com.ailab.system.report.model.ReportDataBudget;
import com.ailab.system.report.model.ReportDataCodec;
import com.ailab.system.report.model.ReportPeriod;
import com.ailab.system.report.model.ReportPerformancePin;
import com.ailab.system.report.model.ReportSectionData;
import com.ailab.system.report.config.ReportSectionConfig;
import com.ailab.system.domain.LabReportSummary;
import com.ailab.system.service.LabAccessService;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.service.ISysMenuService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Short database lifecycle operations. File work is deliberately performed outside transaction methods. */
@Component
public class ReportGenerationOrchestrator {
    private final LabReportMapper mapper; private final LabAccessService access; private final ISysMenuService menus;
    private final ReportArtifactStore store; private final ReportDataCodec codec; private final ReportManualImportPersistence manualImports; private final Clock clock;

    @Autowired
    public ReportGenerationOrchestrator(LabReportMapper mapper, LabAccessService access, ISysMenuService menus,
            ReportArtifactStore store, ReportDataCodec codec, ReportManualImportPersistence manualImports) {
        this(mapper, access, menus, store, codec, manualImports, Clock.systemDefaultZone());
    }
    public ReportGenerationOrchestrator(LabReportMapper mapper, LabAccessService access, ISysMenuService menus,
            ReportArtifactStore store, ReportDataCodec codec, Clock clock) {
        this(mapper, access, menus, store, codec, new ReportManualImportPersistence(mapper), clock);
    }
    ReportGenerationOrchestrator(LabReportMapper mapper, LabAccessService access, ISysMenuService menus,
            ReportArtifactStore store, ReportDataCodec codec, ReportManualImportPersistence manualImports, Clock clock) {
        this.mapper = mapper; this.access = access; this.menus = menus; this.store = store; this.codec = codec;
        this.manualImports = manualImports; this.clock = clock;
    }

    @Transactional
    public LabReportInstance createGeneration(Long templateId, String period, String bizLine, Long actorUserId) {
        access.requireManager(actorUserId); ReportPeriod parsed;
        try { parsed = ReportPeriod.parse(period); } catch (IllegalArgumentException ex) { throw new ServiceException(ex.getMessage()); }
        requireBizLine(bizLine); LabReportTemplate snapshot=requiredTemplate(mapper.selectTemplateById(templateId));
        mapper.selectMaxTemplateRevisionForUpdate(snapshot.getTemplateCode());
        LabReportTemplate template = requiredTemplate(mapper.selectTemplateForUpdate(templateId));
        if(!snapshot.getTemplateCode().equals(template.getTemplateCode()))throw new ServiceException("Template family changed concurrently");
        if (!template.isLatest() || !"ENABLED".equals(template.getStatus()) || !parsed.getKind().name().equals(template.getPeriodType())) throw new ServiceException("Only the latest enabled template can generate the requested report type");
        List<LabReportSection> sections = safe(mapper.selectSections(templateId));
        Map<String,String> manualSummaryTexts=manualSummaryTexts(sections,period,bizLine);requireManualCompleteness(sections,manualSummaryTexts);
        Integer max = mapper.selectMaxReportRevisionForUpdate(template.getTemplateCode(), period, bizLine);
        String performancePeriod=parsed.getKind()==ReportPeriod.Kind.QUARTER?period.substring(0,4)+"-Q"+period.charAt(5):period;
        List<ReportPerformancePin> performancePins = safe(mapper.selectSourcePerformancePins(performancePeriod, bizLine));
        int performanceRevision = 0; for (ReportPerformancePin pin : performancePins) performanceRevision=Math.max(performanceRevision,pin.getRevisionNo());
        LabReportInstance instance = base(template, period, bizLine, max == null ? 1 : max + 1, actorUserId);
        instance.setSensitiveFlag(sensitive(sections) ? "1" : "0"); instance.setSourceType("AUTO");
        instance.setSourcePerfRevision(performanceRevision);
        instance.setSourceDataJson(codec.encodeSourceSnapshot(performancePins,manualSummaryTexts));
        affected(mapper.insertReportInstance(instance), "Report generation version was not created");
        return instance;
    }

    /** Creates a durable manual draft; its DATA job publishes files outside the DB transaction. */
    public LabReportInstance importMarkdown(Long sourceReportId, String markdown, Long actorUserId) {
        access.requireManager(actorUserId); validateMarkdown(markdown);
        LabReportInstance source=requiredReport(mapper.selectReportById(sourceReportId));
        if(!"SUCCESS".equals(source.getJsonStatus())||!hasText(source.getContentJson()))throw new ServiceException("Only a report with successful persisted data can be imported");
        ReportData original = codec.decode(source.getContentJson());
        Map<String,Object> summary = new LinkedHashMap<String,Object>(); summary.put("text", markdown);
        List<ReportSectionData> sections = Collections.singletonList(new ReportSectionData("MANUAL_IMPORT", "MANUAL", "手工回编", Collections.<Map<String,Object>>emptyList(), summary));
        Map<String,Object> metadata = new LinkedHashMap<String,Object>(original.getMetadata()); metadata.put("sourceType", "MANUAL_IMPORT"); metadata.put("sourceReportId", sourceReportId);
        ReportData edited = new ReportData(original.getContext(), source.getTemplateCode(), source.getTemplateRevision(), sections, metadata);
        return manualImports.create(sourceReportId,markdown,codec.encode(edited),actorUserId).getTarget();
    }

    @Transactional
    public LabReportInstance finalizeReport(Long reportId, int expectedVersion, Long actorUserId) {
        access.requireManager(actorUserId); LabReportInstance located = requiredReport(mapper.selectReportById(reportId));
        mapper.lockReportFamily(located.getTemplateCode(), located.getPeriod(), located.getBizLine());
        LabReportInstance report = requiredReport(mapper.selectReportForUpdate(reportId));
        if (report.getVersion() == null || report.getVersion().intValue() != expectedVersion) throw new ServiceException("Report changed concurrently; reload before finalizing");
        if (!"DRAFT".equals(report.getLifecycleStatus())) throw new ServiceException("Finalized and superseded reports are immutable");
        requireManualCompleteness(safe(mapper.selectSections(report.getTemplateId())),codec.decodeManualSummaryTexts(report.getSourceDataJson()));
        for (String status : statuses(report)) if (!"SUCCESS".equals(status)) throw new ServiceException("JSON, Markdown, Word and PDF must all succeed before finalization");
        for (String path : paths(report)) if (!hasText(path)) throw new ServiceException("JSON, Markdown, Word and PDF must all have durable artifacts before finalization");
        if(mapper.countActiveReportJobs(reportId)!=0)throw new ServiceException("Report generation jobs must finish before finalization");
        mapper.supersedeCurrentReport(report.getTemplateCode(), report.getPeriod(), report.getBizLine(), reportId, actor(actorUserId));
        affected(mapper.finalizeReport(reportId, expectedVersion, actor(actorUserId)), "Report finalization changed concurrently");
        report.setLifecycleStatus("FINALIZED"); report.setCurrentFlag("1"); report.setFinalFlag("1"); report.setVersion(expectedVersion + 1); return report;
    }

    public LabReportInstance authorizeView(Long reportId, Long actorUserId) {
        LabAccessContext actor = access.context(actorUserId); LabReportInstance report = requiredReport(mapper.selectReportById(reportId));
        Set<String> permissions = menus.selectMenuPermsByUserId(actorUserId);
        if ("1".equals(report.getSensitiveFlag()) && (permissions == null || !permissions.contains("lab:report:sensitive"))) throw new ServiceException("Sensitive report permission is required");
        if (!"lab_manager".equals(actor.getRoleKey()) && !("FINALIZED".equals(report.getLifecycleStatus()) || "SUPERSEDED".equals(report.getLifecycleStatus()))) {
            throw new ServiceException("Only immutable report history is visible outside management");
        }
        return report;
    }

    public ReportArtifact authorizeArtifact(Long reportId, String format, Long actorUserId) {
        LabReportInstance report = authorizeView(reportId, actorUserId); String path = path(report, format); String status = status(report, format);
        if (!"SUCCESS".equals(status) || path == null) throw new ServiceException("Requested report artifact is not ready");
        return new ReportArtifact(store.resolve(path, format), "report-" + report.getPeriod() + "-v" + report.getRevisionNo() + extension(format), contentType(format));
    }

    public String retryStep(LabReportInstance report, String artifact) {
        if (report == null || !"DRAFT".equals(report.getLifecycleStatus())) throw new ServiceException("Only a draft report can be retried");
        if ("JSON".equals(artifact) || "MARKDOWN".equals(artifact)) {
            if ("FAILED".equals(report.getJsonStatus()) || "FAILED".equals(report.getMarkdownStatus())) return "DATA";
        } else if ("WORD".equals(artifact)) {
            if ("FAILED".equals(report.getWordStatus()) && successful(report.getJsonStatus(), report.getMarkdownStatus())) return "WORD";
        } else if ("PDF".equals(artifact)) {
            if ("FAILED".equals(report.getPdfStatus()) && "SUCCESS".equals(report.getWordStatus())) return "PDF";
        } else throw new ServiceException("Unsupported report artifact");
        throw new ServiceException("Only failed artifacts can be retried; successful artifacts are immutable");
    }

    private LabReportInstance base(LabReportTemplate template, String period, String bizLine, int revision, Long actorUserId) {
        LabReportInstance value = new LabReportInstance(); value.setReportNo(reportNo(period, bizLine)); value.setTemplateId(template.getId());
        value.setTemplateCode(template.getTemplateCode()); value.setTemplateRevision(template.getRevisionNo()); value.setPeriod(period); value.setBizLine(bizLine);
        value.setRevisionNo(revision); value.setLifecycleStatus("DRAFT"); value.setCurrentFlag("0"); value.setFinalFlag("0");
        value.setJsonStatus("PENDING"); value.setMarkdownStatus("PENDING"); value.setWordStatus("NOT_REQUESTED"); value.setPdfStatus("NOT_REQUESTED");
        value.setVersion(0); value.setDelFlag("0"); value.setCreateBy(actor(actorUserId)); return value;
    }
    private boolean sensitive(List<LabReportSection> sections) { for (LabReportSection section : sections) if (!"0".equals(section.getVisibleFlag()) && (section.isSensitive() || "PERF_SUMMARY".equals(section.getDataSource()) || hasText(section.getSensitivePermission()))) return true; return false; }
    private Map<String,String> manualSummaryTexts(List<LabReportSection> sections,String period,String bizLine){
        Map<String,LabReportSummary> available=new LinkedHashMap<String,LabReportSummary>();for(LabReportSummary value:safe(mapper.selectSummaries(period,bizLine)))available.put(value.getSectionCode(),value);
        Map<String,String> result=new LinkedHashMap<String,String>();for(LabReportSection section:sections){if("0".equals(section.getVisibleFlag())||!"MANUAL".equals(section.getSectionType()))continue;LabReportSummary summary=available.get(section.getSectionCode());result.put(section.getSectionCode(),summary==null||summary.getSummaryText()==null?"":summary.getSummaryText());}return result;
    }
    private void requireManualCompleteness(List<LabReportSection> sections,Map<String,String> summaries){
        for(LabReportSection section:sections){if("0".equals(section.getVisibleFlag())||!"MANUAL".equals(section.getSectionType()))continue;ReportSectionConfig config=new ReportSectionConfig(section);if(!Boolean.TRUE.equals(config.getRenderConfig().get("required")))continue;if(!hasText(summaries.get(section.getSectionCode())))throw new ServiceException("Required manual report section is incomplete: "+section.getSectionCode());}
    }
    private String reportNo(String period, String bizLine) { String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12); return "RPT-" + period.replaceAll("[^A-Za-z0-9]", "") + "-" + bizLine + "-" + suffix; }
    private void validateMarkdown(String value) { if (value == null || value.indexOf('\0') >= 0 || value.getBytes(StandardCharsets.UTF_8).length > ReportDataBudget.manualMarkdownByteLimit()) throw new ServiceException("Markdown is missing, invalid or too large"); }
    private void requireBizLine(String value) { if (value == null || !value.matches("[A-Za-z0-9_-]{1,32}")) throw new ServiceException("Invalid business line"); }
    private String path(LabReportInstance value, String format) { if ("JSON".equals(format)) return value.getJsonPath(); if ("MARKDOWN".equals(format)) return value.getMarkdownPath(); if ("WORD".equals(format)) return value.getWordPath(); if ("PDF".equals(format)) return value.getPdfPath(); throw new ServiceException("Unsupported report artifact"); }
    private String status(LabReportInstance value, String format) { if ("JSON".equals(format)) return value.getJsonStatus(); if ("MARKDOWN".equals(format)) return value.getMarkdownStatus(); if ("WORD".equals(format)) return value.getWordStatus(); if ("PDF".equals(format)) return value.getPdfStatus(); throw new ServiceException("Unsupported report artifact"); }
    private String extension(String format) { return "JSON".equals(format) ? ".json" : "MARKDOWN".equals(format) ? ".md" : "WORD".equals(format) ? ".docx" : ".pdf"; }
    private String contentType(String format) { return "JSON".equals(format) ? "application/json" : "MARKDOWN".equals(format) ? "text/markdown;charset=UTF-8" : "WORD".equals(format) ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document" : "application/pdf"; }
    private List<String> statuses(LabReportInstance value) { List<String> result = new ArrayList<String>(); result.add(value.getJsonStatus()); result.add(value.getMarkdownStatus()); result.add(value.getWordStatus()); result.add(value.getPdfStatus()); return result; }
    private List<String> paths(LabReportInstance value) { List<String> result = new ArrayList<String>(); result.add(value.getJsonPath()); result.add(value.getMarkdownPath()); result.add(value.getWordPath()); result.add(value.getPdfPath()); return result; }
    private boolean successful(String... values) { for (String value : values) if (!"SUCCESS".equals(value)) return false; return true; }
    private LabReportTemplate requiredTemplate(LabReportTemplate value) { if (value == null) throw new ServiceException("Report template does not exist"); return value; }
    private LabReportInstance requiredReport(LabReportInstance value) { if (value == null) throw new ServiceException("Report does not exist"); return value; }
    private void affected(int value, String message) { if (value != 1) throw new ServiceException(message); }
    private String actor(Long value) { return String.valueOf(value); }
    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
    private <T> List<T> safe(List<T> value) { return value == null ? Collections.<T>emptyList() : value; }
}
