package com.ailab.system.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailab.system.domain.LabReportInstance;
import com.ailab.system.domain.LabReportSection;
import com.ailab.system.domain.LabReportTemplate;
import com.ailab.system.domain.LabReportJob;
import com.ailab.system.domain.LabReportSummary;
import com.ailab.system.controller.LabReportController;
import com.ailab.system.dto.LabAccessContext;
import com.ailab.system.mapper.LabReportMapper;
import com.ailab.system.config.LabProperties;
import com.ailab.system.report.config.ReportSectionConfig;
import com.ailab.system.report.exporter.JsonReportExporter;
import com.ailab.system.report.exporter.MarkdownReportExporter;
import com.ailab.system.report.exporter.PdfReportExporter;
import com.ailab.system.report.exporter.ReportExporter;
import com.ailab.system.report.exporter.ReportExporterRegistry;
import com.ailab.system.report.model.ReportContext;
import com.ailab.system.report.model.ReportDataCodec;
import com.ailab.system.report.model.ReportSectionData;
import com.ailab.system.report.model.ReportPerformancePin;
import com.ailab.system.report.model.TrustedReportContextFactory;
import com.ailab.system.report.provider.DataSourceProvider;
import com.ailab.system.report.provider.DataSourceProviderRegistry;
import com.ailab.system.report.renderer.SectionRenderer;
import com.ailab.system.report.renderer.SectionRendererRegistry;
import com.ailab.system.service.LabAccessService;
import com.ailab.system.service.impl.LabReportTempFileEligibilityImpl;
import com.ailab.system.service.impl.LabReportServiceImpl;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.service.ISysMenuService;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

class ReportGenerationOrchestratorTest {
    private LabReportMapper mapper;
    private LabAccessService access;
    private ISysMenuService menus;
    private ReportArtifactStore store;
    private ReportGenerationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        mapper = mock(LabReportMapper.class); access = mock(LabAccessService.class);
        menus = mock(ISysMenuService.class); store = mock(ReportArtifactStore.class);
        orchestrator = new ReportGenerationOrchestrator(mapper, access, menus, store, new ReportDataCodec(),
                Clock.fixed(Instant.parse("2026-08-08T01:00:00Z"), ZoneOffset.UTC));
        LabAccessContext manager = new LabAccessContext(); manager.setUserId(1001L); manager.setMemberId(11L);
        manager.setRoleKey("lab_manager"); manager.setBizLine("manage"); when(access.context(1001L)).thenReturn(manager);
        doAnswer(call -> { ((LabReportInstance) call.getArgument(0)).setId(31L); return 1; })
                .when(mapper).insertReportInstance(any(LabReportInstance.class));
    }

    @Test
    void generationPinsExactTemplatePerformanceRevisionAndSensitiveSnapshot() {
        LabReportTemplate template = template(); when(mapper.selectTemplateById(7L)).thenReturn(template);when(mapper.selectTemplateForUpdate(7L)).thenReturn(template);
        when(mapper.selectSections(7L)).thenReturn(Arrays.asList(section("TASKS", false), section("PERF", true)));
        when(mapper.selectMaxReportRevisionForUpdate("monthly", "2026-07", "ALL")).thenReturn(4);
        when(mapper.selectSourcePerformancePins("2026-07", "ALL")).thenReturn(Arrays.asList(new ReportPerformancePin(11L,3),new ReportPerformancePin(12L,1)));

        LabReportInstance instance = orchestrator.createGeneration(7L, "2026-07", "ALL", 1001L);

        assertEquals("monthly", instance.getTemplateCode()); assertEquals(6, instance.getTemplateRevision());
        assertEquals(5, instance.getRevisionNo()); assertEquals(3, instance.getSourcePerfRevision());
        assertEquals("1", instance.getSensitiveFlag()); assertEquals("AUTO", instance.getSourceType());
        assertEquals("DRAFT", instance.getLifecycleStatus());
        assertTrue(instance.getSourceDataJson().contains("\"memberId\":11") && instance.getSourceDataJson().contains("\"memberId\":12"));
        assertEquals("PENDING", instance.getJsonStatus()); assertEquals("NOT_REQUESTED", instance.getWordStatus()); assertEquals("NOT_REQUESTED", instance.getPdfStatus());
    }

    @Test
    void quarterlyGenerationPinsTheCanonicalPerformancePeriod() {
        LabReportTemplate quarterly=template();quarterly.setPeriodType("QUARTER");when(mapper.selectTemplateById(7L)).thenReturn(quarterly);when(mapper.selectTemplateForUpdate(7L)).thenReturn(quarterly);when(mapper.selectSections(7L)).thenReturn(Collections.<LabReportSection>emptyList());

        orchestrator.createGeneration(7L,"2026Q3","ALL",1001L);

        verify(mapper).selectSourcePerformancePins("2026-Q3","ALL");
    }

    @Test
    void historicalTemplateRevisionCannotGenerateANewReport() {
        LabReportTemplate historical=template();historical.setLatestFlag("0");when(mapper.selectTemplateById(7L)).thenReturn(historical);when(mapper.selectTemplateForUpdate(7L)).thenReturn(historical);

        assertThrows(ServiceException.class,()->orchestrator.createGeneration(7L,"2026-07","ALL",1001L));

        verify(mapper,never()).insertReportInstance(any());
    }

    @Test
    void requiredManualSectionsMustBeCompleteBeforeGeneration() {
        LabReportTemplate template=template();when(mapper.selectTemplateById(7L)).thenReturn(template);when(mapper.selectTemplateForUpdate(7L)).thenReturn(template);
        LabReportSection manual=section("EXEC_SUMMARY",false);manual.setSectionType("MANUAL");manual.setDataSource(null);manual.setManualFlag("1");manual.setRenderConfigJson("{\"required\":true}");when(mapper.selectSections(7L)).thenReturn(Collections.singletonList(manual));when(mapper.selectSummaries("2026-07","ALL")).thenReturn(Collections.<LabReportSummary>emptyList());
        assertThrows(ServiceException.class,()->orchestrator.createGeneration(7L,"2026-07","ALL",1001L));verify(mapper,never()).insertReportInstance(any());
    }

    @Test
    void requiredManualSectionsNeedRenderableTextRatherThanUnusedJson() {
        LabReportTemplate template=template();when(mapper.selectTemplateById(7L)).thenReturn(template);when(mapper.selectTemplateForUpdate(7L)).thenReturn(template);
        LabReportSection manual=section("EXEC_SUMMARY",false);manual.setSectionType("MANUAL");manual.setDataSource(null);manual.setManualFlag("1");manual.setRenderConfigJson("{\"required\":true}");when(mapper.selectSections(7L)).thenReturn(Collections.singletonList(manual));
        LabReportSummary summary=new LabReportSummary();summary.setSectionCode("EXEC_SUMMARY");summary.setSummaryText(" ");summary.setSummaryJson("{\"unused\":\"cannot be rendered\"}");when(mapper.selectSummaries("2026-07","ALL")).thenReturn(Collections.singletonList(summary));

        assertThrows(ServiceException.class,()->orchestrator.createGeneration(7L,"2026-07","ALL",1001L));

        verify(mapper,never()).insertReportInstance(any());
    }

    @Test
    void generationPinsManualSummaryTextForTheWholeArtifactPipeline() {
        LabReportTemplate template=template();when(mapper.selectTemplateById(7L)).thenReturn(template);when(mapper.selectTemplateForUpdate(7L)).thenReturn(template);
        LabReportSection manual=section("EXEC_SUMMARY",false);manual.setSectionType("MANUAL");manual.setDataSource(null);manual.setManualFlag("1");manual.setRenderConfigJson("{\"required\":true,\"placeholder\":\"required\"}");
        LabReportSummary summary=new LabReportSummary();summary.setSectionCode("EXEC_SUMMARY");summary.setSummaryText("Pinned management summary");summary.setSummaryJson("{}");summary.setSourceRevision(9);
        when(mapper.selectSections(7L)).thenReturn(Collections.singletonList(manual));when(mapper.selectSummaries("2026-07","ALL")).thenReturn(Collections.singletonList(summary));

        LabReportInstance instance=orchestrator.createGeneration(7L,"2026-07","ALL",1001L);

        assertEquals("Pinned management summary",new ReportDataCodec().decodeManualSummaryTexts(instance.getSourceDataJson()).get("EXEC_SUMMARY"));
    }

    @Test
    void finalizationCannotBeBypassedByRestoringMutableManualSummaryAfterGeneration() {
        LabReportInstance complete=draft(31L,4);complete.setSourceDataJson(new ReportDataCodec().encodeSourceSnapshot(Collections.<ReportPerformancePin>emptyList(),Collections.singletonMap("EXEC_SUMMARY","")));
        LabReportSection manual=section("EXEC_SUMMARY",false);manual.setSectionType("MANUAL");manual.setDataSource(null);manual.setManualFlag("1");manual.setRenderConfigJson("{\"required\":true}");
        LabReportSummary restored=new LabReportSummary();restored.setSectionCode("EXEC_SUMMARY");restored.setSummaryText("Restored after artifacts were generated");
        when(mapper.selectReportById(31L)).thenReturn(complete);when(mapper.lockReportFamily("monthly","2026-07","ALL")).thenReturn(Collections.singletonList(complete));when(mapper.selectReportForUpdate(31L)).thenReturn(complete);when(mapper.selectSections(7L)).thenReturn(Collections.singletonList(manual));when(mapper.selectSummaries("2026-07","ALL")).thenReturn(Collections.singletonList(restored));

        assertThrows(ServiceException.class,()->orchestrator.finalizeReport(31L,4,1001L));

        verify(mapper,never()).finalizeReport(any(),any(),any());
    }

    @Test
    void finalizationRequiresAllFourArtifactsAndOptimisticallySupersedesPriorFinal() {
        LabReportInstance incomplete = draft(31L, 4); incomplete.setPdfStatus("FAILED");
        when(mapper.selectReportById(31L)).thenReturn(incomplete);
        when(mapper.lockReportFamily("monthly", "2026-07", "ALL")).thenReturn(Collections.singletonList(incomplete));
        when(mapper.selectReportForUpdate(31L)).thenReturn(incomplete);
        assertThrows(ServiceException.class, () -> orchestrator.finalizeReport(31L, 4, 1001L));
        verify(mapper, never()).finalizeReport(any(), any(), any());

        LabReportInstance complete = draft(31L, 4); complete.setPdfStatus("SUCCESS");
        when(mapper.selectReportById(31L)).thenReturn(complete);
        when(mapper.selectReportForUpdate(31L)).thenReturn(complete);
        when(mapper.supersedeCurrentReport("monthly", "2026-07", "ALL", 31L, "1001")).thenReturn(1);
        when(mapper.finalizeReport(31L, 4, "1001")).thenReturn(1);
        LabReportInstance finalized = orchestrator.finalizeReport(31L, 4, 1001L);
        assertEquals("FINALIZED", finalized.getLifecycleStatus()); assertEquals("1", finalized.getCurrentFlag());
        verify(mapper).supersedeCurrentReport("monthly", "2026-07", "ALL", 31L, "1001");
    }

    @Test
    void finalizationRejectsSuccessMarkersWithoutDurableArtifactPaths() {
        LabReportInstance incomplete=draft(31L,4);incomplete.setMarkdownPath(null);
        when(mapper.selectReportById(31L)).thenReturn(incomplete);when(mapper.lockReportFamily("monthly","2026-07","ALL")).thenReturn(Collections.singletonList(incomplete));when(mapper.selectReportForUpdate(31L)).thenReturn(incomplete);

        assertThrows(ServiceException.class,()->orchestrator.finalizeReport(31L,4,1001L));

        verify(mapper,never()).finalizeReport(any(),any(),any());
    }

    @Test
    void finalizationWaitsForTheLastDurableJobToFinishProgression() {
        LabReportInstance complete=draft(31L,4);when(mapper.selectReportById(31L)).thenReturn(complete);when(mapper.lockReportFamily("monthly","2026-07","ALL")).thenReturn(Collections.singletonList(complete));when(mapper.selectReportForUpdate(31L)).thenReturn(complete);when(mapper.countActiveReportJobs(31L)).thenReturn(1);

        assertThrows(ServiceException.class,()->orchestrator.finalizeReport(31L,4,1001L));

        verify(mapper,never()).finalizeReport(any(),any(),any());
    }

    @Test
    void staleOrImmutableFinalizationIsRejected() {
        LabReportInstance immutable = draft(31L, 5); immutable.setLifecycleStatus("FINALIZED");
        when(mapper.selectReportById(31L)).thenReturn(immutable);
        when(mapper.lockReportFamily("monthly", "2026-07", "ALL")).thenReturn(Collections.singletonList(immutable));
        when(mapper.selectReportForUpdate(31L)).thenReturn(immutable);
        assertThrows(ServiceException.class, () -> orchestrator.finalizeReport(31L, 4, 1001L));
    }

    @Test
    void markdownImportCreatesManualRevisionWithoutAcceptingSecurityOrDataSourceMetadata() throws Exception {
        LabReportInstance source = draft(20L, 7); source.setLifecycleStatus("FINALIZED");
        source.setContentJson("{\"context\":{\"period\":\"2026-07\",\"bizLine\":\"ALL\",\"requesterId\":11,\"generatedAt\":\"2026-08-08T01:00:00Z\",\"attributes\":{}},\"templateCode\":\"monthly\",\"templateRevision\":6,\"sections\":[],\"metadata\":{}}");
        when(mapper.selectReportById(20L)).thenReturn(source);
        when(mapper.selectMaxReportRevisionForUpdate("monthly", "2026-07", "ALL")).thenReturn(7);
        String edited = "---\ndataSource: EVIL_SQL\nsensitivePermission: none\n---\n# Edited body";

        LabReportInstance imported = orchestrator.importMarkdown(20L, edited, 1001L);

        assertEquals("MANUAL_IMPORT", imported.getSourceType()); assertEquals(8, imported.getRevisionNo());
        assertEquals(source.getSensitiveFlag(), imported.getSensitiveFlag());
        assertEquals(source.getTemplateCode(), imported.getTemplateCode());
        assertEquals("SUCCESS", imported.getJsonStatus()); assertEquals("SUCCESS", imported.getMarkdownStatus());
        assertEquals("PENDING", imported.getWordStatus()); assertEquals("NOT_REQUESTED", imported.getPdfStatus());
        assertEquals(edited, imported.getContentMarkdown());
        verify(mapper).lockReportFamily("monthly", "2026-07", "ALL");
        assertTrue(imported.getContentJson().contains("MANUAL_IMPORT"));verify(store,never()).publish(any(),any(),any(),any(),any(),any());
    }

    @Test
    void markdownImportUsesTheNeutralModelReservedTextBudget() {
        LabReportInstance source=draft(20L,7);source.setLifecycleStatus("FINALIZED");source.setContentJson("{\"context\":{\"period\":\"2026-07\",\"bizLine\":\"ALL\",\"requesterId\":11,\"generatedAt\":\"2026-08-08T01:00:00Z\",\"attributes\":{}},\"templateCode\":\"monthly\",\"templateRevision\":6,\"sections\":[],\"metadata\":{}}");when(mapper.selectReportById(20L)).thenReturn(source);when(mapper.selectMaxReportRevisionForUpdate("monthly","2026-07","ALL")).thenReturn(7);
        int limit=com.ailab.system.report.model.ReportDataBudget.manualMarkdownByteLimit();char[] acceptedChars=new char[limit];java.util.Arrays.fill(acceptedChars,'x');char[] rejectedChars=new char[limit+1];java.util.Arrays.fill(rejectedChars,'x');

        assertEquals("SUCCESS",orchestrator.importMarkdown(20L,new String(acceptedChars),1001L).getMarkdownStatus());
        assertThrows(ServiceException.class,()->orchestrator.importMarkdown(20L,new String(rejectedChars),1001L));
    }

    @Test
    void everySensitiveViewAndDownloadReauthorizesCurrentPermissionSnapshot() {
        LabReportInstance sensitive = draft(31L, 4); sensitive.setSensitiveFlag("1");
        when(mapper.selectReportById(31L)).thenReturn(sensitive);
        when(menus.selectMenuPermsByUserId(1001L)).thenReturn(Collections.<String>emptySet());
        assertThrows(ServiceException.class, () -> orchestrator.authorizeView(31L, 1001L));
        when(menus.selectMenuPermsByUserId(1001L)).thenReturn(Collections.singleton("lab:report:sensitive"));
        assertEquals(31L, orchestrator.authorizeView(31L, 1001L).getId());
        verify(menus, org.mockito.Mockito.times(2)).selectMenuPermsByUserId(1001L);
    }

    @Test
    void retryStartsOnlyAtFailedArtifactAndNeverOverwritesSuccess() {
        LabReportInstance value = draft(31L, 4); value.setWordStatus("SUCCESS"); value.setPdfStatus("FAILED");
        assertEquals("PDF", orchestrator.retryStep(value, "PDF"));
        assertThrows(ServiceException.class, () -> orchestrator.retryStep(value, "WORD"));
        value.setJsonStatus("FAILED");
        assertEquals("DATA", orchestrator.retryStep(value, "JSON"));
    }

    @Test
    void lifecycleMapperXmlParsesAndNeverInterpolatesConfigurationIntoSql() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/mapper/lab/LabReportMapper.xml")) {
            assertTrue(input != null); Configuration configuration = new Configuration();
            new XMLMapperBuilder(input, configuration, "mapper/lab/LabReportMapper.xml", configuration.getSqlFragments()).parse();
            assertTrue(configuration.hasStatement("com.ailab.system.mapper.LabReportMapper.finalizeReport"));
            assertTrue(configuration.hasStatement("com.ailab.system.mapper.LabReportMapper.selectRecoverableReportJobs"));
        }
        String xml = new String(Files.readAllBytes(java.nio.file.Paths.get("src/main/resources/mapper/lab/LabReportMapper.xml")), StandardCharsets.UTF_8);
        assertTrue(!xml.contains("${"), "report lifecycle SQL must use bound parameters only");
        String history = xml.substring(xml.indexOf("<select id=\"selectReportHistory\""),
                xml.indexOf("</select>", xml.indexOf("<select id=\"selectReportHistory\"")));
        assertTrue(history.contains("</if>\n        <if test=\"!sensitive\">and sensitive_flag='0'</if>"),
                "sensitive history filtering must apply even after a manager loses the sensitive permission");
    }

    @Test
    void lineLeadsCanReachManualSummaryWriteBeforeObjectScopeAuthorization() throws Exception {
        PreAuthorize guard = LabReportController.class.getMethod("saveSummary", LabReportSummary.class)
                .getAnnotation(PreAuthorize.class);
        assertTrue(guard != null && guard.value().contains("lab:report:list"),
                "summary writes must be reachable by line leads; the service enforces matching biz-line scope");
    }

    @Test
    void workerCommitsDataArtifactsThenDurablyQueuesWordWithoutAnHttpThread() throws Exception {
        LabReportJob job = job(51L, "DATA"); LabReportInstance report = draft(31L, 0); report.setJsonStatus("PENDING"); report.setMarkdownStatus("PENDING");
        when(mapper.selectReportJobById(51L)).thenReturn(job); when(mapper.claimReportJob(any(),any(),any(),any(),any())).thenReturn(1);
        when(mapper.selectReportById(31L)).thenReturn(report); when(mapper.markDataPending(31L,51L,"run-token-1234567890","1001")).thenReturn(1);
        when(mapper.selectTemplateById(7L)).thenReturn(template()); LabReportSection configured=section("TASKS",false);
        when(mapper.selectSections(7L)).thenReturn(Collections.singletonList(configured)); when(mapper.selectSummaries("2026-07","ALL")).thenReturn(Collections.emptyList());
        when(mapper.completeJson(eq(31L),eq(51L),eq("run-token-1234567890"),any(),any(),eq("1001"))).thenReturn(1);
        when(mapper.completeMarkdown(eq(31L),eq(51L),eq("run-token-1234567890"),any(),any(),eq("1001"))).thenReturn(1);
        when(store.publish(eq(31L),eq(51L),eq("run-token-1234567890"),eq("report-31"),eq("JSON"),any())).thenReturn("archive/report-31/runs/run-token-1234567890/report-31.json");
        when(store.publish(eq(31L),eq(51L),eq("run-token-1234567890"),eq("report-31"),eq("MARKDOWN"),any())).thenReturn("archive/report-31/runs/run-token-1234567890/report-31.md");
        DataSourceProvider provider=new DataSourceProvider(){public String getId(){return "GOAL_PROGRESS";}public boolean supports(String id){return getId().equals(id);}public ReportSectionData load(ReportContext c,ReportSectionConfig s){return new ReportSectionData(s.getSectionCode(),s.getSectionType(),s.getTitle(),Collections.<java.util.Map<String,Object>>emptyList(),Collections.<String,Object>singletonMap("text","data"));}};
        SectionRenderer renderer=new SectionRenderer(){public String getId(){return "TEXT";}public boolean supports(String id){return getId().equals(id);}public ReportSectionData render(ReportContext c,ReportSectionConfig s,ReportSectionData d){return d;}};
        LabAccessContext manager=new LabAccessContext();manager.setUserId(1001L);manager.setMemberId(11L);manager.setRoleKey("lab_manager");manager.setBizLine("manage");when(access.context(1001L)).thenReturn(manager);when(menus.selectMenuPermsByUserId(1001L)).thenReturn(Collections.singleton("lab:report:sensitive"));
        ReportJobDispatcher downstream=mock(ReportJobDispatcher.class);
        ReportGenerationWorker worker=new ReportGenerationWorker(mapper,lock(),new TrustedReportContextFactory(access,menus),new DataSourceProviderRegistry(Collections.singletonList(provider)),new SectionRendererRegistry(Collections.singletonList(renderer)),new ReportExporterRegistry(Arrays.<ReportExporter>asList(new JsonReportExporter(),new MarkdownReportExporter())),store,new ReportDataCodec(),downstream,Clock.fixed(Instant.parse("2026-08-08T01:00:00Z"),ZoneOffset.UTC));

        worker.execute(51L);

        verify(mapper).completeJson(eq(31L),eq(51L),eq("run-token-1234567890"),any(),eq("archive/report-31/runs/run-token-1234567890/report-31.json"),eq("1001"));
        verify(mapper).completeMarkdown(eq(31L),eq(51L),eq("run-token-1234567890"),any(),eq("archive/report-31/runs/run-token-1234567890/report-31.md"),eq("1001"));
        verify(downstream).advance(eq(51L),eq(31L),eq("WORD"),eq("run-token-1234567890"),eq("1001"),any());
    }

    @Test
    void dataWorkerRendersThePinnedManualSummaryWithoutReloadingMutableSummaryRows() {
        LabReportJob job=job(63L,"DATA");LabReportInstance report=draft(31L,0);report.setJsonStatus("PENDING");report.setMarkdownStatus("PENDING");
        report.setSourceDataJson(new ReportDataCodec().encodeSourceSnapshot(Collections.<ReportPerformancePin>emptyList(),Collections.singletonMap("EXEC_SUMMARY","Pinned management decision")));
        when(mapper.selectReportJobById(63L)).thenReturn(job);when(mapper.claimReportJob(any(),any(),any(),any(),any())).thenReturn(1);when(mapper.selectReportById(31L)).thenReturn(report);when(mapper.markDataPending(31L,63L,"run-token-1234567890","1001")).thenReturn(1);when(mapper.selectTemplateById(7L)).thenReturn(template());
        LabReportSection configured=section("EXEC_SUMMARY",false);configured.setSectionType("MANUAL");configured.setDataSource(null);configured.setManualFlag("1");configured.setRenderConfigJson("{\"required\":true}");when(mapper.selectSections(7L)).thenReturn(Collections.singletonList(configured));
        when(mapper.completeJson(eq(31L),eq(63L),eq("run-token-1234567890"),any(),any(),eq("1001"))).thenReturn(1);when(mapper.completeMarkdown(eq(31L),eq(63L),eq("run-token-1234567890"),any(),any(),eq("1001"))).thenReturn(1);
        when(store.publish(eq(31L),eq(63L),eq("run-token-1234567890"),eq("report-31"),eq("JSON"),any())).thenReturn("archive/report-31/runs/run-token-1234567890/report-31.json");when(store.publish(eq(31L),eq(63L),eq("run-token-1234567890"),eq("report-31"),eq("MARKDOWN"),any())).thenReturn("archive/report-31/runs/run-token-1234567890/report-31.md");
        DataSourceProvider provider=new DataSourceProvider(){public String getId(){return "MANUAL_SUMMARY";}public boolean supports(String id){return getId().equals(id);}public ReportSectionData load(ReportContext c,ReportSectionConfig s){return new ReportSectionData(s.getSectionCode(),s.getSectionType(),s.getTitle(),Collections.<java.util.Map<String,Object>>emptyList(),Collections.<String,Object>emptyMap());}};
        SectionRenderer renderer=new SectionRenderer(){public String getId(){return "MANUAL";}public boolean supports(String id){return getId().equals(id);}public ReportSectionData render(ReportContext c,ReportSectionConfig s,ReportSectionData d){return d;}};
        when(menus.selectMenuPermsByUserId(1001L)).thenReturn(Collections.singleton("lab:report:sensitive"));ReportJobDispatcher downstream=mock(ReportJobDispatcher.class);
        ReportGenerationWorker worker=new ReportGenerationWorker(mapper,lock(),new TrustedReportContextFactory(access,menus),new DataSourceProviderRegistry(Collections.singletonList(provider)),new SectionRendererRegistry(Collections.singletonList(renderer)),new ReportExporterRegistry(Arrays.<ReportExporter>asList(new JsonReportExporter(),new MarkdownReportExporter())),store,new ReportDataCodec(),downstream,Clock.fixed(Instant.parse("2026-08-08T01:00:00Z"),ZoneOffset.UTC));

        worker.execute(63L);

        org.mockito.ArgumentCaptor<String> canonical=org.mockito.ArgumentCaptor.forClass(String.class);verify(mapper).completeJson(eq(31L),eq(63L),eq("run-token-1234567890"),canonical.capture(),any(),eq("1001"));
        assertTrue(canonical.getValue().contains("Pinned management decision"));verify(mapper,never()).selectSummaries(any(),any());verify(downstream).advance(eq(63L),eq(31L),eq("WORD"),eq("run-token-1234567890"),eq("1001"),any());
    }

    @Test
    void pdfFailureKeepsSuccessfulWordAndPersistsBoundedRetryableDiagnostics() {
        LabReportJob job=job(52L,"PDF");LabReportInstance report=draft(31L,0);report.setPdfStatus("PENDING");report.setWordPath("archive/report-31/report-31.docx");
        when(mapper.selectReportJobById(52L)).thenReturn(job);when(mapper.claimReportJob(any(),any(),any(),any(),any())).thenReturn(1);when(mapper.selectReportById(31L)).thenReturn(report);when(mapper.markPdfPending(31L,52L,"run-token-1234567890","1001")).thenReturn(1);when(mapper.failPdf(eq(31L),eq(52L),eq("run-token-1234567890"),any(),eq("1001"))).thenReturn(1);when(mapper.failReportJob(eq(52L),eq("run-token-1234567890"),any(),eq("1001"),any())).thenReturn(1);when(store.read(report.getWordPath(),"WORD")).thenReturn(new byte[]{1,2,3});
        LabProperties properties=new LabProperties();properties.setLibreOfficeExecutable("definitely-missing-office");
        ReportGenerationWorker worker=new ReportGenerationWorker(mapper,lock(),null,new DataSourceProviderRegistry(Collections.<DataSourceProvider>emptyList()),new SectionRendererRegistry(Collections.<SectionRenderer>emptyList()),new ReportExporterRegistry(Collections.<ReportExporter>singletonList(new PdfReportExporter(properties))),store,new ReportDataCodec(),mock(ReportJobDispatcher.class),Clock.fixed(Instant.parse("2026-08-08T01:00:00Z"),ZoneOffset.UTC));

        worker.execute(52L);

        verify(mapper).failPdf(eq(31L),eq(52L),eq("run-token-1234567890"),any(),eq("1001"));verify(mapper).failReportJob(eq(52L),eq("run-token-1234567890"),any(),eq("1001"),any());verify(mapper,never()).failWord(any(),any(),any(),any(),any());
    }

    @Test
    void artifactStoreRejectsTraversalAndCannotOverwritePublishedSuccess(@TempDir Path root) throws Exception {
        LabProperties properties=new LabProperties();properties.setOutputDirectory(root.resolve("reports").toString());properties.setTempDirectory(root.resolve("reports/tmp").toString());ReportArtifactStore actual=new ReportArtifactStore(properties);
        String relative=actual.publish(9L,91L,"run-token-11111111","report-9","JSON","{}".getBytes(StandardCharsets.UTF_8));assertTrue(Files.isRegularFile(actual.resolve(relative,"JSON")));
        assertThrows(ServiceException.class,()->actual.publish(9L,91L,"run-token-11111111","report-9","JSON","new".getBytes(StandardCharsets.UTF_8)));
        assertThrows(ServiceException.class,()->actual.resolve("../secret.json","JSON"));
    }

    @Test
    void artifactStoreDeletesOnlyOldUnreferencedRunDirectories(@TempDir Path root) throws Exception {
        LabProperties properties=new LabProperties();properties.setOutputDirectory(root.resolve("reports").toString());properties.setTempDirectory(root.resolve("reports/tmp").toString());ReportArtifactStore actual=new ReportArtifactStore(properties);String referenced=actual.publish(9L,91L,"run-token-reference-1111","report-9","JSON","{}".getBytes(StandardCharsets.UTF_8));String orphan=actual.publish(10L,101L,"run-token-orphan-222222","report-10","JSON","{}".getBytes(StandardCharsets.UTF_8));Path referencedFile=actual.resolve(referenced,"JSON");Path orphanFile=actual.resolve(orphan,"JSON");java.nio.file.attribute.FileTime old=java.nio.file.attribute.FileTime.from(Instant.parse("2026-07-01T00:00:00Z"));Files.setLastModifiedTime(referencedFile,old);Files.setLastModifiedTime(orphanFile,old);Files.setLastModifiedTime(referencedFile.getParent(),old);Files.setLastModifiedTime(orphanFile.getParent(),old);

        assertEquals(1,actual.cleanOrphanRuns(Collections.singletonList(referenced),Instant.parse("2026-08-01T00:00:00Z")));

        assertTrue(Files.exists(referencedFile));assertTrue(!Files.exists(orphanFile.getParent()));
    }

    @Test
    void artifactCleanupDoesNotDeleteANewEmptyRunDirectory(@TempDir Path root) throws Exception {
        LabProperties properties=new LabProperties();properties.setOutputDirectory(root.resolve("reports").toString());properties.setTempDirectory(root.resolve("reports/tmp").toString());ReportArtifactStore actual=new ReportArtifactStore(properties);
        Path activeRun=root.resolve("reports/archive/report-11/runs/run-token-active-3333");Files.createDirectories(activeRun);

        assertEquals(0,actual.cleanOrphanRuns(Collections.<String>emptyList(),Instant.parse("2026-08-01T00:00:00Z")));

        assertTrue(Files.isDirectory(activeRun),"cleanup must not race the publish window between createDirectories and atomic move");
    }

    @Test
    void artifactStoreDeletesOldUnreferencedFilesInsideARetainedRun(@TempDir Path root) throws Exception {
        LabProperties properties=new LabProperties();properties.setOutputDirectory(root.resolve("reports").toString());properties.setTempDirectory(root.resolve("reports/tmp").toString());ReportArtifactStore actual=new ReportArtifactStore(properties);
        String referenced=actual.publish(9L,91L,"run-token-mixed-111111","report-9","JSON","{}".getBytes(StandardCharsets.UTF_8));
        String orphan=actual.publish(9L,91L,"run-token-mixed-111111","report-9-extra","MARKDOWN","orphan".getBytes(StandardCharsets.UTF_8));
        Path referencedFile=actual.resolve(referenced,"JSON");Path orphanFile=actual.resolve(orphan,"MARKDOWN");java.nio.file.attribute.FileTime old=java.nio.file.attribute.FileTime.from(Instant.parse("2026-07-01T00:00:00Z"));Files.setLastModifiedTime(referencedFile,old);Files.setLastModifiedTime(orphanFile,old);Files.setLastModifiedTime(orphanFile.getParent(),old);

        assertEquals(1,actual.cleanOrphanRuns(Collections.singletonList(referenced),Instant.parse("2026-08-01T00:00:00Z")));

        assertTrue(Files.exists(referencedFile));assertTrue(!Files.exists(orphanFile));assertTrue(Files.exists(referencedFile.getParent()));
    }

    @Test
    void recoveredSuccessfulDataStepQueuesWordWithoutOverwritingArtifacts() {
        LabReportJob job=job(53L,"DATA");LabReportInstance report=draft(31L,0);
        when(mapper.selectReportJobById(53L)).thenReturn(job);when(mapper.claimReportJob(any(),any(),any(),any(),any())).thenReturn(1);when(mapper.selectReportById(31L)).thenReturn(report);
        ReportJobDispatcher downstream=mock(ReportJobDispatcher.class);
        ReportGenerationWorker worker=new ReportGenerationWorker(mapper,lock(),null,new DataSourceProviderRegistry(Collections.<DataSourceProvider>emptyList()),new SectionRendererRegistry(Collections.<SectionRenderer>emptyList()),new ReportExporterRegistry(Collections.<ReportExporter>emptyList()),store,new ReportDataCodec(),downstream,Clock.fixed(Instant.parse("2026-08-08T01:00:00Z"),ZoneOffset.UTC));

        worker.execute(53L);

        verify(mapper,never()).markDataPending(any(),any(),any(),any());verify(store,never()).publish(any(),any(),any(),any(),any(),any());
        verify(downstream).advance(eq(53L),eq(31L),eq("WORD"),eq("run-token-1234567890"),eq("1001"),any());
    }

    @Test
    void progressionFailureLeavesRunningJobForStaleRecoveryInsteadOfBreakingTheChain() {
        LabReportJob job=job(55L,"DATA");LabReportInstance report=draft(31L,0);when(mapper.selectReportJobById(55L)).thenReturn(job);when(mapper.claimReportJob(any(),any(),any(),any(),any())).thenReturn(1);when(mapper.selectReportById(31L)).thenReturn(report);
        ReportJobDispatcher downstream=mock(ReportJobDispatcher.class);org.mockito.Mockito.doThrow(new ServiceException("database unavailable")).when(downstream).advance(any(),any(),any(),any(),any(),any());
        ReportGenerationWorker worker=new ReportGenerationWorker(mapper,lock(),null,new DataSourceProviderRegistry(Collections.<DataSourceProvider>emptyList()),new SectionRendererRegistry(Collections.<SectionRenderer>emptyList()),new ReportExporterRegistry(Collections.<ReportExporter>emptyList()),store,new ReportDataCodec(),downstream,Clock.fixed(Instant.parse("2026-08-08T01:00:00Z"),ZoneOffset.UTC));

        worker.execute(55L);

        verify(mapper,never()).failReportJob(any(),any(),any(),any(),any());verify(mapper,never()).failData(any(),any(),any(),any(),any());
    }

    @Test
    void redisRenewalFailureStopsTheRunBeforeItCanPublishOrWriteFailureState() throws Exception {
        LabReportJob job=job(57L,"WORD");LabReportInstance report=draft(31L,0);report.setWordStatus("PENDING");
        ReportContext persistedContext=new ReportContext("2026-07","ALL",11L,Instant.parse("2026-08-08T01:00:00Z"),Collections.<String,Object>emptyMap());
        report.setContentJson(new ReportDataCodec().encode(new com.ailab.system.report.model.ReportData(persistedContext,"monthly",6,Collections.<ReportSectionData>emptyList(),Collections.<String,Object>emptyMap())));
        when(mapper.selectReportJobById(57L)).thenReturn(job);when(mapper.claimReportJob(any(),any(),any(),any(),any())).thenReturn(1);when(mapper.selectReportById(31L)).thenReturn(report);when(mapper.markWordPending(31L,57L,"run-token-1234567890","1001")).thenReturn(1);
        final AtomicReference<Runnable> heartbeat=new AtomicReference<Runnable>();ScheduledExecutorService executor=mock(ScheduledExecutorService.class);
        org.mockito.Mockito.doAnswer(call->{heartbeat.set((Runnable)call.getArgument(0));return mock(ScheduledFuture.class);}).when(executor).scheduleAtFixedRate(any(Runnable.class),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyLong(),eq(TimeUnit.SECONDS));
        ReportJobLock lost=new ReportJobLock(){public String tryAcquire(Long id,String step){return "run-token-1234567890";}public boolean renew(Long id,String step,String token){return false;}public void release(Long id,String step,String token){}};
        ReportExporter word=new ReportExporter(){public String getId(){return "WORD";}public boolean supports(String format){return "WORD".equals(format);}public byte[] export(com.ailab.system.report.model.ReportData value){heartbeat.get().run();return new byte[]{1};}};
        ReportGenerationWorker worker=new ReportGenerationWorker(mapper,lost,null,new DataSourceProviderRegistry(Collections.<DataSourceProvider>emptyList()),new SectionRendererRegistry(Collections.<SectionRenderer>emptyList()),new ReportExporterRegistry(Collections.singletonList(word)),store,new ReportDataCodec(),mock(ReportJobDispatcher.class),executor,Clock.fixed(Instant.parse("2026-08-08T01:00:00Z"),ZoneOffset.UTC));

        worker.execute(57L);

        verify(store,never()).publish(any(),any(),any(),any(),any(),any());verify(mapper,never()).completeWord(any(),any(),any(),any(),any());verify(mapper,never()).failWord(any(),any(),any(),any(),any());verify(mapper,never()).failReportJob(any(),any(),any(),any(),any());
    }

    @Test
    void staleWorkerDeletesOnlyItsOwnUncommittedPathWhenFenceRejectsCompletion() throws Exception {
        LabReportJob job=job(58L,"WORD");LabReportInstance report=draft(31L,0);report.setWordStatus("PENDING");
        ReportContext persistedContext=new ReportContext("2026-07","ALL",11L,Instant.parse("2026-08-08T01:00:00Z"),Collections.<String,Object>emptyMap());
        report.setContentJson(new ReportDataCodec().encode(new com.ailab.system.report.model.ReportData(persistedContext,"monthly",6,Collections.<ReportSectionData>emptyList(),Collections.<String,Object>emptyMap())));
        when(mapper.selectReportJobById(58L)).thenReturn(job);when(mapper.claimReportJob(any(),any(),any(),any(),any())).thenReturn(1);when(mapper.selectReportById(31L)).thenReturn(report);when(mapper.markWordPending(31L,58L,"run-token-1234567890","1001")).thenReturn(1);
        when(store.publish(eq(31L),eq(58L),eq("run-token-1234567890"),eq("report-31"),eq("WORD"),any())).thenReturn("archive/report-31/runs/run-token-1234567890/report-31.docx");
        ReportExporter word=new ReportExporter(){public String getId(){return "WORD";}public boolean supports(String format){return "WORD".equals(format);}public byte[] export(com.ailab.system.report.model.ReportData value){return new byte[]{1};}};
        ReportGenerationWorker worker=new ReportGenerationWorker(mapper,lock(),null,new DataSourceProviderRegistry(Collections.<DataSourceProvider>emptyList()),new SectionRendererRegistry(Collections.<SectionRenderer>emptyList()),new ReportExporterRegistry(Collections.singletonList(word)),store,new ReportDataCodec(),mock(ReportJobDispatcher.class),Clock.fixed(Instant.parse("2026-08-08T01:00:00Z"),ZoneOffset.UTC));

        worker.execute(58L);

        verify(store).deleteUncommitted("archive/report-31/runs/run-token-1234567890/report-31.docx");verify(mapper,never()).failWord(any(),any(),any(),any(),any());verify(mapper,never()).failReportJob(any(),any(),any(),any(),any());
    }

    @Test
    void ambiguousDatabaseCompletionFailureNeverDeletesAPossiblyReferencedArtifact() throws Exception {
        LabReportJob job=job(60L,"WORD");LabReportInstance report=draft(31L,0);report.setWordStatus("PENDING");
        ReportContext persistedContext=new ReportContext("2026-07","ALL",11L,Instant.parse("2026-08-08T01:00:00Z"),Collections.<String,Object>emptyMap());
        report.setContentJson(new ReportDataCodec().encode(new com.ailab.system.report.model.ReportData(persistedContext,"monthly",6,Collections.<ReportSectionData>emptyList(),Collections.<String,Object>emptyMap())));
        when(mapper.selectReportJobById(60L)).thenReturn(job);when(mapper.claimReportJob(any(),any(),any(),any(),any())).thenReturn(1);when(mapper.selectReportById(31L)).thenReturn(report);when(mapper.markWordPending(31L,60L,"run-token-1234567890","1001")).thenReturn(1);
        String path="archive/report-31/runs/run-token-1234567890/report-31.docx";when(store.publish(eq(31L),eq(60L),eq("run-token-1234567890"),eq("report-31"),eq("WORD"),any())).thenReturn(path);
        when(mapper.completeWord(31L,60L,"run-token-1234567890",path,"1001")).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("commit outcome unknown"));
        ReportExporter word=new ReportExporter(){public String getId(){return "WORD";}public boolean supports(String format){return "WORD".equals(format);}public byte[] export(com.ailab.system.report.model.ReportData value){return new byte[]{1};}};
        ReportGenerationWorker worker=new ReportGenerationWorker(mapper,lock(),null,new DataSourceProviderRegistry(Collections.<DataSourceProvider>emptyList()),new SectionRendererRegistry(Collections.<SectionRenderer>emptyList()),new ReportExporterRegistry(Collections.singletonList(word)),store,new ReportDataCodec(),mock(ReportJobDispatcher.class),Clock.fixed(Instant.parse("2026-08-08T01:00:00Z"),ZoneOffset.UTC));

        worker.execute(60L);

        verify(store,never()).deleteUncommitted(path);
        verify(mapper,never()).failWord(any(),any(),any(),any(),any());
        verify(mapper,never()).failReportJob(any(),any(),any(),any(),any());
    }

    @Test
    void persistedUserErrorUsesAWhitelistedCodeAndNeverLeaksTheInternalException() throws Exception {
        LabReportJob job=job(59L,"WORD");LabReportInstance report=draft(31L,0);report.setWordStatus("PENDING");
        ReportContext persistedContext=new ReportContext("2026-07","ALL",11L,Instant.parse("2026-08-08T01:00:00Z"),Collections.<String,Object>emptyMap());
        report.setContentJson(new ReportDataCodec().encode(new com.ailab.system.report.model.ReportData(persistedContext,"monthly",6,Collections.<ReportSectionData>emptyList(),Collections.<String,Object>emptyMap())));
        when(mapper.selectReportJobById(59L)).thenReturn(job);when(mapper.claimReportJob(any(),any(),any(),any(),any())).thenReturn(1);when(mapper.selectReportById(31L)).thenReturn(report);when(mapper.markWordPending(31L,59L,"run-token-1234567890","1001")).thenReturn(1);when(mapper.failWord(eq(31L),eq(59L),eq("run-token-1234567890"),any(),eq("1001"))).thenReturn(1);when(mapper.failReportJob(eq(59L),eq("run-token-1234567890"),any(),eq("1001"),any())).thenReturn(1);
        ReportExporter word=new ReportExporter(){public String getId(){return "WORD";}public boolean supports(String format){return "WORD".equals(format);}public byte[] export(com.ailab.system.report.model.ReportData value)throws java.io.IOException{throw new java.io.IOException("jdbc:mysql://private-host password=hunter2");}};
        ReportGenerationWorker worker=new ReportGenerationWorker(mapper,lock(),null,new DataSourceProviderRegistry(Collections.<DataSourceProvider>emptyList()),new SectionRendererRegistry(Collections.<SectionRenderer>emptyList()),new ReportExporterRegistry(Collections.singletonList(word)),store,new ReportDataCodec(),mock(ReportJobDispatcher.class),Clock.fixed(Instant.parse("2026-08-08T01:00:00Z"),ZoneOffset.UTC));

        worker.execute(59L);

        org.mockito.ArgumentCaptor<String> error=org.mockito.ArgumentCaptor.forClass(String.class);verify(mapper).failWord(eq(31L),eq(59L),eq("run-token-1234567890"),error.capture(),eq("1001"));assertTrue(error.getValue().startsWith("REPORT_")&&!error.getValue().contains("private-host")&&!error.getValue().contains("hunter2"));
    }

    @Test
    void partialDataRetryRegeneratesOnlyFailedMarkdownFromSuccessfulCanonicalJson() {
        LabReportJob job=job(54L,"DATA");LabReportInstance report=draft(31L,0);report.setMarkdownStatus("FAILED");
        ReportContext persistedContext=new ReportContext("2026-07","ALL",11L,Instant.parse("2026-08-08T01:00:00Z"),Collections.<String,Object>emptyMap());
        report.setContentJson(new ReportDataCodec().encode(new com.ailab.system.report.model.ReportData(persistedContext,"monthly",6,Collections.<ReportSectionData>emptyList(),Collections.<String,Object>emptyMap())));
        when(mapper.selectReportJobById(54L)).thenReturn(job);when(mapper.claimReportJob(any(),any(),any(),any(),any())).thenReturn(1);when(mapper.selectReportById(31L)).thenReturn(report);when(mapper.markDataPending(31L,54L,"run-token-1234567890","1001")).thenReturn(1);when(mapper.completeMarkdown(eq(31L),eq(54L),eq("run-token-1234567890"),any(),any(),eq("1001"))).thenReturn(1);when(store.publish(eq(31L),eq(54L),eq("run-token-1234567890"),eq("report-31"),eq("MARKDOWN"),any())).thenReturn("archive/report-31/runs/run-token-1234567890/report-31.md");
        ReportJobDispatcher downstream=mock(ReportJobDispatcher.class);
        ReportGenerationWorker worker=new ReportGenerationWorker(mapper,lock(),null,new DataSourceProviderRegistry(Collections.<DataSourceProvider>emptyList()),new SectionRendererRegistry(Collections.<SectionRenderer>emptyList()),new ReportExporterRegistry(Collections.<ReportExporter>singletonList(new MarkdownReportExporter())),store,new ReportDataCodec(),downstream,Clock.fixed(Instant.parse("2026-08-08T01:00:00Z"),ZoneOffset.UTC));

        worker.execute(54L);

        verify(mapper,never()).completeJson(any(),any(),any(),any(),any(),any());verify(mapper).completeMarkdown(eq(31L),eq(54L),eq("run-token-1234567890"),any(),eq("archive/report-31/runs/run-token-1234567890/report-31.md"),eq("1001"));verify(downstream).advance(eq(54L),eq(31L),eq("WORD"),eq("run-token-1234567890"),eq("1001"),any());
    }

    @Test
    void generationCreatesDraftAndQueueInOneShortTransaction() throws Exception {
        assertTrue(LabReportServiceImpl.class.getMethod("generate",Long.class,String.class,String.class,Long.class)
                .isAnnotationPresent(Transactional.class));
    }

    @Test
    void markdownImportAndItsArtifactReconciliationJobShareOneRollbackBoundary() throws Exception {
        ReportGenerationOrchestrator imports=mock(ReportGenerationOrchestrator.class);ReportJobDispatcher jobs=mock(ReportJobDispatcher.class);
        LabReportInstance imported=draft(88L,0);imported.setJsonStatus("SUCCESS");imported.setMarkdownStatus("SUCCESS");imported.setWordStatus("PENDING");imported.setPdfStatus("NOT_REQUESTED");when(imports.importMarkdown(31L,"# edited",1001L)).thenReturn(imported);
        when(jobs.queue(88L,"DATA","1001")).thenThrow(new ServiceException("queue unavailable"));
        LabReportServiceImpl service=new LabReportServiceImpl(mapper,access,menus,imports,jobs);

        assertThrows(ServiceException.class,()->service.importMarkdown(31L,"edit.md","# edited".getBytes(StandardCharsets.UTF_8),1001L));

        assertTrue(LabReportServiceImpl.class.getMethod("importMarkdown",Long.class,String.class,byte[].class,Long.class).isAnnotationPresent(Transactional.class));
        verify(mapper,never()).failWord(any(),any(),any(),any(),any());
    }

    @Test
    void manualImportDataJobPublishesPersistedJsonAndRawMarkdownWhenPathsAreMissing() {
        LabReportJob job=job(61L,"DATA");LabReportInstance report=draft(31L,0);report.setSourceType("MANUAL_IMPORT");report.setJsonPath(null);report.setMarkdownPath(null);report.setContentMarkdown("# Edited exactly");
        ReportContext context=new ReportContext("2026-07","ALL",11L,Instant.parse("2026-08-08T01:00:00Z"),Collections.<String,Object>emptyMap());report.setContentJson(new ReportDataCodec().encode(new com.ailab.system.report.model.ReportData(context,"monthly",6,Collections.<ReportSectionData>emptyList(),Collections.<String,Object>emptyMap())));
        when(mapper.selectReportJobById(61L)).thenReturn(job);when(mapper.claimReportJob(any(),any(),any(),any(),any())).thenReturn(1);when(mapper.selectReportById(31L)).thenReturn(report);when(mapper.markDataPending(31L,61L,"run-token-1234567890","1001")).thenReturn(1);
        when(store.publish(eq(31L),eq(61L),eq("run-token-1234567890"),eq("report-31"),eq("JSON"),any())).thenReturn("archive/report-31/runs/run-token-1234567890/report-31.json");when(store.publish(eq(31L),eq(61L),eq("run-token-1234567890"),eq("report-31"),eq("MARKDOWN"),any())).thenReturn("archive/report-31/runs/run-token-1234567890/report-31.md");
        when(mapper.completeJson(eq(31L),eq(61L),eq("run-token-1234567890"),any(),any(),eq("1001"))).thenReturn(1);when(mapper.completeMarkdown(eq(31L),eq(61L),eq("run-token-1234567890"),any(),any(),eq("1001"))).thenReturn(1);
        ReportJobDispatcher downstream=mock(ReportJobDispatcher.class);ReportGenerationWorker worker=new ReportGenerationWorker(mapper,lock(),null,new DataSourceProviderRegistry(Collections.<DataSourceProvider>emptyList()),new SectionRendererRegistry(Collections.<SectionRenderer>emptyList()),new ReportExporterRegistry(Collections.<ReportExporter>singletonList(new JsonReportExporter())),store,new ReportDataCodec(),downstream,Clock.fixed(Instant.parse("2026-08-08T01:00:00Z"),ZoneOffset.UTC));

        worker.execute(61L);

        verify(mapper).completeJson(eq(31L),eq(61L),eq("run-token-1234567890"),eq(report.getContentJson()),eq("archive/report-31/runs/run-token-1234567890/report-31.json"),eq("1001"));
        verify(mapper).completeMarkdown(31L,61L,"run-token-1234567890","# Edited exactly","archive/report-31/runs/run-token-1234567890/report-31.md","1001");
        verify(downstream).advance(eq(61L),eq(31L),eq("WORD"),eq("run-token-1234567890"),eq("1001"),any());
    }

    @Test
    void markdownImportCannotExceedTheWordTextBudget() {
        ReportGenerationOrchestrator imports=mock(ReportGenerationOrchestrator.class);LabReportServiceImpl service=new LabReportServiceImpl(mapper,access,menus,imports,mock(ReportJobDispatcher.class));
        byte[] oversized=new byte[1024*1024+1];java.util.Arrays.fill(oversized,(byte)'a');
        assertThrows(ServiceException.class,()->service.importMarkdown(31L,"edit.md",oversized,1001L));verify(imports,never()).importMarkdown(any(),any(),any());
    }

    @Test
    void summaryUpsertReturnsTheDatabaseIdentityAndIncrementedRevision() {
        LabReportServiceImpl service=new LabReportServiceImpl(mapper,access,menus,orchestrator,mock(ReportJobDispatcher.class));LabReportSummary input=new LabReportSummary();input.setPeriod("2026-07");input.setBizLine("ALL");input.setSectionCode("EXEC");input.setSummaryText("done");LabReportSummary stored=new LabReportSummary();stored.setId(77L);stored.setPeriod("2026-07");stored.setBizLine("ALL");stored.setSectionCode("EXEC");stored.setSummaryText("done");stored.setSourceRevision(5);when(mapper.upsertSummary(any(LabReportSummary.class))).thenReturn(2);when(mapper.selectSummary("2026-07","ALL","EXEC")).thenReturn(stored);
        LabReportSummary result=service.saveSummary(input,1001L);
        assertEquals(77L,result.getId());assertEquals(5,result.getSourceRevision());
    }

    @Test
    void summaryApiAcceptsEveryCanonicalReportPeriod() {
        LabReportServiceImpl service=new LabReportServiceImpl(mapper,access,menus,orchestrator,mock(ReportJobDispatcher.class));LabReportSummary input=new LabReportSummary();input.setPeriod("2026Q3");input.setBizLine("ALL");input.setSectionCode("EXEC");input.setSummaryText("quarterly summary");LabReportSummary stored=new LabReportSummary();stored.setId(78L);stored.setPeriod("2026Q3");stored.setBizLine("ALL");stored.setSectionCode("EXEC");stored.setSourceRevision(1);when(mapper.upsertSummary(any(LabReportSummary.class))).thenReturn(1);when(mapper.selectSummary("2026Q3","ALL","EXEC")).thenReturn(stored);

        assertEquals(78L,service.saveSummary(input,1001L).getId());
    }

    @Test
    void summaryJsonIsStreamBudgetedBeforeTreeMaterialization() {
        LabReportServiceImpl service=new LabReportServiceImpl(mapper,access,menus,orchestrator,mock(ReportJobDispatcher.class));StringBuilder deep=new StringBuilder();for(int i=0;i<70;i++)deep.append("{\"x\":");deep.append('0');for(int i=0;i<70;i++)deep.append('}');LabReportSummary input=new LabReportSummary();input.setPeriod("2026-07");input.setBizLine("ALL");input.setSectionCode("EXEC");input.setSummaryText("rendered");input.setSummaryJson(deep.toString());

        assertThrows(ServiceException.class,()->service.saveSummary(input,1001L));

        verify(mapper,never()).upsertSummary(any());
    }

    @Test
    void reportHistoryDtoProjectionPreservesTheMapperPageTotal() {
        com.github.pagehelper.Page<LabReportInstance> mapped=new com.github.pagehelper.Page<LabReportInstance>(2,10);mapped.setTotal(37L);mapped.add(draft(31L,0));when(mapper.selectReportHistory(null,null,true,false)).thenReturn(mapped);when(menus.selectMenuPermsByUserId(1001L)).thenReturn(Collections.<String>emptySet());LabReportServiceImpl service=new LabReportServiceImpl(mapper,access,menus,orchestrator,mock(ReportJobDispatcher.class));

        java.util.List<com.ailab.system.dto.ReportStatusView> result=service.history(null,null,1001L);

        assertTrue(result instanceof com.github.pagehelper.Page);assertEquals(37L,((com.github.pagehelper.Page<?>)result).getTotal());
    }

    @Test
    void artifactFailurePersistenceErrorLeavesTheJobRunningForRecovery() throws Exception {
        LabReportJob job=job(62L,"WORD");LabReportInstance report=draft(31L,0);report.setWordStatus("PENDING");ReportContext context=new ReportContext("2026-07","ALL",11L,Instant.parse("2026-08-08T01:00:00Z"),Collections.<String,Object>emptyMap());report.setContentJson(new ReportDataCodec().encode(new com.ailab.system.report.model.ReportData(context,"monthly",6,Collections.<ReportSectionData>emptyList(),Collections.<String,Object>emptyMap())));
        when(mapper.selectReportJobById(62L)).thenReturn(job);when(mapper.claimReportJob(any(),any(),any(),any(),any())).thenReturn(1);when(mapper.selectReportById(31L)).thenReturn(report);when(mapper.markWordPending(31L,62L,"run-token-1234567890","1001")).thenReturn(1);when(mapper.failWord(eq(31L),eq(62L),eq("run-token-1234567890"),any(),eq("1001"))).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("temporary write failure"));
        ReportExporter word=new ReportExporter(){public String getId(){return "WORD";}public boolean supports(String format){return "WORD".equals(format);}public byte[] export(com.ailab.system.report.model.ReportData value)throws java.io.IOException{throw new java.io.IOException("export failed");}};ReportGenerationWorker worker=new ReportGenerationWorker(mapper,lock(),null,new DataSourceProviderRegistry(Collections.<DataSourceProvider>emptyList()),new SectionRendererRegistry(Collections.<SectionRenderer>emptyList()),new ReportExporterRegistry(Collections.singletonList(word)),store,new ReportDataCodec(),mock(ReportJobDispatcher.class),Clock.fixed(Instant.parse("2026-08-08T01:00:00Z"),ZoneOffset.UTC));

        worker.execute(62L);

        verify(mapper,never()).failReportJob(any(),any(),any(),any(),any());
    }

    @Test
    void tempCleanupEligibilityFailsClosedForUnknownOrActiveReports() {
        LabReportTempFileEligibilityImpl eligibility=new LabReportTempFileEligibilityImpl(mapper);
        assertTrue(!eligibility.isDeletionEligible(java.nio.file.Paths.get("unknown/file.part")));
        LabReportJob terminal=job(77L,"PDF");terminal.setJobStatus("SUCCESS");when(mapper.selectReportById(31L)).thenReturn(draft(31L,0));when(mapper.selectReportJobById(77L)).thenReturn(terminal);when(mapper.countActiveReportJobs(31L)).thenReturn(1);
        java.nio.file.Path residue=java.nio.file.Paths.get("lo-report-31-job-77-run-token-1234567890-12345/out/report.pdf");assertTrue(!eligibility.isDeletionEligible(residue));
        when(mapper.countActiveReportJobs(31L)).thenReturn(0);assertTrue(eligibility.isDeletionEligible(residue));
        java.nio.file.Path artifactResidue=java.nio.file.Paths.get("report-31-job-77-run-run-token-1234567890/report-31-1.part");assertTrue(eligibility.isDeletionEligible(artifactResidue));
    }

    private LabReportJob job(Long id,String step){LabReportJob value=new LabReportJob();value.setId(id);value.setReportId(31L);value.setJobType(step);value.setJobStatus("QUEUED");value.setVersion(0);value.setCreateBy("1001");return value;}
    private ReportJobLock lock(){return new ReportJobLock(){public String tryAcquire(Long reportId,String step){return "run-token-1234567890";}public void release(Long reportId,String step,String token){}};}

    private LabReportTemplate template() { LabReportTemplate value = new LabReportTemplate(); value.setId(7L);
        value.setTemplateCode("monthly"); value.setTemplateName("Monthly"); value.setPeriodType("MONTH");
        value.setRevisionNo(6); value.setLatestFlag("1"); value.setStatus("ENABLED"); value.setVersion(2);value.setHeaderJson("{}");value.setStyleJson("{}"); return value; }
    private LabReportSection section(String code, boolean sensitive) { LabReportSection value = new LabReportSection();
        value.setSectionCode(code); value.setSectionName(code); value.setSectionType("TEXT"); value.setSortNo(10);
        value.setDataSource(sensitive ? "PERF_SUMMARY" : "GOAL_PROGRESS"); value.setQueryConfigJson("{}");
        value.setRenderConfigJson("{}"); value.setStyleConfigJson("{}"); value.setManualFlag("0"); value.setVisibleFlag("1");
        value.setSensitiveFlag(sensitive ? "1" : "0"); value.setSensitivePermission(sensitive ? "lab:report:sensitive" : null); return value; }
    private LabReportInstance draft(Long id, int version) { LabReportInstance value = new LabReportInstance(); value.setId(id);
        value.setTemplateId(7L); value.setTemplateCode("monthly"); value.setTemplateRevision(6); value.setPeriod("2026-07");
        value.setBizLine("ALL"); value.setRevisionNo(4); value.setLifecycleStatus("DRAFT"); value.setCurrentFlag("0");
        value.setFinalFlag("0"); value.setSensitiveFlag("0"); value.setSourceType("AUTO"); value.setSourcePerfRevision(3);
        value.setSourceDataJson("{\"performancePins\":[]}");
        value.setContentJson("{}"); value.setContentMarkdown("# report"); value.setJsonStatus("SUCCESS");
        value.setJsonPath("archive/report-"+id+"/report.json");value.setMarkdownStatus("SUCCESS");value.setMarkdownPath("archive/report-"+id+"/report.md");value.setWordStatus("SUCCESS");value.setWordPath("archive/report-"+id+"/report.docx");value.setPdfStatus("SUCCESS");value.setPdfPath("archive/report-"+id+"/report.pdf");value.setVersion(version); return value; }
}
