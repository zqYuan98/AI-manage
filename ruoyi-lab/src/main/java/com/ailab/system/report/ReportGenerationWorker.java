package com.ailab.system.report;

import com.ailab.system.domain.LabReportInstance;
import com.ailab.system.domain.LabReportJob;
import com.ailab.system.domain.LabReportSection;
import com.ailab.system.domain.LabReportSummary;
import com.ailab.system.domain.LabReportTemplate;
import com.ailab.system.mapper.LabReportMapper;
import com.ailab.system.report.config.ReportConfigCatalog;
import com.ailab.system.report.config.ReportSectionConfig;
import com.ailab.system.report.exporter.PdfReportExporter;
import com.ailab.system.report.exporter.ReportExporterRegistry;
import com.ailab.system.report.model.ReportContext;
import com.ailab.system.report.model.ReportData;
import com.ailab.system.report.model.ReportDataBudget;
import com.ailab.system.report.model.ReportDataCodec;
import com.ailab.system.report.model.ReportQueryCriteria;
import com.ailab.system.report.model.ReportSectionData;
import com.ailab.system.report.model.TrustedReportContextFactory;
import com.ailab.system.report.provider.DataSourceProviderRegistry;
import com.ailab.system.report.renderer.SectionRendererRegistry;
import com.ruoyi.common.exception.ServiceException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Reloading worker: each durable state transition is committed before any exporter is invoked. */
@Component
public class ReportGenerationWorker {
    private static final Logger LOG = LoggerFactory.getLogger(ReportGenerationWorker.class);
    private final LabReportMapper mapper; private final ReportJobLock lock; private final TrustedReportContextFactory contexts;
    private final DataSourceProviderRegistry providers; private final SectionRendererRegistry renderers; private final ReportExporterRegistry exporters;
    private final ReportArtifactStore store; private final ReportDataCodec codec; private final ReportJobDispatcher dispatcher; private final ScheduledExecutorService heartbeatExecutor; private final Clock clock;
    @Autowired
    public ReportGenerationWorker(LabReportMapper mapper, ReportJobLock lock, TrustedReportContextFactory contexts,
            DataSourceProviderRegistry providers, SectionRendererRegistry renderers, ReportExporterRegistry exporters,
            ReportArtifactStore store, ReportDataCodec codec, @Lazy ReportJobDispatcher dispatcher,
            @Qualifier("reportHeartbeatExecutor") ScheduledExecutorService heartbeatExecutor) {
        this(mapper, lock, contexts, providers, renderers, exporters, store, codec, dispatcher, heartbeatExecutor, Clock.systemDefaultZone());
    }
    public ReportGenerationWorker(LabReportMapper mapper, ReportJobLock lock, TrustedReportContextFactory contexts,
            DataSourceProviderRegistry providers, SectionRendererRegistry renderers, ReportExporterRegistry exporters,
            ReportArtifactStore store, ReportDataCodec codec, ReportJobDispatcher dispatcher, Clock clock) {
        this(mapper,lock,contexts,providers,renderers,exporters,store,codec,dispatcher,null,clock);
    }
    ReportGenerationWorker(LabReportMapper mapper, ReportJobLock lock, TrustedReportContextFactory contexts,
            DataSourceProviderRegistry providers, SectionRendererRegistry renderers, ReportExporterRegistry exporters,
            ReportArtifactStore store, ReportDataCodec codec, ReportJobDispatcher dispatcher,
            ScheduledExecutorService heartbeatExecutor, Clock clock) {
        this.mapper=mapper;this.lock=lock;this.contexts=contexts;this.providers=providers;this.renderers=renderers;this.exporters=exporters;
        this.store=store;this.codec=codec;this.dispatcher=dispatcher;this.heartbeatExecutor=heartbeatExecutor;this.clock=clock;
    }

    public void execute(Long jobId) {
        LabReportJob job = mapper.selectReportJobById(jobId); if (job == null || !"QUEUED".equals(job.getJobStatus())) return;
        String token = null; ScheduledFuture<?> heartbeat=null;AtomicBoolean heartbeatHealthy=new AtomicBoolean(true); boolean claimed=false;
        String actor = hasText(job.getCreateBy()) ? job.getCreateBy() : "report-worker";
        try {
            token = lock.tryAcquire(job.getReportId(), job.getJobType());
            if (token == null) return;
            if (mapper.claimReportJob(jobId, job.getVersion(), token, actor, Date.from(clock.instant())) != 1) return;
            claimed=true; job.setRunToken(token);
            heartbeat=startHeartbeat(job,actor,token,heartbeatHealthy);
            requireHealthy(heartbeatHealthy);
            LabReportInstance report = required(mapper.selectReportById(job.getReportId()));
            if (!"DRAFT".equals(report.getLifecycleStatus())) throw new ServiceException("Immutable report cannot run generation jobs");
            if (!successful(report, job.getJobType())) {
                if ("DATA".equals(job.getJobType())) data(job, report, token, actor, heartbeatHealthy);
                else if ("WORD".equals(job.getJobType())) word(job, report, token, actor, heartbeatHealthy);
                else if ("PDF".equals(job.getJobType())) pdf(job, report, token, actor, heartbeatHealthy);
                else throw new ServiceException("Unsupported report generation step");
            }
            requireHealthy(heartbeatHealthy);
            try { dispatcher.advance(jobId, report.getId(), next(job.getJobType()), token, actor, Date.from(clock.instant())); }
            catch(RuntimeException progressionFailure){LOG.error("AI Lab report job {} progression remains RUNNING for stale recovery",jobId,progressionFailure);return;}
        } catch (OwnershipLostException ex) {
            LOG.warn("AI Lab report job {} stopped because its durable run fence was lost", jobId);
        } catch (LeaseLostException ex) {
            LOG.warn("AI Lab report job {} stopped because its distributed lease was lost", jobId);
        } catch (CompletionUnknownException ex) {
            LOG.error("AI Lab report job {} completion outcome is unknown; it remains RUNNING for stale recovery", jobId, ex.getCause());
        } catch (RuntimeException ex) {
            String error = error(ex);
            if (claimed) {
                boolean artifactFailurePersisted=false;
                try { artifactFailurePersisted=failArtifact(job, token, error, actor)==1; } catch (RuntimeException stateError) { LOG.error("Could not persist report artifact failure for job {}; it remains RUNNING for stale recovery", jobId, stateError); }
                if(artifactFailurePersisted)try { requireOwned(mapper.failReportJob(jobId, token, error, actor, Date.from(clock.instant()))); } catch (RuntimeException stateError) { LOG.error("Could not persist report job failure for {}", jobId, stateError); }
            }
            LOG.error("AI Lab report job {} failed: {}", jobId, error, ex);
        } finally {
            if(heartbeat!=null)heartbeat.cancel(false);
            if (token != null) try { lock.release(job.getReportId(), job.getJobType(), token); } catch (RuntimeException ex) { LOG.error("AI Lab report lock release failed for job {}", jobId, ex); }
        }
    }

    private ScheduledFuture<?> startHeartbeat(final LabReportJob job,final String actor,final String token,final AtomicBoolean healthy){
        if(heartbeatExecutor==null)return null;
        try{return heartbeatExecutor.scheduleAtFixedRate(new Runnable(){@Override public void run(){try{if(!lock.renew(job.getReportId(),job.getJobType(),token)){healthy.set(false);LOG.warn("AI Lab report lock lease could not be renewed for job {}",job.getId());return;}if(mapper.heartbeatReportJob(job.getId(),token,actor)!=1){healthy.set(false);}}catch(RuntimeException ex){healthy.set(false);LOG.error("AI Lab report heartbeat failed for job {}",job.getId(),ex);}}},60,60,TimeUnit.SECONDS);}
        catch(RejectedExecutionException ex){throw new ServiceException("Report job heartbeat could not be scheduled");}
    }

    private void data(LabReportJob job, LabReportInstance report, String runToken, String actor, AtomicBoolean healthy) {
        boolean needJson = !"SUCCESS".equals(report.getJsonStatus()) || !hasText(report.getJsonPath());
        boolean needMarkdown = !"SUCCESS".equals(report.getMarkdownStatus()) || !hasText(report.getMarkdownPath());
        requireHealthy(healthy); requireOwned(mapper.markDataPending(report.getId(), job.getId(), runToken, actor));
        boolean manual="MANUAL_IMPORT".equals(report.getSourceType());
        ReportData value = needJson && !manual ? build(report, actor) : codec.decode(report.getContentJson());
        String name = "report-" + report.getId();
        if (needJson) {
            byte[] json; try { json = exporters.require("JSON").export(value); } catch (Exception ex) { throw failure(ex); }
            requireHealthy(healthy); String path = store.publish(report.getId(), job.getId(), runToken, name, "JSON", json);
            try { requireHealthy(healthy); requireOwned(mapper.completeJson(report.getId(), job.getId(), runToken, new String(json, StandardCharsets.UTF_8), path, actor)); }
            catch (OwnershipLostException | LeaseLostException ex) { cleanup(path); throw ex; }
            catch (RuntimeException ex) { throw new CompletionUnknownException(ex); }
        }
        if (needMarkdown) {
            byte[] markdown; try { markdown = manual ? report.getContentMarkdown().getBytes(StandardCharsets.UTF_8) : exporters.require("MARKDOWN").export(value); } catch (Exception ex) { throw failure(ex); }
            requireHealthy(healthy); String path = store.publish(report.getId(), job.getId(), runToken, name, "MARKDOWN", markdown);
            try { requireHealthy(healthy); requireOwned(mapper.completeMarkdown(report.getId(), job.getId(), runToken, new String(markdown, StandardCharsets.UTF_8), path, actor)); }
            catch (OwnershipLostException | LeaseLostException ex) { cleanup(path); throw ex; }
            catch (RuntimeException ex) { throw new CompletionUnknownException(ex); }
        }
    }

    private void word(LabReportJob job, LabReportInstance report, String runToken, String actor, AtomicBoolean healthy) {
        requireHealthy(healthy); requireOwned(mapper.markWordPending(report.getId(), job.getId(), runToken, actor));
        ReportData value = codec.decode(report.getContentJson()); byte[] bytes;
        try { bytes = exporters.require("WORD").export(value); } catch (Exception ex) { throw failure(ex); }
        requireHealthy(healthy); String name = "report-" + report.getId();
        String path = store.publish(report.getId(), job.getId(), runToken, name, "WORD", bytes);
        try { requireHealthy(healthy); requireOwned(mapper.completeWord(report.getId(), job.getId(), runToken, path, actor)); }
        catch (OwnershipLostException | LeaseLostException ex) { cleanup(path); throw ex; }
        catch (RuntimeException ex) { throw new CompletionUnknownException(ex); }
    }

    private void pdf(LabReportJob job, LabReportInstance report, String runToken, String actor, AtomicBoolean healthy) {
        requireHealthy(healthy); requireOwned(mapper.markPdfPending(report.getId(), job.getId(), runToken, actor));
        byte[] word = store.read(report.getWordPath(), "WORD"); byte[] bytes;
        try {
            if (!(exporters.require("PDF") instanceof PdfReportExporter)) throw new ServiceException("PDF exporter cannot reuse the successful Word artifact");
            bytes = ((PdfReportExporter) exporters.require("PDF")).exportFromWord(word, "report-" + report.getId() + "-job-" + job.getId() + "-run-" + runToken);
        } catch (Exception ex) { throw failure(ex); }
        requireHealthy(healthy); String name = "report-" + report.getId();
        String path = store.publish(report.getId(), job.getId(), runToken, name, "PDF", bytes);
        try { requireHealthy(healthy); requireOwned(mapper.completePdf(report.getId(), job.getId(), runToken, path, actor)); }
        catch (OwnershipLostException | LeaseLostException ex) { cleanup(path); throw ex; }
        catch (RuntimeException ex) { throw new CompletionUnknownException(ex); }
    }

    private ReportData build(LabReportInstance report, String actor) {
        LabReportTemplate template = mapper.selectTemplateById(report.getTemplateId());
        if (template == null || !report.getTemplateCode().equals(template.getTemplateCode()) || !report.getTemplateRevision().equals(template.getRevisionNo())) throw new ServiceException("Pinned report template revision is unavailable");
        Long actorId; try { actorId = Long.valueOf(actor); } catch (NumberFormatException ex) { throw new ServiceException("Report job actor is invalid"); }
        if(report.getSourceCloseRevision()==null||report.getSourceFormalRevision()==null||report.getSourceExecutionCutoff()==null||"1".equals(report.getPreviewOnly()))throw new ServiceException("Pinned formal report facts are unavailable");
        Map<String,Object> attributes = new LinkedHashMap<String,Object>(); attributes.put("performanceRevision", report.getSourcePerfRevision());
        attributes.put("closeRevision",report.getSourceCloseRevision());attributes.put("formalRevision",report.getSourceFormalRevision());
        attributes.put("executionCutoff",report.getSourceExecutionCutoff().toInstant().toString());attributes.put("finalSnapshot",Boolean.TRUE);attributes.put("manualRevisionPinned",Boolean.TRUE);
        List<com.ailab.system.report.model.ReportPerformancePin> pins=codec.decodePerformancePins(report.getSourceDataJson());if(pins!=null)attributes.put("performancePins", pinValues(pins));
        ReportContext base = contexts.create(actorId, report.getPeriod(), report.getSourceExecutionCutoff().toInstant(), attributes);
        ReportContext context = new ReportContext(report.getPeriod(), report.getBizLine(), base.getRequesterId(), base.getGeneratedAt(), base.getAccessScope(), attributes);
        Map<String,String> summaries = codec.decodeManualSummaryTexts(report.getSourceDataJson()); List<ReportSectionData> values = new ArrayList<ReportSectionData>();
        ReportDataBudget.Accumulator budget=ReportDataBudget.accumulator(context);
        for (LabReportSection row : safe(mapper.selectSections(report.getTemplateId()))) {
            if ("0".equals(row.getVisibleFlag())) continue; ReportSectionConfig section = new ReportSectionConfig(row);
            String providerId = "MANUAL".equals(section.getSectionType()) ? ReportConfigCatalog.MANUAL_SUMMARY : section.getDataSource();
            Map<String,Object> sectionAttributes=new LinkedHashMap<String,Object>(attributes);sectionAttributes.put(ReportQueryCriteria.SOURCE_FETCH_LIMIT_ATTRIBUTE,Integer.valueOf(budget.sourceFetchLimit()));
            ReportContext sectionContext=new ReportContext(context.getPeriod(),context.getBizLine(),context.getRequesterId(),context.getGeneratedAt(),context.getAccessScope(),sectionAttributes);
            ReportSectionData source = providers.require(providerId).load(sectionContext, section);
            String manual = summaries.get(section.getSectionCode());
            if (manual != null) { Map<String,Object> summary = new LinkedHashMap<String,Object>(source.getSummary()); summary.put("manualText", manual); source = new ReportSectionData(source.getSectionCode(), source.getSectionType(), source.getTitle(), source.getRows(), summary); }
            ReportSectionData rendered=renderers.require(section.getSectionType()).render(sectionContext, section, source);budget.accept(rendered);values.add(rendered);
        }
        Map<String,Object> metadata = new LinkedHashMap<String,Object>(); metadata.put("reportId", report.getId()); metadata.put("sourcePerformanceRevision", report.getSourcePerfRevision()); metadata.put("sourceType", report.getSourceType());
        metadata.put("sourceCloseRevision",report.getSourceCloseRevision());metadata.put("sourceFormalRevision",report.getSourceFormalRevision());metadata.put("executionCutoff",report.getSourceExecutionCutoff().toInstant().toString());
        metadata.put("header", codec.decodeObject(template.getHeaderJson(), "template header"));
        metadata.put("style", codec.decodeObject(template.getStyleJson(), "template style"));
        budget.complete(metadata);
        return new ReportData(context, report.getTemplateCode(), report.getTemplateRevision(), values, metadata);
    }

    private List<Map<String,Object>> pinValues(List<com.ailab.system.report.model.ReportPerformancePin> pins) { List<Map<String,Object>> result=new ArrayList<Map<String,Object>>();for(com.ailab.system.report.model.ReportPerformancePin pin:pins){Map<String,Object> value=new LinkedHashMap<String,Object>();value.put("memberId",pin.getMemberId());value.put("revisionNo",pin.getRevisionNo());result.add(value);}return result; }
    private String next(String step) { return "DATA".equals(step) ? "WORD" : "WORD".equals(step) ? "PDF" : null; }
    private boolean successful(LabReportInstance report, String step) {
        if ("DATA".equals(step)) return "SUCCESS".equals(report.getJsonStatus()) && hasText(report.getJsonPath()) && "SUCCESS".equals(report.getMarkdownStatus()) && hasText(report.getMarkdownPath());
        if ("WORD".equals(step)) return "SUCCESS".equals(report.getWordStatus()) && hasText(report.getWordPath());
        if ("PDF".equals(step)) return "SUCCESS".equals(report.getPdfStatus()) && hasText(report.getPdfPath());
        throw new ServiceException("Unsupported report generation step");
    }
    private int failArtifact(LabReportJob job, String runToken, String error, String actor) { if ("DATA".equals(job.getJobType())) return mapper.failData(job.getReportId(),job.getId(),runToken,error,actor); else if ("WORD".equals(job.getJobType())) return mapper.failWord(job.getReportId(),job.getId(),runToken,error,actor); else if ("PDF".equals(job.getJobType())) return mapper.failPdf(job.getReportId(),job.getId(),runToken,error,actor); return 0; }
    private RuntimeException failure(Exception value) { return value instanceof RuntimeException ? (RuntimeException) value : new ReportGenerationFailure(value); }
    private LabReportInstance required(LabReportInstance value) { if (value == null) throw new ServiceException("Report does not exist"); return value; }
    private String error(Throwable value) {
        Throwable cause=value;while(cause!=null){
            String type=cause.getClass().getSimpleName();String message=cause.getMessage()==null?"":cause.getMessage().toLowerCase(java.util.Locale.ROOT);
            if(cause instanceof IllegalArgumentException||message.contains("limit")||message.contains("too large")||message.contains("exceed"))return "REPORT_DATA_LIMIT: Report data exceeds a configured safety limit.";
            if(type.contains("LibreOffice")||message.contains("libreoffice"))return "REPORT_PDF_UNAVAILABLE: PDF conversion is temporarily unavailable; the Word file remains available.";
            if(cause instanceof java.io.IOException)return "REPORT_EXPORT_FAILED: An artifact could not be generated; retry is available.";
            cause=cause.getCause();
        }
        return "REPORT_GENERATION_FAILED: Report generation failed; retry the failed step or contact an administrator.";
    }
    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
    private void cleanup(String path) { if (path != null) try { store.deleteUncommitted(path); } catch (RuntimeException ex) { LOG.error("Could not clean an uncommitted report artifact", ex); } }
    private void requireHealthy(AtomicBoolean healthy) { if (!healthy.get()) throw new LeaseLostException(); }
    private void requireOwned(int affected) { if (affected != 1) throw new OwnershipLostException(); }
    private <T> List<T> safe(List<T> value) { return value == null ? Collections.<T>emptyList() : value; }
    private static final class OwnershipLostException extends RuntimeException { private static final long serialVersionUID = 1L; }
    private static final class LeaseLostException extends RuntimeException { private static final long serialVersionUID = 1L; }
    private static final class CompletionUnknownException extends RuntimeException { private static final long serialVersionUID = 1L; CompletionUnknownException(Throwable cause){super(cause);} }
    private static final class ReportGenerationFailure extends RuntimeException { private static final long serialVersionUID = 1L; ReportGenerationFailure(Throwable cause){super("Report artifact exporter failed",cause);} }
}
