package com.ailab.system.dto;

import java.util.Date;

/** Client supplied fields for the deliberately small weekly commitment workflow. */
public class WeeklyCommitmentCommand {
    private Long parentTaskId;
    private String title;
    private String deliverable;
    private String period;
    private Date planDate;
    private Date actualFinishTime;
    private String resultDescription;
    private String failReason;
    private String nextAction;
    private String reason;
    private String coordinationRequired;
    private Long coordinationOwnerId;
    private Long coordinationDeptId;
    private String coordinationContent;
    private String coordinationSupport;

    public Long getParentTaskId(){return parentTaskId;} public void setParentTaskId(Long value){parentTaskId=value;}
    public String getTitle(){return title;} public void setTitle(String value){title=value;}
    public String getDeliverable(){return deliverable;} public void setDeliverable(String value){deliverable=value;}
    public String getPeriod(){return period;} public void setPeriod(String value){period=value;}
    public Date getPlanDate(){return copy(planDate);} public void setPlanDate(Date value){planDate=copy(value);}
    public Date getActualFinishTime(){return copy(actualFinishTime);} public void setActualFinishTime(Date value){actualFinishTime=copy(value);}
    public String getResultDescription(){return resultDescription;} public void setResultDescription(String value){resultDescription=value;}
    public String getFailReason(){return failReason;} public void setFailReason(String value){failReason=value;}
    public String getNextAction(){return nextAction;} public void setNextAction(String value){nextAction=value;}
    public String getReason(){return reason;} public void setReason(String value){reason=value;}
    public String getCoordinationRequired(){return coordinationRequired;} public void setCoordinationRequired(String value){coordinationRequired=value;}
    public Long getCoordinationOwnerId(){return coordinationOwnerId;} public void setCoordinationOwnerId(Long value){coordinationOwnerId=value;}
    public Long getCoordinationDeptId(){return coordinationDeptId;} public void setCoordinationDeptId(Long value){coordinationDeptId=value;}
    public String getCoordinationContent(){return coordinationContent;} public void setCoordinationContent(String value){coordinationContent=value;}
    public String getCoordinationSupport(){return coordinationSupport;} public void setCoordinationSupport(String value){coordinationSupport=value;}
    private Date copy(Date value){return value==null?null:new Date(value.getTime());}
}
