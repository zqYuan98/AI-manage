package com.ailab.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailab.system.dto.DashboardKpiFact;
import com.ailab.system.dto.DashboardActionItem;
import com.ailab.system.dto.DashboardOverview;
import com.ailab.system.dto.GoalHealth;
import com.ailab.system.dto.GoalHealthFact;
import com.ailab.system.dto.GoalTrendPoint;
import com.ailab.system.dto.LabAccessContext;
import com.ailab.system.dto.CommitmentProgress;
import com.ailab.system.dto.ProgressComparison;
import com.ailab.system.mapper.LabDashboardMapper;
import com.ailab.system.service.impl.LabAccessServiceImpl;
import com.ailab.system.service.impl.LabDashboardServiceImpl;
import com.ruoyi.common.exception.ServiceException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.system.service.ISysMenuService;

@ExtendWith(MockitoExtension.class)
class LabDashboardServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-15T01:00:00Z"), ZoneId.of("Asia/Shanghai"));
    @Mock private LabDashboardMapper mapper;
    @Mock private LabAccessService access;
    @Mock private ISysMenuService menus;
    private LabDashboardService service;

    @BeforeEach
    void setUp() { service = new LabDashboardServiceImpl(mapper, access, menus, CLOCK); }

    @Test
    void healthThresholdsUseMostSevereRiskAtFiveAndFifteenPointBoundaries() {
        assertEquals("GREEN", service.calculateHealth(fact("50", "45", 0, false, false)).getStatus());
        assertEquals("YELLOW", service.calculateHealth(fact("50.01", "45", 0, false, false)).getStatus());
        assertEquals("YELLOW", service.calculateHealth(fact("60", "45", 0, false, false)).getStatus());
        assertEquals("RED", service.calculateHealth(fact("60.01", "45", 0, false, false)).getStatus());
        assertEquals("RED", service.calculateHealth(fact("45", "45", 0, false, true)).getStatus());
    }

    @Test
    void dashboardComparisonNamesLegacyAndOperationalProgressWithoutSilentCutover() {
        GoalHealthFact legacy = fact("50", "45", 0, false, false);
        CommitmentProgress named = new CommitmentProgress(); named.setExecutionAsOf(Date.from(CLOCK.instant()));
        named.setOperationalProgress(new BigDecimal("62.50")); named.setFormalProgress(new BigDecimal("40.00"));
        named.setCalculationVersion("COMMITMENT_PROGRESS_V1");

        ProgressComparison comparison = service.compareProgress(legacy, named);

        assertEquals("LEGACY", comparison.getActiveProjection());
        assertEquals(new BigDecimal("45"), comparison.getLegacyProgress());
        assertEquals(new BigDecimal("62.50"), comparison.getNamedProgress().getOperationalProgress());
    }

    @Test
    void healthThresholdsTreatSevenAndFourteenDayBlocksAsYellowAndOverFourteenAsRed() {
        assertEquals("GREEN", service.calculateHealth(fact("45", "45", 6, false, false)).getStatus());
        assertEquals("YELLOW", service.calculateHealth(fact("45", "45", 7, false, false)).getStatus());
        assertEquals("YELLOW", service.calculateHealth(fact("45", "45", 14, false, false)).getStatus());
        assertEquals("RED", service.calculateHealth(fact("45", "45", 15, false, false)).getStatus());
        assertEquals("YELLOW", service.calculateHealth(fact("45", "45", 0, true, false)).getStatus());
    }

    @Test
    void dashboardReturnsFiveActionableKpisWithDefinitionsPeriodsTimestampsAndFilters() {
        LabAccessContext manager = manager();
        when(mapper.selectGoalHealthFacts(eq(2026), any(), eq(manager), eq(LabDashboardMapper.MAX_GOAL_HEALTH_ROWS + 1)))
                .thenReturn(Collections.singletonList(fact("50", "40", 8, false, false)));
        DashboardKpiFact kpi = new DashboardKpiFact(); kpi.setKeyTaskCompletionRate(new BigDecimal("72.50"));
        kpi.setOverdueOrPendingCount(3); kpi.setBlockedOverSevenCount(2); kpi.setAssetsWithoutBackupCount(1);
        when(mapper.selectKpiFact(eq("2026-08"), any(), eq(manager))).thenReturn(kpi);
        emptyDashboardQueriesFor(manager);

        DashboardOverview result = service.getOverview("2026-08", 1L);

        assertEquals(5, result.getKpis().size());
        result.getKpis().forEach(metric -> {
            assertEquals("2026-08", metric.getPeriod());
            assertFalse(metric.getDefinition().isEmpty());
            assertEquals(Instant.parse("2026-08-15T01:00:00Z"), metric.getLastUpdated().toInstant());
            assertFalse(metric.getDrillDownFilters().isEmpty());
        });
        assertEquals("YELLOW", result.getGoalHealth().get(0).getStatus());
        assertEquals(Collections.singletonList(11L), result.getKpis().get(0).getDrillDownFilters().get("goalIds"));
        assertEquals(Boolean.TRUE, result.getKpis().get(0).getDrillDownFilters().get("goalIdsFilter"));
        assertEquals("month", result.getKpis().get(1).getDrillDownFilters().get("taskLevel"));
        assertEquals(Boolean.TRUE, result.getKpis().get(4).getDrillDownFilters().get("singlePointRisk"));
        assertTrue(result.getKpis().get(4).getDefinition().contains("关键资产")
                        && result.getKpis().get(4).getDefinition().contains("当前有效且已部署/验收"),
                "asset KPI metadata must disclose the same risk-relevance predicate as the server policy");
        assertFalse(result.getKpis().get(4).getDrillDownFilters().containsKey("backupMissing"));
        assertFalse(result.getKpis().get(4).getDrillDownFilters().containsKey("status"),
                "status=ACTIVE would incorrectly hide inactive critical assets from the exact drill");
        assertEquals(Arrays.asList("DRAFT", "ACTIVE"), result.getKpis().get(2).getDrillDownFilters().get("workflowStatuses"));
        assertEquals(Boolean.TRUE, result.getKpis().get(2).getDrillDownFilters().get("overdueOrPending"));
        assertEquals("1", result.getKpis().get(3).getDrillDownFilters().get("currentBlockFlag"));
        assertTrue(result.getKpis().get(3).getDrillDownFilters().get("blockStartBefore") instanceof Date);
        assertFalse(result.getKpis().get(3).getDrillDownFilters().containsKey("blockFlag"));
    }

    @Test
    void historicalPeriodDrivesGoalYearTrendAndEndOfRequestedMonthAsOf() {
        LabAccessContext manager = manager();
        when(mapper.selectGoalHealthFacts(eq(2025), any(), eq(manager), eq(LabDashboardMapper.MAX_GOAL_HEALTH_ROWS + 1))).thenReturn(Collections.<GoalHealthFact>emptyList());
        when(mapper.selectKpiFact(eq("2025-04"), any(), eq(manager))).thenReturn(new DashboardKpiFact());
        when(mapper.selectGoalProgressTrend(eq(2025), any(), eq(manager))).thenReturn(Collections.<GoalTrendPoint>emptyList());
        emptyDashboardQueriesFor("2025-04", manager);

        service.getOverview("2025-04", 1L);

        ArgumentCaptor<java.sql.Date> asOf = ArgumentCaptor.forClass(java.sql.Date.class);
        verify(mapper).selectGoalHealthFacts(eq(2025), asOf.capture(), eq(manager), eq(LabDashboardMapper.MAX_GOAL_HEALTH_ROWS + 1));
        assertEquals(LocalDate.of(2025, 4, 30), asOf.getValue().toLocalDate());
        verify(mapper).selectGoalProgressTrend(eq(2025), any(), eq(manager));
    }

    @Test
    void dashboardRejectsAnOversizedGoalProjectionBeforeRendering() {
        LabAccessContext manager = manager();
        when(mapper.selectGoalHealthFacts(eq(2026), any(), eq(manager),
                eq(LabDashboardMapper.MAX_GOAL_HEALTH_ROWS + 1)))
                .thenReturn(Collections.nCopies(LabDashboardMapper.MAX_GOAL_HEALTH_ROWS + 1, new GoalHealthFact()));
        ServiceException error = assertThrows(ServiceException.class, () -> service.getOverview("2026-08", 1L));
        assertTrue(error.getMessage().contains("row limit"));
    }

    @Test
    void malformedPeriodReportsThePeriodField() {
        ServiceException error = assertThrows(ServiceException.class, () -> service.getOverview("2026-8", 1L));
        assertTrue(error.getMessage().toLowerCase().contains("period"));
    }

    @Test
    void leadScopeIsTrustedBusinessLineAndGoalsRemainGloballyReadable() {
        LabAccessContext lead = context(2L, 102L, LabAccessServiceImpl.LEAD, "algorithm");
        when(access.context(2L)).thenReturn(lead);
        when(mapper.selectGoalHealthFacts(eq(2026), any(), eq(lead), eq(LabDashboardMapper.MAX_GOAL_HEALTH_ROWS + 1))).thenReturn(Collections.<GoalHealthFact>emptyList());
        when(mapper.selectKpiFact(eq("2026-08"), any(), eq(lead))).thenReturn(new DashboardKpiFact());
        emptyDashboardQueriesFor(lead);

        service.getOverview("2026-08", 2L);

        verify(mapper).selectMemberLoads(eq("2026-08"), any(), any(), eq(lead));
        verify(mapper).selectGoalHealthFacts(eq(2026), any(), eq(lead), eq(LabDashboardMapper.MAX_GOAL_HEALTH_ROWS + 1));
    }

    @Test
    void memberDashboardLoadsOnlyPublicIprAndFinalReportsWithoutHeatmapCoordinationOrPerformance() {
        LabAccessContext member = context(3L, 103L, LabAccessServiceImpl.MEMBER, "algorithm");
        when(access.context(3L)).thenReturn(member);
        when(mapper.selectGoalHealthFacts(eq(2026), any(), eq(member), eq(LabDashboardMapper.MAX_GOAL_HEALTH_ROWS + 1))).thenReturn(Collections.<GoalHealthFact>emptyList());
        when(mapper.selectKpiFact(eq("2026-08"), any(), eq(member))).thenReturn(new DashboardKpiFact());
        when(mapper.selectTaskStatusDistribution("2026-08", member)).thenReturn(Collections.emptyList());
        when(mapper.selectRecentIpr(any(), eq(member))).thenReturn(Collections.emptyList());
        when(mapper.selectRecentReports("2026-08", member,false)).thenReturn(Collections.emptyList());
        when(mapper.selectLatestReport("2026-08", member,false)).thenReturn(null);

        DashboardOverview result = service.getOverview("2026-08", 3L);

        assertTrue(result.getMemberLoads().isEmpty());
        assertTrue(result.getRecentReports().isEmpty());
        verify(mapper, never()).selectMemberLoads(any(), any(), any(), any());
        verify(mapper).selectRecentReports("2026-08", member,false);
        verify(mapper).selectLatestReport("2026-08", member,false);
        verify(mapper, never()).selectPerformanceSummary(any(), any());
        verify(mapper).selectRecentIpr(any(), eq(member));
        verify(mapper, never()).selectCoordinationItems(any(), any());
    }

    @Test
    void goalHealthCarriesExplainableCalculationContract() {
        GoalHealth health = service.calculateHealth(fact("65", "40", 14, true, false));
        assertEquals(new BigDecimal("25.00"), health.getLag());
        assertEquals("RED", health.getStatus());
        assertTrue(health.getDefinition().contains("到期子项权重"));
        assertEquals("2026", health.getPeriod());
    }

    @Test
    void goalTrendIsReturnedWithPeriodDefinitionTimestampAndDrillDown() {
        LabAccessContext manager = manager();
        when(mapper.selectGoalHealthFacts(eq(2026), any(), eq(manager), eq(LabDashboardMapper.MAX_GOAL_HEALTH_ROWS + 1))).thenReturn(Collections.<GoalHealthFact>emptyList());
        when(mapper.selectKpiFact(eq("2026-08"), any(), eq(manager))).thenReturn(new DashboardKpiFact());
        GoalTrendPoint point = new GoalTrendPoint(); point.setGoalId(11L); point.setGoalName("Annual goal"); point.setPeriod("2026-08"); point.setExpectedProgress(new BigDecimal("55")); point.setActualProgress(new BigDecimal("40"));
        when(mapper.selectGoalProgressTrend(eq(2026), any(), eq(manager))).thenReturn(Collections.singletonList(point));
        emptyDashboardQueriesFor(manager);

        DashboardOverview result = service.getOverview("2026-08", 1L);

        assertEquals(1, result.getGoalTrend().size());
        assertTrue(result.getGoalTrend().get(0).getDefinition().contains("累计"));
        assertEquals(Instant.parse("2026-08-15T01:00:00Z"), result.getGoalTrend().get(0).getLastUpdated().toInstant());
        assertEquals(Long.valueOf(11L), result.getGoalTrend().get(0).getDrillDownFilters().get("goalId"));
        assertEquals("2026-08", result.getGoalTrend().get(0).getDrillDownFilters().get("periodTo"));
        assertEquals("month", result.getGoalTrend().get(0).getDrillDownFilters().get("taskLevel"));
        assertEquals("key", result.getGoalTrend().get(0).getDrillDownFilters().get("taskType"));
        assertTrue(result.getGoalTrend().get(0).getDrillDownFilters().get("asOf") instanceof Date);
        assertFalse(result.getGoalTrend().get(0).getDrillDownFilters().containsKey("period"),
                "a cumulative point must not drill with period equality");
    }

    @Test
    void goalTrendDtoRepresentsTwoIndependentSixtyPercentGoalSeries() throws Exception {
        GoalTrendPoint first = new GoalTrendPoint();
        GoalTrendPoint second = new GoalTrendPoint();
        java.lang.reflect.Method setGoalId = GoalTrendPoint.class.getMethod("setGoalId", Long.class);
        java.lang.reflect.Method setGoalName = GoalTrendPoint.class.getMethod("setGoalName", String.class);
        java.lang.reflect.Method getGoalId = GoalTrendPoint.class.getMethod("getGoalId");
        setGoalId.invoke(first, 11L); setGoalName.invoke(first, "Goal A"); first.setActualProgress(new BigDecimal("60"));
        setGoalId.invoke(second, 12L); setGoalName.invoke(second, "Goal B"); second.setActualProgress(new BigDecimal("60"));

        List<GoalTrendPoint> points = Arrays.asList(first, second);
        Set<Object> series = new HashSet<Object>();
        for (GoalTrendPoint point : points) {
            series.add(getGoalId.invoke(point));
            assertTrue(point.getActualProgress().compareTo(new BigDecimal("100")) <= 0);
        }
        assertEquals(2, series.size(), "60% + 60% must remain two chart series instead of one 120% point");
    }

    @Test
    void latestReportCarriesTheSameExplainableActionMetadataAsTheReportList() {
        LabAccessContext manager = manager();
        when(mapper.selectGoalHealthFacts(eq(2026), any(), eq(manager), eq(LabDashboardMapper.MAX_GOAL_HEALTH_ROWS + 1))).thenReturn(Collections.<GoalHealthFact>emptyList());
        when(mapper.selectKpiFact(eq("2026-08"), any(), eq(manager))).thenReturn(new DashboardKpiFact());
        emptyDashboardQueriesFor(manager);
        DashboardActionItem latest = new DashboardActionItem(); latest.setId(77L); latest.setTitle("R-2026-08");
        when(mapper.selectLatestReport("2026-08", manager,false)).thenReturn(latest);

        DashboardActionItem result = service.getOverview("2026-08", 1L).getLatestReport();

        assertEquals("2026-08", result.getPeriod());
        assertFalse(result.getDefinition().isEmpty());
        assertEquals(Instant.parse("2026-08-15T01:00:00Z"), result.getLastUpdated().toInstant());
        assertEquals(77L, result.getDrillDownFilters().get("id"));
        assertEquals("2026-08", result.getDrillDownFilters().get("period"));
    }

    @Test
    void managerDashboardReportQueriesReceiveTheCurrentSensitivePermissionSnapshot() {
        LabAccessContext manager=manager();when(menus.selectMenuPermsByUserId(1L)).thenReturn(Collections.<String>emptySet());when(mapper.selectGoalHealthFacts(eq(2026),any(),eq(manager),eq(LabDashboardMapper.MAX_GOAL_HEALTH_ROWS+1))).thenReturn(Collections.<GoalHealthFact>emptyList());when(mapper.selectKpiFact(eq("2026-08"),any(),eq(manager))).thenReturn(new DashboardKpiFact());emptyDashboardQueriesFor(manager);

        service.getOverview("2026-08",1L);

        verify(mapper).selectRecentReports("2026-08",manager,false);verify(mapper).selectLatestReport("2026-08",manager,false);
    }

    private GoalHealthFact fact(String expected, String actual, int blockDays, boolean delayed, boolean overdue) {
        GoalHealthFact value = new GoalHealthFact(); value.setGoalId(11L); value.setGoalTitle("Annual goal"); value.setYear(2026);
        value.setExpectedProgress(new BigDecimal(expected)); value.setActualProgress(new BigDecimal(actual));
        value.setMaxOpenBlockDays(blockDays); value.setDelayedFocusTask(delayed); value.setOverdueUnsubmittedFocusTask(overdue);
        return value;
    }

    private LabAccessContext manager() {
        LabAccessContext manager = context(1L, 901L, LabAccessServiceImpl.MANAGER, "manage");
        when(access.context(1L)).thenReturn(manager);
        return manager;
    }

    private void emptyDashboardQueriesFor(LabAccessContext context) { emptyDashboardQueriesFor("2026-08", context); }
    private void emptyDashboardQueriesFor(String period, LabAccessContext context) {
        when(mapper.selectTaskStatusDistribution(eq(period), eq(context))).thenReturn(Collections.emptyList());
        when(mapper.selectMemberLoads(eq(period), any(), any(), eq(context))).thenReturn(Collections.emptyList());
        when(mapper.selectCoordinationItems(eq(period), eq(context))).thenReturn(Collections.emptyList());
        when(mapper.selectRecentIpr(any(), eq(context))).thenReturn(Collections.emptyList());
        when(mapper.selectRecentReports(eq(period), eq(context),eq(false))).thenReturn(Collections.emptyList());
        when(mapper.selectLatestReport(eq(period), eq(context),eq(false))).thenReturn(null);
        if (LabAccessServiceImpl.MANAGER.equals(context.getRoleKey())) {
            when(mapper.selectPerformanceSummary(eq(period), eq(context))).thenReturn(Collections.emptyList());
        }
    }

    private LabAccessContext context(Long user, Long member, String role, String line) {
        LabAccessContext value = new LabAccessContext(); value.setUserId(user); value.setMemberId(member);
        value.setRoleKey(role); value.setBizLine(line); return value;
    }
}
