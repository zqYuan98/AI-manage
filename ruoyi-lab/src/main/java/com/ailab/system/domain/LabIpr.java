package com.ailab.system.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import java.util.Date;

public class LabIpr extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String iprNo;
    private String iprName;
    private String iprType;
    private String iprStage;
    private Long ownerId;
    private Date plannedSubmitDate;
    private Date actualSubmitDate;
    private String acceptanceNo;
    private String certificateNo;
    private Date authorizedDate;
    private String evidenceUrl;
    private String status;
    private String stageChangeReason;
    private Integer version;
    private String delFlag;
    private String ownerName;
    private String ownerBizLine;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public String getIprNo(){return iprNo;} public void setIprNo(String v){iprNo=v;}
    public String getIprName(){return iprName;} public void setIprName(String v){iprName=v;}
    public String getIprType(){return iprType;} public void setIprType(String v){iprType=v;}
    public String getIprStage(){return iprStage;} public void setIprStage(String v){iprStage=v;}
    public Long getOwnerId(){return ownerId;} public void setOwnerId(Long v){ownerId=v;}
    public Date getPlannedSubmitDate(){return plannedSubmitDate;} public void setPlannedSubmitDate(Date v){plannedSubmitDate=v;}
    public Date getActualSubmitDate(){return actualSubmitDate;} public void setActualSubmitDate(Date v){actualSubmitDate=v;}
    public String getAcceptanceNo(){return acceptanceNo;} public void setAcceptanceNo(String v){acceptanceNo=v;}
    public String getCertificateNo(){return certificateNo;} public void setCertificateNo(String v){certificateNo=v;}
    public Date getAuthorizedDate(){return authorizedDate;} public void setAuthorizedDate(Date v){authorizedDate=v;}
    public String getEvidenceUrl(){return evidenceUrl;} public void setEvidenceUrl(String v){evidenceUrl=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getStageChangeReason(){return stageChangeReason;} public void setStageChangeReason(String v){stageChangeReason=v;}
    public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;}
    public String getDelFlag(){return delFlag;} public void setDelFlag(String v){delFlag=v;}
    public String getOwnerName(){return ownerName;} public void setOwnerName(String v){ownerName=v;}
    public String getOwnerBizLine(){return ownerBizLine;} public void setOwnerBizLine(String v){ownerBizLine=v;}
}
