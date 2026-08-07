package com.ailab.system.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;
import java.util.Date;

/** Immutable task block episode; closing an episode never deletes its history. */
public class LabTaskBlockEvent extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long taskId;
    private Integer episodeNo;
    private String blockType;
    private String blockReason;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date blockStartTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date blockEndTime;
    private String blockStatus;
    private Long resolverId;
    private String resolution;
    private String delFlag;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Integer getEpisodeNo() { return episodeNo; }
    public void setEpisodeNo(Integer episodeNo) { this.episodeNo = episodeNo; }
    public String getBlockType() { return blockType; }
    public void setBlockType(String blockType) { this.blockType = blockType; }
    public String getBlockReason() { return blockReason; }
    public void setBlockReason(String blockReason) { this.blockReason = blockReason; }
    public Date getBlockStartTime() { return blockStartTime; }
    public void setBlockStartTime(Date blockStartTime) { this.blockStartTime = blockStartTime; }
    public Date getBlockEndTime() { return blockEndTime; }
    public void setBlockEndTime(Date blockEndTime) { this.blockEndTime = blockEndTime; }
    public String getBlockStatus() { return blockStatus; }
    public void setBlockStatus(String blockStatus) { this.blockStatus = blockStatus; }
    public Long getResolverId() { return resolverId; }
    public void setResolverId(Long resolverId) { this.resolverId = resolverId; }
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
}
