package com.ailab.system.report.config;

import com.ailab.system.report.provider.DataSourceProvider;
import com.ailab.system.report.provider.DataSourceProviderRegistry;
import com.ailab.system.report.provider.ReportFieldSpec;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Public stable identifiers for the built-in report configuration contract. */
public final class ReportConfigCatalog {
    public static final String DEFAULT_SENSITIVE_PERMISSION = "lab:report:sensitive";

    public static final String TABLE = "TABLE";
    public static final String STAT = "STAT";
    public static final String TEXT = "TEXT";
    public static final String MANUAL = "MANUAL";
    public static final String GROUP_TEXT = "GROUP_TEXT";
    public static final String CHART = "CHART";

    public static final String GOAL_PROGRESS = "GOAL_PROGRESS";
    public static final String TASK_DETAIL = "TASK_DETAIL";
    public static final String TASK_STAT = "TASK_STAT";
    public static final String TASK_UNDONE = "TASK_UNDONE";
    public static final String TASK_NEXT = "TASK_NEXT";
    public static final String TASK_COORD = "TASK_COORD";
    public static final String TASK_BLOCK = "TASK_BLOCK";
    public static final String ASSET_SUMMARY = "ASSET_SUMMARY";
    public static final String IPR_SUMMARY = "IPR_SUMMARY";
    public static final String PERF_SUMMARY = "PERF_SUMMARY";
    public static final String MANUAL_SUMMARY = "MANUAL_SUMMARY";

    private static final Set<String> SECTION_TYPES = set(TABLE, STAT, TEXT, MANUAL, GROUP_TEXT, CHART);
    private static final Set<String> PROVIDER_IDS = set(GOAL_PROGRESS, TASK_DETAIL, TASK_STAT, TASK_UNDONE,
            TASK_NEXT, TASK_COORD, TASK_BLOCK, ASSET_SUMMARY, IPR_SUMMARY, PERF_SUMMARY, MANUAL_SUMMARY);
    private static final Set<String> FILTER_OPERATORS = set("EQ", "NE", "IN", "GTE", "LTE", "BETWEEN");
    private static final Set<String> REPORT_TYPES = set("WEEK", "MONTH", "QUARTER", "YEAR");
    private static final Set<String> TEMPLATE_STATUSES = set("ENABLED", "DISABLED");
    private static final Set<String> QUERY_FIELDS = set("id", "period", "taskPeriod", "bizLine", "owner", "ownerId", "ownerName", "memberId",
            "status", "resultStatus", "taskLevel", "planDate", "title", "deliverable", "failReason", "nextAction",
            "coordination", "coordinationSupport", "block", "blockType", "blockReason", "blockStartTime", "goalId", "goalTitle", "year", "expectedProgress", "progressRate",
            "assetNo", "assetName", "assetStage", "assetType", "backupOwnerId", "criticalFlag", "backupOwnerStatus", "singlePointRisk",
            "iprNo", "iprName", "iprStage", "iprType", "plannedSubmitDate", "actualSubmitDate", "score", "revisionNo", "redLineFlag", "confirmationStatus", "total", "result", "sectionCode");
    private static final Map<String, Set<String>> COMPATIBLE_PROVIDERS = compatibility();

    private ReportConfigCatalog() { }

    public static Set<String> sectionTypes() { return SECTION_TYPES; }
    public static Set<String> providerIds() { return PROVIDER_IDS; }
    public static Set<String> filterOperators() { return FILTER_OPERATORS; }
    public static Set<String> queryFields() { return QUERY_FIELDS; }
    public static Set<String> reportTypes() { return REPORT_TYPES; }
    public static Set<String> templateStatuses() { return TEMPLATE_STATUSES; }

    public static Set<String> compatibleProviders(String sectionType) {
        Set<String> providers = COMPATIBLE_PROVIDERS.get(sectionType);
        return providers == null ? Collections.<String>emptySet() : providers;
    }

    public static boolean supportsPeriod(String providerId,String periodType){
        if(providerId==null||periodType==null)return false;
        if(PERF_SUMMARY.equals(providerId))return "MONTH".equals(periodType)||"QUARTER".equals(periodType);
        if(GOAL_PROGRESS.equals(providerId))return "MONTH".equals(periodType)||"QUARTER".equals(periodType)||"YEAR".equals(periodType);
        if(TASK_NEXT.equals(providerId))return "MONTH".equals(periodType);
        return PROVIDER_IDS.contains(providerId)&&REPORT_TYPES.contains(periodType);
    }

    /** Immutable, server-owned choices consumed by the visual template designer. */
    public static Map<String, Object> designerMetadata() {
        Map<String, Set<String>> compatible = new LinkedHashMap<String, Set<String>>();
        for (String type : SECTION_TYPES) compatible.put(type, compatibleProviders(type));
        Map<String, Set<String>> periods = new LinkedHashMap<String, Set<String>>();
        for (String provider : PROVIDER_IDS) {
            Set<String> supported = new LinkedHashSet<String>();
            for (String period : REPORT_TYPES) if (supportsPeriod(provider, period)) supported.add(period);
            periods.put(provider, Collections.unmodifiableSet(supported));
        }
        List<String> variables = Collections.unmodifiableList(new ArrayList<String>(Arrays.asList(
                "context.period", "context.bizLine", "context.generatedAt", "rows", "summary",
                "metadata.sectionCode", "metadata.title")));
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("sectionTypes", SECTION_TYPES); result.put("providers", PROVIDER_IDS);
        result.put("operators", FILTER_OPERATORS); result.put("queryFields", QUERY_FIELDS);
        result.put("reportTypes", REPORT_TYPES); result.put("templateStatuses", TEMPLATE_STATUSES);
        result.put("compatibleProviders", Collections.unmodifiableMap(compatible));
        result.put("providerPeriods", Collections.unmodifiableMap(periods));
        result.put("freemarkerVariables", variables);
        return Collections.unmodifiableMap(result);
    }

    /** Adds provider-local field types/operators without duplicating their runtime schema. */
    public static Map<String, Object> designerMetadata(DataSourceProviderRegistry registry) {
        if (registry == null) throw new IllegalArgumentException("Report provider registry is required");
        Map<String, Object> result = new LinkedHashMap<String, Object>(designerMetadata());
        Map<String, List<Map<String, Object>>> providers = new LinkedHashMap<String, List<Map<String, Object>>>();
        Map<String, Set<String>> metrics = new LinkedHashMap<String, Set<String>>();
        for (Map.Entry<String, DataSourceProvider> entry : registry.asMap().entrySet()) {
            List<Map<String, Object>> fields = new ArrayList<Map<String, Object>>();
            for (ReportFieldSpec spec : entry.getValue().getFieldSpecs()) {
                Map<String, Object> field = new LinkedHashMap<String, Object>();
                field.put("name", spec.getName()); field.put("type", spec.getType().name());
                field.put("operators", spec.getOperators());
                fields.add(Collections.unmodifiableMap(field));
            }
            providers.put(entry.getKey(), Collections.unmodifiableList(fields));
            metrics.put(entry.getKey(), entry.getValue().getSupportedMetrics());
        }
        result.put("providerFields", Collections.unmodifiableMap(providers));
        result.put("providerMetrics", Collections.unmodifiableMap(metrics));
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, Set<String>> compatibility() {
        Map<String, Set<String>> values = new LinkedHashMap<String, Set<String>>();
        values.put(TABLE, set(TASK_DETAIL, TASK_UNDONE, TASK_NEXT, TASK_COORD, TASK_BLOCK,
                ASSET_SUMMARY, IPR_SUMMARY, MANUAL_SUMMARY));
        values.put(STAT, set(GOAL_PROGRESS, TASK_STAT, ASSET_SUMMARY, IPR_SUMMARY, PERF_SUMMARY));
        values.put(TEXT, set(GOAL_PROGRESS, TASK_NEXT, TASK_BLOCK, MANUAL_SUMMARY));
        values.put(MANUAL, Collections.<String>emptySet());
        values.put(GROUP_TEXT, set(TASK_DETAIL, TASK_COORD, GOAL_PROGRESS, MANUAL_SUMMARY));
        values.put(CHART, set(GOAL_PROGRESS, TASK_STAT, PERF_SUMMARY));
        return Collections.unmodifiableMap(values);
    }

    private static Set<String> set(String... values) {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(values)));
    }
}
