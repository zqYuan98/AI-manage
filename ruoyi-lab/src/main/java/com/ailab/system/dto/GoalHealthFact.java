package com.ailab.system.dto;

import java.math.BigDecimal;

/** Database-aggregated inputs for the explainable annual-goal health policy. */
public class GoalHealthFact {
    private Long goalId;
    private String goalTitle;
    private Integer year;
    private BigDecimal expectedProgress;
    private BigDecimal actualProgress;
    private Integer maxOpenBlockDays;
    private boolean delayedFocusTask;
    private boolean overdueUnsubmittedFocusTask;

    public Long getGoalId() { return goalId; }
    public void setGoalId(Long goalId) { this.goalId = goalId; }
    public String getGoalTitle() { return goalTitle; }
    public void setGoalTitle(String goalTitle) { this.goalTitle = goalTitle; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public BigDecimal getExpectedProgress() { return expectedProgress; }
    public void setExpectedProgress(BigDecimal expectedProgress) { this.expectedProgress = expectedProgress; }
    public BigDecimal getActualProgress() { return actualProgress; }
    public void setActualProgress(BigDecimal actualProgress) { this.actualProgress = actualProgress; }
    public Integer getMaxOpenBlockDays() { return maxOpenBlockDays; }
    public void setMaxOpenBlockDays(Integer maxOpenBlockDays) { this.maxOpenBlockDays = maxOpenBlockDays; }
    public boolean isDelayedFocusTask() { return delayedFocusTask; }
    public void setDelayedFocusTask(boolean delayedFocusTask) { this.delayedFocusTask = delayedFocusTask; }
    public boolean isOverdueUnsubmittedFocusTask() { return overdueUnsubmittedFocusTask; }
    public void setOverdueUnsubmittedFocusTask(boolean overdueUnsubmittedFocusTask) { this.overdueUnsubmittedFocusTask = overdueUnsubmittedFocusTask; }
}
