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
import java.time.temporal.TemporalAccessor;
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
    void persistedSensitiveSectionsCannotBeDowngradedOrHaveTheirPermissionCleared() {
        LabReportSection persisted = section("TEXT", "GOAL_PROGRESS");
        persisted.setSensitiveFlag("1");
        persisted.setSensitivePermission("lab:report:sensitive");
        LabReportSection downgrade = section("TEXT", "GOAL_PROGRESS");
        downgrade.setSensitiveFlag("0");
        assertThrows(IllegalStateException.class, () -> validator.validateUpdate(persisted, downgrade));
        LabReportSection clearPermission = section("TEXT", "GOAL_PROGRESS");
        clearPermission.setSensitiveFlag("1");
        assertThrows(IllegalStateException.class, () -> validator.validateUpdate(persisted, clearPermission));
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
        String compact = schema.replaceAll("\\s+", " ");
        int reportTable = compact.indexOf("create table if not exists `lab_report_instance`");
        int pinUpgrade = compact.indexOf("table_name='lab_report_instance' and column_name='template_code'");
        int reportSeed = compact.indexOf("insert into `lab_report_instance`");
        assertTrue(pinUpgrade > reportTable && pinUpgrade < reportSeed, "pin upgrade must run after table creation and before every report seed");
        assertTrue(compact.contains("information_schema.columns") && compact.contains("information_schema.statistics"));
        assertTrue(compact.contains("add column `template_code` varchar(64) null") && compact.contains("add column `template_revision` int null"));
        assertTrue(compact.contains("update `lab_report_instance` r left join `lab_report_template` t"));
        assertFalse(compact.contains("t.`id`=r.`template_id` and t.`del_flag`='0'"), "historical pins must use a soft-deleted template revision when it still exists");
        assertTrue(compact.contains("index_name='idx_lab_report_instance_template_pin'"));
        assertFalse(Files.exists(root.resolve("sql/migrations/20260808_report_template_pin.sql")), "main bootstrap is the only runner-needed pin migration");
        String legacy = new String(Files.readAllBytes(root.resolve("sql/test/ailab-legacy-fixture.sql")), StandardCharsets.UTF_8).toLowerCase();
        assertTrue(legacy.contains("legacy-report-template-39990") && legacy.contains("create table `lab_report_instance`"));
        assertFalse(legacy.substring(legacy.indexOf("create table `lab_report_instance`")).contains("`template_code` varchar(64)"), "legacy fixture must predate pin columns");
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
    void registriesRequireOwnIdSupportAndIndexEveryDeclaredCapabilityWithoutShadowing() {
        assertThrows(IllegalStateException.class, () -> new DataSourceProviderRegistry(Collections.singletonList(new DataSourceProvider() {
            @Override public String getId() { return "impl"; } @Override public boolean supports(String id) { return "TASK_DETAIL".equals(id); }
            @Override public ReportSectionData load(ReportContext context, ReportSectionConfig config) { return null; }
        })));
        assertThrows(IllegalStateException.class, () -> new SectionRendererRegistry(Collections.singletonList(new SectionRenderer() {
            @Override public String getId() { return "impl"; } @Override public boolean supports(String id) { return "TABLE".equals(id); }
            @Override public ReportSectionData render(ReportContext context, ReportSectionConfig config, ReportSectionData data) { return data; }
        })));
        assertThrows(IllegalStateException.class, () -> new ReportExporterRegistry(Collections.singletonList(new ReportExporter() {
            @Override public String getId() { return "impl"; } @Override public boolean supports(String id) { return "JSON".equals(id); }
            @Override public byte[] export(com.ailab.system.report.model.ReportData data) { return new byte[0]; }
        })));
        DataSourceProvider custom = provider("custom", "CUSTOM_CAPABILITY");
        assertEquals(custom, new DataSourceProviderRegistry(Collections.singletonList(custom)).require("CUSTOM_CAPABILITY"));
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
        TemporalAccessor mutableTemporal = new TemporalAccessor() {
            @Override public boolean isSupported(java.time.temporal.TemporalField field) { return false; }
            @Override public java.time.temporal.ValueRange range(java.time.temporal.TemporalField field) { throw new UnsupportedOperationException(); }
            @Override public long getLong(java.time.temporal.TemporalField field) { throw new UnsupportedOperationException(); }
            @Override public <R> R query(java.time.temporal.TemporalQuery<R> query) { return null; }
            @Override public String toString() { throw new AssertionError("must not call custom scalar toString"); }
        };
        assertThrows(IllegalArgumentException.class, () -> new ReportContext("2026-08", "ALL", 1L, Instant.EPOCH,
                Collections.<String, Object>singletonMap("unsafe", mutableTemporal)));
    }

    @Test
    void templateLatestIsPerCodeWhileDefaultIsGlobalPerReportType() {
        LabReportTemplate alpha = template("alpha", 1, 1, true, true, "ENABLED");
        LabReportTemplate beta = template("beta", 1, 1, true, false, "ENABLED");
        TemplateFamily family = new TemplateFamily(Arrays.asList(alpha, beta));
        family.publishAsDefault(template("beta", 2, 0, false, false, "ENABLED"));
        int alphaLatest = 0;
        for (LabReportTemplate item : family.snapshot()) if ("alpha".equals(item.getTemplateCode()) && item.isLatest()) alphaLatest++;
        assertEquals(1, alphaLatest, "switching defaults must not clear another family latest revision");
        assertEquals(1, family.defaultLatestEnabledCount("MONTH"));
    }

    @Test
    void sectionConfigCarriesAnImmutableRendererReadySnapshot() {
        LabReportSection source = section("TABLE", "TASK_DETAIL");
        source.setId(9L); source.setSectionCode("DELIVERY"); source.setSectionName("Delivery"); source.setSortNo(20);
        source.setManualFlag("1"); source.setVisibleFlag("0"); source.setSensitivePermission("lab:report:sensitive");
        source.setStyleConfigJson("{\"titleLevel\":\"H2\",\"nested\":{\"color\":\"blue\"}}");
        ReportSectionConfig config = new ReportSectionConfig(source);
        assertEquals(Long.valueOf(9L), config.getSectionId());
        assertEquals("Delivery", config.getSectionName());
        assertEquals("Delivery", config.getTitle());
        assertEquals("H2", config.getTitleLevel());
        assertEquals(Integer.valueOf(20), config.getSortNo());
        assertTrue(config.isManual()); assertFalse(config.isVisible());
        assertEquals("lab:report:sensitive", config.getSensitivePermission());
        assertThrows(UnsupportedOperationException.class, () -> mutableMap((Map<?, ?>) config.getStyleConfig().get("nested")).put("color", "red"));
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
            @Override public boolean supports(String value) { return id.equals(value) || supported.equals(value); }
            @Override public java.util.Set<String> getSupportedIds() { return Collections.singleton(supported); }
            @Override public ReportSectionData load(ReportContext context, ReportSectionConfig config) { return null; }
        };
    }

    private SectionRenderer renderer(final String id, final String supported) {
        return new SectionRenderer() {
            @Override public String getId() { return id; }
            @Override public boolean supports(String value) { return id.equals(value) || supported.equals(value); }
            @Override public java.util.Set<String> getSupportedIds() { return Collections.singleton(supported); }
            @Override public ReportSectionData render(ReportContext context, ReportSectionConfig config, ReportSectionData data) { return data; }
        };
    }

    private ReportExporter exporter(final String id, final String supported) {
        return new ReportExporter() {
            @Override public String getId() { return id; }
            @Override public boolean supports(String value) { return id.equals(value) || supported.equals(value); }
            @Override public java.util.Set<String> getSupportedIds() { return Collections.singleton(supported); }
            @Override public byte[] export(com.ailab.system.report.model.ReportData data) throws IOException { return new byte[0]; }
        };
    }

    @SuppressWarnings("unchecked")
    private Map<Object, Object> mutableMap(Map<?, ?> value) { return (Map<Object, Object>) value; }
}
