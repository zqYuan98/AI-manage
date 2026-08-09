package com.ailab.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.ailab.system.constant.LabConstants;
import com.ailab.system.domain.LabTask;
import com.ailab.system.dto.CommitmentProgress;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;

class LabCommitmentCalculationServiceTest {
    private static final Date AS_OF = date("2026-08-15T23:59:59Z");
    private final LabCommitmentCalculationService calculator = new LabCommitmentCalculationService();

    @Test
    void executionRateUsesOnlyDueActivatedNonCancelledCommitmentsAndCountsEveryDenominatorState() {
        LabTask month = month(10L, "ACTIVE", "DOING", "2026-08-31T00:00:00Z");
        List<LabTask> facts = new ArrayList<LabTask>();
        facts.add(week(1L, "SELF_DONE", "EXCEEDED", "2026-08-01T00:00:00Z", "2026-08-10T00:00:00Z"));
        facts.add(week(2L, "SELF_DONE", "ONTIME", "2026-08-02T00:00:00Z", "2026-08-10T00:00:00Z"));
        facts.add(week(3L, "SELF_DONE", "DELAYED", "2026-08-03T00:00:00Z", "2026-08-10T00:00:00Z"));
        facts.add(week(4L, "SELF_UNDONE", "UNDONE", "2026-08-04T00:00:00Z", "2026-08-11T00:00:00Z"));
        facts.add(week(5L, "ACTIVE", "DOING", "2026-08-05T00:00:00Z", "2026-08-12T00:00:00Z"));
        LabTask blocked = week(6L, "ACTIVE", "DOING", "2026-08-06T00:00:00Z", "2026-08-12T00:00:00Z");
        blocked.setBlockedAtAsOf(Boolean.TRUE); blocked.setBlockStartTime(date("2026-08-08T00:00:00Z")); facts.add(blocked);
        facts.add(week(7L, "SELF_DONE", "ONTIME", "2026-08-01T00:00:00Z", "2026-08-20T00:00:00Z"));
        facts.add(week(8L, "CANCELLED", "DOING", "2026-08-01T00:00:00Z", "2026-08-10T00:00:00Z"));
        facts.add(week(9L, "SELF_DONE", "ONTIME", "2026-08-16T00:00:00Z", "2026-08-10T00:00:00Z"));

        CommitmentProgress progress = calculator.calculateMonth(month, facts, AS_OF, null, null, false);

        assertEquals(3, progress.getNumerator());
        assertEquals(6, progress.getDenominator());
        assertEquals(new BigDecimal("50.00"), progress.getExecutionRate());
        assertEquals(Integer.valueOf(3), progress.getStatusCounts().get("SELF_DONE"));
        assertEquals(Integer.valueOf(1), progress.getStatusCounts().get("SELF_UNDONE"));
        assertEquals(Integer.valueOf(2), progress.getStatusCounts().get("ACTIVE"));
        assertEquals(6, progress.getStatusCounts().values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(1, progress.getBlockedCount());
        assertEquals("COMMITMENT_PROGRESS_V1", progress.getCalculationVersion());
        assertEquals(AS_OF, progress.getExecutionAsOf());
    }

    @Test
    void emptyDenominatorIsNamedAndNeverInventsZeroOrOneHundredPercent() {
        CommitmentProgress progress = calculator.calculateMonth(
                month(10L, "ACTIVE", "DOING", "2026-08-31T00:00:00Z"),
                Arrays.asList(week(1L, "ACTIVE", "DOING", "2026-08-20T00:00:00Z", "2026-08-20T00:00:00Z")),
                AS_OF, null, null, false);

        assertEquals(0, progress.getNumerator());
        assertEquals(0, progress.getDenominator());
        assertNull(progress.getExecutionRate());
    }

    @Test
    void formalAndPerformanceCoefficientsAreDifferentNamedContracts() {
        for (String result : Arrays.asList("EXCEEDED", "ONTIME", "DELAYED")) {
            assertEquals(BigDecimal.ONE, calculator.formalCoefficient(result));
        }
        assertEquals(BigDecimal.ZERO, calculator.formalCoefficient("UNDONE"));
        assertEquals(new BigDecimal("1.2"), calculator.performanceCoefficient("EXCEEDED"));
        assertEquals(BigDecimal.ONE, calculator.performanceCoefficient("ONTIME"));
        assertEquals(new BigDecimal("0.7"), calculator.performanceCoefficient("DELAYED"));
        assertEquals(BigDecimal.ZERO, calculator.performanceCoefficient("UNDONE"));
    }

    @Test
    void unconfirmedCloseIsFormalZeroWithoutForgingConfirmationAndWeeklyFactsNeverChangeIt() {
        LabTask month = month(10L, "ACTIVE", "DOING", "2026-08-10T00:00:00Z");
        CommitmentProgress progress = calculator.calculateMonth(month,
                Arrays.asList(week(1L, "SELF_DONE", "EXCEEDED", "2026-08-01T00:00:00Z", "2026-08-05T00:00:00Z")),
                AS_OF, 31L, 9L, true);

        assertEquals(new BigDecimal("100.00"), progress.getOperationalProgress());
        assertEquals(new BigDecimal("0.00"), progress.getFormalProgress());
        assertEquals("ACTIVE", month.getWorkflowStatus());
        assertEquals(Long.valueOf(31L), progress.getFormalRevision());
        assertEquals(Long.valueOf(9L), progress.getCloseRevision());
    }

    @Test
    void reopenedCurrentRowKeepsOperationalExecutionButFormalProgressUsesImmutableAcceptedResult() {
        LabTask month = month(10L, "ACTIVE", "DOING", "2026-08-31T00:00:00Z");
        CommitmentProgress progress = calculator.calculateMonth(month,
                Arrays.asList(week(1L, "SELF_DONE", "ONTIME", "2026-08-01T00:00:00Z", "2026-08-05T00:00:00Z")),
                AS_OF, "DELAYED", true, 41L, null, false);

        assertEquals(new BigDecimal("100.00"), progress.getOperationalProgress());
        assertEquals(new BigDecimal("100.00"), progress.getFormalProgress());
        assertEquals("ACTIVE", month.getWorkflowStatus());
        assertEquals(Long.valueOf(41L), progress.getFormalRevision());
    }

    @Test
    void weightedMilestoneAndAnnualProgressUseTheSameGoldenValuesAndRiskBands() {
        CommitmentProgress fiftyOperationalZeroFormal = projection("50", "0", "100", "GREEN");
        CommitmentProgress accepted = projection("100", "100", "100", "GREEN");
        CommitmentProgress milestone = calculator.aggregateWeighted(Arrays.asList(
                LabCommitmentCalculationService.weighted(fiftyOperationalZeroFormal, "60"),
                LabCommitmentCalculationService.weighted(accepted, "40")), AS_OF, 31L, 9L);

        assertEquals(new BigDecimal("70.00"), milestone.getOperationalProgress());
        assertEquals(new BigDecimal("40.00"), milestone.getFormalProgress());
        assertEquals(new BigDecimal("100.00"), milestone.getExpectedProgress());
        assertEquals("RED", milestone.getRiskBand());

        CommitmentProgress earlyQuarter = projection("20", "0", "100", "YELLOW");
        CommitmentProgress annual = calculator.aggregateWeighted(Arrays.asList(
                LabCommitmentCalculationService.weighted(milestone, "25"),
                LabCommitmentCalculationService.weighted(earlyQuarter, "75")), AS_OF, 31L, 9L);
        assertEquals(new BigDecimal("32.50"), annual.getOperationalProgress());
        assertEquals(new BigDecimal("10.00"), annual.getFormalProgress());
        assertEquals(new BigDecimal("100.00"), annual.getExpectedProgress());
        assertEquals("RED", annual.getRiskBand());

        assertEquals("GREEN", calculator.riskBand(new BigDecimal("100"), new BigDecimal("95")));
        assertEquals("YELLOW", calculator.riskBand(new BigDecimal("100.01"), new BigDecimal("95")));
        assertEquals("YELLOW", calculator.riskBand(new BigDecimal("110"), new BigDecimal("95")));
        assertEquals("RED", calculator.riskBand(new BigDecimal("110.01"), new BigDecimal("95")));
    }

    private CommitmentProgress projection(String operational, String formal, String expected, String risk) {
        CommitmentProgress value = new CommitmentProgress();
        value.setExecutionAsOf(AS_OF); value.setOperationalProgress(new BigDecimal(operational));
        value.setFormalProgress(new BigDecimal(formal)); value.setExpectedProgress(new BigDecimal(expected));
        value.setRiskBand(risk); value.setCalculationVersion("COMMITMENT_PROGRESS_V1");
        return value;
    }

    private LabTask month(Long id, String workflow, String result, String planDate) {
        LabTask task = new LabTask(); task.setId(id); task.setTaskLevel(LabConstants.TASK_LEVEL_MONTH);
        task.setWorkflowStatus(workflow); task.setResultStatus(result); task.setPlanDate(date(planDate));
        return task;
    }

    private LabTask week(Long id, String execution, String result, String activatedAt, String planDate) {
        LabTask task = new LabTask(); task.setId(id); task.setParentId(10L); task.setTaskLevel(LabConstants.TASK_LEVEL_WEEK);
        task.setExecutionStatus(execution); task.setResultStatus(result); task.setExecutionActivatedAt(date(activatedAt));
        task.setPlanDate(date(planDate)); task.setBlockedAtAsOf(Boolean.FALSE); return task;
    }

    private static Date date(String value) { return Date.from(Instant.parse(value)); }
}
