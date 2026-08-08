package com.ailab.system.report.renderer;

import com.ailab.system.report.config.ReportSectionConfig;
import com.ailab.system.report.model.ReportContext;
import com.ailab.system.report.model.ReportSectionData;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared validation and deterministic scalar formatting for built-in renderers. */
final class RendererSupport {
    static final String EMPTY = "暂无数据";
    private RendererSupport() { }
    static void require(String expected, ReportContext context, ReportSectionConfig config, ReportSectionData source) {
        if (context == null || config == null || source == null || !expected.equals(config.getSectionType()) || !expected.equals(source.getSectionType()) || !config.getSectionCode().equals(source.getSectionCode()))
            throw new IllegalArgumentException("Renderer input type does not match " + expected);
    }
    static ReportSectionData result(ReportSectionConfig config, List<Map<String, Object>> rows, Map<String, Object> summary) {
        return new ReportSectionData(config.getSectionCode(), config.getSectionType(), config.getTitle(), rows, summary);
    }
    static String text(Object value) {
        if (value == null) return "—";
        if (value instanceof Boolean) return ((Boolean) value).booleanValue() ? "是" : "否";
        if (value instanceof BigDecimal) return ((BigDecimal) value).stripTrailingZeros().toPlainString();
        if (value instanceof Number) return new BigDecimal(String.valueOf(value)).stripTrailingZeros().toPlainString();
        String result = String.valueOf(value).trim(); return result.isEmpty() ? "—" : result;
    }
    static Map<String, Object> map(Object... entries) { Map<String, Object> result = new LinkedHashMap<String, Object>(); for (int i = 0; i < entries.length; i += 2) result.put(String.valueOf(entries[i]), entries[i + 1]); return result; }
    static List<String> strings(Object value) { if (!(value instanceof List)) return Collections.emptyList(); List<String> result = new ArrayList<String>(); for (Object item : (List<?>) value) result.add(String.valueOf(item)); return result; }
    static void safeText(String value) { if (value != null && (value.contains("<") || value.contains(">"))) throw new IllegalArgumentException("Raw HTML is not permitted in renderer configuration"); }
}
