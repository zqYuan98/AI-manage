package com.ailab.system.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.ruoyi.common.annotation.Log;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.InputStream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;

class LabWorkbenchControllerTest {
    @Test
    void exposesThreeRoleSpecificReadEndpointsUnderTheDashboardPermission() throws Exception {
        for (String name : new String[]{"manager", "lead", "member"}) {
            java.lang.reflect.Method method = LabWorkbenchController.class
                    .getMethod(name, String.class, Instant.class);
            assertEquals("@ss.hasPermi('lab:dashboard:view')", method.getAnnotation(PreAuthorize.class).value());
            assertEquals("/" + name, method.getAnnotation(GetMapping.class).value()[0]);
        }

        java.lang.reflect.Method decisions = LabManagementDecisionController.class
                .getMethod("list", String.class, String.class);
        assertEquals("@ss.hasPermi('lab:dashboard:view')",
                decisions.getAnnotation(PreAuthorize.class).value());
    }

    @Test
    void decisionMutationIsAuditedWithoutLoggingRequestBodies() throws Exception {
        java.lang.reflect.Method method = LabManagementDecisionController.class
                .getMethod("create", com.ailab.system.domain.LabManagementDecision.class);
        assertNotNull(method.getAnnotation(PostMapping.class));
        Log log = method.getAnnotation(Log.class);
        assertNotNull(log);
        assertEquals(false, log.isSaveRequestData());
        assertEquals(false, log.isSaveResponseData());
    }

    @Test
    void workbenchSqlScopesRowsBeforeStableOrderingAndNeverInterpolatesSql() throws Exception {
        String xml = new String(Files.readAllBytes(Paths.get("src/main/resources/mapper/lab/LabWorkbenchMapper.xml")), StandardCharsets.UTF_8)
                .toLowerCase().replaceAll("\\s+", "");
        assertEquals(false, xml.contains("${"));
        assertEquals(true, xml.contains("t.owner_id=#{scope.memberid}"));
        assertEquals(true, xml.contains("t.biz_line=#{scope.bizline}"));
        assertEquals(true, xml.contains("m.biz_line=#{scope.bizline}"));
        assertEquals(true, xml.contains("orderbyd.due_date,d.id"));
        assertEquals(true, xml.contains("orderbyt.plan_date,t.id"));
        assertEquals(true, xml.contains("limit201"));
    }

    @Test
    void workbenchAndDecisionMappersParseWithMyBatis() throws Exception {
        Configuration configuration = new Configuration();
        for (String resource : new String[]{"mapper/lab/LabWorkbenchMapper.xml", "mapper/lab/LabManagementDecisionMapper.xml"}) {
            try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
                assertNotNull(input);
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
    }
}
