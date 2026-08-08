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
import com.ailab.system.mapper.LabDashboardMapper;
import com.ailab.system.dto.GoalHealthFact;
import com.ailab.system.dto.LabAccessContext;
import com.ailab.system.service.LabAccessService;
import com.ailab.system.report.model.TrustedReportContextFactory;
import com.ruoyi.system.service.ISysMenuService;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.Date;
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
        assertTrue(ReportConfigCatalog.queryFields().contains("taskPeriod"));
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
    void sensitiveSectionRequiresItsExactSnapshottedPermissionBeforeMapperAccess() throws Exception {
        final int[] mapperCalls = new int[1];
        LabReportDataMapper mapper = (LabReportDataMapper) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {LabReportDataMapper.class}, (proxy, method, args) -> { mapperCalls[0]++; return Collections.singletonList(row()); });
        PerfSummaryProvider provider = new PerfSummaryProvider(); inject(provider, mapper);
        ReportSectionConfig restricted = sensitivePerf("lab:report:restricted");
        ReportContext defaultOnly = new ReportContext("2026-08", "platform", 30005L, Instant.EPOCH,
                trustedManagerScope("lab:report:sensitive"), Collections.<String,Object>emptyMap());
        assertThrows(SecurityException.class, () -> provider.load(defaultOnly, restricted));
        assertEquals(0, mapperCalls[0], "authorization must fail before mapper access");
        ReportContext custom = new ReportContext("2026-08", "platform", 30005L, Instant.EPOCH,
                trustedManagerScope("lab:report:restricted"), Collections.<String,Object>emptyMap());
        assertEquals(1, provider.load(custom, restricted).getRows().size());
        assertEquals(1, mapperCalls[0]);
    }

    @Test
    void blockedTaskProjectionJoinsEveryOwnerAliasItSelects() throws Exception {
        String xml = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/main/resources/mapper/lab/LabReportDataMapper.xml")), java.nio.charset.StandardCharsets.UTF_8);
        String statement = xml.substring(xml.indexOf("<select id=\"selectBlockedTasks\""), xml.indexOf("</select>", xml.indexOf("<select id=\"selectBlockedTasks\"")));
        assertTrue(statement.contains("left join lab_member owner_member"));
        assertTrue(statement.contains("left join sys_user owner_user"));
    }

    @Test
    void detailSourcesUseServerSideFetchLimitAndRejectOverflowBeforeTemplateFiltering() throws Exception {
        TaskDetailProvider provider = new TaskDetailProvider();
        List<Map<String,Object>> overflow = new java.util.ArrayList<Map<String,Object>>();
        for (int i=0;i<5001;i++) { Map<String,Object> item=row(); item.put("id",Long.valueOf(i)); overflow.add(item); }
        inject(provider, (LabReportDataMapper) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {LabReportDataMapper.class},
                (proxy, method, args) -> overflow));
        assertThrows(IllegalStateException.class, () -> provider.load(context("2026-08"), sectionFor("TASK_DETAIL")));
        String xml = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/main/resources/mapper/lab/LabReportDataMapper.xml")), java.nio.charset.StandardCharsets.UTF_8);
        for (String statementId : Arrays.asList("selectTasks","selectUndoneTasks","selectNextTasks","selectCoordinationTasks","selectBlockedTasks","selectAssets","selectIprs","selectCurrentPerfScores")) {
            String statement = xml.substring(xml.indexOf("<select id=\""+statementId+"\""), xml.indexOf("</select>",xml.indexOf("<select id=\""+statementId+"\"")));
            assertTrue(statement.contains("limit #{sourceFetchLimit}"), statementId);
        }
    }

    @Test
    void goalAuthorityProjectionUsesTheSameServerHardCap() throws Exception {
        GoalProgressProvider provider = new GoalProgressProvider();
        List<GoalHealthFact> overflow = new java.util.AbstractList<GoalHealthFact>() {
            @Override public GoalHealthFact get(int index) { GoalHealthFact fact = new GoalHealthFact(); fact.setGoalId(Long.valueOf(index)); return fact; }
            @Override public int size() { return ReportQueryCriteria.MAX_SOURCE_ROWS + 1; }
        };
        injectDashboard(provider, (LabDashboardMapper) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {LabDashboardMapper.class}, (proxy, method, args) -> overflow));
        assertThrows(IllegalStateException.class, () -> provider.load(context("2026-08"), sectionFor("GOAL_PROGRESS")));
        String xml = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/main/resources/mapper/lab/LabDashboardMapper.xml")), java.nio.charset.StandardCharsets.UTF_8);
        String statement = xml.substring(xml.indexOf("<select id=\"selectGoalHealthFacts\""), xml.indexOf("</select>", xml.indexOf("<select id=\"selectGoalHealthFacts\"")));
        assertTrue(statement.contains("limit #{sourceFetchLimit}"));
    }

    @Test
    void localDateBoundariesAreDeclaredAsSqlDateMapperContracts() throws Exception {
        assertEquals(java.sql.Date.class, ReportQueryCriteria.class.getMethod("getDateStart").getReturnType());
        assertEquals(java.sql.Date.class, ReportQueryCriteria.class.getMethod("getDateEnd").getReturnType());
        java.lang.reflect.Method goalMethod = Arrays.stream(LabDashboardMapper.class.getMethods())
                .filter(method -> method.getName().equals("selectGoalHealthFacts")).findFirst().get();
        assertEquals(4, goalMethod.getParameterTypes().length);
        assertEquals(java.sql.Date.class, goalMethod.getParameterTypes()[1]);
        assertEquals(int.class, goalMethod.getParameterTypes()[3]);

        Configuration configuration = new Configuration();
        configuration.addMapper(LabDashboardMapper.class);
        try (InputStream input = getClass().getResourceAsStream("/mapper/lab/LabDashboardMapper.xml")) {
            assertTrue(input != null);
            new XMLMapperBuilder(input, configuration, "mapper/lab/LabDashboardMapper.xml", configuration.getSqlFragments()).parse();
        }
        Map<String,Object> parameters = new LinkedHashMap<String,Object>();
        parameters.put("year", 2026); parameters.put("asOf", java.sql.Date.valueOf("2026-08-31"));
        parameters.put("scope", new LabAccessContext()); parameters.put("sourceFetchLimit", ReportQueryCriteria.MAX_SOURCE_ROWS + 1);
        org.apache.ibatis.mapping.BoundSql bound = configuration
                .getMappedStatement("com.ailab.system.mapper.LabDashboardMapper.selectGoalHealthFacts").getBoundSql(parameters);
        assertTrue(bound.getParameterMappings().stream().anyMatch(mapping -> "asOf".equals(mapping.getProperty())
                && mapping.getJdbcType() == org.apache.ibatis.type.JdbcType.DATE));
        assertTrue(bound.getParameterMappings().stream().anyMatch(mapping -> "sourceFetchLimit".equals(mapping.getProperty())));
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
            if (provider instanceof PerfSummaryProvider) { assertThrows(SecurityException.class, () -> provider.load(context("2026-08"), sectionFor(provider.getId()))); continue; }
            if (!(provider instanceof ManualSummaryProvider)) inject(provider, mapper);
            if (provider instanceof GoalProgressProvider) injectDashboard((GoalProgressProvider) provider, emptyDashboardMapper());
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
    void standardPeriodPlaceholderBindsContextInsteadOfFilteringRowsAsLiteral() throws Exception {
        TaskDetailProvider provider = new TaskDetailProvider(); inject(provider, mapperWith(row()));
        ReportSectionData data = provider.load(context("2026-08"), sectionWithPeriodPlaceholder());
        assertEquals(1, data.getRows().size());
    }

    @Test
    void reportScopeFactoriesAreNotPublicEscalationApis() {
        for (java.lang.reflect.Method method : ReportAccessScope.class.getMethods()) {
            assertTrue(!method.getName().equals("manager") && !method.getName().equals("lead"), "untrusted callers must not mint privileged scopes");
        }
    }

    @Test
    void providersDeclareAndEnforceSupportedPeriodKinds() {
        ManualSummaryProvider manual = new ManualSummaryProvider();
        for (String period : Arrays.asList("2026-08", "2026-W32", "2026Q3", "2026")) {
            assertEquals("MANUAL_NOTE", manual.load(context(period), manual()).getSectionCode());
        }
        assertThrows(IllegalArgumentException.class, () -> new TaskNextProvider().load(context("2026Q3"), sectionFor("TASK_NEXT")));
    }

    @Test
    void goalProgressUsesDashboardAuthorityAtThePeriodEnd() throws Exception {
        GoalProgressProvider provider = new GoalProgressProvider();
        GoalHealthFact fact = new GoalHealthFact(); fact.setGoalId(7L); fact.setGoalTitle("Annual goal");
        fact.setYear(2026); fact.setActualProgress(new java.math.BigDecimal("61.20"));
        fact.setExpectedProgress(new java.math.BigDecimal("66.00"));
        final Date[] asOf = new Date[1];
        LabDashboardMapper dashboard = (LabDashboardMapper) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {LabDashboardMapper.class}, (proxy, method, args) -> {
                    if (method.getName().equals("selectGoalHealthFacts")) { asOf[0] = (Date) args[1]; return Collections.singletonList(fact); }
                    return Collections.emptyList();
                });
        injectDashboard(provider, dashboard);
        ReportSectionData section = provider.load(context("2026Q3"), sectionFor("GOAL_PROGRESS"));
        assertEquals("2026Q3", section.getRows().get(0).get("period"));
        assertEquals(new java.math.BigDecimal("61.20"), section.getRows().get(0).get("progressRate"));
        assertTrue(asOf[0] instanceof java.sql.Date, "goal asOf must preserve a local calendar date without timezone conversion");
        assertEquals("2026-09-30", new java.text.SimpleDateFormat("yyyy-MM-dd").format(asOf[0]));
    }

    @Test
    void performanceQuarterReadsTheExistingCalibrationPeriodWithoutRecalculation() throws Exception {
        PerfSummaryProvider provider = new PerfSummaryProvider();
        ReportAccessScope scope = trustedManagerScope();
        ReportContext trusted = new ReportContext("2026Q3", "platform", 30005L, Instant.EPOCH, scope, Collections.<String, Object>emptyMap());
        final String[] queriedPeriod = new String[1];
        LabReportDataMapper mapper = (LabReportDataMapper) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {LabReportDataMapper.class}, (proxy, method, args) -> { queriedPeriod[0] = ((ReportQueryCriteria) args[0]).getPerformancePeriod(); return Collections.singletonList(row()); });
        inject(provider, mapper);
        assertEquals(1, provider.load(trusted, perf()).getRows().size());
        assertEquals("2026-Q3", queriedPeriod[0]);
    }

    @Test
    void providersRejectConfiguredFieldsThatTheyDoNotActuallyReturn() {
        assertThrows(IllegalArgumentException.class, () -> new AssetSummaryProvider().load(context("2026-08"), sectionWithFilter("ASSET_SUMMARY", "period", "EQ", "2026-08")));
        assertThrows(IllegalArgumentException.class, () -> new TaskDetailProvider().load(context("2026-08"), sectionWithFilter("TASK_DETAIL", "memberId", "EQ", 30005L)));
    }

    @Test
    void trustedFactoryUsesServerResolvedManagerPermissions() {
        LabAccessService access = org.mockito.Mockito.mock(LabAccessService.class);
        ISysMenuService menus = org.mockito.Mockito.mock(ISysMenuService.class);
        LabAccessContext actor = new LabAccessContext(); actor.setRoleKey("lab_manager"); actor.setBizLine("platform"); actor.setMemberId(30005L);
        org.mockito.Mockito.when(access.context(9L)).thenReturn(actor);
        org.mockito.Mockito.when(menus.selectMenuPermsByUserId(9L)).thenReturn(Collections.singleton("lab:report:sensitive"));
        ReportAccessScope scope = new TrustedReportContextFactory(access, menus).resolve(9L);
        assertEquals(ReportAccessScope.Kind.MANAGER, scope.getKind());
        assertTrue(scope.hasPermission("lab:report:sensitive"));
    }

    @Test
    void renderGroupByProducesDeterministicGroupsForCoordination() throws Exception {
        TaskCoordProvider provider = new TaskCoordProvider(); Map<String,Object> value = row(); value.put("bizLine", "platform"); value.put("coordination", "help");
        inject(provider, mapperWith(value));
        LabReportSection source = new LabReportSection(); source.setSectionCode("LINE_GROUP"); source.setSectionName("Lines"); source.setSectionType("GROUP_TEXT"); source.setDataSource("TASK_COORD"); source.setManualFlag("0"); source.setVisibleFlag("1"); source.setQueryConfigJson("{\"filters\":[]}"); source.setRenderConfigJson("{\"groupBy\":\"bizLine\"}"); source.setStyleConfigJson("{}");
        ReportSectionData data = provider.load(context("2026-08"), new ReportSectionConfig(source));
        assertEquals("platform", ((Map<?, ?>) ((List<?>) data.getSummary().get("groups")).get(0)).get("key"));
    }

    @Test
    void taskPeriodsCompileToTypedWeekAndMonthRanges() {
        ReportQueryCriteria week = new ReportQueryCriteria("2026-W32", context("2026-W32").getAccessScope());
        ReportQueryCriteria quarter = new ReportQueryCriteria("2026Q3", context("2026Q3").getAccessScope());
        assertEquals("2026-08-03", new java.text.SimpleDateFormat("yyyy-MM-dd").format(week.getDateStart()));
        assertEquals("2026-08-09", new java.text.SimpleDateFormat("yyyy-MM-dd").format(week.getDateEnd()));
        assertTrue(week.getDateStart() instanceof java.sql.Date && week.getDateEnd() instanceof java.sql.Date);
        assertEquals("2026-07", quarter.getMonthStart());
        assertEquals("2026-09", quarter.getMonthEnd());
        assertThrows(IllegalArgumentException.class, () -> new ReportQueryCriteria("2025-W53", context("2025").getAccessScope()));
        ReportQueryCriteria week53 = new ReportQueryCriteria("2026-W53", context("2026-W53").getAccessScope());
        assertEquals("2026-12-28", new java.text.SimpleDateFormat("yyyy-MM-dd").format(week53.getDateStart()));
        assertEquals("2027-01-03", new java.text.SimpleDateFormat("yyyy-MM-dd").format(week53.getDateEnd()));
    }

    @Test
    void undoneProjectionKeepsUndoneResultsAndUnconfirmedWorkButExcludesConfirmedDone() throws Exception {
        String xml = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/main/resources/mapper/lab/LabReportDataMapper.xml")), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(xml.contains("(t.result_status='UNDONE' or t.workflow_status in ('DRAFT','ACTIVE','PENDING_REVIEW'))"));
    }

    @Test
    void iprProjectionIsAllReadableAndUsesTypedDateRange() throws Exception {
        String xml = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/main/resources/mapper/lab/LabReportDataMapper.xml")), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(xml.contains("coalesce(i.actual_submit_date,i.planned_submit_date) between #{dateStart} and #{dateEnd}"));
        assertTrue(!xml.substring(xml.indexOf("<select id=\"selectIprs\""), xml.indexOf("</select>", xml.indexOf("<select id=\"selectIprs\""))).contains("iprScope"));
    }

    @Test
    void taskStatRecomputesTotalAfterConfiguredFilterBeforeLimit() throws Exception {
        TaskStatProvider provider = new TaskStatProvider();
        Map<String, Object> active = row(); active.put("status", "ACTIVE"); active.put("total", 2);
        Map<String, Object> confirmed = row(); confirmed.put("status", "CONFIRMED"); confirmed.put("total", 3);
        LabReportDataMapper mapper = (LabReportDataMapper) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {LabReportDataMapper.class},
                (proxy, method, args) -> Arrays.asList(active, confirmed));
        inject(provider, mapper);
        ReportSectionData data = provider.load(context("2026-08"), sectionWithFilter("TASK_STAT", "status", "EQ", "ACTIVE"));
        assertEquals(new java.math.BigDecimal("2"), data.getSummary().get("total"));
        assertEquals(1, data.getSummary().get("matchedCount"));
    }

    @Test
    void taskNextUsesNextMonthAsItsNormalizedPeriodAndPlaceholderFilter() throws Exception {
        TaskNextProvider provider = new TaskNextProvider(); Map<String,Object> value = row(); value.put("period", "2026-09"); value.put("taskPeriod", "2026-09");
        inject(provider, mapperWith(value));
        ReportSectionData data = provider.load(context("2026-08"), sectionWithNextPeriodPlaceholder());
        assertEquals("2026-09", data.getRows().get(0).get("period"));
        assertEquals("2026-09", data.getRows().get(0).get("taskPeriod"));
        assertEquals("2026-09", data.getSummary().get("nextPeriod"));
        String xml = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/main/resources/mapper/lab/LabReportDataMapper.xml")), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(xml.substring(xml.indexOf("<sql id=\"nextTaskColumns\""), xml.indexOf("</sql>", xml.indexOf("<sql id=\"nextTaskColumns\""))).contains("#{nextPeriod} as period"));
    }

    @Test
    void everyProviderPublishesAFixedOrderedSchemaAndRetainsNullableColumns() throws Exception {
        Map<String,Object> sparse = new LinkedHashMap<String,Object>(); sparse.put("id", 1L);
        for (AbstractLabDataSourceProvider provider : providers()) {
            if (provider instanceof ManualSummaryProvider) {
                assertEquals(Collections.singletonList("sectionCode"), provider.getOutputSchema());
                continue;
            }
            if (provider instanceof GoalProgressProvider) {
                GoalHealthFact fact = new GoalHealthFact(); fact.setGoalId(1L);
                injectDashboard((GoalProgressProvider) provider, (LabDashboardMapper) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {LabDashboardMapper.class},
                        (proxy, method, args) -> Collections.singletonList(fact)));
            } else inject(provider, mapperWith(sparse));
            ReportContext providerContext = provider instanceof PerfSummaryProvider
                    ? new ReportContext("2026-08", "platform", 30005L, Instant.EPOCH, trustedManagerScope(), Collections.<String,Object>emptyMap())
                    : context("2026-08");
            ReportSectionData data = provider.load(providerContext, sectionFor(provider.getId()));
            assertEquals(provider.getOutputSchema(), new java.util.ArrayList<String>(data.getRows().get(0).keySet()), provider.getId());
            assertThrows(UnsupportedOperationException.class, () -> data.getRows().get(0).put("id", 2L));
        }
    }

    @Test
    void jdbcTemporalValuesAreNormalizedToImmutableStableIsoScalars() throws Exception {
        TaskDetailProvider detail = new TaskDetailProvider(); Map<String,Object> task = row(); task.put("planDate", java.sql.Date.valueOf("2026-08-03")); inject(detail, mapperWith(task));
        assertEquals("2026-08-03", detail.load(context("2026-08"), sectionFor("TASK_DETAIL")).getRows().get(0).get("planDate"));
        TaskBlockProvider block = new TaskBlockProvider(); Map<String,Object> blocked = row(); blocked.put("blockStartTime", java.sql.Timestamp.from(Instant.parse("2026-08-03T10:15:30Z"))); inject(block, mapperWith(blocked));
        assertEquals("2026-08-03T10:15:30Z", block.load(context("2026-08"), sectionFor("TASK_BLOCK")).getRows().get(0).get("blockStartTime"));
    }

    @Test
    void allSixCanonicalOperatorsAndSortLimitChangeTheReturnedTaskRows() throws Exception {
        TaskDetailProvider provider = new TaskDetailProvider();
        Map<String,Object> one=row(); one.put("id",1L); Map<String,Object> two=row(); two.put("id",2L); Map<String,Object> three=row(); three.put("id",3L);
        inject(provider, (LabReportDataMapper) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {LabReportDataMapper.class},
                (proxy, method, args) -> Arrays.asList(one,two,three)));
        assertEquals(1, provider.load(context("2026-08"), sectionWithJson("TASK_DETAIL", "{\"filters\":[{\"field\":\"id\",\"operator\":\"EQ\",\"value\":2}]}" )).getRows().size());
        assertEquals(2, provider.load(context("2026-08"), sectionWithJson("TASK_DETAIL", "{\"filters\":[{\"field\":\"id\",\"operator\":\"NE\",\"value\":2}]}" )).getRows().size());
        assertEquals(2, provider.load(context("2026-08"), sectionWithJson("TASK_DETAIL", "{\"filters\":[{\"field\":\"id\",\"operator\":\"IN\",\"value\":[1,3]}]}" )).getRows().size());
        assertEquals(2, provider.load(context("2026-08"), sectionWithJson("TASK_DETAIL", "{\"filters\":[{\"field\":\"id\",\"operator\":\"GTE\",\"value\":2}]}" )).getRows().size());
        assertEquals(2, provider.load(context("2026-08"), sectionWithJson("TASK_DETAIL", "{\"filters\":[{\"field\":\"id\",\"operator\":\"LTE\",\"value\":2}]}" )).getRows().size());
        ReportSectionData sorted = provider.load(context("2026-08"), sectionWithJson("TASK_DETAIL", "{\"filters\":[{\"field\":\"id\",\"operator\":\"BETWEEN\",\"value\":[1,2]}],\"sort\":\"id\",\"limit\":1}"));
        assertEquals(1, sorted.getRows().size()); assertEquals(1L, sorted.getRows().get(0).get("id")); assertEquals(2, sorted.getSummary().get("matchedCount"));
    }

    @Test
    void typedFieldSpecsRejectWrongValuesOperatorsAndNullOrdering() throws Exception {
        TaskDetailProvider provider = new TaskDetailProvider(); Map<String,Object> value=row(); value.put("id",null); value.put("block",Boolean.TRUE); inject(provider,mapperWith(value));
        assertThrows(IllegalArgumentException.class, () -> provider.load(context("2026-08"), sectionWithJson("TASK_DETAIL", "{\"filters\":[{\"field\":\"id\",\"operator\":\"EQ\",\"value\":\"2\"}]}")));
        assertThrows(IllegalArgumentException.class, () -> provider.load(context("2026-08"), sectionWithJson("TASK_DETAIL", "{\"filters\":[{\"field\":\"status\",\"operator\":\"GTE\",\"value\":\"ACTIVE\"}]}")));
        assertThrows(IllegalArgumentException.class, () -> provider.load(context("2026-08"), sectionWithJson("TASK_DETAIL", "{\"filters\":[{\"field\":\"id\",\"operator\":\"EQ\",\"value\":null}]}")));
        assertThrows(IllegalArgumentException.class, () -> provider.load(context("2026-08"), sectionWithJson("TASK_DETAIL", "{\"filters\":[{\"field\":\"block\",\"operator\":\"EQ\",\"value\":\"true\"}]}")));
        assertEquals(0, provider.load(context("2026-08"), sectionWithJson("TASK_DETAIL", "{\"filters\":[{\"field\":\"id\",\"operator\":\"LTE\",\"value\":2}]}" )).getRows().size());
    }

    @Test
    void dateFieldSpecsSupportTypedRangesAfterJdbcNormalization() throws Exception {
        TaskDetailProvider provider = new TaskDetailProvider(); Map<String,Object> value=row(); value.put("planDate",java.sql.Date.valueOf("2026-08-15")); inject(provider,mapperWith(value));
        assertEquals(1, provider.load(context("2026-08"), sectionWithJson("TASK_DETAIL", "{\"filters\":[{\"field\":\"planDate\",\"operator\":\"BETWEEN\",\"value\":[\"2026-08-01\",\"2026-08-31\"]}]}" )).getRows().size());
        assertThrows(IllegalArgumentException.class, () -> provider.load(context("2026-08"), sectionWithJson("TASK_DETAIL", "{\"filters\":[{\"field\":\"planDate\",\"operator\":\"GTE\",\"value\":\"August\"}]}")));
    }

    @Test
    void filteredAggregateSummariesUseThePreLimitRows() throws Exception {
        GoalProgressProvider goal = new GoalProgressProvider(); GoalHealthFact first = new GoalHealthFact(); first.setGoalId(1L); first.setActualProgress(new java.math.BigDecimal("40")); GoalHealthFact second = new GoalHealthFact(); second.setGoalId(2L); second.setActualProgress(new java.math.BigDecimal("80"));
        injectDashboard(goal, (LabDashboardMapper) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {LabDashboardMapper.class}, (proxy, method, args) -> Arrays.asList(first, second)));
        assertEquals(new java.math.BigDecimal("40.00"), goal.load(context("2026-08"), sectionWithFilter("GOAL_PROGRESS", "goalId", "EQ", 1)).getSummary().get("averageProgressRate"));
        PerfSummaryProvider perf = new PerfSummaryProvider(); Map<String,Object> low=row(); low.put("memberId",1L); low.put("score",80); Map<String,Object> high=row(); high.put("memberId",2L); high.put("score",100); inject(perf, (LabReportDataMapper) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {LabReportDataMapper.class}, (proxy, method, args) -> Arrays.asList(low,high)));
        ReportContext manager = new ReportContext("2026-08", "platform", 30005L, Instant.EPOCH, trustedManagerScope(), Collections.<String,Object>emptyMap());
        assertEquals(new java.math.BigDecimal("80.00"), perf.load(manager, sectionWithFilter("PERF_SUMMARY", "memberId", "EQ", 1)).getSummary().get("averageScore"));
        AssetSummaryProvider assets = new AssetSummaryProvider(); Map<String,Object> risky=row(); risky.put("assetNo","RISK"); risky.put("status","ACTIVE"); risky.put("assetStage","DEPLOYED"); risky.put("criticalFlag","1"); risky.put("backupOwnerId",null); Map<String,Object> safe=row(); safe.put("assetNo","SAFE"); safe.put("status","ACTIVE"); safe.put("assetStage","DEPLOYED"); safe.put("criticalFlag","1"); safe.put("backupOwnerId",2L); safe.put("backupOwnerStatus","ACTIVE"); inject(assets, (LabReportDataMapper) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {LabReportDataMapper.class}, (proxy, method, args) -> Arrays.asList(risky,safe)));
        assertEquals(1, assets.load(context("2026-08"), sectionWithFilter("ASSET_SUMMARY", "assetNo", "EQ", "RISK")).getSummary().get("singlePointRiskCount"));
    }

    @Test
    void standardSeedTaskDetailAndStatPeriodConfigsAreExecutable() throws Exception {
        String seed = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("..", "sql", "ailab.sql")), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(seed.contains("'TASK_TABLE'") && seed.contains("'SCORE_STAT'"));
        TaskDetailProvider detail = new TaskDetailProvider(); Map<String,Object> weekly=row(); weekly.put("period","2026-W32"); weekly.put("taskPeriod","2026-W32"); inject(detail,mapperWith(weekly));
        assertEquals(1, detail.load(context("2026-W32"), sectionWithPeriodPlaceholder()).getRows().size());
        TaskStatProvider stat = new TaskStatProvider(); Map<String,Object> bucket=row(); bucket.put("period","2026-W32"); bucket.put("total",1); inject(stat,mapperWith(bucket));
        assertEquals(1, stat.load(context("2026-W32"), sectionWithFilter("TASK_STAT", "period", "EQ", "${period}")).getRows().size());
    }

    @Test void taskDetailProviderContract() throws Exception { assertReadOnlyProvider(new TaskDetailProvider()); }
    @Test void taskUndoneProviderContract() throws Exception { assertReadOnlyProvider(new TaskUndoneProvider()); }
    @Test void taskNextProviderContract() throws Exception { assertReadOnlyProvider(new TaskNextProvider()); }
    @Test void taskCoordProviderContract() throws Exception { assertReadOnlyProvider(new TaskCoordProvider()); }
    @Test void taskBlockProviderContract() throws Exception { assertReadOnlyProvider(new TaskBlockProvider()); }
    @Test void assetSummaryProviderContract() throws Exception { assertReadOnlyProvider(new AssetSummaryProvider()); }
    @Test void iprSummaryProviderContract() throws Exception { assertReadOnlyProvider(new IprSummaryProvider()); }

    @Test
    void reportMapperXmlUsesOnlyBoundParametersAndParses() throws Exception {
        Configuration configuration = new Configuration();
        configuration.addMapper(LabReportDataMapper.class);
        try (InputStream input = getClass().getResourceAsStream("/mapper/lab/LabReportDataMapper.xml")) {
            assertTrue(input != null);
            new XMLMapperBuilder(input, configuration, "mapper/lab/LabReportDataMapper.xml", configuration.getSqlFragments()).parse();
        }
        ReportQueryCriteria member = new ReportQueryCriteria("2026-08", context("2026-08").getAccessScope());
        for (String statement : Arrays.asList("selectTasks", "selectUndoneTasks", "selectNextTasks", "selectCoordinationTasks", "selectBlockedTasks", "selectTaskStats", "selectAssets", "selectIprs", "selectCurrentPerfScores")) {
            String sql = configuration.getMappedStatement("com.ailab.system.mapper.LabReportDataMapper." + statement).getBoundSql(member).getSql();
            assertTrue(!sql.trim().isEmpty()); assertTrue(!sql.contains("${"));
        }
        assertTrue(configuration.getMappedStatement("com.ailab.system.mapper.LabReportDataMapper.selectTasks").getBoundSql(member).getSql().contains("? as period"));
        assertTrue(configuration.getMappedStatement("com.ailab.system.mapper.LabReportDataMapper.selectTaskStats").getBoundSql(member).getSql().contains("? as period"));
        ReportQueryCriteria next = new ReportQueryCriteria("2026-08", member.getScope());
        assertEquals("nextPeriod", configuration.getMappedStatement("com.ailab.system.mapper.LabReportDataMapper.selectNextTasks")
                .getBoundSql(next).getParameterMappings().get(0).getProperty(), "next rows must be projected and filtered as the next month");
    }

    @Test
    void mysqlIntegrationFixtureExecutesAllNineReportMapperStatements() throws Exception {
        String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("..","ruoyi-admin","src","test","java","com","ailab","system","mapper","LabMapperMySqlIT.java")), java.nio.charset.StandardCharsets.UTF_8);
        for(String method:Arrays.asList("selectTasks","selectUndoneTasks","selectNextTasks","selectCoordinationTasks","selectBlockedTasks","selectTaskStats","selectAssets","selectIprs","selectCurrentPerfScores"))
            assertTrue(source.contains("reportDataMapper."+method+"("),method);
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
        return sensitivePerf("lab:report:sensitive");
    }
    private ReportSectionConfig sensitivePerf(String permission) {
        LabReportSection section = new LabReportSection();
        section.setSectionCode("PERF"); section.setSectionName("Performance"); section.setSectionType("STAT");
        section.setDataSource(ReportConfigCatalog.PERF_SUMMARY); section.setManualFlag("0"); section.setVisibleFlag("1");
        section.setSensitiveFlag("1"); section.setSensitivePermission(permission);
        section.setQueryConfigJson("{}"); section.setRenderConfigJson("{}"); section.setStyleConfigJson("{}");
        return new ReportSectionConfig(section);
    }

    private List<AbstractLabDataSourceProvider> providers() {
        return Arrays.asList(new GoalProgressProvider(), new TaskDetailProvider(), new TaskStatProvider(), new TaskUndoneProvider(),
                new TaskNextProvider(), new TaskCoordProvider(), new TaskBlockProvider(), new AssetSummaryProvider(),
                new IprSummaryProvider(), new PerfSummaryProvider(), new ManualSummaryProvider());
    }
    private Map<String, Object> row() {
        Map<String, Object> row = new LinkedHashMap<String, Object>(); row.put("id", 1L); row.put("period", "2026-08"); row.put("status", "ACTIVE");
        row.put("resultStatus", "DOING"); row.put("total", 1); row.put("progressRate", 50); row.put("score", 90); return row;
    }
    private void inject(AbstractLabDataSourceProvider provider, LabReportDataMapper mapper) throws Exception {
        Field field = AbstractLabDataSourceProvider.class.getDeclaredField("mapper"); field.setAccessible(true); field.set(provider, mapper);
    }
    private void injectDashboard(GoalProgressProvider provider, LabDashboardMapper mapper) throws Exception {
        Field field = GoalProgressProvider.class.getDeclaredField("dashboardMapper"); field.setAccessible(true); field.set(provider, mapper);
    }
    private ReportAccessScope trustedManagerScope(String... permissions) throws Exception {
        java.lang.reflect.Method method = ReportAccessScope.class.getDeclaredMethod("manager", java.util.Collection.class);
        method.setAccessible(true);
        return (ReportAccessScope) method.invoke(null, permissions.length == 0
                ? Collections.singleton("lab:report:sensitive") : Arrays.asList(permissions));
    }
    private LabReportDataMapper mapperWith(Map<String, Object> value) {
        return (LabReportDataMapper) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {LabReportDataMapper.class},
                (proxy, method, args) -> Collections.<Map<String, Object>>singletonList(value));
    }
    private LabDashboardMapper emptyDashboardMapper() {
        return (LabDashboardMapper) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {LabDashboardMapper.class},
                (proxy, method, args) -> Collections.emptyList());
    }
    private void assertReadOnlyProvider(AbstractLabDataSourceProvider provider) throws Exception {
        inject(provider, mapperWith(row()));
        ReportSectionData data = provider.load(context("2026-08"), sectionFor(provider.getId()));
        assertEquals(1, data.getRows().size());
        assertThrows(UnsupportedOperationException.class, () -> data.getRows().get(0).put("mutate", true));
    }
    private ReportSectionConfig sectionWithPeriodPlaceholder() {
        LabReportSection section = new LabReportSection(); section.setSectionCode("TASK_DETAIL"); section.setSectionName("Tasks"); section.setSectionType("TABLE"); section.setDataSource("TASK_DETAIL");
        section.setManualFlag("0"); section.setVisibleFlag("1"); section.setQueryConfigJson("{\"filters\":[{\"field\":\"period\",\"operator\":\"EQ\",\"value\":\"${period}\"}]}"); section.setRenderConfigJson("{}"); section.setStyleConfigJson("{}"); return new ReportSectionConfig(section);
    }
    private ReportSectionConfig sectionWithNextPeriodPlaceholder() {
        LabReportSection section = new LabReportSection(); section.setSectionCode("TASK_NEXT"); section.setSectionName("Next"); section.setSectionType("TABLE"); section.setDataSource("TASK_NEXT");
        section.setManualFlag("0"); section.setVisibleFlag("1"); section.setQueryConfigJson("{\"filters\":[{\"field\":\"period\",\"operator\":\"EQ\",\"value\":\"${nextPeriod}\"}]}"); section.setRenderConfigJson("{}"); section.setStyleConfigJson("{}"); return new ReportSectionConfig(section);
    }
    private ReportSectionConfig sectionWithFilter(String providerId, String field, String operator, Object value) {
        LabReportSection section = new LabReportSection(); section.setSectionCode(providerId); section.setSectionName(providerId); section.setSectionType(providerId.equals("ASSET_SUMMARY") || providerId.equals("TASK_STAT") || providerId.equals("GOAL_PROGRESS") || providerId.equals("PERF_SUMMARY") ? "STAT" : "TABLE"); section.setDataSource(providerId);
        String jsonValue=value==null?"null":value instanceof Number||value instanceof Boolean?String.valueOf(value):"\""+value+"\"";
        section.setManualFlag("0"); section.setVisibleFlag("1"); section.setQueryConfigJson("{\"filters\":[{\"field\":\"" + field + "\",\"operator\":\"" + operator + "\",\"value\":" + jsonValue + "}]}"); section.setRenderConfigJson("{}"); section.setStyleConfigJson("{}"); return new ReportSectionConfig(section);
    }
    private ReportSectionConfig sectionWithJson(String providerId, String queryConfig) {
        LabReportSection section = new LabReportSection(); section.setSectionCode(providerId); section.setSectionName(providerId); section.setDataSource(providerId); section.setManualFlag("0"); section.setVisibleFlag("1");
        section.setSectionType("TABLE"); section.setQueryConfigJson(queryConfig); section.setRenderConfigJson("{}"); section.setStyleConfigJson("{}"); return new ReportSectionConfig(section);
    }
    private ReportSectionConfig sectionFor(String providerId) {
        if (ReportConfigCatalog.MANUAL_SUMMARY.equals(providerId)) return manual();
        LabReportSection section = new LabReportSection(); section.setSectionCode(providerId); section.setSectionName(providerId);
        section.setDataSource(providerId); section.setManualFlag("0"); section.setVisibleFlag("1"); section.setQueryConfigJson("{}"); section.setRenderConfigJson("{}"); section.setStyleConfigJson("{}");
        section.setSectionType(ReportConfigCatalog.PERF_SUMMARY.equals(providerId) || ReportConfigCatalog.GOAL_PROGRESS.equals(providerId) || ReportConfigCatalog.TASK_STAT.equals(providerId) ? "STAT" : "TABLE");
        return new ReportSectionConfig(section);
    }
}
