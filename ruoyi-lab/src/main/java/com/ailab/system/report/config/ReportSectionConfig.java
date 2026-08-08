package com.ailab.system.report.config;

import com.ailab.system.domain.LabReportSection;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable renderer input. JSON is parsed once and recursively frozen at the persistence boundary. */
public final class ReportSectionConfig {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Long sectionId; private final String sectionCode; private final String sectionName; private final String title;
    private final String titleLevel; private final String sectionType; private final Integer sortNo; private final String dataSource;
    private final Map<String, Object> queryConfig; private final Map<String, Object> renderConfig; private final Map<String, Object> styleConfig;
    private final boolean manual; private final boolean visible; private final boolean sensitive; private final String sensitivePermission;

    public ReportSectionConfig(LabReportSection source) {
        if (source == null) throw new IllegalArgumentException("section is required");
        sectionId = source.getId(); sectionCode = source.getSectionCode(); sectionName = source.getSectionName(); title = source.getSectionName();
        sectionType = source.getSectionType(); sortNo = source.getSortNo(); dataSource = source.getDataSource();
        queryConfig = jsonObject(source.getQueryConfigJson()); renderConfig = jsonObject(source.getRenderConfigJson()); styleConfig = jsonObject(source.getStyleConfigJson());
        Object level = styleConfig.get("titleLevel"); titleLevel = level instanceof String ? (String) level : null;
        manual = "1".equals(source.getManualFlag()); visible = !"0".equals(source.getVisibleFlag()); sensitive = source.isSensitive(); sensitivePermission = source.getSensitivePermission();
    }

    public Long getSectionId() { return sectionId; } public String getSectionCode() { return sectionCode; } public String getSectionName() { return sectionName; }
    public String getTitle() { return title; } public String getTitleLevel() { return titleLevel; } public String getSectionType() { return sectionType; }
    public Integer getSortNo() { return sortNo; } public String getDataSource() { return dataSource; }
    public Map<String, Object> getQueryConfig() { return queryConfig; } public Map<String, Object> getRenderConfig() { return renderConfig; } public Map<String, Object> getStyleConfig() { return styleConfig; }
    public boolean isManual() { return manual; } public boolean isVisible() { return visible; } public boolean isSensitive() { return sensitive; }
    public String getSensitivePermission() { return sensitivePermission; }
    /** Raw forms remain available for APIs that persist the validated source verbatim. */
    public String getQueryConfigJson() { return json(queryConfig); }
    public String getRenderConfigJson() { return json(renderConfig); }

    private static Map<String, Object> jsonObject(String source) {
        try {
            if (source == null || source.trim().isEmpty()) return Collections.emptyMap();
            Map<String, Object> value = JSON.readValue(source, new TypeReference<LinkedHashMap<String, Object>>() { });
            return freezeMap(value);
        } catch (IOException ex) { throw new IllegalArgumentException("Invalid section JSON", ex); }
    }
    private static String json(Map<String, Object> source) { try { return JSON.writeValueAsString(source); } catch (IOException ex) { throw new IllegalStateException("Cannot serialize section JSON", ex); } }
    private static Map<String, Object> freezeMap(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> item : source.entrySet()) copy.put(item.getKey(), freeze(item.getValue()));
        return Collections.unmodifiableMap(copy);
    }
    @SuppressWarnings("unchecked") private static Object freeze(Object value) {
        if (value instanceof Map) return freezeMap((Map<String, Object>) value);
        if (value instanceof Collection) { List<Object> copy = new ArrayList<Object>(); for (Object item : (Collection<?>) value) copy.add(freeze(item)); return Collections.unmodifiableList(copy); }
        return value;
    }
}
