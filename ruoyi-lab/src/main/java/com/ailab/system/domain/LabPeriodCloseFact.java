package com.ailab.system.domain;

import com.ruoyi.common.core.domain.BaseEntity;

/** 月结修订中的类型化业务事实。 */
public class LabPeriodCloseFact extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long closeSnapshotId;
    private String factType;
    private Long businessId;
    private String factJson;
    private String delFlag;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCloseSnapshotId() { return closeSnapshotId; }
    public void setCloseSnapshotId(Long closeSnapshotId) { this.closeSnapshotId = closeSnapshotId; }
    public String getFactType() { return factType; }
    public void setFactType(String factType) { this.factType = factType; }
    public Long getBusinessId() { return businessId; }
    public void setBusinessId(Long businessId) { this.businessId = businessId; }
    public String getFactJson() { return factJson; }
    public void setFactJson(String factJson) { this.factJson = factJson; }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
}
