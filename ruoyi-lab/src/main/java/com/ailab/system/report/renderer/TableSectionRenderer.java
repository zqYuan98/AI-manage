package com.ailab.system.report.renderer;

import com.ailab.system.report.config.ReportSectionConfig;
import com.ailab.system.report.model.ReportContext;
import com.ailab.system.report.model.ReportSectionData;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class TableSectionRenderer implements SectionRenderer {
    @Override public String getId() { return "TABLE"; }
    @Override public boolean supports(String value) { return "TABLE".equals(value); }
    @Override public ReportSectionData render(ReportContext context, ReportSectionConfig config, ReportSectionData source) {
        RendererSupport.require(getId(), context, config, source);
        Object rawColumns = config.getRenderConfig().get("columns"); if (!(rawColumns instanceof List) || ((List<?>) rawColumns).isEmpty()) throw new IllegalArgumentException("TABLE columns are required");
        List<String> fields = new ArrayList<String>(); List<String> headers = new ArrayList<String>(); List<String> alignments = new ArrayList<String>(); List<String> widths = new ArrayList<String>(); Set<String> seen = new HashSet<String>();
        for (Object raw : (List<?>) rawColumns) {
            String field; String label; String align = "left"; String width = "";
            if (raw instanceof String) { field = (String) raw; label = field; }
            else if (raw instanceof Map) { Map<?, ?> column = (Map<?, ?>) raw; field = String.valueOf(column.get("field")); label = column.get("label") == null ? field : String.valueOf(column.get("label")); if (column.get("align") != null) align = String.valueOf(column.get("align")).toLowerCase(java.util.Locale.ROOT); if (column.get("width") != null) width = String.valueOf(column.get("width")); }
            else throw new IllegalArgumentException("Invalid TABLE column");
            if (!field.matches("[A-Za-z][A-Za-z0-9_]{0,63}") || !seen.add(field) || !("left".equals(align) || "center".equals(align) || "right".equals(align)) || (!width.isEmpty() && !width.matches("([1-9][0-9]{0,2}%|[1-9][0-9]{0,3}px)"))) throw new IllegalArgumentException("Invalid TABLE column");
            RendererSupport.safeText(label); fields.add(field); headers.add(label); alignments.add(align); widths.add(width);
        }
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> sourceRow : source.getRows()) { Map<String, Object> row = new java.util.LinkedHashMap<String, Object>(); for (String field : fields) row.put(field, RendererSupport.text(sourceRow.get(field))); rows.add(row); }
        return RendererSupport.result(config, rows, RendererSupport.map("fields", fields, "headers", headers, "alignments", alignments, "widths", widths, "empty", rows.isEmpty() ? RendererSupport.EMPTY : ""));
    }
}
