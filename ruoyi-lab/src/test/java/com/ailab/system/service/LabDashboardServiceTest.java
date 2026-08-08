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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LabDashboardServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-15T01:00:00Z"), ZoneId.of("Asia/Shanghai"));
    @Mock private LabDashboardMapper mapper;
    @Mock private LabAccessService access;
    private LabDashboardService service;

    @BeforeEach
    void setUp() { service = new LabDashboardServiceImpl(mapper, access, CLOCK); }

    @Test
    void healthThresholdsUseMostSevereRiskAtFiveAndFifteenPointBoundaries() {
        assertEquals("GREEN", service.calculateHealth(fact("50", "45", 0, false, false)).getStatus());
        assertEquals("YELLOW", service.calculateHealth(fact("50.01", "45", 0, false, false)).getStatus());
        assertEquals("YELLOW", service.calculateHealth(fact("60", "45", 0, false, false)).getStatus());
        assertEquals("RED", service.calculateHealth(fact("60.01", "45", 0, false, false)).getStatus());
        assertEquals("RED", service.calculateHealth(fact("45", "45", 0, false, true)).getStatus());
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
        when(mapper.selectGoalHealthFacts(eq(2026), any(), eq(manager)))
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
        assertEquals(Boolean.TRUE, result.getKpis().get(4).getDrillDownFilters().get("singlePointRisk"));
        assertFalse(result.getKpis().get(4).getDrillDownFilters().containsKey("backupMissing"));
        assertEquals(Arrays.asList("DRAFT", "ACTIVE"), result.getKpis().get(2).getDrillDownFilters().get("workflowStatuses"));
        assertEquals(Boolean.TRUE, result.getKpis().get(2).getDrillDownFilters().get("overdueOrPending"));
        assertEquals("1", result.getKpis().get(3).getDrillDownFilters().get("currentBlockFlag"));
        assertTrue(result.getKpis().get(3).getDrillDownFilters().get("blockStartBefore") instanceof Date);
        assertFalse(result.getKpis().get(3).getDrillDownFilters().containsKey("blockFlag"));
    }

    @Test
    void historicalPeriodDrivesGoalYearTrendAndEndOfRequestedMonthAsOf() {
        LabAccessContext manager = manager();
        when(mapper.selectGoalHealthFacts(eq(2025), any(), eq(manager))).thenReturn(Collections.<GoalHealthFact>emptyList());
        when(mapper.selectKpiFact(eq("2025-04"), any(), eq(manager))).thenReturn(new DashboardKpiFact());
        when(mapper.selectGoalProgressTrend(eq(2025), any(), eq(manager))).thenReturn(Collections.<GoalTrendPoint>emptyList());
        emptyDashboardQueriesFor("2025-04", manager);

        service.getOverview("2025-04", 1L);

        ArgumentCaptor<Date> asOf = ArgumentCaptor.forClass(Date.class);
        verify(mapper).selectGoalHealthFacts(eq(2025), asOf.capture(), eq(manager));
        assertEquals(LocalDate.of(2025, 4, 30), asOf.getValue().toInstant().atZone(CLOCK.getZone()).toLocalDate());
        verify(mapper).selectGoalProgressTrend(eq(2025), any(), eq(manager));
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
        when(mapper.selectGoalHealthFacts(eq(2026), any(), eq(lead))).thenReturn(Collections.<GoalHealthFact>emptyList());
        when(mapper.selectKpiFact(eq("2026-08"), any(), eq(lead))).thenReturn(new DashboardKpiFact());
        emptyDashboardQueriesFor(lead);

        service.getOverview("2026-08", 2L);

        verify(mapper).selectMemberLoads(eq("2026-08"), any(), any(), eq(lead));
        verify(mapper).selectGoalHealthFacts(eq(2026), any(), eq(lead));
    }

    @Test
    void memberDashboardLoadsOnlyPublicIprAndFinalReportsWithoutHeatmapCoordinationOrPerformance() {
        LabAccessContext member = context(3L, 103L, LabAccessServiceImpl.MEMBER, "algorithm");
        when(access.context(3L)).thenReturn(member);
        when(mapper.selectGoalHealthFacts(eq(2026), any(), eq(member))).thenReturn(Collections.<GoalHealthFact>emptyList());
        when(mapper.selectKpiFact(eq("2026-08"), any(), eq(member))).thenReturn(new DashboardKpiFact());
        when(mapper.selectTaskStatusDistribution("2026-08", member)).thenReturn(Collections.emptyList());
        when(mapper.selectRecentIpr(any(), eq(member))).thenReturn(Collections.emptyList());
        when(mapper.selectRecentReports("2026-08", member)).thenReturn(Collections.emptyList());
        when(mapper.selectLatestReport("2026-08", member)).thenReturn(null);

        DashboardOverview result = service.getOverview("2026-08", 3L);

        assertTrue(result.getMemberLoads().isEmpty());
        assertTrue(result.getRecentReports().isEmpty());
        verify(mapper, never()).selectMemberLoads(any(), any(), any(), any());
        verify(mapper).selectRecentReports("2026-08", member);
        verify(mapper).selectLatestReport("2026-08", member);
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
        when(mapper.selectGoalHealthFacts(eq(2026), any(), eq(manager))).thenReturn(Collections.<GoalHealthFact>emptyList());
        when(mapper.selectKpiFact(eq("2026-08"), any(), eq(manager))).thenReturn(new DashboardKpiFact());
        GoalTrendPoint point = new GoalTrendPoint(); point.setPeriod("2026-08"); point.setExpectedProgress(new BigDecimal("55")); point.setActualProgress(new BigDecimal("40"));
        when(mapper.selectGoalProgressTrend(eq(2026), any(), eq(manager))).thenReturn(Collections.singletonList(point));
        emptyDashboardQueriesFor(manager);

        DashboardOverview result = service.getOverview("2026-08", 1L);

        assertEquals(1, result.getGoalTrend().size());
        assertTrue(result.getGoalTrend().get(0).getDefinition().contains("累计"));
        assertEquals(Instant.parse("2026-08-15T01:00:00Z"), result.getGoalTrend().get(0).getLastUpdated().toInstant());
        assertEquals("2026-08", result.getGoalTrend().get(0).getDrillDownFilters().get("period"));
    }

    @Test
    void latestReportCarriesTheSameExplainableActionMetadataAsTheReportList() {
        LabAccessContext manager = manager();
        when(mapper.selectGoalHealthFacts(eq(2026), any(), eq(manager))).thenReturn(Collections.<GoalHealthFact>emptyList());
        when(mapper.selectKpiFact(eq("2026-08"), any(), eq(manager))).thenReturn(new DashboardKpiFact());
        emptyDashboardQueriesFor(manager);
        DashboardActionItem latest = new DashboardActionItem(); latest.setId(77L); latest.setTitle("R-2026-08");
        when(mapper.selectLatestReport("2026-08", manager)).thenReturn(latest);

        DashboardActionItem result = service.getOverview("2026-08", 1L).getLatestReport();

        assertEquals("2026-08", result.getPeriod());
        assertFalse(result.getDefinition().isEmpty());
        assertEquals(Instant.parse("2026-08-15T01:00:00Z"), result.getLastUpdated().toInstant());
        assertEquals(77L, result.getDrillDownFilters().get("id"));
        assertEquals("2026-08", result.getDrillDownFilters().get("period"));
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
        when(mapper.selectRecentReports(eq(period), eq(context))).thenReturn(Collections.emptyList());
        when(mapper.selectLatestReport(eq(period), eq(context))).thenReturn(null);
        if (LabAccessServiceImpl.MANAGER.equals(context.getRoleKey())) {
            when(mapper.selectPerformanceSummary(eq(period), eq(context))).thenReturn(Collections.emptyList());
        }
    }

    private LabAccessContext context(Long user, Long member, String role, String line) {
        LabAccessContext value = new LabAccessContext(); value.setUserId(user); value.setMemberId(member);
        value.setRoleKey(role); value.setBizLine(line); return value;
    }
}
