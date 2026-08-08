package com.ailab.system.dto;

import java.util.ArrayList;
import java.util.List;

public class DashboardOverview {
    private List<DashboardMetric> kpis = new ArrayList<DashboardMetric>();
    private List<GoalHealth> goalHealth = new ArrayList<GoalHealth>();
    private List<GoalTrendPoint> goalTrend = new ArrayList<GoalTrendPoint>();
    private List<DashboardCountItem> taskStatusDistribution = new ArrayList<DashboardCountItem>();
    private List<MemberLoad> memberLoads = new ArrayList<MemberLoad>();
    private List<DashboardActionItem> coordinationItems = new ArrayList<DashboardActionItem>();
    private List<DashboardActionItem> recentIpr = new ArrayList<DashboardActionItem>();
    private List<DashboardActionItem> recentReports = new ArrayList<DashboardActionItem>();
    private DashboardActionItem latestReport;
    private List<DashboardCountItem> performanceSummary = new ArrayList<DashboardCountItem>();
    public List<DashboardMetric> getKpis() { return kpis; }
    public void setKpis(List<DashboardMetric> value) { kpis = value; }
    public List<GoalHealth> getGoalHealth() { return goalHealth; }
    public void setGoalHealth(List<GoalHealth> value) { goalHealth = value; }
    public List<GoalTrendPoint> getGoalTrend() { return goalTrend; }
    public void setGoalTrend(List<GoalTrendPoint> value) { goalTrend = value; }
    public List<DashboardCountItem> getTaskStatusDistribution() { return taskStatusDistribution; }
    public void setTaskStatusDistribution(List<DashboardCountItem> value) { taskStatusDistribution = value; }
    public List<MemberLoad> getMemberLoads() { return memberLoads; }
    public void setMemberLoads(List<MemberLoad> value) { memberLoads = value; }
    public List<DashboardActionItem> getCoordinationItems() { return coordinationItems; }
    public void setCoordinationItems(List<DashboardActionItem> value) { coordinationItems = value; }
    public List<DashboardActionItem> getRecentIpr() { return recentIpr; }
    public void setRecentIpr(List<DashboardActionItem> value) { recentIpr = value; }
    public List<DashboardActionItem> getRecentReports() { return recentReports; }
    public void setRecentReports(List<DashboardActionItem> value) { recentReports = value; }
    public DashboardActionItem getLatestReport() { return latestReport; }
    public void setLatestReport(DashboardActionItem value) { latestReport = value; }
    public List<DashboardCountItem> getPerformanceSummary() { return performanceSummary; }
    public void setPerformanceSummary(List<DashboardCountItem> value) { performanceSummary = value; }
}
