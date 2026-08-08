package com.ailab.system.report.renderer;

import com.ailab.system.report.config.ReportSectionConfig;
import com.ailab.system.report.model.ReportContext;
import com.ailab.system.report.model.ReportSectionData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class GroupTextSectionRenderer implements SectionRenderer {
    @Override public String getId() { return "GROUP_TEXT"; }
    @Override public boolean supports(String value) { return "GROUP_TEXT".equals(value); }
    @Override public ReportSectionData render(ReportContext context, ReportSectionConfig config, ReportSectionData source) {
        RendererSupport.require(getId(), context, config, source); String field = configuredField(config);
        List<Map<String, Object>> groups = fromTaskNineGroups(source.getSummary().get("groups"), field, source.getRows());
        if (groups.isEmpty()) groups = groupRows(field, source.getRows());
        return RendererSupport.result(config, java.util.Collections.<Map<String, Object>>emptyList(), RendererSupport.map("groupBy", field, "groups", groups, "empty", groups.isEmpty() ? RendererSupport.EMPTY : ""));
    }
    private String configuredField(ReportSectionConfig config) { Object value = config.getRenderConfig().get("groupBy"); if (value == null) value = config.getQueryConfig().get("groupBy"); String field = value == null ? null : String.valueOf(value); if (field == null || !field.matches("[A-Za-z][A-Za-z0-9_]{0,63}")) throw new IllegalArgumentException("GROUP_TEXT groupBy is required"); return field; }
    private List<Map<String, Object>> fromTaskNineGroups(Object raw, String configuredField, List<Map<String, Object>> rows) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>(); if (!(raw instanceof List)) return result;
        for (Object item : (List<?>) raw) { if (!(item instanceof Map)) throw new IllegalArgumentException("Invalid summary groups"); Map<?, ?> group = (Map<?, ?>) item; Object groupField = group.get("field"); if (groupField != null && !configuredField.equals(String.valueOf(groupField))) throw new IllegalArgumentException("Summary group field does not match configuration"); Object key = group.get("key"); String canonicalKey = String.valueOf(key); List<Map<String, Object>> matching = new ArrayList<Map<String, Object>>(); for (Map<String, Object> row : rows) if (canonicalKey.equals(String.valueOf(row.get(configuredField)))) matching.add(row); int count = group.get("count") instanceof Number ? ((Number) group.get("count")).intValue() : matching.size(); result.add(RendererSupport.map("title", key == null || "null".equals(canonicalKey) ? "（未分组）" : RendererSupport.text(key), "rows", matching, "count", Integer.valueOf(count), "summary", "共" + count + "项")); }
        return result;
    }
    private List<Map<String, Object>> groupRows(String field, List<Map<String, Object>> rows) { Map<String, List<Map<String, Object>>> buckets = new LinkedHashMap<String, List<Map<String, Object>>>(); for (Map<String, Object> row : rows) { String key = row.get(field) == null ? "\u0000" : RendererSupport.text(row.get(field)); List<Map<String, Object>> bucket = buckets.get(key); if (bucket == null) { bucket = new ArrayList<Map<String, Object>>(); buckets.put(key, bucket); } bucket.add(row); } List<Map<String, Object>> result = new ArrayList<Map<String, Object>>(); for (Map.Entry<String, List<Map<String, Object>>> item : buckets.entrySet()) { String title = "\u0000".equals(item.getKey()) ? "（未分组）" : item.getKey(); result.add(RendererSupport.map("title", title, "rows", item.getValue(), "count", Integer.valueOf(item.getValue().size()), "summary", "共" + item.getValue().size() + "项")); } return result; }
}
