package com.ailab.system.dto;

/** Current trusted report scope; never populated from request data or persisted snapshots. */
public final class LabReportAccessScope {
    private final boolean manager;
    private final String bizLine;
    private final boolean sensitiveGranted;
    private final boolean shareAllFinalizedNonSensitive;

    public LabReportAccessScope(boolean manager, String bizLine, boolean sensitiveGranted,
            boolean shareAllFinalizedNonSensitive) {
        this.manager = manager;
        this.bizLine = bizLine;
        this.sensitiveGranted = sensitiveGranted;
        this.shareAllFinalizedNonSensitive = shareAllFinalizedNonSensitive;
    }

    public boolean isManager() { return manager; }
    public String getBizLine() { return bizLine; }
    public boolean isSensitiveGranted() { return sensitiveGranted; }
    public boolean isShareAllFinalizedNonSensitive() { return shareAllFinalizedNonSensitive; }
}
