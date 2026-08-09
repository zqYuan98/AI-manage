package com.ailab.system.report.provider;

/** Defines which immutable fact boundary a report provider must use for a final report. */
public enum ReportFactClassification {
    FORMAL_SNAPSHOT,
    FORMAL_CLOSE_SNAPSHOT,
    CONTEXT_SNAPSHOT,
    MANUAL_REVISION;

    static ReportFactClassification requireForProvider(String id) {
        if ("GOAL_PROGRESS".equals(id) || "PERF_SUMMARY".equals(id)) return FORMAL_SNAPSHOT;
        if ("TASK_DETAIL".equals(id) || "TASK_STAT".equals(id) || "TASK_UNDONE".equals(id)) return FORMAL_CLOSE_SNAPSHOT;
        if ("TASK_NEXT".equals(id) || "TASK_COORD".equals(id) || "TASK_BLOCK".equals(id)
                || "ASSET_SUMMARY".equals(id) || "IPR_SUMMARY".equals(id)) return CONTEXT_SNAPSHOT;
        if ("MANUAL_SUMMARY".equals(id)) return MANUAL_REVISION;
        throw new IllegalArgumentException("Unknown report provider classification: " + id);
    }
}
