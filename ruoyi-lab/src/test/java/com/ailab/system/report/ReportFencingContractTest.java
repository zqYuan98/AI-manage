package com.ailab.system.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailab.system.config.LabProperties;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Durable ownership contracts that prevent a recovered stale worker from publishing or committing. */
class ReportFencingContractTest {
    @Test
    void everyRunPublishesToAnIndependentImmutableOwnedPath(@TempDir Path root) throws Exception {
        LabProperties properties=new LabProperties();properties.setOutputDirectory(root.resolve("reports").toString());properties.setTempDirectory(root.resolve("reports/tmp").toString());
        ReportArtifactStore store=new ReportArtifactStore(properties);
        Method publish=ReportArtifactStore.class.getMethod("publish",Long.class,Long.class,String.class,String.class,String.class,byte[].class);

        String winner=(String)publish.invoke(store,9L,51L,"winner-token-123456","report-9","JSON","winner".getBytes(StandardCharsets.UTF_8));
        String loser=(String)publish.invoke(store,9L,52L,"loser-token-1234567","report-9","JSON","loser".getBytes(StandardCharsets.UTF_8));

        assertNotEquals(winner,loser);assertEquals("winner",new String(store.read(winner,"JSON"),StandardCharsets.UTF_8));
        store.deleteUncommitted(loser);assertTrue(Files.isRegularFile(store.resolve(winner,"JSON")),"loser cleanup must never delete the winner's artifact");
    }

    @Test
    void mapperSqlFencesEveryWorkerMutationAndRecoveryRechecksHeartbeat() throws Exception {
        String xml=new String(Files.readAllBytes(java.nio.file.Paths.get("src/main/resources/mapper/lab/LabReportMapper.xml")),StandardCharsets.UTF_8)
                .toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+"," ");
        assertTrue(xml.contains("property=\"runtoken\" column=\"run_token\""));
        for(String id:new String[]{"markdatapending","completejson","completemarkdown","faildata","markwordpending","completeword","failword","markpdfpending","completepdf","failpdf"}){
            String statement=statement(xml,"update",id);assertTrue(statement.contains("job_status='running'")&&statement.contains("run_token=#{runtoken}"),id+" must be fenced by the current run token");
        }
        for(String id:new String[]{"heartbeatreportjob","completereportjob","failreportjob"})assertTrue(statement(xml,"update",id).contains("run_token=#{runtoken}"),id+" must be fenced");
        String reset=statement(xml,"update","resetstalereportjob");assertTrue(reset.contains("run_token=#{runtoken}")&&reset.contains("&lt;=#{stalebefore}")&&reset.contains("run_token=null"),"recovery must not reset a freshly heartbeating run");
    }

    @Test
    void finalReportSchemaPersistsEveryFormalFactPin() throws Exception {
        String sql=new String(Files.readAllBytes(java.nio.file.Paths.get("../sql/ailab.sql")),StandardCharsets.UTF_8)
                .toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+"," ");
        for(String column:new String[]{"source_close_revision","source_formal_revision","source_execution_cutoff","preview_only","json_hash","markdown_hash","word_hash","pdf_hash"})
            assertTrue(sql.contains("`"+column+"`"),column);
        String mapper=new String(Files.readAllBytes(java.nio.file.Paths.get("src/main/resources/mapper/lab/LabReportMapper.xml")),StandardCharsets.UTF_8)
                .toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+"," ");
        assertTrue(mapper.contains("selectlatestclosesnapshotforupdate"));
        String close=statement(mapper,"select","selectlatestclosesnapshotforupdate");
        assertTrue(close.contains("join lab_period_close"));
        assertTrue(close.contains("close_status='closed'"));
        assertTrue(close.contains("period_version=s.period_version"));
        assertTrue(statement(mapper,"update","finalizereport").contains("preview_only='0'"));
        assertTrue(statement(mapper,"update","finalizereport").contains("source_close_revision is not null"));
    }

    private String statement(String xml,String tag,String id){String start="<"+tag+" id=\""+id+"\"";int from=xml.indexOf(start);int to=xml.indexOf("</"+tag+">",from);assertTrue(from>=0&&to>from,"missing mapper statement "+id);return xml.substring(from,to);}
}
