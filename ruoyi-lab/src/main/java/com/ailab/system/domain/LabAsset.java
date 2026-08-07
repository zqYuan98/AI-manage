package com.ailab.system.domain;

import com.ruoyi.common.core.domain.BaseEntity;

public class LabAsset extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String assetNo;
    private String assetName;
    private String assetVersion;
    private String assetType;
    private String assetStage;
    private Long primaryOwnerId;
    private Long backupOwnerId;
    private String testSetUrl;
    private String deployPackageUrl;
    private String documentUrl;
    private String resourceUrl;
    private String repositoryUrl;
    private String capacityDesc;
    private Integer reuseCount;
    private String criticalFlag;
    private String status;
    private Integer version;
    private String delFlag;
    private String primaryOwnerName;
    private String primaryOwnerBizLine;
    private String backupOwnerName;
    private String backupOwnerStatus;
    private boolean singlePointRisk;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public String getAssetNo(){return assetNo;} public void setAssetNo(String v){assetNo=v;}
    public String getAssetName(){return assetName;} public void setAssetName(String v){assetName=v;}
    public String getAssetVersion(){return assetVersion;} public void setAssetVersion(String v){assetVersion=v;}
    public String getAssetType(){return assetType;} public void setAssetType(String v){assetType=v;}
    public String getAssetStage(){return assetStage;} public void setAssetStage(String v){assetStage=v;}
    public Long getPrimaryOwnerId(){return primaryOwnerId;} public void setPrimaryOwnerId(Long v){primaryOwnerId=v;}
    public Long getBackupOwnerId(){return backupOwnerId;} public void setBackupOwnerId(Long v){backupOwnerId=v;}
    public String getTestSetUrl(){return testSetUrl;} public void setTestSetUrl(String v){testSetUrl=v;}
    public String getDeployPackageUrl(){return deployPackageUrl;} public void setDeployPackageUrl(String v){deployPackageUrl=v;}
    public String getDocumentUrl(){return documentUrl;} public void setDocumentUrl(String v){documentUrl=v;}
    public String getResourceUrl(){return resourceUrl;} public void setResourceUrl(String v){resourceUrl=v;}
    public String getRepositoryUrl(){return repositoryUrl;} public void setRepositoryUrl(String v){repositoryUrl=v;}
    public String getCapacityDesc(){return capacityDesc;} public void setCapacityDesc(String v){capacityDesc=v;}
    public Integer getReuseCount(){return reuseCount;} public void setReuseCount(Integer v){reuseCount=v;}
    public String getCriticalFlag(){return criticalFlag;} public void setCriticalFlag(String v){criticalFlag=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;}
    public String getDelFlag(){return delFlag;} public void setDelFlag(String v){delFlag=v;}
    public String getPrimaryOwnerName(){return primaryOwnerName;} public void setPrimaryOwnerName(String v){primaryOwnerName=v;}
    public String getPrimaryOwnerBizLine(){return primaryOwnerBizLine;} public void setPrimaryOwnerBizLine(String v){primaryOwnerBizLine=v;}
    public String getBackupOwnerName(){return backupOwnerName;} public void setBackupOwnerName(String v){backupOwnerName=v;}
    public String getBackupOwnerStatus(){return backupOwnerStatus;} public void setBackupOwnerStatus(String v){backupOwnerStatus=v;}
    public boolean isSinglePointRisk(){return singlePointRisk;} public void setSinglePointRisk(boolean v){singlePointRisk=v;}
}
