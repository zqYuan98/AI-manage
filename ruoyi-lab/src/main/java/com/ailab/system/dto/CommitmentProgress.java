package com.ailab.system.dto;

import java.math.BigDecimal;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/** Named operational and formal progress projection at one immutable cutoff. */
public class CommitmentProgress {
    private Date executionAsOf;
    private int numerator;
    private int denominator;
    private BigDecimal executionRate;
    private Map<String, Integer> statusCounts = new LinkedHashMap<String, Integer>();
    private int blockedCount;
    private BigDecimal operationalProgress;
    private BigDecimal formalProgress;
    private BigDecimal expectedProgress;
    private BigDecimal deviation;
    private String riskBand;
    private String calculationVersion;
    private Long formalRevision;
    private Long closeRevision;
    private BigDecimal operationalWeight;
    private BigDecimal formalWeight;
    private BigDecimal expectedWeight;

    public Date getExecutionAsOf() { return copy(executionAsOf); }
    public void setExecutionAsOf(Date value) { executionAsOf = copy(value); }
    public int getNumerator() { return numerator; }
    public void setNumerator(int value) { numerator = value; }
    public int getDenominator() { return denominator; }
    public void setDenominator(int value) { denominator = value; }
    public BigDecimal getExecutionRate() { return executionRate; }
    public void setExecutionRate(BigDecimal value) { executionRate = value; }
    public Map<String, Integer> getStatusCounts() { return new LinkedHashMap<String, Integer>(statusCounts); }
    public void setStatusCounts(Map<String, Integer> value) {
        statusCounts = value == null ? new LinkedHashMap<String, Integer>() : new LinkedHashMap<String, Integer>(value);
    }
    public int getBlockedCount() { return blockedCount; }
    public void setBlockedCount(int value) { blockedCount = value; }
    public BigDecimal getOperationalProgress() { return operationalProgress; }
    public void setOperationalProgress(BigDecimal value) { operationalProgress = value; }
    public BigDecimal getFormalProgress() { return formalProgress; }
    public void setFormalProgress(BigDecimal value) { formalProgress = value; }
    public BigDecimal getExpectedProgress() { return expectedProgress; }
    public void setExpectedProgress(BigDecimal value) { expectedProgress = value; }
    public BigDecimal getDeviation() { return deviation; }
    public void setDeviation(BigDecimal value) { deviation = value; }
    public String getRiskBand() { return riskBand; }
    public void setRiskBand(String value) { riskBand = value; }
    public String getCalculationVersion() { return calculationVersion; }
    public void setCalculationVersion(String value) { calculationVersion = value; }
    public Long getFormalRevision() { return formalRevision; }
    public void setFormalRevision(Long value) { formalRevision = value; }
    public Long getCloseRevision() { return closeRevision; }
    public void setCloseRevision(Long value) { closeRevision = value; }
    public BigDecimal getOperationalWeight() { return operationalWeight; }
    public void setOperationalWeight(BigDecimal value) { operationalWeight = value; }
    public BigDecimal getFormalWeight() { return formalWeight; }
    public void setFormalWeight(BigDecimal value) { formalWeight = value; }
    public BigDecimal getExpectedWeight() { return expectedWeight; }
    public void setExpectedWeight(BigDecimal value) { expectedWeight = value; }

    private Date copy(Date value) { return value == null ? null : new Date(value.getTime()); }
}
