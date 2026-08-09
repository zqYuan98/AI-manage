package com.ailab.system.constant;

/** Stable values shared with the AI laboratory dictionary data. */
public final class LabConstants {
    public static final String WORKFLOW_DRAFT = "DRAFT";
    public static final String WORKFLOW_ACTIVE = "ACTIVE";
    public static final String WORKFLOW_PENDING_REVIEW = "PENDING_REVIEW";
    public static final String WORKFLOW_CONFIRMED = "CONFIRMED";

    public static final String RESULT_DOING = "DOING";
    public static final String RESULT_EXCEEDED = "EXCEEDED";
    public static final String RESULT_ONTIME = "ONTIME";
    public static final String RESULT_DELAYED = "DELAYED";
    public static final String RESULT_UNDONE = "UNDONE";

    public static final String EXECUTION_PLANNED = "PLANNED";
    public static final String EXECUTION_ACTIVE = "ACTIVE";
    public static final String EXECUTION_SELF_DONE = "SELF_DONE";
    public static final String EXECUTION_SELF_UNDONE = "SELF_UNDONE";
    public static final String EXECUTION_CANCELLED = "CANCELLED";
    public static final String EXECUTION_EVENT_MIGRATED_BASELINE = "MIGRATED_BASELINE";
    public static final String MIGRATION_TERMINAL_WITH_OPEN_BLOCK = "TERMINAL_WITH_OPEN_BLOCK";
    public static final String MIGRATION_AMBIGUOUS_LEGACY_COMBINATION = "AMBIGUOUS_LEGACY_COMBINATION";
    public static final String MIGRATION_TERMINAL_UNRESOLVED = "MIGRATION_TERMINAL_UNRESOLVED";

    public static final String EVIDENCE_AUDIT_PENDING = "PENDING";
    public static final String EVIDENCE_AUDIT_APPROVED = "APPROVED";

    public static final String TASK_LEVEL_MONTH = "month";
    public static final String TASK_LEVEL_WEEK = "week";
    public static final String TASK_TYPE_KEY = "key";
    public static final String TASK_TYPE_DAILY = "daily";
    public static final String YES = "1";
    public static final String NO = "0";

    public static final String REVIEW_PENDING = "PENDING";
    public static final String REVIEW_APPROVED = "APPROVED";
    public static final String PERIOD_OPEN = "OPEN";
    public static final String PERIOD_CLOSED = "CLOSED";
    public static final String PERF_RESULT_NORMAL = "NORMAL";
    public static final String PERF_RESULT_RED_LINE = "RED_LINE";
    public static final String PERF_FORMULA_VERSION = "AILAB_PERF_V1";

    public static final String COLLAB_CROSS_DEPT = "CROSS_DEPT";
    public static final String COLLAB_KNOWLEDGE = "KNOWLEDGE";
    public static final String COLLAB_BACKUP = "BACKUP";
    public static final String COLLAB_OVERDUE = "OVERDUE";
    public static final String COLLAB_DEDUCTION = "DEDUCTION";

    private LabConstants() {
    }
}
