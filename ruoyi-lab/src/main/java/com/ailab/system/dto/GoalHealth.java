package com.ailab.system.dto;

import java.math.BigDecimal;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

public class GoalHealth {
    private Long goalId;
    private String title;
    private BigDecimal expectedProgress;
    private BigDecimal actualProgress;
    private BigDecimal lag;
    private String status;
    private String period;
    private String definition;
    private Date lastUpdated;
    private Map<String, Object> drillDownFilters = new LinkedHashMap<String, Object>();

    public Long getGoalId() { return goalId; }
    public void setGoalId(Long goalId) { this.goalId = goalId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public BigDecimal getExpectedProgress() { return expectedProgress; }
    public void setExpectedProgress(BigDecimal expectedProgress) { this.expectedProgress = expectedProgress; }
    public BigDecimal getActualProgress() { return actualProgress; }
    public void setActualProgress(BigDecimal actualProgress) { this.actualProgress = actualProgress; }
    public BigDecimal getLag() { return lag; }
    public void setLag(BigDecimal lag) { this.lag = lag; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    public String getDefinition() { return definition; }
    public void setDefinition(String definition) { this.definition = definition; }
    public Date getLastUpdated() { return lastUpdated == null ? null : new Date(lastUpdated.getTime()); }
    public void setLastUpdated(Date lastUpdated) { this.lastUpdated = lastUpdated == null ? null : new Date(lastUpdated.getTime()); }
    public Map<String, Object> getDrillDownFilters() { return drillDownFilters; }
    public void setDrillDownFilters(Map<String, Object> drillDownFilters) { this.drillDownFilters = drillDownFilters; }
}
