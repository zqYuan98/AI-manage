package com.ailab.system.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailab.system.controller.LabPerformanceController;
import com.ailab.system.dto.CalibrationCommand;
import com.ailab.system.dto.RedLineRevokeCommand;
import com.ailab.system.mapper.LabPerformanceMapper;
import com.ailab.system.service.impl.LabPerformanceServiceImpl;
import com.ruoyi.common.annotation.Log;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class LabPerformanceContractTest {
    @Test
    void calibrationCommandDoesNotExposeClientControlledResultStatus() {
        for (java.lang.reflect.Field field : CalibrationCommand.class.getDeclaredFields()) {
            assertFalse("resultStatus".equals(field.getName()));
        }
        for (Method method : CalibrationCommand.class.getMethods()) {
            assertFalse("getResultStatus".equals(method.getName()) || "setResultStatus".equals(method.getName()));
        }
    }

    @Test
    void calibrationCommandIgnoresLegacyJsonResultStatus() throws Exception {
        CalibrationCommand command = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                "{\"score\":88,\"comment\":\"review\",\"resultStatus\":\"RED_LINE\"}", CalibrationCommand.class);
        assertTrue(command.getScore().intValue() == 88 && "review".equals(command.getComment()));
    }

    @Test
    void managerHistoryEndpointUsesDedicatedManagerOnlyPermission() throws Exception {
        Method method = LabPerformanceController.class.getDeclaredMethod("revisions", Long.class, String.class);
        org.springframework.security.access.prepost.PreAuthorize authorize = method.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);
        org.springframework.web.bind.annotation.GetMapping mapping = method.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class);
        assertTrue(authorize != null && authorize.value().contains("lab:perf:history"));
        assertTrue(mapping != null && java.util.Arrays.asList(mapping.value()).contains("/member/{memberId}/revisions"));
    }

    @Test
    void currentPersonalQueryExcludesHistoryAndRevisionQueryReturnsEveryRevisionDescending() throws Exception {
        String xml = text(root().resolve("ruoyi-lab/src/main/resources/mapper/lab/LabPerformanceMapper.xml")).toLowerCase().replaceAll("\\s+", " ");
        assertTrue(xml.contains("id=\"selectscoresformember\"") && xml.contains("member_id=#{memberid} and period=#{period} and current_flag='1' and del_flag='0'"));
        assertTrue(xml.contains("id=\"selectscorerevisions\"") && xml.contains("member_id=#{memberid} and period=#{period} and del_flag='0' order by revision_no desc,id desc"));
    }

    @Test
    void periodCreationAvoidsGapLockAndEarlyConsistentReadAndCollaborationLockHasCoveringIndex() throws Exception {
        for (Method method : LabPerformanceMapper.class.getMethods()) {
            assertFalse("selectPeriod".equals(method.getName()), "close must not establish an early repeatable-read snapshot");
        }
        String xml = text(root().resolve("ruoyi-lab/src/main/resources/mapper/lab/LabPerformanceMapper.xml")).toLowerCase().replaceAll("\\s+", " ");
        assertFalse(xml.contains("id=\"selectperiod\""), "close must use ensure then a locking period read");
        assertTrue(xml.contains("on duplicate key update id=last_insert_id(id)"), "period ensure must acquire an exclusive duplicate-key lock");
        String sql = text(root().resolve("sql/ailab.sql")).toLowerCase().replace("`", "").replaceAll("\\s+", "");
        assertTrue(sql.contains("keyidx_lab_collab_period_id(period,id)"));
        assertTrue(sql.contains("addindexidx_lab_collab_period_id(period,id)"));
    }

    @Test
    void mapperXmlParsesAndMapsEveryPerformanceAuditField() throws Exception {
        Configuration configuration = new Configuration();
        configuration.addMapper(LabPerformanceMapper.class);
        try (InputStream input = LabPerformanceContractTest.class.getResourceAsStream("/mapper/lab/LabPerformanceMapper.xml")) {
            assertTrue(input != null);
            new XMLMapperBuilder(input, configuration, "mapper/lab/LabPerformanceMapper.xml", configuration.getSqlFragments()).parse();
        }
        Set<String> properties = new HashSet<String>();
        for (ResultMapping mapping : configuration.getResultMap("com.ailab.system.mapper.LabPerformanceMapper.ScoreResult").getResultMappings()) properties.add(mapping.getProperty());
        for (String field : new String[] {"deliveryScore","qualityScore","collaborationScore","detailJson","calculationVersion","cutoffTime","resultStatus",
                "redLineFlag","redLineCorrectionJson","confirmationStatus","calibrateScore","calibrationTime","revisionNo","currentFlag","version"}) {
            assertTrue(properties.contains(field), "missing score result mapping: " + field);
        }
    }

    @Test
    void mapperUsesBoundParametersStableLocksAndObjectScopes() throws Exception {
        String xml = text(root().resolve("ruoyi-lab/src/main/resources/mapper/lab/LabPerformanceMapper.xml")).toLowerCase().replaceAll("\\s+", " ");
        assertFalse(xml.contains("${"));
        assertTrue(xml.contains("where period=#{period} and del_flag='0' order by id for update"));
        assertTrue(xml.contains("member_status='active'") && xml.contains("order by m.id for update"));
        assertTrue(xml.contains("current_flag='1' and del_flag='0' order by member_id,id for update"));
        assertFalse(xml.contains("insert ignore into lab_period_close"));
        assertTrue(xml.contains("on duplicate key update id=last_insert_id(id)") && xml.contains("insert ignore into lab_collaboration_record"));
        assertTrue(xml.contains("id=\"selectcollaborationbyid\"") && xml.contains("id=\"selectcollaborationsforperiodforupdate\""));
        assertTrue(xml.contains("from lab_collaboration_record where period=#{period} and del_flag='0' order by id for update"));
        assertTrue(xml.contains("rolekey == 'lab_lead'") && xml.contains("rolekey == 'lab_member'"));
        assertTrue(xml.contains("json_extract(detail_json,'$.redlinetriggers')"));
        assertTrue(xml.contains("current_flag='1' and confirmation_status='pending'"));
    }

    @Test
    void schemaHasIdempotentOverdueAndOneCurrentRevisionContracts() throws Exception {
        String sql = text(root().resolve("sql/ailab.sql")).toLowerCase().replace("`", "").replaceAll("\\s+", "");
        assertTrue(sql.contains("uniquekeyuk_lab_collab_idempotency(idempotency_key,idempotency_unique_flag)"));
        assertTrue(sql.contains("uniquekeyuk_lab_perf_member_period_current(member_id,period,current_unique_flag)"));
        assertTrue(sql.contains("row_number()over(partitionbymember_id,periodorderbyrevision_nodesc,iddesc)"));
        assertTrue(sql.indexOf("updatelab_perf_scorepjoinailab_perf_current_keep") < sql.indexOf("adduniqueindexuk_lab_perf_member_period_current"));
        for (String column : new String[] {"delivery_score","quality_score","collaboration_score","calculation_version","cutoff_time","red_line_correction_json","reopen_history_json"}) assertTrue(sql.contains(column));
        assertTrue(sql.contains("lab_collaboration_category") && sql.contains("cross_dept") && sql.contains("knowledge")
                && sql.contains("backup") && sql.contains("overdue") && sql.contains("deduction"));
    }

    @Test
    void sensitiveAuditEndpointsNeverPersistRequestBodies() throws Exception {
        assertSensitive("createCollaboration", com.ailab.system.domain.LabCollaborationRecord.class);
        assertSensitive("reopen", String.class, String.class);
        assertSensitive("revoke", Long.class, RedLineRevokeCommand.class);
        assertSensitive("calibrate", String.class, Long.class, CalibrationCommand.class);
    }

    @Test
    void roleSeedsExposePersonalPerformanceWithoutGrantingManagerWorkflow() throws Exception {
        String sql = text(root().resolve("sql/ailab.sql")).replaceAll("\\s+", " ");
        String leadMenus = roleMenus(sql, "30002");
        String memberMenus = roleMenus(sql, "30003");
        String managerMenus = roleMenus(sql, "30001");
        assertTrue(leadMenus.contains("31009"));
        assertFalse(leadMenus.contains("31090"), "line leads must not receive period close");
        assertFalse(leadMenus.contains("31094"), "line leads must not receive quarterly calibration");
        assertTrue(memberMenus.contains("31009"), "members need lab:perf:list for personal scores and confirmations");
        assertTrue(managerMenus.contains("31095"), "managers need performance history permission");
        assertFalse(leadMenus.contains("31095"), "line leads must not receive performance history");
        assertFalse(memberMenus.contains("31095"), "members must not receive performance history");
    }

    @Test
    void stateChangingPerformanceMethodsAreTransactional() throws Exception {
        for (String method : new String[] {"closePeriod","reopenPeriod","revokeRedLine","calibrateQuarter"}) {
            Method found = null;
            for (Method candidate : LabPerformanceServiceImpl.class.getDeclaredMethods()) if (candidate.getName().equals(method)) found = candidate;
            assertTrue(found != null && found.getAnnotation(Transactional.class) != null, method + " must be transactional");
        }
    }

    private void assertSensitive(String name, Class<?>... types) throws Exception {
        Log log = LabPerformanceController.class.getDeclaredMethod(name, types).getAnnotation(Log.class);
        assertTrue(log != null && !log.isSaveRequestData() && !log.isSaveResponseData());
    }
    private String roleMenus(String sql, String roleId) {
        Matcher matcher = Pattern.compile("SELECT " + roleId + ",`menu_id` FROM `sys_menu` WHERE `menu_id` IN \\(([^)]*)\\);", Pattern.CASE_INSENSITIVE).matcher(sql);
        assertTrue(matcher.find(), "missing role menu seed for " + roleId);
        return matcher.group(1);
    }
    private Path root(){Path p=Paths.get(System.getProperty("user.dir")).toAbsolutePath();while(p!=null&&!Files.exists(p.resolve("sql/ailab.sql")))p=p.getParent();if(p==null)throw new IllegalStateException("root not found");return p;}
    private String text(Path path) throws Exception{return new String(Files.readAllBytes(path),StandardCharsets.UTF_8);}
}
