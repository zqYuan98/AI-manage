package com.ailab.system.report;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailab.system.domain.LabReportSection;
import com.ailab.system.report.config.ReportSectionConfig;
import com.ailab.system.report.model.ReportContext;
import com.ailab.system.report.model.ReportData;
import com.ailab.system.report.model.ReportDataBudget;
import com.ailab.system.report.model.ReportDataCodec;
import com.ailab.system.report.model.ReportQueryCriteria;
import com.ailab.system.report.model.ReportSectionData;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportHardeningTest {
    @Test
    void aggregateRowsAreBoundedAcrossAllSectionsBeforeExport() {
        List<Map<String,Object>> rows=new ArrayList<Map<String,Object>>();
        for(int i=0;i<5000;i++)rows.add(Collections.<String,Object>singletonMap("id",Integer.valueOf(i)));
        List<ReportSectionData> sections=new ArrayList<ReportSectionData>();
        for(int i=0;i<6;i++)sections.add(new ReportSectionData("S"+i,"TABLE","Section",rows,Collections.<String,Object>emptyMap()));
        ReportContext context=new ReportContext("2026-07","ALL",1L,Instant.parse("2026-08-08T01:00:00Z"),Collections.<String,Object>emptyMap());
        assertThrows(IllegalArgumentException.class,()->new ReportData(context,"monthly",1,sections,Collections.<String,Object>emptyMap()));
    }

    @Test
    void canonicalJsonIsStreamBudgetedBeforeTreeMaterialization() throws Exception {
        StringBuilder deep=new StringBuilder();for(int i=0;i<70;i++)deep.append("{\"x\":");deep.append("0");for(int i=0;i<70;i++)deep.append('}');
        assertThrows(IllegalArgumentException.class,()->new ReportDataCodec().decode(deep.toString()));
        String source=new String(Files.readAllBytes(java.nio.file.Paths.get("src/main/java/com/ailab/system/report/model/ReportDataCodec.java")),StandardCharsets.UTF_8);
        assertTrue(source.contains("validateJsonStream")&&source.indexOf("validateJsonStream")<source.indexOf("JSON.readTree(source)"),"stream budgets must execute before readTree");
    }

    @Test
    void publicStatusAndJobDtosExcludeBodiesPathsPinsFencesAndRawErrors() throws Exception {
        java.util.Set<String> status=new java.util.HashSet<String>();for(java.lang.reflect.Method method:com.ailab.system.dto.ReportStatusView.class.getMethods())status.add(method.getName());
        assertTrue(!status.contains("getContentJson")&&!status.contains("getContentMarkdown")&&!status.contains("getSourceDataJson")&&!status.contains("getJsonPath")&&!status.contains("getWordPath")&&!status.contains("getJsonError"));
        java.util.Set<String> jobs=new java.util.HashSet<String>();for(java.lang.reflect.Method method:com.ailab.system.dto.ReportJobView.class.getMethods())jobs.add(method.getName());
        assertTrue(!jobs.contains("getRunToken")&&!jobs.contains("getIdempotencyKey")&&!jobs.contains("getErrorMessage"));
        assertTrue(com.ailab.system.service.LabReportService.class.getMethod("body",Long.class,Long.class).getReturnType().equals(com.ailab.system.dto.ReportBodyView.class));
        assertTrue(com.ailab.system.controller.LabReportController.class.getMethod("body",Long.class,javax.servlet.http.HttpServletResponse.class).getReturnType().equals(com.ruoyi.common.core.domain.AjaxResult.class));
        String controller=new String(Files.readAllBytes(java.nio.file.Paths.get("src/main/java/com/ailab/system/controller/LabReportController.java")),StandardCharsets.UTF_8);
        int bodyStart=controller.indexOf("public AjaxResult body");int bodyEnd=controller.indexOf("@PreAuthorize",bodyStart+1);String bodyMethod=controller.substring(bodyStart,bodyEnd);
        assertTrue(bodyMethod.contains("preventCaching(response)"),"the sensitive online body endpoint must explicitly disable caching");
        for(String signature:new String[]{"public TableDataInfo history","public AjaxResult status","public AjaxResult jobs"}){int start=controller.indexOf(signature);int end=controller.indexOf("@PreAuthorize",start+1);assertTrue(start>=0&&controller.substring(start,end).contains("preventCaching(response)"),signature+" must disable caching so every sensitive read is reauthorized");}
        assertTrue(controller.contains("private, no-store")&&controller.contains("no-cache")&&controller.contains("nosniff"));
    }

    @Test
    void heartbeatSchedulingIsIsolatedFromTheSaturatedExportExecutor() throws Exception {
        String worker=new String(Files.readAllBytes(java.nio.file.Paths.get("src/main/java/com/ailab/system/report/ReportGenerationWorker.java")),StandardCharsets.UTF_8);String config=new String(Files.readAllBytes(java.nio.file.Paths.get("src/main/java/com/ailab/system/config/ReportExecutorConfig.java")),StandardCharsets.UTF_8);
        assertTrue(worker.contains("@Qualifier(\"reportHeartbeatExecutor\")"),"export backlog must not starve durable lease heartbeats");
        assertTrue(config.contains("@Bean(name=\"reportHeartbeatExecutor\"")&&config.contains("ScheduledThreadPoolExecutor"));
    }

    @Test
    void controllerAndOrchestratorShareTheReservedManualMarkdownLimit() throws Exception {
        String controller=new String(Files.readAllBytes(java.nio.file.Paths.get("src/main/java/com/ailab/system/controller/LabReportController.java")),StandardCharsets.UTF_8);
        String orchestrator=new String(Files.readAllBytes(java.nio.file.Paths.get("src/main/java/com/ailab/system/report/ReportGenerationOrchestrator.java")),StandardCharsets.UTF_8);
        assertTrue(controller.contains("ReportDataBudget.manualMarkdownByteLimit()"));
        assertTrue(orchestrator.contains("ReportDataBudget.manualMarkdownByteLimit()"));
        assertTrue(com.ailab.system.report.model.ReportDataBudget.manualMarkdownByteLimit()<1024*1024);
    }

    @Test
    void reportHistoryUsesABoundedPageAndNeverSelectsCanonicalBodiesOrStorageInternals() throws Exception {
        String controller=new String(Files.readAllBytes(java.nio.file.Paths.get("src/main/java/com/ailab/system/controller/LabReportController.java")),StandardCharsets.UTF_8).replaceAll("\\s+","");
        assertTrue(controller.contains("MAX_HISTORY_PAGE_SIZE=100"));
        assertTrue(controller.contains("TableSupport.buildPageRequest()")&&controller.contains("getPageSize()>MAX_HISTORY_PAGE_SIZE"));
        assertTrue(controller.contains("PageHelper.startPage(page.getPageNum(),page.getPageSize()).setReasonable(page.getReasonable())")&&!controller.contains(";startPage();"),
                "history must ignore client orderByColumn so large hidden JSON columns cannot be sorted");
        String xml=new String(Files.readAllBytes(java.nio.file.Paths.get("src/main/resources/mapper/lab/LabReportMapper.xml")),StandardCharsets.UTF_8);
        int columnsStart=xml.indexOf("<sql id=\"reportHistoryColumns\"");int columnsEnd=xml.indexOf("</sql>",columnsStart);assertTrue(columnsStart>=0&&columnsEnd>columnsStart);
        String projection=xml.substring(columnsStart,columnsEnd);for(String forbidden:new String[]{"source_data_json","content_json","content_markdown","json_path","word_path","pdf_path","json_error","remark"})assertTrue(!projection.contains(forbidden),"history projection leaked "+forbidden);
        int historyStart=xml.indexOf("<select id=\"selectReportHistory\"");int historyEnd=xml.indexOf("</select>",historyStart);String history=xml.substring(historyStart,historyEnd);assertTrue(history.contains("refid=\"reportHistoryColumns\"")&&!history.contains("refid=\"reportColumns\""));
    }

    @Test
    void aggregateBudgetIsConsumedBeforeTheNextProviderQuery() throws Exception {
        List<Map<String,Object>> rows=new ArrayList<Map<String,Object>>();
        for(int i=0;i<5000;i++)rows.add(Collections.<String,Object>singletonMap("id",Integer.valueOf(i)));
        ReportContext context=new ReportContext("2026-07","ALL",1L,Instant.parse("2026-08-08T01:00:00Z"),Collections.<String,Object>emptyMap());
        ReportDataBudget.Accumulator budget=ReportDataBudget.accumulator(context);
        for(int i=0;i<5;i++)budget.accept(new ReportSectionData("S"+i,"TABLE","Section",rows,Collections.<String,Object>emptyMap()));
        assertEquals(1,budget.sourceFetchLimit(),"the next provider may fetch only one overflow sentinel row");
        assertThrows(IllegalArgumentException.class,()->budget.accept(new ReportSectionData("OVER","TABLE","Section",Collections.singletonList(Collections.<String,Object>singletonMap("id",1)),Collections.<String,Object>emptyMap())));

        Map<String,Object> attributes=new java.util.LinkedHashMap<String,Object>();
        attributes.put(ReportQueryCriteria.SOURCE_FETCH_LIMIT_ATTRIBUTE,Integer.valueOf(17));
        ReportContext limited=new ReportContext("2026-07","ALL",1L,Instant.parse("2026-08-08T01:00:00Z"),attributes);
        LabReportSection row=new LabReportSection();row.setSectionCode("DETAIL");row.setSectionName("Detail");row.setSectionType("TABLE");row.setDataSource("TASK_DETAIL");row.setQueryConfigJson("{}");row.setRenderConfigJson("{}");row.setStyleConfigJson("{}");row.setManualFlag("0");row.setVisibleFlag("1");row.setSensitiveFlag("0");
        assertEquals(17,ReportQueryCriteria.from(limited,new ReportSectionConfig(row)).getSourceFetchLimit());

        String worker=new String(Files.readAllBytes(java.nio.file.Paths.get("src/main/java/com/ailab/system/report/ReportGenerationWorker.java")),StandardCharsets.UTF_8).replaceAll("\\s+","");
        assertTrue(worker.indexOf("budget.accept(rendered)")<worker.indexOf("values.add(rendered)"),"each rendered section must consume the aggregate budget before it is retained");
    }

    @Test
    void everyDurableClaimAdvancesTheAttemptCounter() throws Exception {
        String xml=new String(Files.readAllBytes(java.nio.file.Paths.get("src/main/resources/mapper/lab/LabReportMapper.xml")),StandardCharsets.UTF_8).replaceAll("\\s+","").toLowerCase(java.util.Locale.ROOT);
        int start=xml.indexOf("<updateid=\"claimreportjob\"");int end=xml.indexOf("</update>",start);String claim=xml.substring(start,end);
        assertTrue(claim.contains("attempt_count=coalesce(attempt_count,0)+1"),"initial, legacy NULL, and recovered claims must have distinct attempt numbers");
        assertTrue(!claim.contains("casewhenattempt_count"));

        String sql=new String(Files.readAllBytes(java.nio.file.Paths.get("../sql/ailab.sql")),StandardCharsets.UTF_8).replace("`","").replaceAll("\\s+","").toLowerCase(java.util.Locale.ROOT);
        assertTrue(sql.contains("updatelab_report_jobsetattempt_count=0whereattempt_countisnull"));
        assertTrue(sql.contains("modifycolumnattempt_countintnotnulldefault0"));
    }

    @Test
    void sourceSnapshotsCannotBeEncodedBeyondTheManualTextBudgetTheyMustLaterDecode() {
        Map<String,String> summaries=new java.util.LinkedHashMap<String,String>();String value=String.join("",Collections.nCopies(65536,"x"));
        for(int i=0;i<9;i++)summaries.put("S"+i,value);
        ReportDataCodec codec=new ReportDataCodec();
        assertThrows(IllegalArgumentException.class,()->codec.encodeSourceSnapshot(Collections.<com.ailab.system.report.model.ReportPerformancePin>emptyList(),summaries));
        Map<String,String> escaped=new java.util.LinkedHashMap<String,String>();String control=String.join("",Collections.nCopies(65536,"\u0001"));
        for(int i=0;i<8;i++)escaped.put("C"+i,control);
        assertThrows(IllegalArgumentException.class,()->codec.encodeSourceSnapshot(Collections.<com.ailab.system.report.model.ReportPerformancePin>emptyList(),escaped));
    }
}
