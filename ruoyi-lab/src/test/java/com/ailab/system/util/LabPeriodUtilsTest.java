package com.ailab.system.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LabPeriodUtilsTest {
    @Test
    void parsesAndFormatsMonthPeriodsCanonically() {
        LabPeriodUtils.PeriodRange range = LabPeriodUtils.parse("2026-02");

        assertEquals("2026-02", range.getPeriod());
        assertEquals(LocalDate.of(2026, 2, 1), range.getStartDate());
        assertEquals(LocalDate.of(2026, 2, 28), range.getEndDate());
    }

    @Test
    void supportsLeapYearMonthBoundaries() {
        LabPeriodUtils.PeriodRange range = LabPeriodUtils.parse("2024-02");

        assertEquals(LocalDate.of(2024, 2, 29), range.getEndDate());
    }

    @Test
    void parsesIsoWeeksUsingMondayToSundayBoundaries() {
        LabPeriodUtils.PeriodRange range = LabPeriodUtils.parse("2026-W01");

        assertEquals("2026-W01", range.getPeriod());
        assertEquals(LocalDate.of(2025, 12, 29), range.getStartDate());
        assertEquals(LocalDate.of(2026, 1, 4), range.getEndDate());
    }

    @Test
    void acceptsValidWeekFiftyThreeOnlyInEligibleIsoYears() {
        LabPeriodUtils.PeriodRange range = LabPeriodUtils.parse("2020-W53");

        assertEquals(LocalDate.of(2020, 12, 28), range.getStartDate());
        assertEquals(LocalDate.of(2021, 1, 3), range.getEndDate());
        assertThrows(IllegalArgumentException.class, () -> LabPeriodUtils.parse("2025-W53"));
    }

    @Test
    void rejectsMalformedAndImpossiblePeriods() {
        assertThrows(IllegalArgumentException.class, () -> LabPeriodUtils.parse("2026-13"));
        assertThrows(IllegalArgumentException.class, () -> LabPeriodUtils.parse("2026-W00"));
        assertThrows(IllegalArgumentException.class, () -> LabPeriodUtils.parse("2026-W1"));
        assertThrows(IllegalArgumentException.class, () -> LabPeriodUtils.parse("2026/08"));
    }
}
