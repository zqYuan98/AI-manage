package com.ailab.system.report.exporter;

import com.ailab.system.report.model.ReportData;
import com.ailab.system.report.model.ReportSectionData;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Deterministic canonical UTF-8 JSON exporter with no arbitrary object serialization. */
@Component
public final class JsonReportExporter implements ReportExporter {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    @Override public String getId() { return "JSON"; }
    @Override public boolean supports(String value) { return "JSON".equals(value); }
    @Override public byte[] export(ReportData data) throws IOException { if (data == null) throw new IllegalArgumentException("report data is required"); StringBuilder out = new StringBuilder(); out.append('{'); property(out, "context", context(data)); out.append(','); property(out, "templateCode", data.getTemplateCode()); out.append(','); property(out, "templateRevision", Integer.valueOf(data.getTemplateRevision())); out.append(','); out.append("\"sections\":"); array(out, data.getSections()); out.append(','); out.append("\"metadata\":"); object(out, data.getMetadata()); out.append('}'); byte[] bytes = out.toString().getBytes(StandardCharsets.UTF_8); if (bytes.length > MAX_BYTES) throw new IOException("Report export exceeds size limit"); return bytes; }
    private Map<String, Object> context(ReportData data) { java.util.LinkedHashMap<String, Object> value = new java.util.LinkedHashMap<String, Object>(); value.put("period", data.getContext().getPeriod()); value.put("bizLine", data.getContext().getBizLine()); value.put("requesterId", data.getContext().getRequesterId()); value.put("generatedAt", data.getContext().getGeneratedAt().toString()); value.put("attributes", data.getContext().getAttributes()); return value; }
    private void section(StringBuilder out, ReportSectionData value) { out.append('{'); property(out, "sectionCode", value.getSectionCode()); out.append(','); property(out, "sectionType", value.getSectionType()); out.append(','); property(out, "title", value.getTitle()); out.append(','); out.append("\"rows\":"); array(out, value.getRows()); out.append(','); out.append("\"summary\":"); object(out, value.getSummary()); out.append('}'); }
    private void property(StringBuilder out, String name, Object value) { string(out, name); out.append(':'); value(out, value); }
    private void value(StringBuilder out, Object value) { if (value == null) out.append("null"); else if (value instanceof String || value instanceof Character) string(out, String.valueOf(value)); else if (value instanceof Boolean) out.append(value); else if (value instanceof Number) number(out, (Number) value); else if (value instanceof Map) object(out, (Map<?, ?>) value); else if (value instanceof Collection) array(out, (Collection<?>) value); else throw new IllegalArgumentException("Unsupported canonical JSON value"); }
    private void number(StringBuilder out, Number value) { if ((value instanceof Double && !Double.isFinite(value.doubleValue())) || (value instanceof Float && !Float.isFinite(value.floatValue()))) throw new IllegalArgumentException("JSON numbers must be finite"); if (value instanceof BigDecimal) out.append(canonical((BigDecimal) value)); else if (value instanceof BigInteger || value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) out.append(value.toString()); else out.append(canonical(BigDecimal.valueOf(value.doubleValue()))); }
    private String canonical(BigDecimal value) { BigDecimal normalized = value.stripTrailingZeros(); return normalized.signum() == 0 ? "0" : normalized.toPlainString(); }
    private void object(StringBuilder out, Map<?, ?> values) { java.util.List<String> keys = new java.util.ArrayList<String>(); for (Object key : values.keySet()) { if (!(key instanceof String)) throw new IllegalArgumentException("JSON map key must be a string"); keys.add((String) key); } java.util.Collections.sort(keys); out.append('{'); for (int i = 0; i < keys.size(); i++) { String key = keys.get(i); property(out, key, values.get(key)); if (i + 1 < keys.size()) out.append(','); } out.append('}'); }
    private void array(StringBuilder out, Collection<?> values) { out.append('['); Iterator<?> iterator = values.iterator(); while (iterator.hasNext()) { Object value = iterator.next(); if (value instanceof ReportSectionData) section(out, (ReportSectionData) value); else value(out, value); if (iterator.hasNext()) out.append(','); } out.append(']'); }
    private void string(StringBuilder out, String value) { out.append('"'); for (int i = 0; i < value.length(); i++) { char c = value.charAt(i); if (c == '"' || c == '\\') out.append('\\').append(c); else if (c == '\n') out.append("\\n"); else if (c == '\r') out.append("\\r"); else if (c == '\t') out.append("\\t"); else if (c < 0x20) out.append(String.format(java.util.Locale.ROOT, "\\u%04x", Integer.valueOf(c))); else out.append(c); } out.append('"'); }
}
