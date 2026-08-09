package com.ailab.system.service;

import com.ailab.system.constant.LabConstants;
import com.ailab.system.domain.LabTask;
import com.ailab.system.dto.CommitmentProgress;
import com.ruoyi.common.exception.ServiceException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Single deterministic contract for operational, formal and performance coefficients. */
@Component
public class LabCommitmentCalculationService {
    public static final String CALCULATION_VERSION = "COMMITMENT_PROGRESS_V1";
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal FIVE = new BigDecimal("5");
    private static final BigDecimal FIFTEEN = new BigDecimal("15");
    private static final List<String> DENOMINATOR_STATUSES = Arrays.asList(
            LabConstants.EXECUTION_SELF_DONE, LabConstants.EXECUTION_SELF_UNDONE, LabConstants.EXECUTION_ACTIVE);

    public CommitmentProgress calculateMonth(LabTask month, List<LabTask> commitments, Date asOf,
            Long formalRevision, Long closeRevision, boolean closeMode) {
        boolean accepted = LabConstants.WORKFLOW_CONFIRMED.equals(month == null ? null : month.getWorkflowStatus());
        return calculateMonth(month, commitments, asOf,
                month == null ? null : month.getResultStatus(), accepted,
                formalRevision, closeRevision, closeMode);
    }

    public CommitmentProgress calculateMonth(LabTask month, List<LabTask> commitments, Date asOf,
            String immutableFormalResultStatus, boolean formalAccepted,
            Long formalRevision, Long closeRevision, boolean closeMode) {
        if (month == null || month.getId() == null || !LabConstants.TASK_LEVEL_MONTH.equals(month.getTaskLevel())
                || commitments == null || asOf == null) {
            throw new ServiceException("月度承诺进展缺少必要事实");
        }
        Map<String, Integer> counts = emptyCounts();
        int numerator = 0, denominator = 0, blocked = 0, maxBlockDays = 0;
        for (LabTask fact : commitments) {
            if (!included(month.getId(), fact, asOf)) continue;
            String status = fact.getExecutionStatus();
            if (!DENOMINATOR_STATUSES.contains(status)) {
                throw new ServiceException("到期承诺包含不支持的执行状态: " + status);
            }
            denominator++;
            counts.put(status, counts.get(status) + 1);
            if (LabConstants.EXECUTION_SELF_DONE.equals(status)) numerator++;
            if (Boolean.TRUE.equals(fact.getBlockedAtAsOf())) {
                blocked++;
                if (fact.getBlockStartTime() != null && !fact.getBlockStartTime().after(asOf)) {
                    long days = Duration.between(fact.getBlockStartTime().toInstant(), asOf.toInstant()).toDays();
                    maxBlockDays = Math.max(maxBlockDays, (int) Math.max(0L, days));
                }
            }
        }
        BigDecimal rate = denominator == 0 ? null : new BigDecimal(numerator).multiply(HUNDRED)
                .divide(new BigDecimal(denominator), 2, RoundingMode.HALF_UP);
        BigDecimal formal = formalAccepted
                ? formalCoefficient(immutableFormalResultStatus).multiply(HUNDRED) : BigDecimal.ZERO;
        if (closeMode && !formalAccepted) formal = BigDecimal.ZERO;
        BigDecimal operational = LabConstants.WORKFLOW_CONFIRMED.equals(month.getWorkflowStatus())
                ? formalCoefficient(month.getResultStatus()).multiply(HUNDRED)
                : rate == null ? BigDecimal.ZERO : rate;
        BigDecimal expected = month.getPlanDate() != null && !month.getPlanDate().after(asOf) ? HUNDRED : BigDecimal.ZERO;
        CommitmentProgress result = base(asOf, formalRevision, closeRevision);
        result.setOperationalWeight(zero(month.getGoalWeight()));
        result.setFormalWeight(zero(month.getGoalWeight()));
        result.setExpectedWeight(zero(month.getGoalWeight()));
        result.setNumerator(numerator); result.setDenominator(denominator); result.setExecutionRate(rate);
        result.setStatusCounts(counts); result.setBlockedCount(blocked);
        result.setOperationalProgress(percent(operational)); result.setFormalProgress(percent(formal));
        result.setExpectedProgress(percent(expected)); result.setDeviation(percent(expected.subtract(operational)));
        String risk = riskBand(expected, operational);
        if (maxBlockDays > 14 || counts.get(LabConstants.EXECUTION_ACTIVE) > 0) risk = mostSevere(risk, "RED");
        else if (maxBlockDays >= 7) risk = mostSevere(risk, "YELLOW");
        result.setRiskBand(risk);
        return result;
    }

    public CommitmentProgress aggregateWeighted(List<WeightedProgress> children, Date asOf,
            Long formalRevision, Long closeRevision) {
        if (children == null || asOf == null) throw new ServiceException("加权进展缺少必要事实");
        BigDecimal operational = BigDecimal.ZERO, formal = BigDecimal.ZERO, expected = BigDecimal.ZERO;
        int numerator = 0, denominator = 0, blocked = 0;
        Map<String, Integer> counts = emptyCounts();
        String childRisk = "GREEN";
        for (WeightedProgress child : children) {
            if (child == null || child.progress == null || child.operationalWeight == null
                    || child.formalWeight == null || child.expectedWeight == null
                    || child.operationalWeight.signum() < 0 || child.formalWeight.signum() < 0
                    || child.expectedWeight.signum() < 0) {
                throw new ServiceException("加权进展包含无效子项");
            }
            CommitmentProgress value = child.progress;
            operational = operational.add(zero(value.getOperationalProgress()).multiply(child.operationalWeight).divide(HUNDRED, 8, RoundingMode.HALF_UP));
            formal = formal.add(zero(value.getFormalProgress()).multiply(child.formalWeight).divide(HUNDRED, 8, RoundingMode.HALF_UP));
            expected = expected.add(zero(value.getExpectedProgress()).multiply(child.expectedWeight).divide(HUNDRED, 8, RoundingMode.HALF_UP));
            numerator += value.getNumerator(); denominator += value.getDenominator(); blocked += value.getBlockedCount();
            Map<String, Integer> childCounts = value.getStatusCounts();
            for (String status : DENOMINATOR_STATUSES) counts.put(status, counts.get(status) + integer(childCounts.get(status)));
            childRisk = mostSevere(childRisk, value.getRiskBand());
        }
        CommitmentProgress result = base(asOf, formalRevision, closeRevision);
        result.setNumerator(numerator); result.setDenominator(denominator); result.setBlockedCount(blocked);
        result.setExecutionRate(denominator == 0 ? null : new BigDecimal(numerator).multiply(HUNDRED)
                .divide(new BigDecimal(denominator), 2, RoundingMode.HALF_UP));
        result.setStatusCounts(counts); result.setOperationalProgress(percent(operational));
        result.setFormalProgress(percent(formal)); result.setExpectedProgress(percent(expected));
        result.setDeviation(percent(expected.subtract(operational)));
        result.setRiskBand(mostSevere(riskBand(expected, operational), childRisk));
        return result;
    }

    public BigDecimal formalCoefficient(String result) {
        return LabConstants.RESULT_EXCEEDED.equals(result) || LabConstants.RESULT_ONTIME.equals(result)
                || LabConstants.RESULT_DELAYED.equals(result) ? BigDecimal.ONE : BigDecimal.ZERO;
    }

    public BigDecimal performanceCoefficient(String result) {
        if (LabConstants.RESULT_EXCEEDED.equals(result)) return new BigDecimal("1.2");
        if (LabConstants.RESULT_ONTIME.equals(result)) return BigDecimal.ONE;
        if (LabConstants.RESULT_DELAYED.equals(result)) return new BigDecimal("0.7");
        return BigDecimal.ZERO;
    }

    public String riskBand(BigDecimal expected, BigDecimal actual) {
        BigDecimal lag = zero(expected).subtract(zero(actual));
        if (lag.compareTo(FIFTEEN) > 0) return "RED";
        if (lag.compareTo(FIVE) > 0) return "YELLOW";
        return "GREEN";
    }

    public static WeightedProgress weighted(CommitmentProgress progress, String weight) {
        BigDecimal value = new BigDecimal(weight);
        return new WeightedProgress(progress, value, value, value);
    }

    public static WeightedProgress weighted(CommitmentProgress progress, BigDecimal weight) {
        return new WeightedProgress(progress, weight, weight, weight);
    }

    public static WeightedProgress weighted(CommitmentProgress progress) {
        return new WeightedProgress(progress, progress.getOperationalWeight(),
                progress.getFormalWeight(), progress.getExpectedWeight());
    }

    private boolean included(Long monthId, LabTask fact, Date asOf) {
        if (fact == null || !LabConstants.TASK_LEVEL_WEEK.equals(fact.getTaskLevel()) || !monthId.equals(fact.getParentId())) return false;
        if (fact.getExecutionActivatedAt() == null || fact.getExecutionActivatedAt().after(asOf)) return false;
        if (fact.getPlanDate() == null || fact.getPlanDate().after(asOf)) return false;
        return !LabConstants.EXECUTION_CANCELLED.equals(fact.getExecutionStatus());
    }

    private CommitmentProgress base(Date asOf, Long formalRevision, Long closeRevision) {
        CommitmentProgress result = new CommitmentProgress(); result.setExecutionAsOf(asOf);
        result.setFormalRevision(formalRevision); result.setCloseRevision(closeRevision);
        result.setCalculationVersion(CALCULATION_VERSION); return result;
    }

    private Map<String, Integer> emptyCounts() {
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        for (String status : DENOMINATOR_STATUSES) counts.put(status, 0);
        return counts;
    }
    private int integer(Integer value) { return value == null ? 0 : value; }
    private BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private BigDecimal percent(BigDecimal value) { return zero(value).setScale(2, RoundingMode.HALF_UP); }
    private String mostSevere(String left, String right) { return severity(right) > severity(left) ? right : left; }
    private int severity(String risk) { return "RED".equals(risk) ? 2 : "YELLOW".equals(risk) ? 1 : 0; }

    public static final class WeightedProgress {
        private final CommitmentProgress progress;
        private final BigDecimal operationalWeight;
        private final BigDecimal formalWeight;
        private final BigDecimal expectedWeight;
        private WeightedProgress(CommitmentProgress progress, BigDecimal operationalWeight,
                BigDecimal formalWeight, BigDecimal expectedWeight) {
            this.progress = progress; this.operationalWeight = operationalWeight;
            this.formalWeight = formalWeight; this.expectedWeight = expectedWeight;
        }
        public CommitmentProgress getProgress() { return progress; }
        public BigDecimal getWeight() { return operationalWeight; }
    }
}
