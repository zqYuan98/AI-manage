package com.ailab.system.report.model;

import java.time.YearMonth;

/** Strict report period grammar shared by provider dispatch and query criteria. */
public final class ReportPeriod {
    public enum Kind { MONTH, WEEK, QUARTER, YEAR }
    private final String value; private final Kind kind;
    private ReportPeriod(String value, Kind kind) { this.value=value; this.kind=kind; }
    public static ReportPeriod parse(String value) {
        if (value != null && value.matches("[0-9]{4}-(0[1-9]|1[0-2])")) return new ReportPeriod(value, Kind.MONTH);
        if (value != null && value.matches("[0-9]{4}-W(0[1-9]|[1-4][0-9]|5[0-3])")) return new ReportPeriod(value, Kind.WEEK);
        if (value != null && value.matches("[0-9]{4}Q[1-4]")) return new ReportPeriod(value, Kind.QUARTER);
        if (value != null && value.matches("[0-9]{4}")) return new ReportPeriod(value, Kind.YEAR);
        throw new IllegalArgumentException("period must be YYYY-MM, YYYY-Www, YYYYQn, or YYYY");
    }
    public String getValue(){return value;} public Kind getKind(){return kind;}
    public String nextMonth(){if(kind!=Kind.MONTH)throw new IllegalArgumentException("next month requires MONTH period");return YearMonth.parse(value).plusMonths(1).toString();}
}
