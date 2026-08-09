package com.ailab.system.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import java.util.Date;

/** Immutable weekly execution transition. */
public class LabTaskExecutionEvent extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long id; private Long taskId; private String fromStatus; private String toStatus;
    private String resultStatus; private Date actualFinishTime; private Long actorId; private String eventType;
    private String reason; private Integer taskVersion; private Integer evidenceVersion; private String idempotencyKey;
    private Date eventTime; private String delFlag;
    public Long getId(){return id;} public void setId(Long value){id=value;}
    public Long getTaskId(){return taskId;} public void setTaskId(Long value){taskId=value;}
    public String getFromStatus(){return fromStatus;} public void setFromStatus(String value){fromStatus=value;}
    public String getToStatus(){return toStatus;} public void setToStatus(String value){toStatus=value;}
    public String getResultStatus(){return resultStatus;} public void setResultStatus(String value){resultStatus=value;}
    public Date getActualFinishTime(){return copy(actualFinishTime);} public void setActualFinishTime(Date value){actualFinishTime=copy(value);}
    public Long getActorId(){return actorId;} public void setActorId(Long value){actorId=value;}
    public String getEventType(){return eventType;} public void setEventType(String value){eventType=value;}
    public String getReason(){return reason;} public void setReason(String value){reason=value;}
    public Integer getTaskVersion(){return taskVersion;} public void setTaskVersion(Integer value){taskVersion=value;}
    public Integer getEvidenceVersion(){return evidenceVersion;} public void setEvidenceVersion(Integer value){evidenceVersion=value;}
    public String getIdempotencyKey(){return idempotencyKey;} public void setIdempotencyKey(String value){idempotencyKey=value;}
    public Date getEventTime(){return copy(eventTime);} public void setEventTime(Date value){eventTime=copy(value);}
    public String getDelFlag(){return delFlag;} public void setDelFlag(String value){delFlag=value;}
    private Date copy(Date value){return value==null?null:new Date(value.getTime());}
}
