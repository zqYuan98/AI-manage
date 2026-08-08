package com.ailab.system.service.impl;

import com.ailab.system.dto.DashboardActionItem;
import com.ailab.system.dto.DashboardCountItem;
import com.ailab.system.dto.DashboardKpiFact;
import com.ailab.system.dto.DashboardMetric;
import com.ailab.system.dto.DashboardOverview;
import com.ailab.system.dto.GoalHealth;
import com.ailab.system.dto.GoalHealthFact;
import com.ailab.system.dto.GoalTrendPoint;
import com.ailab.system.dto.LabAccessContext;
import com.ailab.system.dto.MemberLoad;
import com.ailab.system.mapper.LabDashboardMapper;
import com.ailab.system.service.LabAccessService;
import com.ailab.system.service.LabDashboardService;
import com.ruoyi.common.exception.ServiceException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LabDashboardServiceImpl implements LabDashboardService {
    private static final BigDecimal FIVE = new BigDecimal("5");
    private static final BigDecimal FIFTEEN = new BigDecimal("15");
    private static final String HEALTH_DEFINITION = "期望进度为截至口径日期计划到期子项权重累计；实际进度按已确认月任务结果或执行中月任务的已确认周任务完成比例计算，再按目标与季度权重聚合；风险颜色取最严重值";
    private final LabDashboardMapper mapper;
    private final LabAccessService access;
    private final Clock clock;

    @Autowired
    public LabDashboardServiceImpl(LabDashboardMapper mapper, LabAccessService access) {
        this(mapper, access, Clock.system(ZoneId.of("Asia/Shanghai")));
    }
    public LabDashboardServiceImpl(LabDashboardMapper mapper, LabAccessService access, Clock clock) {
        this.mapper = mapper; this.access = access; this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardOverview getOverview(String period, Long actorUserId) {
        YearMonth requestedMonth = requireMonth(period);
        LabAccessContext scope = access.context(actorUserId);
        Date now = Date.from(clock.instant());
        LocalDate localToday = LocalDate.now(clock);
        boolean historical = requestedMonth.isBefore(YearMonth.from(localToday));
        LocalDate asOfDate = historical ? requestedMonth.atEndOfMonth() : localToday;
        java.sql.Date asOf = java.sql.Date.valueOf(asOfDate);
        List<GoalHealth> health = new ArrayList<GoalHealth>();
        List<GoalHealthFact> healthFacts = safe(mapper.selectGoalHealthFacts(requestedMonth.getYear(), asOf, scope,
                LabDashboardMapper.MAX_GOAL_HEALTH_ROWS + 1));
        if (healthFacts.size() > LabDashboardMapper.MAX_GOAL_HEALTH_ROWS) {
            throw new ServiceException("Dashboard goal projection exceeds the server row limit");
        }
        for (GoalHealthFact fact : healthFacts) health.add(calculateHealth(fact));
        DashboardKpiFact kpi = mapper.selectKpiFact(period, asOf, scope);
        if (kpi == null) kpi = new DashboardKpiFact();

        DashboardOverview result = new DashboardOverview();
        result.setGoalHealth(health);
        List<GoalTrendPoint> trend = safeTrend(mapper.selectGoalProgressTrend(requestedMonth.getYear(), asOf, scope));
        for (GoalTrendPoint point : trend) {
            point.setDefinition("每个年度目标独立累计计划进度，以及已确认月任务或执行中月任务已确认周任务比例形成的实际进度"); point.setLastUpdated(now);
            point.getDrillDownFilters().put("goalId", point.getGoalId());
            point.getDrillDownFilters().put("periodTo", point.getPeriod());
            point.getDrillDownFilters().put("taskLevel", "month");
            point.getDrillDownFilters().put("taskType", "key");
            point.getDrillDownFilters().put("asOf", asOf);
        }
        result.setGoalTrend(trend);
        result.setKpis(kpis(period, health, kpi, now, asOf, asOfDate));
        result.setTaskStatusDistribution(decorateCounts(safeCounts(mapper.selectTaskStatusDistribution(period, scope)), period,
                "按当前任务工作流状态计数", now, "workflowStatus"));
        result.setRecentIpr(decorateActions(safeActions(mapper.selectRecentIpr(asOf, scope)), period,
                "按计划提交日期展示公开的知识产权基础信息", now, "status", "ACTIVE"));
        result.setRecentReports(decorateActions(safeActions(mapper.selectRecentReports(period, scope)), period,
                "当前周期有权读取的非敏感已定稿报告及制品状态", now, "period", period));
        DashboardActionItem latestReport = mapper.selectLatestReport(period, scope);
        if (latestReport != null) {
            decorateActions(Collections.singletonList(latestReport), period,
                    "当前周期最近更新且有权读取的报告", now, "period", period);
        }
        result.setLatestReport(latestReport);
        if (!LabAccessServiceImpl.MEMBER.equals(scope.getRoleKey())) {
            result.setCoordinationItems(decorateActions(safeActions(mapper.selectCoordinationItems(period, scope)), period,
                    "当前周期仍需协调的任务", now, "coordinationRequired", "1"));
            java.sql.Date twoWeekStart = java.sql.Date.valueOf(asOfDate.minusDays(13));
            List<MemberLoad> loads = safeLoads(mapper.selectMemberLoads(period, twoWeekStart, asOf, scope));
            decorateLoads(loads, period, now);
            result.setMemberLoads(loads);
            if (LabAccessServiceImpl.MANAGER.equals(scope.getRoleKey())) {
                result.setPerformanceSummary(decorateCounts(safeCounts(mapper.selectPerformanceSummary(period, scope)), period,
                        "仅部门负责人可见的当前绩效结果状态分布", now, "period", "period"));
            }
        }
        return result;
    }

    @Override
    public GoalHealth calculateHealth(GoalHealthFact fact) {
        if (fact == null) throw new ServiceException("Goal health fact is required");
        BigDecimal expected = zero(fact.getExpectedProgress()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal actual = zero(fact.getActualProgress()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal lag = expected.subtract(actual).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        int blockDays = fact.getMaxOpenBlockDays() == null ? 0 : fact.getMaxOpenBlockDays();
        String status = "GREEN";
        if (lag.compareTo(FIVE) > 0 || blockDays >= 7 || fact.isDelayedFocusTask()) status = "YELLOW";
        if (lag.compareTo(FIFTEEN) > 0 || blockDays > 14 || fact.isOverdueUnsubmittedFocusTask()) status = "RED";
        GoalHealth value = new GoalHealth(); value.setGoalId(fact.getGoalId()); value.setTitle(fact.getGoalTitle());
        value.setExpectedProgress(expected); value.setActualProgress(actual); value.setLag(lag); value.setStatus(status);
        value.setPeriod(String.valueOf(fact.getYear())); value.setDefinition(HEALTH_DEFINITION); value.setLastUpdated(Date.from(clock.instant()));
        value.getDrillDownFilters().put("goalId", fact.getGoalId()); value.getDrillDownFilters().put("year", fact.getYear());
        return value;
    }

    private List<DashboardMetric> kpis(String period, List<GoalHealth> health, DashboardKpiFact fact, Date now,
            Date asOf, LocalDate asOfDate) {
        List<Long> riskGoalIds = new ArrayList<Long>();
        for (GoalHealth item : health) if (!"GREEN".equals(item.getStatus())) riskGoalIds.add(item.getGoalId());
        List<DashboardMetric> values = new ArrayList<DashboardMetric>();
        values.add(metric("annualGoalHealth", "年度目标健康风险", riskGoalIds.size(), "个", period,
                HEALTH_DEFINITION, now, filters("year", Integer.valueOf(period.substring(0, 4)),
                        "goalIdsFilter", Boolean.TRUE, "goalIds", riskGoalIds)));
        values.add(metric("keyTaskCompletion", "当月重点任务完成率", zero(fact.getKeyTaskCompletionRate()), "%", period,
                "已确认且结果完成的当月重点任务绩效权重/全部当月重点任务绩效权重", now,
                filters("period", period, "taskLevel", "month", "taskType", "key")));
        values.add(metric("overdueOrPending", "逾期/未填", integer(fact.getOverdueOrPendingCount()), "项", period,
                "计划日期已过仍未提交，或草稿/执行中存在必填字段缺失的当前任务数", now,
                filters("period", period, "workflowStatuses", Arrays.asList("DRAFT", "ACTIVE"),
                        "overdueOrPending", Boolean.TRUE, "asOf", asOf)));
        java.sql.Date blockStartBefore = java.sql.Date.valueOf(asOfDate.minusDays(7));
        values.add(metric("blockedOverSeven", "阻塞超过7天", integer(fact.getBlockedOverSevenCount()), "项", period,
                "当前OPEN阻塞事件自开始日期已满7天的任务数", now,
                filters("period", period, "currentBlockFlag", "1", "blockStartBefore", blockStartBefore, "asOf", asOf)));
        values.add(metric("assetsWithoutBackup", "无备份资产", integer(fact.getAssetsWithoutBackupCount()), "项", period,
                "关键资产，或当前有效且已部署/验收的资产中，未配置有效备份负责人的数量", now,
                filters("singlePointRisk", Boolean.TRUE)));
        return values;
    }

    private DashboardMetric metric(String code, String name, Object value, String unit, String period, String definition,
            Date now, Map<String, Object> filters) {
        DashboardMetric metric = new DashboardMetric(); metric.setCode(code); metric.setName(name); metric.setValue(value);
        metric.setUnit(unit); metric.setPeriod(period); metric.setDefinition(definition); metric.setLastUpdated(now);
        metric.setDrillDownFilters(filters); return metric;
    }

    private void decorateLoads(List<MemberLoad> values, String period, Date now) {
        for (MemberLoad value : values) {
            BigDecimal weight = zero(value.getKeyTaskWeight());
            value.setHeatLevel(weight.compareTo(new BigDecimal("100")) > 0 ? "HIGH" : weight.compareTo(new BigDecimal("80")) > 0 ? "MEDIUM" : "NORMAL");
            value.setPeriod(period); value.setDefinition("并列展示重点任务权重、执行中任务、近两周周任务、逾期、阻塞和协调数；热力只按重点权重分档"); value.setLastUpdated(now);
            value.getDrillDownFilters().put("period", period); value.getDrillDownFilters().put("ownerId", value.getMemberId());
        }
    }

    private List<DashboardCountItem> decorateCounts(List<DashboardCountItem> values, String period, String definition,
            Date now, String filterName) { return decorateCounts(values, period, definition, now, filterName, "code"); }
    private List<DashboardCountItem> decorateCounts(List<DashboardCountItem> values, String period, String definition,
            Date now, String filterName, String valueSource) {
        for (DashboardCountItem item : values) {
            item.setPeriod(period); item.setDefinition(definition); item.setLastUpdated(now);
            item.getDrillDownFilters().put("period", period);
            item.getDrillDownFilters().put(filterName, "period".equals(valueSource) ? period : item.getCode());
        }
        return values;
    }
    private List<DashboardActionItem> decorateActions(List<DashboardActionItem> values, String period, String definition,
            Date now, String filterName, Object filterValue) {
        for (DashboardActionItem item : values) {
            item.setPeriod(period); item.setDefinition(definition); item.setLastUpdated(now);
            item.getDrillDownFilters().put("period", period); item.getDrillDownFilters().put(filterName, filterValue);
            item.getDrillDownFilters().put("id", item.getId());
        }
        return values;
    }

    private Map<String, Object> filters(Object... pairs) {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) value.put(String.valueOf(pairs[i]), pairs[i + 1]);
        return value;
    }
    private YearMonth requireMonth(String period) {
        try {
            if (period == null || !period.matches("\\d{4}-\\d{2}")) {
                throw new DateTimeParseException("invalid", String.valueOf(period), 0);
            }
            return YearMonth.parse(period);
        } catch (DateTimeParseException e) {
            throw new ServiceException("period: must use a valid YYYY-MM value");
        }
    }
    private BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP); }
    private int integer(Integer value) { return value == null ? 0 : value; }
    private <T> List<T> safe(List<T> value) { return value == null ? Collections.<T>emptyList() : value; }
    private List<DashboardCountItem> safeCounts(List<DashboardCountItem> value) { return value == null ? new ArrayList<DashboardCountItem>() : value; }
    private List<DashboardActionItem> safeActions(List<DashboardActionItem> value) { return value == null ? new ArrayList<DashboardActionItem>() : value; }
    private List<MemberLoad> safeLoads(List<MemberLoad> value) { return value == null ? new ArrayList<MemberLoad>() : value; }
    private List<GoalTrendPoint> safeTrend(List<GoalTrendPoint> value) { return value == null ? new ArrayList<GoalTrendPoint>() : value; }
}
