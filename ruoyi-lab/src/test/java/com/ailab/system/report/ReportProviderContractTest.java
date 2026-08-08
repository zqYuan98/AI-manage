package com.ailab.system.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailab.system.domain.LabReportSection;
import com.ailab.system.report.config.ReportConfigCatalog;
import com.ailab.system.report.config.ReportSectionConfig;
import com.ailab.system.report.model.ReportContext;
import com.ailab.system.report.model.ReportAccessScope;
import com.ailab.system.report.model.ReportQueryCriteria;
import com.ailab.system.report.model.ReportSectionData;
import com.ailab.system.report.provider.AbstractLabDataSourceProvider;
import com.ailab.system.report.provider.AssetSummaryProvider;
import com.ailab.system.report.provider.GoalProgressProvider;
import com.ailab.system.report.provider.IprSummaryProvider;
import com.ailab.system.report.provider.ManualSummaryProvider;
import com.ailab.system.report.provider.PerfSummaryProvider;
import com.ailab.system.report.provider.TaskBlockProvider;
import com.ailab.system.report.provider.TaskCoordProvider;
import com.ailab.system.report.provider.TaskDetailProvider;
import com.ailab.system.report.provider.TaskNextProvider;
import com.ailab.system.report.provider.TaskStatProvider;
import com.ailab.system.report.provider.TaskUndoneProvider;
import com.ailab.system.mapper.LabReportDataMapper;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;

class ReportProviderContractTest {
    @Test
    void providersDeclareEveryStableCatalogIdExactlyOnce() {
        List<AbstractLabDataSourceProvider> providers = Arrays.asList(
                new GoalProgressProvider(), new TaskDetailProvider(), new TaskStatProvider(), new TaskUndoneProvider(),
                new TaskNextProvider(), new TaskCoordProvider(), new TaskBlockProvider(), new AssetSummaryProvider(),
                new IprSummaryProvider(), new PerfSummaryProvider(), new ManualSummaryProvider());
        LinkedHashSet<String> ids = new LinkedHashSet<String>();
        for (AbstractLabDataSourceProvider provider : providers) {
            assertTrue(ids.add(provider.getId()), "duplicate provider id " + provider.getId());
            assertTrue(provider.supports(provider.getId()));
        }
        assertEquals(ReportConfigCatalog.providerIds(), ids);
    }

    @Test
    void periodIsStrictAndManualSummaryIsSafeAndDeeplyImmutable() {
        ManualSummaryProvider provider = new ManualSummaryProvider();
        assertThrows(IllegalArgumentException.class, () -> provider.load(context("2026-8"), manual()));
        ReportSectionData data = provider.load(context("2026-08"), manual());
        assertEquals(Collections.emptyList(), data.getRows());
        assertEquals("Manual placeholder", data.getSummary().get("placeholder"));
        assertThrows(UnsupportedOperationException.class, () -> data.getSummary().put("x", "y"));
    }

    @Test
    void performanceProviderRequiresExplicitSensitivePermissionBeforeDataAccess() {
        assertThrows(SecurityException.class, () -> new PerfSummaryProvider().load(context("2026-08"), perf()));
    }

    @Test
    void dataProviderCannotLoadManualSectionAndAttributesCannotEscalateScope() {
        assertThrows(IllegalArgumentException.class, () -> new TaskDetailProvider().load(context("2026-08"), manual()));
        ReportContext untrustedAttributes = new ReportContext("2026-08", "platform", 30005L, Instant.EPOCH,
                Collections.<String, Object>singletonMap("permissions", Collections.singleton("lab:report:sensitive")));
        assertThrows(SecurityException.class, () -> new PerfSummaryProvider().load(untrustedAttributes, perf()));
    }

    @Test
    void everyProviderProducesAnImmutableEmptySafeSectionFromItsReadOnlyProjection() throws Exception {
        LabReportDataMapper mapper = (LabReportDataMapper) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {LabReportDataMapper.class}, (proxy, method, args) -> Collections.<Map<String, Object>>singletonList(row()));
        for (AbstractLabDataSourceProvider provider : providers()) {
            if (!(provider instanceof ManualSummaryProvider)) inject(provider, mapper);
            ReportContext providerContext = provider instanceof PerfSummaryProvider
                    ? new ReportContext("2026-08", "platform", 30005L, Instant.EPOCH,
                            ReportAccessScope.member("platform", 30005L, Collections.singleton("lab:report:sensitive")), Collections.<String, Object>emptyMap())
                    : context("2026-08");
            ReportSectionData data = provider.load(providerContext, sectionFor(provider.getId()));
            assertEquals(provider.getId(), sectionFor(provider.getId()).getDataSource() == null ? ReportConfigCatalog.MANUAL_SUMMARY : provider.getId());
            assertThrows(UnsupportedOperationException.class, () -> data.getRows().add(Collections.<String, Object>emptyMap()));
        }
    }

    @Test
    void reportMapperXmlUsesOnlyBoundParametersAndParses() throws Exception {
        Configuration configuration = new Configuration();
        configuration.addMapper(LabReportDataMapper.class);
        try (InputStream input = getClass().getResourceAsStream("/mapper/lab/LabReportDataMapper.xml")) {
            assertTrue(input != null);
            new XMLMapperBuilder(input, configuration, "mapper/lab/LabReportDataMapper.xml", configuration.getSqlFragments()).parse();
        }
        ReportQueryCriteria member = new ReportQueryCriteria("2026-08", context("2026-08").getAccessScope());
        String sql = configuration.getMappedStatement("com.ailab.system.mapper.LabReportDataMapper.selectTasks")
                .getBoundSql(member).getSql();
        assertTrue(sql.contains("t.owner_id=?"));
        assertTrue(!sql.contains("${"));
    }

    private ReportContext context(String period) {
        return new ReportContext(period, "platform", 30005L, Instant.parse("2026-08-08T00:00:00Z"), Collections.<String, Object>emptyMap());
    }

    private ReportSectionConfig manual() {
        LabReportSection section = new LabReportSection();
        section.setSectionCode("MANUAL_NOTE"); section.setSectionName("Manual"); section.setSectionType("MANUAL");
        section.setManualFlag("1"); section.setVisibleFlag("1"); section.setQueryConfigJson("{}");
        section.setRenderConfigJson("{\"placeholder\":\"Manual placeholder\"}"); section.setStyleConfigJson("{}");
        return new ReportSectionConfig(section);
    }

    private ReportSectionConfig perf() {
        LabReportSection section = new LabReportSection();
        section.setSectionCode("PERF"); section.setSectionName("Performance"); section.setSectionType("STAT");
        section.setDataSource(ReportConfigCatalog.PERF_SUMMARY); section.setManualFlag("0"); section.setVisibleFlag("1");
        section.setSensitiveFlag("1"); section.setSensitivePermission("lab:report:sensitive");
        section.setQueryConfigJson("{}"); section.setRenderConfigJson("{}"); section.setStyleConfigJson("{}");
        return new ReportSectionConfig(section);
    }

    private List<AbstractLabDataSourceProvider> providers() {
        return Arrays.asList(new GoalProgressProvider(), new TaskDetailProvider(), new TaskStatProvider(), new TaskUndoneProvider(),
                new TaskNextProvider(), new TaskCoordProvider(), new TaskBlockProvider(), new AssetSummaryProvider(),
                new IprSummaryProvider(), new PerfSummaryProvider(), new ManualSummaryProvider());
    }
    private Map<String, Object> row() {
        Map<String, Object> row = new LinkedHashMap<String, Object>(); row.put("id", 1L); row.put("status", "ACTIVE");
        row.put("resultStatus", "DOING"); row.put("total", 1); row.put("progressRate", 50); row.put("score", 90); return row;
    }
    private void inject(AbstractLabDataSourceProvider provider, LabReportDataMapper mapper) throws Exception {
        Field field = AbstractLabDataSourceProvider.class.getDeclaredField("mapper"); field.setAccessible(true); field.set(provider, mapper);
    }
    private ReportSectionConfig sectionFor(String providerId) {
        if (ReportConfigCatalog.MANUAL_SUMMARY.equals(providerId)) return manual();
        LabReportSection section = new LabReportSection(); section.setSectionCode(providerId); section.setSectionName(providerId);
        section.setDataSource(providerId); section.setManualFlag("0"); section.setVisibleFlag("1"); section.setQueryConfigJson("{}"); section.setRenderConfigJson("{}"); section.setStyleConfigJson("{}");
        section.setSectionType(ReportConfigCatalog.PERF_SUMMARY.equals(providerId) || ReportConfigCatalog.GOAL_PROGRESS.equals(providerId) || ReportConfigCatalog.TASK_STAT.equals(providerId) ? "STAT" : "TABLE");
        return new ReportSectionConfig(section);
    }
}
