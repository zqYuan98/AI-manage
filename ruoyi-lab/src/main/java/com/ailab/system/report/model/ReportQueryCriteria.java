package com.ailab.system.report.model;

import com.ailab.system.report.config.ReportSectionConfig;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.WeekFields;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Typed mapper inputs; no renderer/configuration value is ever SQL text. */
public final class ReportQueryCriteria {
    public static final int MAX_SOURCE_ROWS = 5000;
    private final String period; private final String nextPeriod; private final String monthStart; private final String monthEnd; private final java.sql.Date dateStart; private final java.sql.Date dateEnd; private final ReportPeriod reportPeriod; private final ReportAccessScope scope; private final List<Filter> filters; private final String sort; private final String groupBy;
    public ReportQueryCriteria(String period, ReportAccessScope scope) {
        this.reportPeriod = ReportPeriod.parse(period); this.period = reportPeriod.getValue(); this.nextPeriod = reportPeriod.getKind()==ReportPeriod.Kind.MONTH ? reportPeriod.nextMonth() : null; LocalDate[] range = range(reportPeriod); this.monthStart = YearMonth.from(range[0]).toString(); this.monthEnd = YearMonth.from(range[1]).toString(); this.dateStart = java.sql.Date.valueOf(range[0]); this.dateEnd = java.sql.Date.valueOf(range[1]); this.scope = scope; this.filters = Collections.emptyList(); this.sort = null; this.groupBy = null;
    }
    private ReportQueryCriteria(String period, ReportAccessScope scope, List<Filter> filters, String sort, String groupBy) { this.reportPeriod=ReportPeriod.parse(period); this.period = reportPeriod.getValue(); this.nextPeriod = reportPeriod.getKind()==ReportPeriod.Kind.MONTH ? reportPeriod.nextMonth() : null; LocalDate[] range = range(reportPeriod); this.monthStart = YearMonth.from(range[0]).toString(); this.monthEnd = YearMonth.from(range[1]).toString(); this.dateStart = java.sql.Date.valueOf(range[0]); this.dateEnd = java.sql.Date.valueOf(range[1]); this.scope = scope; this.filters = Collections.unmodifiableList(new ArrayList<Filter>(filters)); this.sort = sort; this.groupBy = groupBy; }
    @SuppressWarnings("unchecked") public static ReportQueryCriteria from(ReportContext context, ReportSectionConfig section) {
        List<Filter> filters = new ArrayList<Filter>(); Object raw = section.getQueryConfig().get("filters"); ReportPeriod parsed=ReportPeriod.parse(context.getPeriod()); String next = parsed.getKind()==ReportPeriod.Kind.MONTH ? parsed.nextMonth() : null; if (raw instanceof Iterable) for (Object item : (Iterable<?>) raw) { Map<String,Object> value = (Map<String,Object>) item; filters.add(new Filter(String.valueOf(value.get("field")), String.valueOf(value.get("operator")), placeholder(value.get("value"), context.getPeriod(), next))); }
        Object sort = section.getQueryConfig().get("sort"), group = section.getQueryConfig().get("groupBy"), renderGroup = section.getRenderConfig().get("groupBy");
        if (group != null && renderGroup != null && !group.equals(renderGroup)) throw new IllegalArgumentException("Conflicting report groupBy");
        if (group == null) group = renderGroup;
        return new ReportQueryCriteria(context.getPeriod(), context.getAccessScope(), filters, sort instanceof String ? (String) sort : null, group instanceof String ? (String) group : null);
    }
    public String getPeriod() { return period; } public String getNextPeriod() { return nextPeriod; } public String getPerformancePeriod(){return reportPeriod.getKind()==ReportPeriod.Kind.QUARTER ? period.substring(0,4)+"-Q"+period.charAt(5) : period;} public String getMonthStart(){return monthStart;} public String getMonthEnd(){return monthEnd;} public java.sql.Date getDateStart(){return new java.sql.Date(dateStart.getTime());} public java.sql.Date getDateEnd(){return new java.sql.Date(dateEnd.getTime());} public int getSourceFetchLimit(){return MAX_SOURCE_ROWS+1;} public ReportPeriod getReportPeriod(){return reportPeriod;} public ReportAccessScope getScope() { return scope; } public List<Filter> getFilters() { return filters; } public String getSort() { return sort; } public String getGroupBy() { return groupBy; }
    public static final class Filter { private final String field, operator; private final Object value; Filter(String field, String operator, Object value) { this.field=field; this.operator=operator; this.value=ImmutableReportValue.value(value); } public String getField(){return field;} public String getOperator(){return operator;} public Object getValue(){return value;} }
    @SuppressWarnings("unchecked") private static Object placeholder(Object value, String period, String nextPeriod) { if (value instanceof String) { if ("${period}".equals(value)) return period; if ("${nextPeriod}".equals(value)) return nextPeriod; if (((String) value).contains("${")) throw new IllegalArgumentException("Unsupported report placeholder"); return value; } if (value instanceof List) { List<Object> result = new ArrayList<Object>(); for (Object item : (List<Object>) value) result.add(placeholder(item, period, nextPeriod)); return result; } return value; }
    private static LocalDate[] range(ReportPeriod period) { String value=period.getValue(); int year=Integer.parseInt(value.substring(0,4)); if(period.getKind()==ReportPeriod.Kind.MONTH){LocalDate start=LocalDate.of(year,Integer.parseInt(value.substring(5,7)),1);return new LocalDate[]{start,start.withDayOfMonth(start.lengthOfMonth())};} if(period.getKind()==ReportPeriod.Kind.QUARTER){int month=(Character.digit(value.charAt(5),10)-1)*3+1;LocalDate start=LocalDate.of(year,month,1);return new LocalDate[]{start,start.plusMonths(2).withDayOfMonth(start.plusMonths(2).lengthOfMonth())};} if(period.getKind()==ReportPeriod.Kind.YEAR)return new LocalDate[]{LocalDate.of(year,1,1),LocalDate.of(year,12,31)};int week=Integer.parseInt(value.substring(6,8));LocalDate start=LocalDate.of(year,1,4).with(WeekFields.ISO.weekOfWeekBasedYear(),week).with(WeekFields.ISO.dayOfWeek(),1);return new LocalDate[]{start,start.plusDays(6)}; }
}
