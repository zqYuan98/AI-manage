package com.ailab.system.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Annual goal or direct quarterly milestone persisted in {@code lab_goal}. */
public class LabGoal extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long parentId;
    private String goalLevel;
    private Integer year;
    private String period;
    private String goalNo;
    private String title;
    private String targetValue;
    private String acceptCriteria;
    private Long ownerId;
    private BigDecimal weight;
    private String progressMode;
    private BigDecimal progressRate;
    private String progressDesc;
    private String status;
    private Integer version;
    private String delFlag;
    private List<LabGoal> children = new ArrayList<LabGoal>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getGoalLevel() { return goalLevel; }
    public void setGoalLevel(String goalLevel) { this.goalLevel = goalLevel; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    public String getGoalNo() { return goalNo; }
    public void setGoalNo(String goalNo) { this.goalNo = goalNo; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getTargetValue() { return targetValue; }
    public void setTargetValue(String targetValue) { this.targetValue = targetValue; }
    public String getAcceptCriteria() { return acceptCriteria; }
    public void setAcceptCriteria(String acceptCriteria) { this.acceptCriteria = acceptCriteria; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }
    public String getProgressMode() { return progressMode; }
    public void setProgressMode(String progressMode) { this.progressMode = progressMode; }
    public BigDecimal getProgressRate() { return progressRate; }
    public void setProgressRate(BigDecimal progressRate) { this.progressRate = progressRate; }
    public String getProgressDesc() { return progressDesc; }
    public void setProgressDesc(String progressDesc) { this.progressDesc = progressDesc; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
    public List<LabGoal> getChildren() { return children; }
    public void setChildren(List<LabGoal> children) { this.children = children == null ? new ArrayList<LabGoal>() : children; }
}
