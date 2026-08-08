package com.ailab.system.report.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailab.system.controller.LabReportTemplateController;
import com.ailab.system.report.provider.DataSourceProvider;
import com.ailab.system.report.provider.DataSourceProviderRegistry;
import com.ailab.system.report.provider.TaskDetailProvider;
import com.ailab.system.report.provider.TaskBlockProvider;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

class ReportDesignerMetadataTest {
    @Test
    @SuppressWarnings("unchecked")
    void exposesOnlyServerOwnedDesignerChoicesAndSafeTemplateVariables() {
        Map<String, Object> metadata = ReportConfigCatalog.designerMetadata();

        assertEquals(ReportConfigCatalog.sectionTypes(), metadata.get("sectionTypes"));
        assertEquals(ReportConfigCatalog.providerIds(), metadata.get("providers"));
        assertEquals(ReportConfigCatalog.filterOperators(), metadata.get("operators"));
        assertEquals(ReportConfigCatalog.queryFields(), metadata.get("queryFields"));
        assertEquals(ReportConfigCatalog.reportTypes(), metadata.get("reportTypes"));
        assertEquals(ReportConfigCatalog.templateStatuses(), metadata.get("templateStatuses"));

        Map<String, Set<String>> compatible = (Map<String, Set<String>>) metadata.get("compatibleProviders");
        assertEquals(ReportConfigCatalog.compatibleProviders(ReportConfigCatalog.CHART), compatible.get(ReportConfigCatalog.CHART));
        Map<String, Set<String>> periods = (Map<String, Set<String>>) metadata.get("providerPeriods");
        assertEquals(new java.util.LinkedHashSet<String>(Arrays.asList("MONTH", "QUARTER")), periods.get(ReportConfigCatalog.PERF_SUMMARY));

        List<String> variables = (List<String>) metadata.get("freemarkerVariables");
        assertEquals(Arrays.asList("context.period", "context.bizLine", "context.generatedAt", "rows", "summary", "metadata.sectionCode", "metadata.title"), variables);
        assertTrue(variables.stream().noneMatch(value -> value.contains("class") || value.contains("requesterId") || value.contains("attributes")));
        assertThrows(UnsupportedOperationException.class, () -> compatible.put("EVIL", java.util.Collections.singleton("JAVA")));
        assertThrows(UnsupportedOperationException.class, () -> variables.add(".version"));
    }

    @Test
    void publishesMetadataOnlyThroughTheTemplateReadPermission() throws Exception {
        Method endpoint = LabReportTemplateController.class.getMethod("metadata");
        assertEquals("/metadata", endpoint.getAnnotation(GetMapping.class).value()[0]);
        assertEquals("@ss.hasPermi('lab:template:list')", endpoint.getAnnotation(PreAuthorize.class).value());
    }

    @Test
    @SuppressWarnings("unchecked")
    void derivesTypedFieldsAndOperatorsFromTheRegisteredProviderContract() {
        DataSourceProviderRegistry registry = new DataSourceProviderRegistry(
                Arrays.<DataSourceProvider>asList(new TaskDetailProvider(), new TaskBlockProvider()));

        Map<String, Object> metadata = ReportConfigCatalog.designerMetadata(registry);
        Map<String, List<Map<String, Object>>> providerFields =
                (Map<String, List<Map<String, Object>>>) metadata.get("providerFields");
        List<Map<String, Object>> fields = providerFields.get(ReportConfigCatalog.TASK_DETAIL);
        Map<String, Object> id = fields.stream().filter(value -> "id".equals(value.get("name"))).findFirst().get();
        Map<String, Object> status = fields.stream().filter(value -> "status".equals(value.get("name"))).findFirst().get();
        Map<String, Object> planDate = fields.stream().filter(value -> "planDate".equals(value.get("name"))).findFirst().get();

        assertEquals("NUMBER", id.get("type"));
        assertTrue(((Set<String>) id.get("operators")).contains("GTE"));
        assertEquals(new java.util.LinkedHashSet<String>(Arrays.asList("EQ", "NE", "IN")), status.get("operators"));
        assertEquals("DATE", planDate.get("type"));
        List<Map<String, Object>> blockFields = providerFields.get(ReportConfigCatalog.TASK_BLOCK);
        Map<String, Object> blockStart = blockFields.stream().filter(value -> "blockStartTime".equals(value.get("name"))).findFirst().get();
        assertEquals("DATETIME", blockStart.get("type"));
        Map<String, Set<String>> providerMetrics = (Map<String, Set<String>>) metadata.get("providerMetrics");
        assertEquals(java.util.Collections.singleton("count"), providerMetrics.get(ReportConfigCatalog.TASK_DETAIL));
        assertThrows(UnsupportedOperationException.class, () -> providerMetrics.put("EVIL", java.util.Collections.singleton("sql")));
        assertThrows(UnsupportedOperationException.class, () -> fields.add(new java.util.LinkedHashMap<String, Object>()));
    }
}
