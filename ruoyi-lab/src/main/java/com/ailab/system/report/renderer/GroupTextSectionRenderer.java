package com.ailab.system.report.renderer;

import com.ailab.system.report.config.ReportSectionConfig;
import com.ailab.system.report.model.ReportContext;
import com.ailab.system.report.model.ReportSectionData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class GroupTextSectionRenderer implements SectionRenderer {
    private static final int MAX_GROUPS = 5000;
    private static final int MAX_REFERENCES = 100000;
    @Override public String getId() { return "GROUP_TEXT"; }
    @Override public boolean supports(String value) { return "GROUP_TEXT".equals(value); }
    @Override public ReportSectionData render(ReportContext context, ReportSectionConfig config, ReportSectionData source) {
        RendererSupport.require(getId(), context, config, source); String field = configuredField(config); Map<String, List<Map<String, Object>>> buckets = buckets(field, source.getRows());
        List<Map<String, Object>> groups = fromTaskNineGroups(source.getSummary().get("groups"), field, buckets);
        if (groups.isEmpty()) groups = groupRows(buckets);
        return RendererSupport.result(config, java.util.Collections.<Map<String, Object>>emptyList(), RendererSupport.map("groupBy", field, "groups", groups, "empty", groups.isEmpty() ? RendererSupport.EMPTY : ""));
    }
    private String configuredField(ReportSectionConfig config) { Object value = config.getRenderConfig().get("groupBy"); if (value == null) value = config.getQueryConfig().get("groupBy"); String field = value == null ? null : String.valueOf(value); if (field == null || !field.matches("[A-Za-z][A-Za-z0-9_]{0,63}")) throw new IllegalArgumentException("GROUP_TEXT groupBy is required"); return field; }
    private List<Map<String, Object>> fromTaskNineGroups(Object raw, String configuredField, Map<String, List<Map<String, Object>>> buckets) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>(); if (!(raw instanceof List) || ((List<?>) raw).isEmpty()) return result; if (((List<?>) raw).size() > MAX_GROUPS) throw new IllegalArgumentException("Too many summary groups");
        Set<String> seen = new HashSet<String>(); int references = 0;
        for (Object item : (List<?>) raw) { if (!(item instanceof Map)) throw new IllegalArgumentException("Invalid summary groups"); Map<?, ?> group = (Map<?, ?>) item; Object groupField = group.get("field"); if (groupField == null || !configuredField.equals(String.valueOf(groupField))) throw new IllegalArgumentException("Summary group field does not match configuration"); Object key = group.get("key"); String canonicalKey = String.valueOf(key); if (!seen.add(canonicalKey)) throw new IllegalArgumentException("Duplicate summary group key"); List<Map<String, Object>> matching = buckets.get(canonicalKey); if (matching == null) matching = java.util.Collections.emptyList(); if (exactCount(group.get("count")) != matching.size()) throw new IllegalArgumentException("Summary group count does not match rows"); references += matching.size(); if (references > MAX_REFERENCES) throw new IllegalArgumentException("Too many grouped row references"); int count = matching.size(); result.add(RendererSupport.map("title", key == null || "null".equals(canonicalKey) ? "（未分组）" : RendererSupport.text(key), "rows", matching, "count", Integer.valueOf(count), "summary", "共" + count + "项")); }
        return result;
    }
    private Map<String, List<Map<String, Object>>> buckets(String field, List<Map<String, Object>> rows) { Map<String, List<Map<String, Object>>> result = new LinkedHashMap<String, List<Map<String, Object>>>(); for (Map<String, Object> row : rows) { String key = String.valueOf(row.get(field)); List<Map<String, Object>> bucket = result.get(key); if (bucket == null) { if (result.size() >= MAX_GROUPS) throw new IllegalArgumentException("Too many row groups"); bucket = new ArrayList<Map<String, Object>>(); result.put(key, bucket); } bucket.add(row); } if (rows.size() > MAX_REFERENCES) throw new IllegalArgumentException("Too many grouped row references"); return result; }
    private int exactCount(Object value) { if (!(value instanceof Number)) throw new IllegalArgumentException("Summary group count is required"); try { return new java.math.BigDecimal(value.toString()).intValueExact(); } catch (ArithmeticException | NumberFormatException ex) { throw new IllegalArgumentException("Summary group count must be an integer", ex); } }
    private List<Map<String, Object>> groupRows(Map<String, List<Map<String, Object>>> buckets) { List<Map<String, Object>> result = new ArrayList<Map<String, Object>>(); for (Map.Entry<String, List<Map<String, Object>>> item : buckets.entrySet()) { String title = "null".equals(item.getKey()) ? "（未分组）" : item.getKey(); result.add(RendererSupport.map("title", title, "rows", item.getValue(), "count", Integer.valueOf(item.getValue().size()), "summary", "共" + item.getValue().size() + "项")); } return result; }
}
