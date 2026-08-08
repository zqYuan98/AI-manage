package com.ailab.system.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;
import java.util.Date;

/** Persisted, recipient-specific and idempotent laboratory notification. */
public class LabReminder extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long taskId;
    private String businessType;
    private Long businessId;
    private Integer episodeNo;
    private Long recipientId;
    private String reminderType;
    private String reminderLevel;
    @JsonFormat(pattern = "yyyy-MM-dd") private Date reminderDate;
    private String title;
    private String reminderContent;
    private String readFlag;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") private Date readTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") private Date sendTime;
    private String idempotencyKey;
    private Integer version;
    private String delFlag;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }
    public Long getBusinessId() { return businessId; }
    public void setBusinessId(Long businessId) { this.businessId = businessId; }
    public Integer getEpisodeNo() { return episodeNo; }
    public void setEpisodeNo(Integer episodeNo) { this.episodeNo = episodeNo; }
    public Long getRecipientId() { return recipientId; }
    public void setRecipientId(Long recipientId) { this.recipientId = recipientId; }
    public String getReminderType() { return reminderType; }
    public void setReminderType(String reminderType) { this.reminderType = reminderType; }
    public String getReminderLevel() { return reminderLevel; }
    public void setReminderLevel(String reminderLevel) { this.reminderLevel = reminderLevel; }
    public Date getReminderDate() { return copy(reminderDate); }
    public void setReminderDate(Date reminderDate) { this.reminderDate = copy(reminderDate); }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getReminderContent() { return reminderContent; }
    public void setReminderContent(String reminderContent) { this.reminderContent = reminderContent; }
    public String getReadFlag() { return readFlag; }
    public void setReadFlag(String readFlag) { this.readFlag = readFlag; }
    public Date getReadTime() { return copy(readTime); }
    public void setReadTime(Date readTime) { this.readTime = copy(readTime); }
    public Date getSendTime() { return copy(sendTime); }
    public void setSendTime(Date sendTime) { this.sendTime = copy(sendTime); }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
    private Date copy(Date value) { return value == null ? null : new Date(value.getTime()); }
}
