package com.ailab.system.util;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.WeekFields;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsing and boundary calculations for the laboratory's month and ISO-week periods. */
public final class LabPeriodUtils {
    private static final Pattern MONTH = Pattern.compile("^(\\d{4})-(\\d{2})$");
    private static final Pattern WEEK = Pattern.compile("^(\\d{4})-W(\\d{2})$");
    private static final WeekFields ISO = WeekFields.ISO;

    private LabPeriodUtils() {
    }

    public static PeriodRange parse(String period) {
        if (period == null) {
            throw new IllegalArgumentException("周期不能为空");
        }
        Matcher month = MONTH.matcher(period);
        if (month.matches()) {
            return parseMonth(period);
        }
        Matcher week = WEEK.matcher(period);
        if (week.matches()) {
            return parseWeek(period);
        }
        throw new IllegalArgumentException("周期格式必须为 YYYY-MM 或 YYYY-Www");
    }

    public static PeriodRange parseMonth(String period) {
        if (period == null || !MONTH.matcher(period).matches()) {
            throw new IllegalArgumentException("月份周期格式必须为 YYYY-MM");
        }
        try {
            YearMonth value = YearMonth.parse(period);
            return new PeriodRange(value.toString(), value.atDay(1), value.atEndOfMonth());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("月份周期无效: " + period, exception);
        }
    }

    public static PeriodRange parseWeek(String period) {
        if (period == null) {
            throw new IllegalArgumentException("周周期不能为空");
        }
        Matcher matcher = WEEK.matcher(period);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("周周期格式必须为 YYYY-Www");
        }
        int year = Integer.parseInt(matcher.group(1));
        int week = Integer.parseInt(matcher.group(2));
        int maxWeek = LocalDate.of(year, 12, 28).get(ISO.weekOfWeekBasedYear());
        if (week < 1 || week > maxWeek) {
            throw new IllegalArgumentException("ISO 周序号必须在 01 到 53 之间");
        }
        LocalDate start = LocalDate.of(year, 1, 4)
                .with(ISO.weekOfWeekBasedYear(), week)
                .with(ISO.dayOfWeek(), 1);
        if (start.get(ISO.weekBasedYear()) != year || start.get(ISO.weekOfWeekBasedYear()) != week) {
            throw new IllegalArgumentException("ISO 周周期无效: " + period);
        }
        return new PeriodRange(String.format(Locale.ROOT, "%04d-W%02d", year, week), start, start.plusDays(6));
    }

    public static final class PeriodRange {
        private final String period;
        private final LocalDate startDate;
        private final LocalDate endDate;

        private PeriodRange(String period, LocalDate startDate, LocalDate endDate) {
            this.period = period;
            this.startDate = startDate;
            this.endDate = endDate;
        }

        public String getPeriod() {
            return period;
        }

        public LocalDate getStartDate() {
            return startDate;
        }

        public LocalDate getEndDate() {
            return endDate;
        }
    }
}
