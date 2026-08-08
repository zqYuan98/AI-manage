package com.ailab.system.dto;

import java.math.BigDecimal;

public class DashboardKpiFact {
    private BigDecimal keyTaskCompletionRate = BigDecimal.ZERO;
    private Integer overdueOrPendingCount = 0;
    private Integer blockedOverSevenCount = 0;
    private Integer assetsWithoutBackupCount = 0;
    public BigDecimal getKeyTaskCompletionRate() { return keyTaskCompletionRate; }
    public void setKeyTaskCompletionRate(BigDecimal value) { keyTaskCompletionRate = value; }
    public Integer getOverdueOrPendingCount() { return overdueOrPendingCount; }
    public void setOverdueOrPendingCount(Integer value) { overdueOrPendingCount = value; }
    public Integer getBlockedOverSevenCount() { return blockedOverSevenCount; }
    public void setBlockedOverSevenCount(Integer value) { blockedOverSevenCount = value; }
    public Integer getAssetsWithoutBackupCount() { return assetsWithoutBackupCount; }
    public void setAssetsWithoutBackupCount(Integer value) { assetsWithoutBackupCount = value; }
}
