package com.ailab.system.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailab.system.domain.LabReportInstance;
import com.ailab.system.domain.LabReportSection;
import com.ailab.system.domain.LabReportTemplate;
import com.ailab.system.report.config.ReportConfigValidator;
import com.ailab.system.report.config.ReportConfigValidator.TemplateFamily;
import com.ailab.system.report.config.ReportSectionConfig;
import com.ailab.system.report.exporter.ReportExporter;
import com.ailab.system.report.exporter.ReportExporterRegistry;
import com.ailab.system.report.model.ReportContext;
import com.ailab.system.report.model.ReportSectionData;
import com.ailab.system.report.provider.DataSourceProvider;
import com.ailab.system.report.provider.DataSourceProviderRegistry;
import com.ailab.system.report.renderer.SectionRenderer;
import com.ailab.system.report.renderer.SectionRendererRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ReportConfigValidatorTest {

    private final ReportConfigValidator validator = new ReportConfigValidator();

    @Test
    void acceptsEveryLegalBuiltInSectionAndProviderPair() {
        assertLegal("TABLE", "TASK_DETAIL");
        assertLegal("STAT", "TASK_STAT");
        assertLegal("TEXT", "GOAL_PROGRESS");
        assertLegal("MANUAL", null);
        assertLegal("GROUP_TEXT", "TASK_COORD");
        assertLegal("CHART", "GOAL_PROGRESS");
    }

    @Test
    void rejectsUnknownOrIncompatibleSectionAndProviderPairs() {
        assertThrows(IllegalArgumentException.class, () -> validator.validateSection(section("TABLE", "PERF_SUMMARY")));
        assertThrows(IllegalArgumentException.class, () -> validator.validateSection(section("MANUAL", "TASK_DETAIL")));
        assertThrows(IllegalArgumentException.class, () -> validator.validateSection(section("UNKNOWN", "TASK_DETAIL")));
        assertThrows(IllegalArgumentException.class, () -> validator.validateSection(section("TEXT", "SQL_FRAGMENT")));
    }

    @Test
    void validatesStrictFilterAndColumnSchemasWithoutDynamicSql() {
        LabReportSection valid = section("TABLE", "TASK_DETAIL");
        valid.setQueryConfigJson("{\"filters\":[{\"field\":\"period\",\"operator\":\"EQ\",\"value\":\"2026-08\"}],\"sort\":\"planDate\"}");
        valid.setRenderConfigJson("{\"columns\":[{\"field\":\"owner\",\"label\":\"Owner\",\"align\":\"LEFT\"}],\"limit\":100}");
        validator.validateSection(valid);

        for (String json : Arrays.asList(
                "{\"filters\":[{\"field\":\"period;delete\",\"operator\":\"EQ\",\"value\":\"x\"}]}",
                "{\"filters\":[{\"field\":\"period\",\"operator\":\"LIKE\",\"value\":{}}]}",
                "{\"filters\":[{\"field\":\"period\",\"operator\":\"EQ\",\"value\":\"x\",\"sql\":\"1=1\"}]}",
                "{\"filters\":[],\"unexpected\":true}", "{\"filters\":[]} trailing")) {
            LabReportSection invalid = section("TABLE", "TASK_DETAIL");
            invalid.setQueryConfigJson(json);
            assertThrows(IllegalArgumentException.class, () -> validator.validateSection(invalid));
        }
        LabReportSection tooMany = section("TABLE", "TASK_DETAIL");
        tooMany.setRenderConfigJson("{\"columns\":[\"owner\",\"status\",\"deliverable\",\"planDate\",\"bizLine\",\"title\",\"result\",\"nextAction\",\"coordination\",\"block\",\"extra\"]}");
        assertThrows(IllegalArgumentException.class, () -> validator.validateSection(tooMany));
        for (String json : Arrays.asList(
                "{\"filters\":[{\"field\":\"period\",\"operator\":\"BETWEEN\",\"value\":[\"2026-08\"]}]}",
                "{\"filters\":[{\"field\":\"period\",\"operator\":\"EQ\",\"value\":[\"2026-08\"]}]}",
                "{\"filters\":[]}")) {
            LabReportSection invalid = section("TABLE", "TASK_DETAIL"); invalid.setQueryConfigJson(json);
            if (json.contains("BETWEEN") || json.contains("\"EQ\"")) assertThrows(IllegalArgumentException.class, () -> validator.validateSection(invalid));
        }
        LabReportSection badRender = section("STAT", "TASK_STAT");
        badRender.setRenderConfigJson("{\"metrics\":true}");
        assertThrows(IllegalArgumentException.class, () -> validator.validateSection(badRender));
    }

    @Test
    void importAndSaveShareTheExactSameValidationBoundary() {
        String invalid = "{\"sectionType\":\"TABLE\",\"dataSource\":\"TASK_DETAIL\",\"queryConfig\":{\"filters\":[],\"unknown\":true}}";
        assertThrows(IllegalArgumentException.class, () -> validator.validateForSave(invalid));
        assertThrows(IllegalArgumentException.class, () -> validator.validateForImport(invalid));
    }

    @Test
    void templateFamilyStateTransitionsPreserveHistoricalRevisionAndRejectStaleWrites() {
        LabReportTemplate published = template("monthly", 3, 7, true, true, "ENABLED");
        LabReportTemplate edited = validator.nextRevisionForPublishedEdit(published, 7);
        assertEquals("monthly", edited.getTemplateCode());
        assertEquals(Integer.valueOf(4), edited.getRevisionNo());
        assertEquals(Integer.valueOf(0), edited.getVersion());
        assertFalse(edited.isDefaultTemplate());

        LabReportTemplate savedAsNew = validator.saveAsNewFamily(published, "monthly-management");
        assertEquals("monthly-management", savedAsNew.getTemplateCode());
        assertEquals(Integer.valueOf(1), savedAsNew.getRevisionNo());

        LabReportInstance historical = new LabReportInstance();
        historical.setTemplateCode("monthly");
        historical.setTemplateRevision(3);
        validator.assertHistoricalInstancePinned(historical, Arrays.asList(published, edited));
        assertThrows(IllegalStateException.class, () -> validator.nextRevisionForPublishedEdit(published, 6));
    }

    @Test
    void defaultLatestEnabledStateIsExplicitAndSensitiveSectionsCannotBeDowngraded() {
        TemplateFamily family = new TemplateFamily(Collections.singletonList(template("monthly", 3, 7, true, true, "ENABLED")));
        family.publishAsDefault(template("monthly", 4, 0, false, false, "ENABLED"));
        assertEquals(1, family.defaultLatestEnabledCount("MONTH"));
        assertThrows(IllegalStateException.class, () -> family.publishAsDefault(template("monthly", 5, 0, false, false, "DISABLED")));

        LabReportSection perf = section("STAT", "PERF_SUMMARY");
        perf.setSensitiveFlag("0");
        validator.validateSection(perf);
        assertTrue(perf.isSensitive(), "PERF_SUMMARY must force a persisted sensitive snapshot");
        LabReportSection explicit = section("TEXT", "GOAL_PROGRESS");
        explicit.setSensitivePermission("lab:report:sensitive");
        explicit.setSensitiveFlag("0");
        validator.validateSection(explicit);
        assertTrue(explicit.isSensitive());
    }

    @Test
    void rejectsTemplateFamiliesThatDoNotHaveExactlyOneDefaultLatestEnabledRevision() {
        assertThrows(IllegalStateException.class, () -> new TemplateFamily(Collections.singletonList(template("monthly", 1, 0, true, false, "ENABLED"))));
    }

    @Test
    void persistedSectionSchemaHasTheExplicitSensitivePermissionNeededToCreateAnIrreversibleSnapshot() throws IOException {
        Path root = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("sql/ailab.sql"))) root = root.getParent();
        if (root == null) throw new AssertionError("repository root not found");
        String schema = new String(Files.readAllBytes(root.resolve("sql/ailab.sql")), StandardCharsets.UTF_8).toLowerCase();
        assertTrue(schema.contains("`sensitive_permission` varchar(128)"));
        assertTrue(schema.contains("`template_code` varchar(64) not null") && schema.contains("`template_revision` int not null"));
        assertTrue(schema.contains("'task_detail'") && schema.contains("'task_stat'") && schema.contains("'task_coord'"));
        assertTrue(schema.contains("'filters'") && schema.contains("'bizline'"));
        Path migration = root.resolve("sql/migrations/20260808_report_template_pin.sql");
        String upgrade = new String(Files.readAllBytes(migration), StandardCharsets.UTF_8).toLowerCase().replaceAll("\\s+", " ");
        assertTrue(upgrade.contains("alter table `lab_report_instance`") && upgrade.contains("update `lab_report_instance` r join `lab_report_template` t"));
    }

    @Test
    void registriesFailFastForDuplicatesOrAmbiguousSupportAndUnknownLookups() {
        DataSourceProvider first = provider("one", "TASK_DETAIL");
        assertThrows(IllegalStateException.class, () -> new DataSourceProviderRegistry(Arrays.asList(first, provider("two", "TASK_DETAIL"))));
        assertThrows(IllegalStateException.class, () -> new DataSourceProviderRegistry(Arrays.asList(first, provider("one", "TASK_STAT"))));
        assertThrows(IllegalArgumentException.class, () -> new DataSourceProviderRegistry(Collections.singletonList(first)).require("missing"));
        assertThrows(IllegalStateException.class, () -> new SectionRendererRegistry(Arrays.asList(renderer("one", "TABLE"), renderer("two", "TABLE"))));
        assertThrows(IllegalStateException.class, () -> new ReportExporterRegistry(Arrays.asList(exporter("one", "JSON"), exporter("two", "JSON"))));
        assertEquals(first, new DataSourceProviderRegistry(Collections.singletonList(first)).require("TASK_DETAIL"));
        assertEquals("table", new SectionRendererRegistry(Collections.singletonList(renderer("table", "TABLE"))).require("TABLE").getId());
        assertEquals("json", new ReportExporterRegistry(Collections.singletonList(exporter("json", "JSON"))).require("JSON").getId());
    }

    @Test
    void reportDataContractsDefensivelyCopyCollections() {
        Map<String, Object> attributes = new java.util.LinkedHashMap<String, Object>();
        Map<String, Object> nested = new java.util.LinkedHashMap<String, Object>(); nested.put("period", "2026-08");
        attributes.put("nested", nested);
        ReportContext context = new ReportContext("2026-08", "ALL", 1L, Instant.EPOCH, attributes);
        nested.put("period", "changed");
        assertEquals("2026-08", ((Map<?, ?>) context.getAttributes().get("nested")).get("period"));
        assertThrows(UnsupportedOperationException.class, () -> context.getAttributes().put("x", "y"));
        Map<String, Object> row = new java.util.LinkedHashMap<String, Object>(); row.put("owner", "A");
        ReportSectionData data = new ReportSectionData("TASK", "TABLE", "Tasks", Collections.singletonList(row), Collections.<String, Object>emptyMap());
        row.put("owner", "changed");
        assertEquals("A", data.getRows().get(0).get("owner"));
        assertThrows(UnsupportedOperationException.class, () -> data.getRows().get(0).put("x", "y"));
        assertThrows(IllegalArgumentException.class, () -> new ReportContext("2026-08", "ALL", 1L, Instant.EPOCH,
                Collections.<String, Object>singletonMap("unsafe", new Date())));
        assertThrows(IllegalArgumentException.class, () -> new ReportContext("2026-08", "ALL", 1L, Instant.EPOCH,
                Collections.<String, Object>singletonMap("unsafe", new AtomicInteger(1))));
    }

    @Test
    void reportTypeIsAConsistentAliasForTheSinglePersistedPeriodType() {
        LabReportTemplate template = new LabReportTemplate();
        template.setPeriodType("MONTH"); template.setReportType("WEEK");
        assertEquals("WEEK", template.getPeriodType());
        assertEquals("WEEK", template.getReportType());
    }

    private void assertLegal(String sectionType, String provider) {
        validator.validateSection(section(sectionType, provider));
    }

    private LabReportSection section(String type, String provider) {
        LabReportSection section = new LabReportSection();
        section.setSectionType(type);
        section.setDataSource(provider);
        section.setQueryConfigJson("{\"filters\":[]}");
        section.setRenderConfigJson("{\"columns\":[\"owner\"]}");
        return section;
    }

    private LabReportTemplate template(String code, int revision, int version, boolean latest, boolean defaultTemplate, String status) {
        LabReportTemplate template = new LabReportTemplate();
        template.setTemplateCode(code);
        template.setReportType("MONTH");
        template.setRevisionNo(revision);
        template.setVersion(version);
        template.setLatestFlag(latest ? "1" : "0");
        template.setDefaultFlag(defaultTemplate ? "1" : "0");
        template.setStatus(status);
        return template;
    }

    private DataSourceProvider provider(final String id, final String supported) {
        return new DataSourceProvider() {
            @Override public String getId() { return id; }
            @Override public boolean supports(String value) { return supported.equals(value); }
            @Override public ReportSectionData load(ReportContext context, ReportSectionConfig config) { return null; }
        };
    }

    private SectionRenderer renderer(final String id, final String supported) {
        return new SectionRenderer() {
            @Override public String getId() { return id; }
            @Override public boolean supports(String value) { return supported.equals(value); }
            @Override public ReportSectionData render(ReportContext context, ReportSectionConfig config, ReportSectionData data) { return data; }
        };
    }

    private ReportExporter exporter(final String id, final String supported) {
        return new ReportExporter() {
            @Override public String getId() { return id; }
            @Override public boolean supports(String value) { return supported.equals(value); }
            @Override public byte[] export(com.ailab.system.report.model.ReportData data) throws IOException { return new byte[0]; }
        };
    }
}
