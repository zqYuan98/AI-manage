package com.ailab.system.dto;

import java.util.Date;

/** One trusted task/recipient projection returned by an aggregate reminder query. */
public class ReminderCandidate {
    private String candidateType;
    private String businessType;
    private Long businessId;
    private Long taskId;
    private String taskTitle;
    private Integer episodeNo;
    private Date blockStartTime;
    private Long recipientId;
    private String audience;
    private String missingFields;
    private String period;

    public String getCandidateType() { return candidateType; }
    public void setCandidateType(String candidateType) { this.candidateType = candidateType; }
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }
    public Long getBusinessId() { return businessId; }
    public void setBusinessId(Long businessId) { this.businessId = businessId; }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getTaskTitle() { return taskTitle; }
    public void setTaskTitle(String taskTitle) { this.taskTitle = taskTitle; }
    public Integer getEpisodeNo() { return episodeNo; }
    public void setEpisodeNo(Integer episodeNo) { this.episodeNo = episodeNo; }
    public Date getBlockStartTime() { return blockStartTime == null ? null : new Date(blockStartTime.getTime()); }
    public void setBlockStartTime(Date blockStartTime) { this.blockStartTime = blockStartTime == null ? null : new Date(blockStartTime.getTime()); }
    public Long getRecipientId() { return recipientId; }
    public void setRecipientId(Long recipientId) { this.recipientId = recipientId; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public String getMissingFields() { return missingFields; }
    public void setMissingFields(String missingFields) { this.missingFields = missingFields; }
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
}
