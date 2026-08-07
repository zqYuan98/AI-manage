package com.ailab.system.domain;

import com.ruoyi.common.core.domain.BaseEntity;

public class LabSkill extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String skillCode;
    private String skillName;
    private String skillCategory;
    private String skillDesc;
    private String status;
    private Integer version;
    private String delFlag;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public String getSkillCode(){return skillCode;} public void setSkillCode(String v){skillCode=v;}
    public String getSkillName(){return skillName;} public void setSkillName(String v){skillName=v;}
    public String getSkillCategory(){return skillCategory;} public void setSkillCategory(String v){skillCategory=v;}
    public String getSkillDesc(){return skillDesc;} public void setSkillDesc(String v){skillDesc=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;}
    public String getDelFlag(){return delFlag;} public void setDelFlag(String v){delFlag=v;}
}
