package com.ailab.system.dto;

import com.ailab.system.domain.LabManagementDecision;
import java.util.ArrayList;
import java.util.List;
import java.util.Date;

/** Action-first workbench for managers and same-line leads. */
public class ManagerWorkbench {
    private String period;
    private String scopeType;
    private String bizLine;
    private boolean managerActionsAllowed;
    private Date asOf;
    private List<LabManagementDecision> pendingDecisions = new ArrayList<LabManagementDecision>();
    private List<DashboardActionItem> newBlocks = new ArrayList<DashboardActionItem>();
    private List<DashboardActionItem> forecastDelays = new ArrayList<DashboardActionItem>();
    private List<DashboardActionItem> pendingAcceptance = new ArrayList<DashboardActionItem>();
    private List<DashboardActionItem> staleKeyResults = new ArrayList<DashboardActionItem>();
    private List<MemberLoad> teamCommitments = new ArrayList<MemberLoad>();
    public String getPeriod(){return period;} public void setPeriod(String value){period=value;}
    public String getScopeType(){return scopeType;} public void setScopeType(String value){scopeType=value;}
    public String getBizLine(){return bizLine;} public void setBizLine(String value){bizLine=value;}
    public boolean isManagerActionsAllowed(){return managerActionsAllowed;} public void setManagerActionsAllowed(boolean value){managerActionsAllowed=value;}
    public Date getAsOf(){return copy(asOf);} public void setAsOf(Date value){asOf=copy(value);}
    public List<LabManagementDecision> getPendingDecisions(){return pendingDecisions;} public void setPendingDecisions(List<LabManagementDecision> value){pendingDecisions=safe(value);}
    public List<DashboardActionItem> getNewBlocks(){return newBlocks;} public void setNewBlocks(List<DashboardActionItem> value){newBlocks=safe(value);}
    public List<DashboardActionItem> getForecastDelays(){return forecastDelays;} public void setForecastDelays(List<DashboardActionItem> value){forecastDelays=safe(value);}
    public List<DashboardActionItem> getPendingAcceptance(){return pendingAcceptance;} public void setPendingAcceptance(List<DashboardActionItem> value){pendingAcceptance=safe(value);}
    public List<DashboardActionItem> getStaleKeyResults(){return staleKeyResults;} public void setStaleKeyResults(List<DashboardActionItem> value){staleKeyResults=safe(value);}
    public List<MemberLoad> getTeamCommitments(){return teamCommitments;} public void setTeamCommitments(List<MemberLoad> value){teamCommitments=safe(value);}
    private <T> List<T> safe(List<T> value){return value==null?new ArrayList<T>():new ArrayList<T>(value);}
    private Date copy(Date value){return value==null?null:new Date(value.getTime());}
}
