package com.ailab.system.dto;

/** Locked critical-asset backup fact as of a score cutoff. */
public class PerformanceAssetFact {
    private Long assetId; private String assetName; private Long primaryOwnerId; private boolean activeBackup; private boolean quarterBackupTraining;
    public Long getAssetId(){return assetId;} public void setAssetId(Long v){assetId=v;} public String getAssetName(){return assetName;} public void setAssetName(String v){assetName=v;}
    public Long getPrimaryOwnerId(){return primaryOwnerId;} public void setPrimaryOwnerId(Long v){primaryOwnerId=v;} public boolean isActiveBackup(){return activeBackup;} public void setActiveBackup(boolean v){activeBackup=v;}
    public boolean isQuarterBackupTraining(){return quarterBackupTraining;} public void setQuarterBackupTraining(boolean v){quarterBackupTraining=v;}
}
