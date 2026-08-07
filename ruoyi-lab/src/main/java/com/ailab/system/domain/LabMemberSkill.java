package com.ailab.system.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import java.util.Date;

public class LabMemberSkill extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long memberId;
    private Long skillId;
    private Integer level;
    private Date lastVerifiedDate;
    private String evidenceUrl;
    private Integer version;
    private String delFlag;
    private String skillCode;
    private String skillName;
    private String skillCategory;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public Long getMemberId(){return memberId;} public void setMemberId(Long v){memberId=v;}
    public Long getSkillId(){return skillId;} public void setSkillId(Long v){skillId=v;}
    public Integer getLevel(){return level;} public void setLevel(Integer v){level=v;}
    public Date getLastVerifiedDate(){return lastVerifiedDate;} public void setLastVerifiedDate(Date v){lastVerifiedDate=v;}
    public String getEvidenceUrl(){return evidenceUrl;} public void setEvidenceUrl(String v){evidenceUrl=v;}
    public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;}
    public String getDelFlag(){return delFlag;} public void setDelFlag(String v){delFlag=v;}
    public String getSkillCode(){return skillCode;} public void setSkillCode(String v){skillCode=v;}
    public String getSkillName(){return skillName;} public void setSkillName(String v){skillName=v;}
    public String getSkillCategory(){return skillCategory;} public void setSkillCategory(String v){skillCategory=v;}
}
