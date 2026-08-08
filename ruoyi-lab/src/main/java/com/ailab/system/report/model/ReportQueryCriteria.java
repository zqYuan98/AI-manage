package com.ailab.system.report.model;

import com.ailab.system.report.config.ReportSectionConfig;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Typed mapper inputs; no renderer/configuration value is ever SQL text. */
public final class ReportQueryCriteria {
    private final String period; private final String nextPeriod; private final ReportAccessScope scope; private final List<Filter> filters; private final String sort; private final String groupBy;
    public ReportQueryCriteria(String period, ReportAccessScope scope) {
        this.period = validPeriod(period); this.nextPeriod = YearMonth.parse(this.period).plusMonths(1).toString(); this.scope = scope; this.filters = Collections.emptyList(); this.sort = null; this.groupBy = null;
    }
    private ReportQueryCriteria(String period, ReportAccessScope scope, List<Filter> filters, String sort, String groupBy) { this.period = validPeriod(period); this.nextPeriod = YearMonth.parse(this.period).plusMonths(1).toString(); this.scope = scope; this.filters = Collections.unmodifiableList(new ArrayList<Filter>(filters)); this.sort = sort; this.groupBy = groupBy; }
    @SuppressWarnings("unchecked") public static ReportQueryCriteria from(ReportContext context, ReportSectionConfig section) {
        List<Filter> filters = new ArrayList<Filter>(); Object raw = section.getQueryConfig().get("filters"); if (raw instanceof Iterable) for (Object item : (Iterable<?>) raw) { Map<String,Object> value = (Map<String,Object>) item; filters.add(new Filter(String.valueOf(value.get("field")), String.valueOf(value.get("operator")), value.get("value"))); }
        Object sort = section.getQueryConfig().get("sort"), group = section.getQueryConfig().get("groupBy");
        return new ReportQueryCriteria(context.getPeriod(), context.getAccessScope(), filters, sort instanceof String ? (String) sort : null, group instanceof String ? (String) group : null);
    }
    public String getPeriod() { return period; } public String getNextPeriod() { return nextPeriod; } public ReportAccessScope getScope() { return scope; } public List<Filter> getFilters() { return filters; } public String getSort() { return sort; } public String getGroupBy() { return groupBy; }
    public static final class Filter { private final String field, operator; private final Object value; Filter(String field, String operator, Object value) { this.field=field; this.operator=operator; this.value=ImmutableReportValue.value(value); } public String getField(){return field;} public String getOperator(){return operator;} public Object getValue(){return value;} }
    private static String validPeriod(String period) { if (period == null || !period.matches("[0-9]{4}-(0[1-9]|1[0-2])")) throw new IllegalArgumentException("period must be YYYY-MM"); return period; }
}
