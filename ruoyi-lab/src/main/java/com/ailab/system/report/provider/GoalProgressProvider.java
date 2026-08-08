package com.ailab.system.report.provider;

import com.ailab.system.dto.GoalHealthFact;
import com.ailab.system.dto.LabAccessContext;
import com.ailab.system.mapper.LabDashboardMapper;
import com.ailab.system.report.config.ReportConfigCatalog;
import com.ailab.system.report.config.ReportSectionConfig;
import com.ailab.system.report.model.ReportPeriod;
import com.ailab.system.report.model.ReportQueryCriteria;
import com.ailab.system.report.model.ReportSectionData;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Goal progress is deliberately projected through the dashboard's authoritative health query. */
@Component
public final class GoalProgressProvider extends AbstractLabDataSourceProvider {
    @Autowired(required = false) private LabDashboardMapper dashboardMapper;

    public GoalProgressProvider() {
        super(ReportConfigCatalog.GOAL_PROGRESS,
                new LinkedHashSet<String>(Arrays.asList("period", "goalId", "goalTitle", "year", "progressRate", "expectedProgress")));
    }

    @Override protected boolean supports(ReportPeriod.Kind kind) {
        return kind == ReportPeriod.Kind.MONTH || kind == ReportPeriod.Kind.QUARTER || kind == ReportPeriod.Kind.YEAR;
    }

    @Override protected ReportSectionData loadValidated(ReportQueryCriteria criteria, ReportSectionConfig section) {
        if (dashboardMapper == null) throw new IllegalStateException("Dashboard goal projection is unavailable");
        Date asOf = endOfPeriod(criteria.getReportPeriod());
        List<GoalHealthFact> facts = dashboardMapper.selectGoalHealthFacts(year(criteria.getReportPeriod()), asOf, allGoalsScope());
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (facts != null) for (GoalHealthFact fact : facts) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("period", criteria.getPeriod()); row.put("goalId", fact.getGoalId()); row.put("goalTitle", fact.getGoalTitle());
            row.put("year", fact.getYear()); row.put("progressRate", fact.getActualProgress() == null ? BigDecimal.ZERO : fact.getActualProgress());
            row.put("expectedProgress", fact.getExpectedProgress() == null ? BigDecimal.ZERO : fact.getExpectedProgress());
            rows.add(row);
        }
        Map<String, Object> summary = summaryCount(rows);
        recomputeFilteredSummary(rows, summary);
        return section(criteria, section, rows, summary);
    }
    @Override protected void recomputeFilteredSummary(List<Map<String,Object>> rows, Map<String,Object> summary) {
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> row : rows) total = total.add(number(row.get("progressRate")));
        summary.put("averageProgressRate", rows.isEmpty() ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(rows.size()), 2, RoundingMode.HALF_UP));
    }

    private static LabAccessContext allGoalsScope() {
        LabAccessContext scope = new LabAccessContext(); scope.setRoleKey("lab_manager"); return scope;
    }
    private static int year(ReportPeriod period) { return Integer.parseInt(period.getValue().substring(0, 4)); }
    private static Date endOfPeriod(ReportPeriod period) {
        String value = period.getValue(); LocalDate date;
        if (period.getKind() == ReportPeriod.Kind.MONTH) date = LocalDate.parse(value + "-01").withDayOfMonth(LocalDate.parse(value + "-01").lengthOfMonth());
        else if (period.getKind() == ReportPeriod.Kind.QUARTER) date = LocalDate.of(year(period), (Character.digit(value.charAt(5), 10) * 3), 1).withDayOfMonth(LocalDate.of(year(period), (Character.digit(value.charAt(5), 10) * 3), 1).lengthOfMonth());
        else date = LocalDate.of(year(period), 12, 31);
        // Dashboard SQL compares DATE(asOf); a midday instant keeps the selected calendar day
        // stable when the server/session time zone differs from UTC.
        return Date.from(date.atTime(12, 0).toInstant(java.time.ZoneOffset.UTC));
    }
}
