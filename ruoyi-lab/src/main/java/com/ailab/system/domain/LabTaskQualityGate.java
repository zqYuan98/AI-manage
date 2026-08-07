package com.ailab.system.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;
import java.util.Date;

/** Verifiable quality gate attached to one task. */
public class LabTaskQualityGate extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long taskId;
    private String gateNo;
    private String gateName;
    private String gateStatus;
    private Long evidenceId;
    private Long checkerId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date checkTime;
    private String checkResult;
    private String delFlag;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getGateNo() { return gateNo; }
    public void setGateNo(String gateNo) { this.gateNo = gateNo; }
    public String getGateName() { return gateName; }
    public void setGateName(String gateName) { this.gateName = gateName; }
    public String getGateStatus() { return gateStatus; }
    public void setGateStatus(String gateStatus) { this.gateStatus = gateStatus; }
    public Long getEvidenceId() { return evidenceId; }
    public void setEvidenceId(Long evidenceId) { this.evidenceId = evidenceId; }
    public Long getCheckerId() { return checkerId; }
    public void setCheckerId(Long checkerId) { this.checkerId = checkerId; }
    public Date getCheckTime() { return checkTime; }
    public void setCheckTime(Date checkTime) { this.checkTime = checkTime; }
    public String getCheckResult() { return checkResult; }
    public void setCheckResult(String checkResult) { this.checkResult = checkResult; }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
}
