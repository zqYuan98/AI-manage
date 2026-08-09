package com.ailab.system.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import java.util.Date;

/** 月度任务工作流的不可变审计事件。 */
public class LabTaskWorkflowEvent extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long taskId;
    private String fromStatus;
    private String toStatus;
    private String resultStatus;
    private Long actorId;
    private String eventType;
    private String reason;
    private Integer taskVersion;
    private Date eventTime;
    private String idempotencyKey;
    private String delFlag;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }
    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }
    public String getResultStatus() { return resultStatus; }
    public void setResultStatus(String resultStatus) { this.resultStatus = resultStatus; }
    public Long getActorId() { return actorId; }
    public void setActorId(Long actorId) { this.actorId = actorId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Integer getTaskVersion() { return taskVersion; }
    public void setTaskVersion(Integer taskVersion) { this.taskVersion = taskVersion; }
    public Date getEventTime() { return copy(eventTime); }
    public void setEventTime(Date eventTime) { this.eventTime = copy(eventTime); }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
    private Date copy(Date value) { return value == null ? null : new Date(value.getTime()); }
}
