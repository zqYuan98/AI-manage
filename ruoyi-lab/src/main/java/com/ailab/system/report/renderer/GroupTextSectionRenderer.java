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
        RendererSupport.require(getId(), context, config, source); String field = value(config.getRenderConfig().get("groupBy")); if (field == null) field = value(config.getQueryConfig().get("groupBy")); if (field == null || !field.matches("[A-Za-z][A-Za-z0-9_]{0,63}")) throw new IllegalArgumentException("GROUP_TEXT groupBy is required");
        List<Map<String, Object>> groups = canonicalGroups(source.getSummary().get("groups"));
        if (groups.isEmpty()) {
            Map<String, List<Map<String, Object>>> buckets = new LinkedHashMap<String, List<Map<String, Object>>>(); for (Map<String, Object> row : source.getRows()) { String key = row.get(field) == null ? "（未分组）" : RendererSupport.text(row.get(field)); List<Map<String, Object>> group = buckets.get(key); if (group == null) { group = new ArrayList<Map<String, Object>>(); buckets.put(key, group); } group.add(row); }
            for (Map.Entry<String, List<Map<String, Object>>> entry : buckets.entrySet()) groups.add(RendererSupport.map("title", entry.getKey(), "rows", entry.getValue(), "summary", "共" + entry.getValue().size() + "项"));
        }
        return RendererSupport.result(config, java.util.Collections.<Map<String, Object>>emptyList(), RendererSupport.map("groupBy", field, "groups", groups, "empty", groups.isEmpty() ? RendererSupport.EMPTY : ""));
    }
    private String value(Object value) { return value == null ? null : String.valueOf(value); }
    @SuppressWarnings("unchecked") private List<Map<String, Object>> canonicalGroups(Object raw) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>(); if (!(raw instanceof List)) return result;
        for (Object value : (List<?>) raw) { if (!(value instanceof Map)) throw new IllegalArgumentException("Invalid summary groups"); Map<String, Object> group = (Map<String, Object>) value; Object title = group.get("title"); Object rows = group.get("rows"); result.add(RendererSupport.map("title", title == null ? "（未分组）" : RendererSupport.text(title), "rows", rows instanceof List ? rows : java.util.Collections.emptyList(), "summary", group.get("summary") == null ? "" : RendererSupport.text(group.get("summary")))); }
        return result;
    }
}
