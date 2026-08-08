package com.ailab.system.dto;

import java.math.BigDecimal;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

public class MemberLoad {
    private Long memberId;
    private String memberName;
    private String bizLine;
    private BigDecimal keyTaskWeight = BigDecimal.ZERO;
    private Integer activeTaskCount = 0;
    private Integer recentWeekTaskCount = 0;
    private Integer overdueCount = 0;
    private Integer blockedCount = 0;
    private Integer coordinationCount = 0;
    private String heatLevel;
    private String period;
    private String definition;
    private Date lastUpdated;
    private Map<String, Object> drillDownFilters = new LinkedHashMap<String, Object>();
    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
    public String getMemberName() { return memberName; }
    public void setMemberName(String memberName) { this.memberName = memberName; }
    public String getBizLine() { return bizLine; }
    public void setBizLine(String bizLine) { this.bizLine = bizLine; }
    public BigDecimal getKeyTaskWeight() { return keyTaskWeight; }
    public void setKeyTaskWeight(BigDecimal value) { keyTaskWeight = value; }
    public Integer getActiveTaskCount() { return activeTaskCount; }
    public void setActiveTaskCount(Integer value) { activeTaskCount = value; }
    public Integer getRecentWeekTaskCount() { return recentWeekTaskCount; }
    public void setRecentWeekTaskCount(Integer value) { recentWeekTaskCount = value; }
    public Integer getOverdueCount() { return overdueCount; }
    public void setOverdueCount(Integer value) { overdueCount = value; }
    public Integer getBlockedCount() { return blockedCount; }
    public void setBlockedCount(Integer value) { blockedCount = value; }
    public Integer getCoordinationCount() { return coordinationCount; }
    public void setCoordinationCount(Integer value) { coordinationCount = value; }
    public String getHeatLevel() { return heatLevel; }
    public void setHeatLevel(String heatLevel) { this.heatLevel = heatLevel; }
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    public String getDefinition() { return definition; }
    public void setDefinition(String definition) { this.definition = definition; }
    public Date getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Date lastUpdated) { this.lastUpdated = lastUpdated; }
    public Map<String, Object> getDrillDownFilters() { return drillDownFilters; }
    public void setDrillDownFilters(Map<String, Object> value) { drillDownFilters = value; }
}
