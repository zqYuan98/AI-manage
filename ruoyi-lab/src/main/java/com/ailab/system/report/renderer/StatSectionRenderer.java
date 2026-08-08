package com.ailab.system.report.renderer;

import com.ailab.system.report.config.ReportSectionConfig;
import com.ailab.system.report.model.ReportContext;
import com.ailab.system.report.model.ReportSectionData;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class StatSectionRenderer implements SectionRenderer {
    @Override public String getId() { return "STAT"; }
    @Override public boolean supports(String value) { return "STAT".equals(value); }
    @Override public ReportSectionData render(ReportContext context, ReportSectionConfig config, ReportSectionData source) {
        RendererSupport.require(getId(), context, config, source); List<String> metrics = RendererSupport.strings(config.getRenderConfig().get("metrics")); if (metrics.isEmpty()) metrics = new ArrayList<String>(source.getSummary().keySet());
        if (source.getRows().isEmpty()) return RendererSupport.result(config, java.util.Collections.<Map<String, Object>>emptyList(), RendererSupport.map("metrics", java.util.Collections.emptyList(), "text", "暂无统计数据", "empty", Boolean.TRUE, "rounding", "HALF_UP, scale=2"));
        List<Map<String, Object>> values = new ArrayList<Map<String, Object>>(); StringBuilder sentence = new StringBuilder();
        for (String name : metrics) { Object raw = source.getSummary().get(name); if (raw == null && !source.getRows().isEmpty()) raw = source.getRows().get(0).get(name); if (raw == null && "average".equals(name)) raw = average(source.getRows()); if (raw == null && "top".equals(name)) raw = top(source.getRows()); String value = number(raw); values.add(RendererSupport.map("name", name, "value", value)); if (sentence.length() > 0) sentence.append("；"); sentence.append(name).append(value); }
        if (values.isEmpty()) sentence.append(RendererSupport.EMPTY); else sentence.append("。");
        return RendererSupport.result(config, values, RendererSupport.map("metrics", values, "text", sentence.toString(), "empty", Boolean.FALSE, "rounding", "HALF_UP, scale=2"));
    }
    private String number(Object value) { if (value instanceof Number) return new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(); return RendererSupport.text(value); }
    private BigDecimal average(List<Map<String, Object>> rows) { if (rows.isEmpty()) return BigDecimal.ZERO; BigDecimal total = BigDecimal.ZERO; for (Map<String, Object> row : rows) total = total.add(rowNumber(row)); return total.divide(BigDecimal.valueOf(rows.size()), 2, RoundingMode.HALF_UP); }
    private BigDecimal top(List<Map<String, Object>> rows) { BigDecimal top = BigDecimal.ZERO; for (Map<String, Object> row : rows) top = top.max(rowNumber(row)); return top; }
    private BigDecimal rowNumber(Map<String, Object> row) { Object value = row.get("total"); if (!(value instanceof Number)) value = row.get("value"); return value instanceof Number ? new BigDecimal(String.valueOf(value)) : BigDecimal.ZERO; }
}
