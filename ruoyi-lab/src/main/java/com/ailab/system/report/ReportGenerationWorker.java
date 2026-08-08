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
import com.ailab.system.report.model.ReportDataCodec;
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
            @Qualifier("scheduledExecutorService") ScheduledExecutorService heartbeatExecutor) {
        this(mapper, lock, contexts, providers, renderers, exporters, store, codec, dispatcher, heartbeatExecutor, Clock.systemDefaultZone());
    }
    public ReportGenerationWorker(LabReportMapper mapper, ReportJobLock lock, TrustedReportContextFactory contexts,
            DataSourceProviderRegistry providers, SectionRendererRegistry renderers, ReportExporterRegistry exporters,
            ReportArtifactStore store, ReportDataCodec codec, ReportJobDispatcher dispatcher, Clock clock) {
        this(mapper,lock,contexts,providers,renderers,exporters,store,codec,dispatcher,null,clock);
    }
    private ReportGenerationWorker(LabReportMapper mapper, ReportJobLock lock, TrustedReportContextFactory contexts,
            DataSourceProviderRegistry providers, SectionRendererRegistry renderers, ReportExporterRegistry exporters,
            ReportArtifactStore store, ReportDataCodec codec, ReportJobDispatcher dispatcher,
            ScheduledExecutorService heartbeatExecutor, Clock clock) {
        this.mapper=mapper;this.lock=lock;this.contexts=contexts;this.providers=providers;this.renderers=renderers;this.exporters=exporters;
        this.store=store;this.codec=codec;this.dispatcher=dispatcher;this.heartbeatExecutor=heartbeatExecutor;this.clock=clock;
    }

    public void execute(Long jobId) {
        LabReportJob job = mapper.selectReportJobById(jobId); if (job == null || !"QUEUED".equals(job.getJobStatus())) return;
        String token = null; ScheduledFuture<?> heartbeat=null;AtomicBoolean heartbeatHealthy=new AtomicBoolean(true); String actor = hasText(job.getCreateBy()) ? job.getCreateBy() : "report-worker";
        try {
            token = lock.tryAcquire(job.getReportId(), job.getJobType());
            if (token == null) return;
            if (mapper.claimReportJob(jobId, job.getVersion(), actor, Date.from(clock.instant())) != 1) return;
            heartbeat=startHeartbeat(job,actor,token,heartbeatHealthy);
            LabReportInstance report = required(mapper.selectReportById(job.getReportId()));
            if (!"DRAFT".equals(report.getLifecycleStatus())) throw new ServiceException("Immutable report cannot run generation jobs");
            if (!successful(report, job.getJobType())) {
                if ("DATA".equals(job.getJobType())) data(report, actor);
                else if ("WORD".equals(job.getJobType())) word(report, actor);
                else if ("PDF".equals(job.getJobType())) pdf(report, actor);
                else throw new ServiceException("Unsupported report generation step");
            }
            if(!heartbeatHealthy.get())throw new ServiceException("Report job heartbeat was lost; retry is required");
            try { dispatcher.advance(jobId, report.getId(), next(job.getJobType()), actor, Date.from(clock.instant())); }
            catch(RuntimeException progressionFailure){LOG.error("AI Lab report job {} progression remains RUNNING for stale recovery",jobId,progressionFailure);return;}
        } catch (RuntimeException ex) {
            String error = error(ex);
            try { failArtifact(job, error, actor); } catch (RuntimeException stateError) { LOG.error("Could not persist report artifact failure for job {}", jobId, stateError); }
            try { mapper.failReportJob(jobId, error, actor, Date.from(clock.instant())); } catch (RuntimeException stateError) { LOG.error("Could not persist report job failure for {}", jobId, stateError); }
            LOG.error("AI Lab report job {} failed: {}", jobId, error, ex);
        } finally {
            if(heartbeat!=null)heartbeat.cancel(false);
            if (token != null) try { lock.release(job.getReportId(), job.getJobType(), token); } catch (RuntimeException ex) { LOG.error("AI Lab report lock release failed for job {}", jobId, ex); }
        }
    }

    private ScheduledFuture<?> startHeartbeat(final LabReportJob job,final String actor,final String token,final AtomicBoolean healthy){
        if(heartbeatExecutor==null)return null;
        try{return heartbeatExecutor.scheduleAtFixedRate(new Runnable(){@Override public void run(){try{if(mapper.heartbeatReportJob(job.getId(),actor)!=1){healthy.set(false);return;}if(!lock.renew(job.getReportId(),job.getJobType(),token))LOG.warn("AI Lab report lock lease could not be renewed for job {}",job.getId());}catch(RuntimeException ex){healthy.set(false);LOG.error("AI Lab report heartbeat failed for job {}",job.getId(),ex);}}},60,60,TimeUnit.SECONDS);}
        catch(RejectedExecutionException ex){throw new ServiceException("Report job heartbeat could not be scheduled");}
    }

    private void data(LabReportInstance report, String actor) {
        boolean needJson = !"SUCCESS".equals(report.getJsonStatus());
        boolean needMarkdown = !"SUCCESS".equals(report.getMarkdownStatus());
        if (mapper.markDataPending(report.getId(), actor) != 1) throw new ServiceException("Data artifacts are already successful or report changed");
        boolean manual="MANUAL_IMPORT".equals(report.getSourceType());
        ReportData value = needJson && !manual ? build(report, actor) : codec.decode(report.getContentJson());
        String name = "report-" + report.getId();
        if (needJson) {
            byte[] json; try { json = exporters.require("JSON").export(value); } catch (Exception ex) { throw failure(ex); }
            store.discardOrphan(report.getId(), name, "JSON");
            String path = store.publish(report.getId(), name, "JSON", json);
            try { if (mapper.completeJson(report.getId(), new String(json, StandardCharsets.UTF_8), path, actor) != 1) throw new ServiceException("JSON artifact state changed concurrently"); }
            catch (RuntimeException ex) { cleanup(path); throw ex; }
        }
        if (needMarkdown) {
            byte[] markdown; try { markdown = manual ? report.getContentMarkdown().getBytes(StandardCharsets.UTF_8) : exporters.require("MARKDOWN").export(value); } catch (Exception ex) { throw failure(ex); }
            store.discardOrphan(report.getId(), name, "MARKDOWN");
            String path = store.publish(report.getId(), name, "MARKDOWN", markdown);
            try { if (mapper.completeMarkdown(report.getId(), new String(markdown, StandardCharsets.UTF_8), path, actor) != 1) throw new ServiceException("Markdown artifact state changed concurrently"); }
            catch (RuntimeException ex) { cleanup(path); throw ex; }
        }
    }

    private void word(LabReportInstance report, String actor) {
        if (mapper.markWordPending(report.getId(), actor) != 1) throw new ServiceException("Word artifact is not retryable or its dependencies are incomplete");
        ReportData value = codec.decode(report.getContentJson()); byte[] bytes;
        try { bytes = exporters.require("WORD").export(value); } catch (Exception ex) { throw failure(ex); }
        String name = "report-" + report.getId(); store.discardOrphan(report.getId(), name, "WORD");
        String path = store.publish(report.getId(), name, "WORD", bytes);
        try { if (mapper.completeWord(report.getId(), path, actor) != 1) throw new ServiceException("Word artifact state changed concurrently"); }
        catch (RuntimeException ex) { cleanup(path); throw ex; }
    }

    private void pdf(LabReportInstance report, String actor) {
        if (mapper.markPdfPending(report.getId(), actor) != 1) throw new ServiceException("PDF artifact is not retryable or Word is incomplete");
        byte[] word = store.read(report.getWordPath(), "WORD"); byte[] bytes;
        try {
            if (!(exporters.require("PDF") instanceof PdfReportExporter)) throw new ServiceException("PDF exporter cannot reuse the successful Word artifact");
            bytes = ((PdfReportExporter) exporters.require("PDF")).exportFromWord(word, "report-" + report.getId());
        } catch (Exception ex) { throw failure(ex); }
        String name = "report-" + report.getId(); store.discardOrphan(report.getId(), name, "PDF");
        String path = store.publish(report.getId(), name, "PDF", bytes);
        try { if (mapper.completePdf(report.getId(), path, actor) != 1) throw new ServiceException("PDF artifact state changed concurrently"); }
        catch (RuntimeException ex) { cleanup(path); throw ex; }
    }

    private ReportData build(LabReportInstance report, String actor) {
        LabReportTemplate template = mapper.selectTemplateById(report.getTemplateId());
        if (template == null || !report.getTemplateCode().equals(template.getTemplateCode()) || !report.getTemplateRevision().equals(template.getRevisionNo())) throw new ServiceException("Pinned report template revision is unavailable");
        Long actorId; try { actorId = Long.valueOf(actor); } catch (NumberFormatException ex) { throw new ServiceException("Report job actor is invalid"); }
        Map<String,Object> attributes = new LinkedHashMap<String,Object>(); attributes.put("performanceRevision", report.getSourcePerfRevision());
        List<com.ailab.system.report.model.ReportPerformancePin> pins=codec.decodePerformancePins(report.getSourceDataJson());if(pins!=null)attributes.put("performancePins", pinValues(pins));
        ReportContext base = contexts.create(actorId, report.getPeriod(), clock.instant(), attributes);
        ReportContext context = new ReportContext(report.getPeriod(), report.getBizLine(), base.getRequesterId(), base.getGeneratedAt(), base.getAccessScope(), attributes);
        Map<String,LabReportSummary> summaries = summaries(report); List<ReportSectionData> values = new ArrayList<ReportSectionData>();
        for (LabReportSection row : safe(mapper.selectSections(report.getTemplateId()))) {
            if ("0".equals(row.getVisibleFlag())) continue; ReportSectionConfig section = new ReportSectionConfig(row);
            String providerId = "MANUAL".equals(section.getSectionType()) ? ReportConfigCatalog.MANUAL_SUMMARY : section.getDataSource();
            ReportSectionData source = providers.require(providerId).load(context, section);
            LabReportSummary manual = summaries.get(section.getSectionCode());
            if (manual != null) { Map<String,Object> summary = new LinkedHashMap<String,Object>(source.getSummary()); summary.put("manualText", manual.getSummaryText()); source = new ReportSectionData(source.getSectionCode(), source.getSectionType(), source.getTitle(), source.getRows(), summary); }
            values.add(renderers.require(section.getSectionType()).render(context, section, source));
        }
        Map<String,Object> metadata = new LinkedHashMap<String,Object>(); metadata.put("reportId", report.getId()); metadata.put("sourcePerformanceRevision", report.getSourcePerfRevision()); metadata.put("sourceType", report.getSourceType());
        metadata.put("header", codec.decodeObject(template.getHeaderJson(), "template header"));
        metadata.put("style", codec.decodeObject(template.getStyleJson(), "template style"));
        return new ReportData(context, report.getTemplateCode(), report.getTemplateRevision(), values, metadata);
    }

    private Map<String,LabReportSummary> summaries(LabReportInstance report) { Map<String,LabReportSummary> result = new LinkedHashMap<String,LabReportSummary>(); for (LabReportSummary value : safe(mapper.selectSummaries(report.getPeriod(), report.getBizLine()))) result.put(value.getSectionCode(), value); return result; }
    private List<Map<String,Object>> pinValues(List<com.ailab.system.report.model.ReportPerformancePin> pins) { List<Map<String,Object>> result=new ArrayList<Map<String,Object>>();for(com.ailab.system.report.model.ReportPerformancePin pin:pins){Map<String,Object> value=new LinkedHashMap<String,Object>();value.put("memberId",pin.getMemberId());value.put("revisionNo",pin.getRevisionNo());result.add(value);}return result; }
    private String next(String step) { return "DATA".equals(step) ? "WORD" : "WORD".equals(step) ? "PDF" : null; }
    private boolean successful(LabReportInstance report, String step) {
        if ("DATA".equals(step)) return "SUCCESS".equals(report.getJsonStatus()) && "SUCCESS".equals(report.getMarkdownStatus());
        if ("WORD".equals(step)) return "SUCCESS".equals(report.getWordStatus());
        if ("PDF".equals(step)) return "SUCCESS".equals(report.getPdfStatus());
        throw new ServiceException("Unsupported report generation step");
    }
    private void failArtifact(LabReportJob job, String error, String actor) { if ("DATA".equals(job.getJobType())) mapper.failData(job.getReportId(), error, actor); else if ("WORD".equals(job.getJobType())) mapper.failWord(job.getReportId(), error, actor); else if ("PDF".equals(job.getJobType())) mapper.failPdf(job.getReportId(), error, actor); }
    private RuntimeException failure(Exception value) { return value instanceof RuntimeException ? (RuntimeException) value : new ServiceException(error(value)); }
    private LabReportInstance required(LabReportInstance value) { if (value == null) throw new ServiceException("Report does not exist"); return value; }
    private String error(Throwable value) { String text = value == null ? "Unknown report failure" : value.getMessage(); if (!hasText(text)) text = value.getClass().getSimpleName(); text = text.replaceAll("[\\r\\n\\p{Cntrl}]", " ").trim(); return text.length() <= 1000 ? text : text.substring(0, 1000); }
    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
    private void cleanup(String path) { if (path != null) try { store.deleteUncommitted(path); } catch (RuntimeException ex) { LOG.error("Could not clean an uncommitted report artifact", ex); } }
    private <T> List<T> safe(List<T> value) { return value == null ? Collections.<T>emptyList() : value; }
}
