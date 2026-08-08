package com.ailab.system.dto;

import java.math.BigDecimal;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

public class GoalTrendPoint {
    private Long goalId;
    private String goalName;
    private String period;
    private BigDecimal expectedProgress;
    private BigDecimal actualProgress;
    private String definition;
    private Date lastUpdated;
    private Map<String, Object> drillDownFilters = new LinkedHashMap<String, Object>();
    public Long getGoalId() { return goalId; }
    public void setGoalId(Long goalId) { this.goalId = goalId; }
    public String getGoalName() { return goalName; }
    public void setGoalName(String goalName) { this.goalName = goalName; }
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    public BigDecimal getExpectedProgress() { return expectedProgress; }
    public void setExpectedProgress(BigDecimal expectedProgress) { this.expectedProgress = expectedProgress; }
    public BigDecimal getActualProgress() { return actualProgress; }
    public void setActualProgress(BigDecimal actualProgress) { this.actualProgress = actualProgress; }
    public String getDefinition() { return definition; }
    public void setDefinition(String definition) { this.definition = definition; }
    public Date getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Date lastUpdated) { this.lastUpdated = lastUpdated; }
    public Map<String, Object> getDrillDownFilters() { return drillDownFilters; }
    public void setDrillDownFilters(Map<String, Object> value) { drillDownFilters = value; }
}
