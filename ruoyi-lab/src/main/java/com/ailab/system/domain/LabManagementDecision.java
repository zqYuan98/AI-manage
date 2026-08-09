package com.ailab.system.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import java.util.Date;

/** Minimal management decision recorded during weekly review. */
public class LabManagementDecision extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long id; private String period; private String bizLine; private String problem; private String decisionContent;
    private Long ownerId; private Date dueDate; private Long relatedGoalId; private Long relatedTaskId;
    private String decisionStatus; private Integer version; private String delFlag;
    public Long getId(){return id;} public void setId(Long value){id=value;}
    public String getPeriod(){return period;} public void setPeriod(String value){period=value;}
    public String getBizLine(){return bizLine;} public void setBizLine(String value){bizLine=value;}
    public String getProblem(){return problem;} public void setProblem(String value){problem=value;}
    public String getDecisionContent(){return decisionContent;} public void setDecisionContent(String value){decisionContent=value;}
    public Long getOwnerId(){return ownerId;} public void setOwnerId(Long value){ownerId=value;}
    public Date getDueDate(){return copy(dueDate);} public void setDueDate(Date value){dueDate=copy(value);}
    public Long getRelatedGoalId(){return relatedGoalId;} public void setRelatedGoalId(Long value){relatedGoalId=value;}
    public Long getRelatedTaskId(){return relatedTaskId;} public void setRelatedTaskId(Long value){relatedTaskId=value;}
    public String getDecisionStatus(){return decisionStatus;} public void setDecisionStatus(String value){decisionStatus=value;}
    public Integer getVersion(){return version;} public void setVersion(Integer value){version=value;}
    public String getDelFlag(){return delFlag;} public void setDelFlag(String value){delFlag=value;}
    private Date copy(Date value){return value==null?null:new Date(value.getTime());}
}
