package com.ailab.system.dto;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

public class DashboardActionItem {
    private Long id;
    private String type;
    private String title;
    private String status;
    private Date dueDate;
    private String period;
    private String definition;
    private Date lastUpdated;
    private Map<String, Object> drillDownFilters = new LinkedHashMap<String, Object>();
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Date getDueDate() { return dueDate; }
    public void setDueDate(Date dueDate) { this.dueDate = dueDate; }
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    public String getDefinition() { return definition; }
    public void setDefinition(String definition) { this.definition = definition; }
    public Date getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Date lastUpdated) { this.lastUpdated = lastUpdated; }
    public Map<String, Object> getDrillDownFilters() { return drillDownFilters; }
    public void setDrillDownFilters(Map<String, Object> value) { drillDownFilters = value; }
}
