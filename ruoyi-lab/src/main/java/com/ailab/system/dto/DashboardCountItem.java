package com.ailab.system.dto;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

public class DashboardCountItem {
    private String code;
    private String name;
    private Integer count;
    private String period;
    private String definition;
    private Date lastUpdated;
    private Map<String, Object> drillDownFilters = new LinkedHashMap<String, Object>();
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getCount() { return count; }
    public void setCount(Integer count) { this.count = count; }
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    public String getDefinition() { return definition; }
    public void setDefinition(String definition) { this.definition = definition; }
    public Date getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Date lastUpdated) { this.lastUpdated = lastUpdated; }
    public Map<String, Object> getDrillDownFilters() { return drillDownFilters; }
    public void setDrillDownFilters(Map<String, Object> drillDownFilters) { this.drillDownFilters = drillDownFilters; }
}
