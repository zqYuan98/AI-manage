package com.ailab.system.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Date;

/** Narrow self-service workbench; every row is server-scoped to memberId. */
public class MemberWorkbench {
    private String period;
    private Long memberId;
    private Date asOf;
    private List<DashboardActionItem> monthlyResults = new ArrayList<DashboardActionItem>();
    private List<DashboardActionItem> weeklyCommitments = new ArrayList<DashboardActionItem>();
    private List<DashboardActionItem> dueItems = new ArrayList<DashboardActionItem>();
    private List<DashboardActionItem> blocks = new ArrayList<DashboardActionItem>();
    private List<DashboardActionItem> missingEvidence = new ArrayList<DashboardActionItem>();
    public String getPeriod(){return period;} public void setPeriod(String value){period=value;}
    public Long getMemberId(){return memberId;} public void setMemberId(Long value){memberId=value;}
    public Date getAsOf(){return copy(asOf);} public void setAsOf(Date value){asOf=copy(value);}
    public List<DashboardActionItem> getMonthlyResults(){return monthlyResults;} public void setMonthlyResults(List<DashboardActionItem> value){monthlyResults=safe(value);}
    public List<DashboardActionItem> getWeeklyCommitments(){return weeklyCommitments;} public void setWeeklyCommitments(List<DashboardActionItem> value){weeklyCommitments=safe(value);}
    public List<DashboardActionItem> getDueItems(){return dueItems;} public void setDueItems(List<DashboardActionItem> value){dueItems=safe(value);}
    public List<DashboardActionItem> getBlocks(){return blocks;} public void setBlocks(List<DashboardActionItem> value){blocks=safe(value);}
    public List<DashboardActionItem> getMissingEvidence(){return missingEvidence;} public void setMissingEvidence(List<DashboardActionItem> value){missingEvidence=safe(value);}
    private <T> List<T> safe(List<T> value){return value==null?new ArrayList<T>():new ArrayList<T>(value);}
    private Date copy(Date value){return value==null?null:new Date(value.getTime());}
}
