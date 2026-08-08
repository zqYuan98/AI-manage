package com.ailab.system.report.exporter;

import com.ailab.system.report.model.ReportData;
import com.ailab.system.report.model.ReportSectionData;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

@Component
public final class MarkdownReportExporter implements ReportExporter {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int PNG_WIDTH = 640, PNG_HEIGHT = 320, MAX_PNG_BYTES = 512 * 1024;
    @Override public String getId() { return "MARKDOWN"; }
    @Override public boolean supports(String value) { return "MARKDOWN".equals(value); }

    @Override public byte[] export(ReportData data) throws IOException {
        if (data == null) throw new IllegalArgumentException("report data is required");
        BoundedMarkdown out = new BoundedMarkdown(MAX_BYTES); out.append("# 人工智能实验室月报\n\n");
        for (ReportSectionData section : data.getSections()) { out.append("## "); appendEscaped(out, section.getTitle(), true); out.append("\n\n"); render(out, section); }
        return out.bytes();
    }

    private void render(BoundedMarkdown out, ReportSectionData section) throws IOException {
        String type = section.getSectionType();
        if ("TABLE".equals(type)) table(out, section);
        else if ("STAT".equals(type)) stat(out, section);
        else if ("TEXT".equals(type) || "MANUAL".equals(type)) text(out, section.getSummary().get("text"));
        else if ("GROUP_TEXT".equals(type)) groups(out, section.getSummary().get("groups"));
        else if ("CHART".equals(type)) chart(out, section.getSummary());
        else text(out, "暂无数据");
    }

    private void table(BoundedMarkdown out, ReportSectionData section) throws IOException {
        if (section.getRows().isEmpty()) { out.append("暂无数据\n\n"); return; }
        List<String> headers = strings(section.getSummary().get("headers")); if (headers.isEmpty()) { out.append("暂无数据\n\n"); return; }
        List<String> aligns = strings(section.getSummary().get("alignments")); out.append('|');
        for (String header : headers) { out.append(' '); appendEscaped(out, header, true); out.append(" |"); }
        out.append("\n|"); for (int i = 0; i < headers.size(); i++) { String align = i < aligns.size() ? aligns.get(i) : "left"; out.append(' ').append("right".equals(align) ? "---:" : "center".equals(align) ? ":---:" : ":---").append(" |"); } out.append('\n');
        for (Map<String, Object> row : section.getRows()) { out.append('|'); for (Object value : row.values()) { out.append(' '); appendEscaped(out, String.valueOf(value), true); out.append(" |"); } out.append('\n'); } out.append('\n');
    }

    private void chart(BoundedMarkdown out, Map<String, Object> summary) throws IOException {
        List<String> categories = strings(summary.get("categories")); List<Map<?, ?>> series = series(summary.get("series")); List<?> values = summary.get("values") instanceof List ? (List<?>) summary.get("values") : Collections.emptyList();
        if (categories.isEmpty()) { out.append("暂无数据\n\n"); return; }
        out.append("| 分类 |"); if (series.isEmpty()) out.append(" 数值 |"); else for (int j = 0; j < series.size(); j++) { out.append(' '); appendEscaped(out, seriesName(series.get(j), j), true); out.append(" |"); }
        out.append("\n| :--- |"); if (series.isEmpty()) out.append(" ---: |"); else for (int j = 0; j < series.size(); j++) out.append(" ---: |"); out.append('\n');
        for (int i = 0; i < categories.size(); i++) { out.append("| "); appendEscaped(out, categories.get(i), true); out.append(" |"); if (series.isEmpty()) { out.append(' '); appendEscaped(out, String.valueOf(value(values, i)), true); out.append(" |\n"); } else { for (Map<?, ?> item : series) { Object raw = item.get("values") instanceof List ? value((List<?>) item.get("values"), i) : "—"; out.append(' '); appendEscaped(out, String.valueOf(raw), true); out.append(" |"); } out.append('\n'); } }
        String png = validPng(summary.get("pngBase64")); if (png != null) out.append("\n![图表](data:image/png;base64,").append(png).append(")\n"); out.append('\n');
    }

    private void stat(BoundedMarkdown out, ReportSectionData section) throws IOException { Object value = section.getSummary().get("text"); if (value != null) text(out, value); else if (section.getRows().isEmpty()) text(out, "暂无数据"); else table(out, section); }
    private void groups(BoundedMarkdown out, Object raw) throws IOException { if (!(raw instanceof List) || ((List<?>) raw).isEmpty()) { out.append("暂无数据\n\n"); return; } for (Object item : (List<?>) raw) { if (!(item instanceof Map)) continue; Map<?, ?> group = (Map<?, ?>) item; out.append("### "); appendEscaped(out, String.valueOf(group.get("title")), true); out.append("\n\n"); text(out, group.get("summary")); } }
    private void text(BoundedMarkdown out, Object value) throws IOException { appendEscaped(out, value == null ? "暂无数据" : String.valueOf(value), false); out.append("\n\n"); }

    private void appendEscaped(BoundedMarkdown out, String value, boolean inline) throws IOException {
        if (value == null) return; boolean lineStart = true;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '\r' || current == '\n') { if (current == '\r' && i + 1 < value.length() && value.charAt(i + 1) == '\n') i++; out.append(inline ? " " : "  \n"); lineStart = true; continue; }
            if (lineStart && current == ' ') { out.append('\u00a0'); continue; }
            if (lineStart && current == '\t') { out.append("\u00a0\u00a0\u00a0\u00a0"); continue; }
            lineStart = false; if ("\\|*_#<>`[]()-+=~!.".indexOf(current) >= 0) out.append('\\');
            if (Character.isHighSurrogate(current) && i + 1 < value.length() && Character.isLowSurrogate(value.charAt(i + 1))) out.append(value.substring(i, ++i + 1)); else out.append(current);
        }
    }

    private List<String> strings(Object raw) { if (!(raw instanceof List)) return Collections.emptyList(); List<String> result = new ArrayList<String>(); for (Object item : (List<?>) raw) result.add(String.valueOf(item)); return result; }
    private List<Map<?, ?>> series(Object raw) { if (!(raw instanceof List)) return Collections.emptyList(); List<Map<?, ?>> result = new ArrayList<Map<?, ?>>(); for (Object item : (List<?>) raw) if (item instanceof Map) result.add((Map<?, ?>) item); return result; }
    private String seriesName(Map<?, ?> item, int index) { Object value = item.get("name"); return value == null ? (index == 0 ? "数值" : "数值" + (index + 1)) : String.valueOf(value); }
    private Object value(List<?> values, int index) { return index < values.size() && values.get(index) != null ? values.get(index) : "—"; }

    private String validPng(Object raw) {
        if (!(raw instanceof String) || ((String) raw).length() > 700000) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode((String) raw); if (bytes.length > MAX_PNG_BYTES || !validPngStructure(bytes)) return null;
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes)); if (image == null || image.getWidth() != PNG_WIDTH || image.getHeight() != PNG_HEIGHT) return null;
            return (String) raw;
        } catch (IOException | IllegalArgumentException ignored) { return null; }
    }

    private boolean validPngStructure(byte[] bytes) {
        byte[] signature = new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10}; if (bytes.length < 33) return false; for (int i = 0; i < signature.length; i++) if (bytes[i] != signature[i]) return false;
        int offset = 8, chunks = 0; boolean header = false, end = false;
        while (offset + 12 <= bytes.length && ++chunks <= 1000) {
            int length = integer(bytes, offset); if (length < 0 || length > bytes.length - offset - 12) return false; int type = integer(bytes, offset + 4); int data = offset + 8; CRC32 crc = new CRC32(); crc.update(bytes, offset + 4, length + 4); if (crc.getValue() != unsignedInteger(bytes, data + length)) return false;
            if (!header) { if (type != 0x49484452 || length != 13) return false; int width = integer(bytes, data), height = integer(bytes, data + 4); if (width != PNG_WIDTH || height != PNG_HEIGHT || (long) width * height > (long) PNG_WIDTH * PNG_HEIGHT || bytes[data + 8] != 8 || bytes[data + 9] != 2 || bytes[data + 10] != 0 || bytes[data + 11] != 0 || bytes[data + 12] != 0) return false; header = true; }
            offset += length + 12; if (type == 0x49454E44) { if (length != 0 || offset != bytes.length) return false; end = true; break; }
        }
        return header && end;
    }
    private int integer(byte[] bytes, int offset) { return (bytes[offset] & 255) << 24 | (bytes[offset + 1] & 255) << 16 | (bytes[offset + 2] & 255) << 8 | bytes[offset + 3] & 255; }
    private long unsignedInteger(byte[] bytes, int offset) { return integer(bytes, offset) & 0xffffffffL; }

    private static final class BoundedMarkdown {
        private final StringBuilder value = new StringBuilder(); private final int maximum; private int bytes;
        BoundedMarkdown(int maximum) { this.maximum = maximum; }
        BoundedMarkdown append(char part) throws IOException { return append(String.valueOf(part)); }
        BoundedMarkdown append(String part) throws IOException { String safe = part == null ? "null" : part; int required = utf8Length(safe); if (required > maximum - bytes) throw new IOException("Markdown export exceeds UTF-8 byte limit"); value.append(safe); bytes += required; return this; }
        byte[] bytes() { return value.toString().getBytes(StandardCharsets.UTF_8); }
        private int utf8Length(String text) { int result = 0; for (int i = 0; i < text.length(); i++) { char current = text.charAt(i); if (current <= 0x7f) result++; else if (current <= 0x7ff) result += 2; else if (Character.isHighSurrogate(current) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) { result += 4; i++; } else if (Character.isSurrogate(current)) result++; else result += 3; } return result; }
    }
}
