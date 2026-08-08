package com.ailab.system.report.exporter;

import com.ailab.system.report.model.ReportData;
import com.ailab.system.report.model.ReportSectionData;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class MarkdownReportExporter implements ReportExporter {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    @Override public String getId() { return "MARKDOWN"; }
    @Override public boolean supports(String value) { return "MARKDOWN".equals(value); }
    @Override public byte[] export(ReportData data) throws IOException { if (data == null) throw new IllegalArgumentException("report data is required"); StringBuilder out = new StringBuilder("# 人工智能实验室月报\n\n"); for (ReportSectionData section : data.getSections()) { out.append("## ").append(inline(section.getTitle())).append("\n\n"); render(out, section); } byte[] result = out.toString().replace("\r\n", "\n").replace('\r', '\n').getBytes(StandardCharsets.UTF_8); if (result.length > MAX_BYTES) throw new IOException("Report export exceeds size limit"); return result; }
    private void render(StringBuilder out, ReportSectionData section) { String type = section.getSectionType(); if ("TABLE".equals(type)) table(out, section); else if ("STAT".equals(type)) stat(out, section); else if ("TEXT".equals(type) || "MANUAL".equals(type)) text(out, section.getSummary().get("text")); else if ("GROUP_TEXT".equals(type)) groups(out, section.getSummary().get("groups")); else if ("CHART".equals(type)) { out.append("图表数据（可移植的 chart-neutral model）：").append(escape(String.valueOf(section.getSummary().get("categories")))).append("\n\n"); } else text(out, "暂无数据"); }
    private void table(StringBuilder out, ReportSectionData section) { List<String> headers = strings(section.getSummary().get("headers")); if (headers.isEmpty()) { out.append("暂无数据\n\n"); return; } List<String> aligns = strings(section.getSummary().get("alignments")); out.append('|'); for (String header : headers) out.append(' ').append(inline(header)).append(" |"); out.append("\n|"); for (int i = 0; i < headers.size(); i++) { String align = i < aligns.size() ? aligns.get(i) : "left"; out.append(" ").append("right".equals(align) ? "---:" : "center".equals(align) ? ":---:" : ":---").append(" |"); } out.append('\n'); for (Map<String, Object> row : section.getRows()) { out.append('|'); for (Object value : row.values()) out.append(' ').append(inline(String.valueOf(value))).append(" |"); out.append('\n'); } out.append('\n'); }
    private void stat(StringBuilder out, ReportSectionData section) { Object text = section.getSummary().get("text"); if (text != null) text(out, text); else if (section.getRows().isEmpty()) text(out, "暂无数据"); else table(out, section); }
    private void groups(StringBuilder out, Object raw) { if (!(raw instanceof List) || ((List<?>) raw).isEmpty()) { out.append("暂无数据\n\n"); return; } for (Object item : (List<?>) raw) { if (!(item instanceof Map)) continue; Map<?, ?> group = (Map<?, ?>) item; out.append("### ").append(inline(String.valueOf(group.get("title")))).append("\n\n"); text(out, group.get("summary")); } }
    private void text(StringBuilder out, Object value) { String text = value == null ? "暂无数据" : String.valueOf(value); String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\\n", -1); for (int i = 0; i < lines.length; i++) { if (i > 0) out.append("  \n"); out.append(escape(lines[i])); } out.append("\n\n"); }
    private List<String> strings(Object raw) { if (!(raw instanceof List)) return Collections.emptyList(); List<String> result = new ArrayList<String>(); for (Object item : (List<?>) raw) result.add(String.valueOf(item)); return result; }
    private String inline(String value) { return escape(value == null ? "" : value.replace("\r", " ").replace("\n", " ")); }
    private String escape(String value) { return value == null ? "" : value.replace("\\", "\\\\").replace("|", "\\|").replace("*", "\\*").replace("_", "\\_").replace("#", "\\#").replace("<", "\\<").replace(">", "\\>").replace("`", "\\`").replace("[", "\\[").replace("]", "\\]").replace("(", "\\(").replace(")", "\\)").replace("-", "\\-").replace("+", "\\+").replace("=", "\\=").replace("~", "\\~").replace("!", "\\!").replace(".", "\\."); }
}
