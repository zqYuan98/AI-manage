package com.ailab.system.report.provider;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Stable IDs shared by validators, registries, configuration UX and persisted templates. */
public final class ReportConfigValidatorIds {
    public static final Set<String> PROVIDER_IDS = Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
            "GOAL_PROGRESS", "TASK_DETAIL", "TASK_STAT", "TASK_UNDONE", "TASK_NEXT", "TASK_COORD", "TASK_BLOCK",
            "ASSET_SUMMARY", "IPR_SUMMARY", "PERF_SUMMARY", "MANUAL_SUMMARY")));
    private ReportConfigValidatorIds() { }
}
