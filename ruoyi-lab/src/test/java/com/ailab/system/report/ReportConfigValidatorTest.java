package com.ailab.system.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailab.system.domain.LabReportInstance;
import com.ailab.system.domain.LabReportSection;
import com.ailab.system.domain.LabReportTemplate;
import com.ailab.system.report.config.ReportConfigCatalog;
import com.ailab.system.report.config.ReportConfigValidator;
import com.ailab.system.report.config.ReportConfigValidator.TemplateFamily;
import com.ailab.system.report.config.ReportSectionConfig;
import com.ailab.system.report.exporter.ReportExporter;
import com.ailab.system.report.exporter.ReportExporterRegistry;
import com.ailab.system.report.model.ReportContext;
import com.ailab.system.report.model.ReportData;
import com.ailab.system.report.model.ReportSectionData;
import com.ailab.system.report.provider.DataSourceProvider;
import com.ailab.system.report.provider.DataSourceProviderRegistry;
import com.ailab.system.report.provider.ManualSummaryProvider;
import com.ailab.system.report.provider.TaskStatProvider;
import com.ailab.system.report.provider.TaskDetailProvider;
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
    void builtInConfigurationIdsHaveOnePublicStableCatalogWithoutRestrictingRegistryExtensions() {
        assertEquals(new java.util.LinkedHashSet<String>(Arrays.asList(
                "GOAL_PROGRESS", "TASK_DETAIL", "TASK_STAT", "TASK_UNDONE", "TASK_NEXT", "TASK_COORD",
                "TASK_BLOCK", "ASSET_SUMMARY", "IPR_SUMMARY", "PERF_SUMMARY", "MANUAL_SUMMARY")),
                ReportConfigCatalog.providerIds());
        assertEquals(new java.util.LinkedHashSet<String>(Arrays.asList(
                "TABLE", "STAT", "TEXT", "MANUAL", "GROUP_TEXT", "CHART")),
                ReportConfigCatalog.sectionTypes());
        assertTrue(ReportConfigCatalog.compatibleProviders("STAT").contains(ReportConfigCatalog.PERF_SUMMARY));
        assertThrows(UnsupportedOperationException.class,
                () -> ReportConfigCatalog.providerIds().add("MUTATED"));

        DataSourceProvider extension = provider("extension", "CUSTOM_EXTENSION");
        assertEquals(extension,
                new DataSourceProviderRegistry(Collections.singletonList(extension)).require("CUSTOM_EXTENSION"));
    }

    @Test
    void rejectsUnknownOrIncompatibleSectionAndProviderPairs() {
        assertThrows(IllegalArgumentException.class, () -> validator.validateSection(section("TABLE", "PERF_SUMMARY")));
        assertThrows(IllegalArgumentException.class, () -> validator.validateSection(section("MANUAL", "TASK_DETAIL")));
        assertThrows(IllegalArgumentException.class, () -> validator.validateSection(section("UNKNOWN", "TASK_DETAIL")));
        assertThrows(IllegalArgumentException.class, () -> validator.validateSection(section("TEXT", "SQL_FRAGMENT")));
    }

    @Test
    void rejectsUnsupportedManualFieldsAndRendererIncompatibleChartTypesAtSaveTime() {
        ReportConfigValidator springValidator = new ReportConfigValidator(new DataSourceProviderRegistry(
                Arrays.<DataSourceProvider>asList(new ManualSummaryProvider(), new TaskStatProvider())));
        LabReportSection manual = section("MANUAL", null);
        manual.setQueryConfigJson("{\"filters\":[{\"field\":\"status\",\"operator\":\"EQ\",\"value\":\"ACTIVE\"}]}");
        assertThrows(IllegalArgumentException.class, () -> springValidator.validateSection(manual));

        LabReportSection chart = section("CHART", "GOAL_PROGRESS");
        chart.setRenderConfigJson("{\"chart\":\"line\"}");
        assertThrows(IllegalArgumentException.class, () -> validator.validateSection(chart));

        LabReportSection grouped = section("GROUP_TEXT", "TASK_COORD");
        grouped.setRenderConfigJson("{\"template\":\"${summary.groups}\"}");
        assertThrows(IllegalArgumentException.class, () -> validator.validateSection(grouped));

        LabReportSection stat = section("STAT", "TASK_STAT");
        stat.setRenderConfigJson("{\"metrics\":[\"notARealMetric\"]}");
        assertThrows(IllegalArgumentException.class, () -> springValidator.validateSection(stat));
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
    void validatesFieldsOperatorsAndValuesAgainstTheSelectedProviderSchema() {
        ReportConfigValidator typed = new ReportConfigValidator(new DataSourceProviderRegistry(
                Collections.<DataSourceProvider>singletonList(new TaskDetailProvider())));
        LabReportSection invalid = section("TABLE", "TASK_DETAIL");
        invalid.setQueryConfigJson("{\"filters\":[{\"field\":\"status\",\"operator\":\"GTE\",\"value\":1}]}");
        invalid.setRenderConfigJson("{\"columns\":[\"memberId\"]}");

        assertThrows(IllegalArgumentException.class, () -> typed.validateSection(invalid));
    }

    @Test
    void rejectsDeepJsonWithAStreamingLimitBeforeBuildingTheTree() {
        StringBuilder deeplyNested = new StringBuilder(8002);
        for (int i = 0; i < 4000; i++) deeplyNested.append('[');
        deeplyNested.append('0');
        for (int i = 0; i < 4000; i++) deeplyNested.append(']');
        LabReportSection invalid = section("TABLE", "TASK_DETAIL");
        invalid.setQueryConfigJson(deeplyNested.toString());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> validator.validateSection(invalid));
        assertTrue(error.getMessage().contains("query configuration"));
    }

    @Test
    void jsonSizeLimitsApplyBeforeBlankNormalization() {
        char[] whitespace = new char[20000];
        Arrays.fill(whitespace, ' ');
        LabReportSection blank = section("TABLE", "TASK_DETAIL");
        blank.setQueryConfigJson(new String(whitespace));
        assertThrows(IllegalArgumentException.class, () -> validator.validateSection(blank));

        LabReportSection prefixed = section("TABLE", "TASK_DETAIL");
        prefixed.setQueryConfigJson(new String(whitespace) + "{}");
        assertThrows(IllegalArgumentException.class, () -> validator.validateSection(prefixed));
    }

    @Test
    void importAndSaveShareTheExactSameValidationBoundary() {
        String invalid = "{\"sectionType\":\"TABLE\",\"dataSource\":\"TASK_DETAIL\",\"queryConfig\":{\"filters\":[],\"unknown\":true}}";
        assertThrows(IllegalArgumentException.class, () -> validator.validateForSave(invalid));
        assertThrows(IllegalArgumentException.class, () -> validator.validateForImport(invalid));
    }

    @Test
    void serializedManualSectionsDeriveTheRequiredManualFlagOnSaveAndImport() {
        String valid = "{\"sectionType\":\"MANUAL\",\"renderConfig\":{\"required\":false,\"placeholder\":\"暂无内容\"}}";
        assertTrue(validator.validateForSave(valid).isManual());
        assertTrue(validator.validateForImport(valid).isManual());
    }

    @Test
    void sectionStyleUsesTheSameStrictBoundaryForObjectsSavesAndImports() {
        LabReportSection valid = section("TABLE", "TASK_DETAIL");
        valid.setStyleConfigJson("{\"titleLevel\":\"H2\",\"width\":\"100%\",\"padding\":{\"top\":8,\"right\":8,\"bottom\":8,\"left\":8}}");
        validator.validateSection(valid);

        for (String invalidStyle : Arrays.asList(
                "{\"position\":\"fixed\"}",
                "{\"titleLevel\":\"H99\"}",
                "{\"padding\":{\"top\":{\"nested\":1}}}",
                "{\"width\":\"100%\"} trailing")) {
            LabReportSection invalid = section("TABLE", "TASK_DETAIL");
            invalid.setStyleConfigJson(invalidStyle);
            assertThrows(IllegalArgumentException.class, () -> validator.validateSection(invalid));
            assertThrows(IllegalArgumentException.class, () -> new ReportSectionConfig(invalid));
        }
        String serialized = "{\"sectionType\":\"TABLE\",\"dataSource\":\"TASK_DETAIL\",\"queryConfig\":{},\"renderConfig\":{},\"styleConfig\":{\"unknown\":true}}";
        assertThrows(IllegalArgumentException.class, () -> validator.validateForSave(serialized));
        assertThrows(IllegalArgumentException.class, () -> validator.validateForImport(serialized));
    }

    @Test
    void templateHeaderAndStyleUseStrictSchemasForObjectsSavesAndImports() {
        LabReportTemplate valid = template("standard", 1, 0, true, true, "ENABLED");
        valid.setHeaderJson("{\"title\":\"AI Laboratory Monthly Report\",\"logo\":\"ai-lab\",\"showGeneratedAt\":true}");
        valid.setStyleJson("{\"theme\":\"blue\",\"font\":\"Microsoft YaHei\",\"primaryColor\":\"#123ABC\",\"pageSize\":\"A4\"}");
        validator.validateTemplate(valid);

        LabReportTemplate unknownHeader = template("standard", 1, 0, true, true, "ENABLED");
        unknownHeader.setHeaderJson("{\"title\":\"Report\",\"script\":\"bad\"}");
        assertThrows(IllegalArgumentException.class, () -> validator.validateTemplate(unknownHeader));
        LabReportTemplate trailingStyle = template("standard", 1, 0, true, true, "ENABLED");
        trailingStyle.setStyleJson("{\"theme\":\"blue\"} trailing");
        assertThrows(IllegalArgumentException.class, () -> validator.validateTemplate(trailingStyle));
        LabReportTemplate deepStyle = template("standard", 1, 0, true, true, "ENABLED");
        deepStyle.setStyleJson(deepObject(10));
        assertThrows(IllegalArgumentException.class, () -> validator.validateTemplate(deepStyle));

        String serialized = "{\"templateCode\":\"standard\",\"templateName\":\"Standard\",\"reportType\":\"MONTH\",\"revisionNo\":1,\"header\":{\"unknown\":true},\"style\":{\"theme\":\"blue\"}}";
        assertThrows(IllegalArgumentException.class, () -> validator.validateTemplateForSave(serialized));
        assertThrows(IllegalArgumentException.class, () -> validator.validateTemplateForImport(serialized));
    }

    @Test
    void templateIdentityAndLifecycleFieldsMatchThePersistenceContractOnEveryBoundary() {
        for (String code : Arrays.asList("bad code", repeat('x', 65))) {
            LabReportTemplate invalid = template(code, 1, 0, true, true, "ENABLED");
            assertThrows(IllegalArgumentException.class, () -> validator.validateTemplate(invalid));
        }
        LabReportTemplate unknownType = template("standard", 1, 0, true, true, "ENABLED");
        unknownType.setReportType("UNKNOWN");
        assertThrows(IllegalArgumentException.class, () -> validator.validateTemplate(unknownType));
        LabReportTemplate unknownStatus = template("standard", 1, 0, true, true, "UNKNOWN");
        assertThrows(IllegalArgumentException.class, () -> validator.validateTemplate(unknownStatus));
        LabReportTemplate missingRevision = template("standard", 1, 0, true, true, "ENABLED");
        missingRevision.setRevisionNo(null);
        assertThrows(IllegalArgumentException.class, () -> validator.validateTemplate(missingRevision));

        for (String serialized : Arrays.asList(
                "{\"templateCode\":\"bad code\",\"templateName\":\"Bad\",\"reportType\":\"MONTH\",\"revisionNo\":1,\"status\":\"ENABLED\",\"header\":{},\"style\":{}}",
                "{\"templateCode\":\"standard\",\"templateName\":\"Standard\",\"reportType\":\"UNKNOWN\",\"revisionNo\":1,\"status\":\"ENABLED\",\"header\":{},\"style\":{}}",
                "{\"templateCode\":\"standard\",\"templateName\":\"Standard\",\"reportType\":\"MONTH\",\"status\":\"ENABLED\",\"header\":{},\"style\":{}}")) {
            assertThrows(IllegalArgumentException.class, () -> validator.validateTemplateForSave(serialized));
            assertThrows(IllegalArgumentException.class, () -> validator.validateTemplateForImport(serialized));
        }
    }

    @Test
    void strictJsonSchemasRejectFractionalValuesForIntegerFields() {
        LabReportSection queryLimit = section("TABLE", "TASK_DETAIL");
        queryLimit.setQueryConfigJson("{\"limit\":1.5}");
        assertThrows(IllegalArgumentException.class, () -> validator.validateSection(queryLimit));
        LabReportSection renderLimit = section("TABLE", "TASK_DETAIL");
        renderLimit.setRenderConfigJson("{\"limit\":1.5}");
        assertThrows(IllegalArgumentException.class, () -> validator.validateSection(renderLimit));
        LabReportSection styleSize = section("TABLE", "TASK_DETAIL");
        styleSize.setStyleConfigJson("{\"fontSize\":12.5,\"padding\":{\"top\":1.5}}");
        assertThrows(IllegalArgumentException.class, () -> validator.validateSection(styleSize));
        String template = "{\"templateCode\":\"standard\",\"revisionNo\":1.5,\"header\":{},\"style\":{\"bodyFontSize\":12.5}}";
        assertThrows(IllegalArgumentException.class, () -> validator.validateTemplateForSave(template));
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
        family.publishAsDefault(template("monthly", 4, 0, false, false, "ENABLED"), 7);
        assertEquals(1, family.defaultLatestEnabledCount("MONTH"));
        assertThrows(IllegalStateException.class, () -> family.publishAsDefault(template("monthly", 5, 0, false, false, "DISABLED"), 0));

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
        LabReportSection customPermission = section("TEXT", "GOAL_PROGRESS");
        customPermission.setSensitiveFlag("1");
        customPermission.setSensitivePermission("lab:report:restricted");
        LabReportSection clearPermission = section("TEXT", "GOAL_PROGRESS");
        clearPermission.setSensitiveFlag("1");
        assertThrows(IllegalStateException.class, () -> validator.validateUpdate(customPermission, clearPermission));

        LabReportSection persistedPerf = section("STAT", "PERF_SUMMARY");
        persistedPerf.setSensitiveFlag("0");
        LabReportSection replacePerfWithOrdinary = section("TEXT", "GOAL_PROGRESS");
        assertThrows(IllegalStateException.class,
                () -> validator.validateUpdate(persistedPerf, replacePerfWithOrdinary));
    }

    @Test
    void everySensitiveSectionReceivesTheCanonicalPermissionAcrossAllValidationPaths() {
        String requiredPermission = ReportConfigCatalog.DEFAULT_SENSITIVE_PERMISSION;
        LabReportSection perf = section("STAT", "PERF_SUMMARY");
        validator.validateSection(perf);
        assertTrue(perf.isSensitive());
        assertEquals(requiredPermission, perf.getSensitivePermission());

        LabReportSection explicitlyFlagged = section("TEXT", "GOAL_PROGRESS");
        explicitlyFlagged.setSensitiveFlag("1");
        validator.validateSection(explicitlyFlagged);
        assertEquals(requiredPermission, explicitlyFlagged.getSensitivePermission());

        LabReportSection ordinary = section("TEXT", "GOAL_PROGRESS");
        validator.validateSection(ordinary);
        assertFalse(ordinary.isSensitive());
        assertNull(ordinary.getSensitivePermission());

        LabReportSection persisted = section("TEXT", "GOAL_PROGRESS");
        persisted.setSensitiveFlag("1");
        persisted.setSensitivePermission(requiredPermission);
        LabReportSection retained = section("TEXT", "GOAL_PROGRESS");
        retained.setSensitiveFlag("1");
        validator.validateUpdate(persisted, retained);
        assertEquals(requiredPermission, retained.getSensitivePermission());

        String perfJson = "{\"sectionType\":\"STAT\",\"dataSource\":\"PERF_SUMMARY\",\"queryConfig\":{},\"renderConfig\":{},\"styleConfig\":{}}";
        String explicitJson = "{\"sectionType\":\"TEXT\",\"dataSource\":\"GOAL_PROGRESS\",\"queryConfig\":{},\"renderConfig\":{},\"styleConfig\":{},\"sensitivePermission\":\"lab:report:sensitive\"}";
        ReportSectionConfig savedPerf = validator.validateForSave(perfJson);
        ReportSectionConfig importedPerf = validator.validateForImport(perfJson);
        ReportSectionConfig savedExplicit = validator.validateForSave(explicitJson);
        ReportSectionConfig importedExplicit = validator.validateForImport(explicitJson);
        for (ReportSectionConfig canonical : Arrays.asList(savedPerf, importedPerf, savedExplicit, importedExplicit)) {
            assertTrue(canonical.isSensitive());
            assertEquals(requiredPermission, canonical.getSensitivePermission());
        }
    }

    @Test
    void sensitivePermissionMatchesThePersistedVarcharBoundaryEverywhere() {
        String tooLong = repeat('p', 129);
        LabReportSection invalid = section("TEXT", "GOAL_PROGRESS");
        invalid.setSensitivePermission(tooLong);
        assertThrows(IllegalArgumentException.class, () -> validator.validateSection(invalid));
        String serialized = "{\"sectionType\":\"TEXT\",\"dataSource\":\"GOAL_PROGRESS\",\"queryConfig\":{},\"renderConfig\":{},\"styleConfig\":{},\"sensitivePermission\":\"" + tooLong + "\"}";
        assertThrows(IllegalArgumentException.class, () -> validator.validateForSave(serialized));
        assertThrows(IllegalArgumentException.class, () -> validator.validateForImport(serialized));
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
        int sectionTable = compact.indexOf("create table if not exists `lab_report_section`");
        int sensitiveUpgrade = compact.indexOf("table_name='lab_report_section' and column_name='sensitive_permission'");
        int sectionSeed = compact.indexOf("insert into `lab_report_section`");
        assertTrue(sensitiveUpgrade > sectionTable && sensitiveUpgrade < sectionSeed,
                "sensitive-permission upgrade must run after section table creation and before every section seed");
        assertTrue(compact.contains("or (`sensitive_permission` is not null and trim(`sensitive_permission`)<>'')"),
                "an existing permission must irreversibly promote the sensitive flag during bootstrap");
        String sectionSeedSql = compact.substring(sectionSeed, compact.indexOf(';', sectionSeed));
        assertTrue(sectionSeedSql.contains("`sensitive_permission`") && sectionSeedSql.contains("'" + ReportConfigCatalog.DEFAULT_SENSITIVE_PERMISSION + "'"),
                "sensitive seed rows must persist their permission after the pre-seed backfill");
        assertTrue(compact.contains("information_schema.columns") && compact.contains("information_schema.statistics"));
        assertTrue(compact.contains("add column `template_code` varchar(64) null") && compact.contains("add column `template_revision` int null"));
        assertTrue(compact.contains("update `lab_report_instance` r left join `lab_report_template` t"));
        assertFalse(compact.contains("t.`id`=r.`template_id` and t.`del_flag`='0'"), "historical pins must use a soft-deleted template revision when it still exists");
        assertTrue(compact.contains("index_name='idx_lab_report_instance_template_pin'"));
        assertFalse(Files.exists(root.resolve("sql/migrations/20260808_report_template_pin.sql")), "main bootstrap is the only runner-needed pin migration");
        String legacy = new String(Files.readAllBytes(root.resolve("sql/test/ailab-legacy-fixture.sql")), StandardCharsets.UTF_8).toLowerCase();
        assertTrue(legacy.contains("legacy-report-template-39990") && legacy.contains("create table `lab_report_instance`"));
        assertFalse(legacy.substring(legacy.indexOf("create table `lab_report_instance`")).contains("`template_code` varchar(64)"), "legacy fixture must predate pin columns");
        assertTrue(legacy.contains("create table `lab_report_section`"));
        String legacySection = legacy.substring(legacy.indexOf("create table `lab_report_section`"), legacy.indexOf("create table `lab_report_template`"));
        assertFalse(legacySection.contains("sensitive_permission"), "legacy section fixture must predate sensitive permission");
        assertTrue(legacy.contains("legacy_flagged") && legacy.contains("'task_stat'"),
                "legacy fixture must include a flag-only sensitive row with no permission column");
        String mysqlIt = new String(Files.readAllBytes(root.resolve("ruoyi-admin/src/test/java/com/ailab/system/mapper/LabMapperMySqlIT.java")), StandardCharsets.UTF_8);
        assertTrue(mysqlIt.contains("insert into lab_report_instance(id,report_no,template_id,template_code,template_revision,period"),
                "MySQL report fixtures must insert mandatory immutable template pins");
        String bootstrapCall = "ScriptUtils.executeSqlScript(connection, new FileSystemResource(root.resolve(\"sql/ailab.sql\")))";
        assertTrue(mysqlIt.indexOf(bootstrapCall) >= 0 && mysqlIt.indexOf(bootstrapCall) != mysqlIt.lastIndexOf(bootstrapCall),
                "MySQL bootstrap must execute the main schema twice to prove idempotence");
        String sensitiveAssertion = "requireAllSensitiveSectionsPinned(connection)";
        assertTrue(mysqlIt.indexOf(sensitiveAssertion) >= 0
                        && mysqlIt.indexOf(sensitiveAssertion) != mysqlIt.lastIndexOf(sensitiveAssertion),
                "MySQL bootstrap must verify every sensitive section after both runs");
        assertTrue(mysqlIt.contains("insertPermissionOnlySensitiveSection(connection)")
                        && mysqlIt.contains("requirePermissionDrivenSensitiveUpgrade(connection)"),
                "the second MySQL bootstrap must promote a permission-only legacy row");
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
    void reportValuesNormalizeEnumsAndDtosHaveValueSemantics() {
        Map<String, Object> enumAttributes = Collections.<String, Object>singletonMap("state", ReportState.READY);
        ReportContext firstContext = new ReportContext("2026-08", "ALL", 1L, Instant.EPOCH, enumAttributes);
        ReportContext equalContext = new ReportContext("2026-08", "ALL", 1L, Instant.EPOCH,
                Collections.<String, Object>singletonMap("state", "READY"));
        assertEquals("READY", firstContext.getAttributes().get("state"));
        assertEquals(firstContext, equalContext);
        assertEquals(firstContext.hashCode(), equalContext.hashCode());

        ReportSectionData firstSection = new ReportSectionData("TASK", "TABLE", "Tasks",
                Collections.singletonList(Collections.<String, Object>singletonMap("owner", "A")),
                Collections.<String, Object>singletonMap("count", 1));
        ReportSectionData equalSection = new ReportSectionData("TASK", "TABLE", "Tasks",
                Collections.singletonList(Collections.<String, Object>singletonMap("owner", "A")),
                Collections.<String, Object>singletonMap("count", 1));
        assertEquals(firstSection, equalSection);
        assertEquals(firstSection.hashCode(), equalSection.hashCode());

        ReportData first = new ReportData(firstContext, "monthly", 3,
                Collections.singletonList(firstSection), Collections.<String, Object>singletonMap("format", "JSON"));
        ReportData equal = new ReportData(equalContext, "monthly", 3,
                Collections.singletonList(equalSection), Collections.<String, Object>singletonMap("format", "JSON"));
        assertEquals(first, equal);
        assertEquals(first.hashCode(), equal.hashCode());
    }

    @Test
    void templateLatestIsPerCodeWhileDefaultIsGlobalPerReportType() {
        LabReportTemplate alpha = template("alpha", 1, 1, true, true, "ENABLED");
        LabReportTemplate beta = template("beta", 1, 1, true, false, "ENABLED");
        TemplateFamily family = new TemplateFamily(Arrays.asList(alpha, beta));
        family.publishAsDefault(template("beta", 2, 0, false, false, "ENABLED"), 1);
        int alphaLatest = 0;
        for (LabReportTemplate item : family.snapshot()) if ("alpha".equals(item.getTemplateCode()) && item.isLatest()) alphaLatest++;
        assertEquals(1, alphaLatest, "switching defaults must not clear another family latest revision");
        assertEquals(1, family.defaultLatestEnabledCount("MONTH"));
    }

    @Test
    void failedTemplatePublicationIsAtomicAndAValidRetryStillSucceeds() {
        TemplateFamily family = new TemplateFamily(Collections.singletonList(
                template("monthly", 3, 7, true, true, "ENABLED")));
        LabReportTemplate wrongType = template("monthly", 4, 0, false, false, "ENABLED");
        wrongType.setReportType("WEEK");
        assertThrows(IllegalStateException.class, () -> family.publishAsDefault(wrongType, 7));

        assertEquals(1, family.snapshot().size());
        assertTrue(family.snapshot().get(0).isLatest());
        assertTrue(family.snapshot().get(0).isDefaultTemplate());
        family.publishAsDefault(template("monthly", 4, 0, false, false, "ENABLED"), 7);
        assertEquals(1, family.defaultLatestEnabledCount("MONTH"));
    }

    @Test
    void templateFamilyPreservesPersistenceIdentityAndEnforcesOrderedOptimisticRevisions() {
        LabReportTemplate current = template("monthly", 3, 7, true, true, "ENABLED");
        current.setId(42L); current.setDelFlag("0"); current.setCreateBy("creator"); current.setUpdateBy("editor");
        current.setCreateTime(new Date(1000L)); current.setUpdateTime(new Date(2000L)); current.setRemark("audit");
        TemplateFamily family = new TemplateFamily(Collections.singletonList(current));
        LabReportTemplate snapshot = family.snapshot().get(0);
        assertEquals(Long.valueOf(42L), snapshot.getId()); assertEquals(Integer.valueOf(7), snapshot.getVersion());
        assertEquals("0", snapshot.getDelFlag()); assertEquals("creator", snapshot.getCreateBy());
        assertEquals("editor", snapshot.getUpdateBy()); assertEquals(new Date(1000L), snapshot.getCreateTime());
        assertEquals(new Date(2000L), snapshot.getUpdateTime()); assertEquals("audit", snapshot.getRemark());

        assertThrows(IllegalStateException.class,
                () -> family.publishAsDefault(template("monthly", 4, 0, false, false, "ENABLED"), 6));
        assertThrows(IllegalStateException.class,
                () -> family.publishAsDefault(template("monthly", 5, 0, false, false, "ENABLED"), 7));
        LabReportTemplate persistedCandidate = template("monthly", 4, 0, false, false, "ENABLED");
        persistedCandidate.setId(99L);
        assertThrows(IllegalStateException.class, () -> family.publishAsDefault(persistedCandidate, 7));
        LabReportTemplate versionedCandidate = template("monthly", 4, 2, false, false, "ENABLED");
        assertThrows(IllegalStateException.class, () -> family.publishAsDefault(versionedCandidate, 7));
        family.publishAsDefault(template("monthly", 4, 0, false, false, "ENABLED"), 7);

        LabReportTemplate duplicate = template("monthly", 3, 8, false, false, "ENABLED");
        assertThrows(IllegalStateException.class, () -> new TemplateFamily(Arrays.asList(current, duplicate)));
        assertThrows(IllegalStateException.class, () -> new TemplateFamily(Collections.singletonList(template("bad", 0, 1, true, true, "ENABLED"))));
        LabReportTemplate revisionTwo = template("ordered", 2, 1, false, false, "ENABLED");
        LabReportTemplate revisionOne = template("ordered", 1, 1, true, true, "ENABLED");
        assertThrows(IllegalStateException.class, () -> new TemplateFamily(Arrays.asList(revisionTwo, revisionOne)));
    }

    @Test
    void latestRevisionMustBeTheMaximumAndPublishingUsesItsVersion() {
        LabReportTemplate staleLatest = template("monthly", 1, 7, true, true, "ENABLED");
        LabReportTemplate newerNonLatest = template("monthly", 2, 8, false, false, "ENABLED");
        assertThrows(IllegalStateException.class,
                () -> new TemplateFamily(Arrays.asList(staleLatest, newerNonLatest)));

        LabReportTemplate oldRevision = template("monthly", 1, 7, false, false, "ENABLED");
        LabReportTemplate currentRevision = template("monthly", 2, 8, true, true, "ENABLED");
        TemplateFamily family = new TemplateFamily(Arrays.asList(oldRevision, currentRevision));
        assertThrows(IllegalStateException.class,
                () -> family.publishAsDefault(template("monthly", 3, 0, false, false, "ENABLED"), 7));
        family.publishAsDefault(template("monthly", 3, 0, false, false, "ENABLED"), 8);
        assertEquals(Integer.valueOf(3), family.snapshot().get(2).getRevisionNo());
        assertTrue(family.snapshot().get(2).isLatest());
    }

    @Test
    void sectionConfigCarriesAnImmutableRendererReadySnapshot() {
        LabReportSection source = section("TABLE", "TASK_DETAIL");
        source.setId(9L); source.setSectionCode("DELIVERY"); source.setSectionName("Delivery"); source.setSortNo(20);
        source.setManualFlag("1"); source.setVisibleFlag("0"); source.setSensitivePermission("lab:report:sensitive");
        source.setStyleConfigJson("{\"titleLevel\":\"H2\",\"padding\":{\"top\":8}}");
        ReportSectionConfig config = new ReportSectionConfig(source);
        assertEquals(Long.valueOf(9L), config.getSectionId());
        assertEquals("Delivery", config.getSectionName());
        assertEquals("Delivery", config.getTitle());
        assertEquals("H2", config.getTitleLevel());
        assertEquals(Integer.valueOf(20), config.getSortNo());
        assertTrue(config.isManual()); assertFalse(config.isVisible());
        assertEquals("lab:report:sensitive", config.getSensitivePermission());
        assertThrows(UnsupportedOperationException.class, () -> mutableMap((Map<?, ?>) config.getStyleConfig().get("padding")).put("top", 10));
    }

    @Test
    void reportTypeIsAConsistentAliasForTheSinglePersistedPeriodType() {
        LabReportTemplate template = new LabReportTemplate();
        template.setPeriodType("MONTH"); template.setReportType("WEEK");
        assertEquals("WEEK", template.getPeriodType());
        assertEquals("WEEK", template.getReportType());
    }

    private enum ReportState {
        READY;
        @Override public String toString() { throw new AssertionError("enum normalization must use name(), not toString()"); }
    }

    private String repeat(char value, int count) {
        char[] values = new char[count]; Arrays.fill(values, value); return new String(values);
    }

    private void assertLegal(String sectionType, String provider) {
        validator.validateSection(section(sectionType, provider));
    }

    private LabReportSection section(String type, String provider) {
        LabReportSection section = new LabReportSection();
        section.setSectionType(type);
        section.setDataSource(provider);
        section.setManualFlag("MANUAL".equals(type) ? "1" : "0");
        section.setQueryConfigJson("{\"filters\":[]}");
        section.setRenderConfigJson("MANUAL".equals(type)
                ? "{\"required\":false,\"placeholder\":\"暂无内容\"}"
                : "GROUP_TEXT".equals(type) ? "{\"groupBy\":\"owner\"}" : "{\"columns\":[\"owner\"]}");
        return section;
    }

    private LabReportTemplate template(String code, int revision, int version, boolean latest, boolean defaultTemplate, String status) {
        LabReportTemplate template = new LabReportTemplate();
        template.setTemplateCode(code);
        template.setTemplateName(code + " template");
        template.setReportType("MONTH");
        template.setRevisionNo(revision);
        template.setVersion(version);
        template.setLatestFlag(latest ? "1" : "0");
        template.setDefaultFlag(defaultTemplate ? "1" : "0");
        template.setStatus(status);
        return template;
    }

    private String deepObject(int depth) {
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < depth; i++) value.append("{\"nested\":");
        value.append('1');
        for (int i = 0; i < depth; i++) value.append('}');
        return value.toString();
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
