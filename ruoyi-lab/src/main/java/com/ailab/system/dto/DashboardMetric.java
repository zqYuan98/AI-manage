package com.ailab.system.dto;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

public class DashboardMetric {
    private String code;
    private String name;
    private Object value;
    private String unit;
    private String period;
    private String definition;
    private Date lastUpdated;
    private Map<String, Object> drillDownFilters = new LinkedHashMap<String, Object>();
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Object getValue() { return value; }
    public void setValue(Object value) { this.value = value; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    public String getDefinition() { return definition; }
    public void setDefinition(String definition) { this.definition = definition; }
    public Date getLastUpdated() { return lastUpdated == null ? null : new Date(lastUpdated.getTime()); }
    public void setLastUpdated(Date value) { lastUpdated = value == null ? null : new Date(value.getTime()); }
    public Map<String, Object> getDrillDownFilters() { return drillDownFilters; }
    public void setDrillDownFilters(Map<String, Object> value) { drillDownFilters = value; }
}
