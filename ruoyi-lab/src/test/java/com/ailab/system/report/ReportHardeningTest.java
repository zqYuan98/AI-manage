package com.ailab.system.report;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailab.system.report.model.ReportContext;
import com.ailab.system.report.model.ReportData;
import com.ailab.system.report.model.ReportDataCodec;
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
}
