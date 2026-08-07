package com.ailab.system.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import java.util.Date;

public class LabOne2One extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long memberId;
    private Long leaderId;
    private Date meetingDate;
    private String topic;
    private String factsEvidence;
    private String difficulties;
    private String nextAction;
    private String managerComment;
    private String status;
    private Integer version;
    private String delFlag;
    private String memberName;
    private String leaderName;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public Long getMemberId(){return memberId;} public void setMemberId(Long v){memberId=v;}
    public Long getLeaderId(){return leaderId;} public void setLeaderId(Long v){leaderId=v;}
    public Date getMeetingDate(){return meetingDate;} public void setMeetingDate(Date v){meetingDate=v;}
    public String getTopic(){return topic;} public void setTopic(String v){topic=v;}
    public String getFactsEvidence(){return factsEvidence;} public void setFactsEvidence(String v){factsEvidence=v;}
    public String getDifficulties(){return difficulties;} public void setDifficulties(String v){difficulties=v;}
    public String getNextAction(){return nextAction;} public void setNextAction(String v){nextAction=v;}
    public String getManagerComment(){return managerComment;} public void setManagerComment(String v){managerComment=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;}
    public String getDelFlag(){return delFlag;} public void setDelFlag(String v){delFlag=v;}
    public String getMemberName(){return memberName;} public void setMemberName(String v){memberName=v;}
    public String getLeaderName(){return leaderName;} public void setLeaderName(String v){leaderName=v;}
}
