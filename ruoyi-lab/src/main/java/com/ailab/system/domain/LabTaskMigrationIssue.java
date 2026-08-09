package com.ailab.system.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import java.util.Date;

/** Persisted quarantine for a legacy state that cannot be mapped safely. */
public class LabTaskMigrationIssue extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long id; private Long taskId; private String issueCode; private String sourceStateJson;
    private String resolutionStatus; private String resolutionCode; private Long resolvedBy; private Date resolvedTime; private Integer version; private String delFlag;
    public Long getId(){return id;} public void setId(Long value){id=value;}
    public Long getTaskId(){return taskId;} public void setTaskId(Long value){taskId=value;}
    public String getIssueCode(){return issueCode;} public void setIssueCode(String value){issueCode=value;}
    public String getSourceStateJson(){return sourceStateJson;} public void setSourceStateJson(String value){sourceStateJson=value;}
    public String getResolutionStatus(){return resolutionStatus;} public void setResolutionStatus(String value){resolutionStatus=value;}
    public String getResolutionCode(){return resolutionCode;} public void setResolutionCode(String value){resolutionCode=value;}
    public Long getResolvedBy(){return resolvedBy;} public void setResolvedBy(Long value){resolvedBy=value;}
    public Date getResolvedTime(){return copy(resolvedTime);} public void setResolvedTime(Date value){resolvedTime=copy(value);}
    public Integer getVersion(){return version;} public void setVersion(Integer value){version=value;}
    public String getDelFlag(){return delFlag;} public void setDelFlag(String value){delFlag=value;}
    private Date copy(Date value){return value==null?null:new Date(value.getTime());}
}
