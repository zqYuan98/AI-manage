package com.ailab.system.dto;

import java.math.BigDecimal;

/** Side-by-side legacy and named projections used during guarded cutover. */
public class ProgressComparison {
    private BigDecimal legacyProgress;
    private CommitmentProgress namedProgress;
    private String activeProjection;

    public BigDecimal getLegacyProgress() { return legacyProgress; }
    public void setLegacyProgress(BigDecimal value) { legacyProgress = value; }
    public CommitmentProgress getNamedProgress() { return namedProgress; }
    public void setNamedProgress(CommitmentProgress value) { namedProgress = value; }
    public String getActiveProjection() { return activeProjection; }
    public void setActiveProjection(String value) { activeProjection = value; }
    public boolean isMatching() {
        return legacyProgress != null && namedProgress != null && namedProgress.getOperationalProgress() != null
                && legacyProgress.compareTo(namedProgress.getOperationalProgress()) == 0;
    }
    public static ProgressComparison legacyActive(BigDecimal legacy, CommitmentProgress named) {
        ProgressComparison result = new ProgressComparison(); result.setLegacyProgress(legacy);
        result.setNamedProgress(named); result.setActiveProjection("LEGACY"); return result;
    }
}
