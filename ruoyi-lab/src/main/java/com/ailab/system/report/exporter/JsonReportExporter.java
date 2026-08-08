package com.ailab.system.report.exporter;

import com.ailab.system.report.model.ReportData;
import com.ailab.system.report.model.ReportSectionData;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Canonical JSON is streamed through hard byte, depth and node budgets. */
@Component
public final class JsonReportExporter implements ReportExporter {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_DEPTH = 64;
    private static final int MAX_NODES = 100000;
    private static final JsonFactory JSON = new JsonFactory();
    @Override public String getId() { return "JSON"; }
    @Override public boolean supports(String value) { return "JSON".equals(value); }

    @Override public byte[] export(ReportData data) throws IOException {
        if (data == null) throw new IllegalArgumentException("report data is required");
        BoundedOutputStream bounded = new BoundedOutputStream(MAX_BYTES); JsonGenerator generator = JSON.createGenerator(bounded, JsonEncoding.UTF8); generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET); State state = new State();
        boolean complete = false; try { writeReport(generator, data, state); generator.close(); complete = true; return bounded.bytes(); }
        finally { if (!complete) try { generator.close(); } catch (IOException ignored) { } }
    }

    private void writeReport(JsonGenerator out, ReportData data, State state) throws IOException {
        state.node(0); out.writeStartObject(); out.writeFieldName("context"); writeContext(out, data, state, 1); out.writeStringField("templateCode", data.getTemplateCode()); state.node(1); out.writeNumberField("templateRevision", data.getTemplateRevision()); state.node(1); out.writeFieldName("sections"); writeSections(out, data.getSections(), state, 1); out.writeFieldName("metadata"); writeValue(out, data.getMetadata(), state, 1); out.writeEndObject();
    }
    private void writeContext(JsonGenerator out, ReportData data, State state, int depth) throws IOException {
        state.node(depth); out.writeStartObject(); out.writeStringField("period", data.getContext().getPeriod()); state.node(depth); if (data.getContext().getBizLine() == null) out.writeNullField("bizLine"); else out.writeStringField("bizLine", data.getContext().getBizLine()); state.node(depth); out.writeNumberField("requesterId", data.getContext().getRequesterId()); state.node(depth); out.writeStringField("generatedAt", data.getContext().getGeneratedAt().toString()); state.node(depth); out.writeFieldName("attributes"); writeValue(out, data.getContext().getAttributes(), state, depth + 1); out.writeEndObject();
    }
    private void writeSections(JsonGenerator out, List<ReportSectionData> sections, State state, int depth) throws IOException { state.enter(sections, depth); try { out.writeStartArray(); for (ReportSectionData section : sections) writeSection(out, section, state, depth + 1); out.writeEndArray(); } finally { state.leave(sections); } }
    private void writeSection(JsonGenerator out, ReportSectionData value, State state, int depth) throws IOException { state.node(depth); out.writeStartObject(); out.writeStringField("sectionCode", value.getSectionCode()); state.node(depth); out.writeStringField("sectionType", value.getSectionType()); state.node(depth); out.writeStringField("title", value.getTitle()); state.node(depth); out.writeFieldName("rows"); writeValue(out, value.getRows(), state, depth + 1); out.writeFieldName("summary"); writeValue(out, value.getSummary(), state, depth + 1); out.writeEndObject(); }

    private void writeValue(JsonGenerator out, Object value, State state, int depth) throws IOException {
        state.node(depth);
        if (value == null) out.writeNull();
        else if (value instanceof String || value instanceof Character) out.writeString(String.valueOf(value));
        else if (value instanceof Boolean) out.writeBoolean(((Boolean) value).booleanValue());
        else if (value instanceof Number) number(out, (Number) value);
        else if (value instanceof Map) object(out, (Map<?, ?>) value, state, depth);
        else if (value instanceof Collection) array(out, (Collection<?>) value, state, depth);
        else throw new IllegalArgumentException("Unsupported canonical JSON value");
    }
    private void number(JsonGenerator out, Number value) throws IOException { if ((value instanceof Double && !Double.isFinite(value.doubleValue())) || (value instanceof Float && !Float.isFinite(value.floatValue()))) throw new IllegalArgumentException("JSON numbers must be finite"); if (value instanceof BigDecimal) out.writeNumber(canonical((BigDecimal) value)); else if (value instanceof BigInteger) out.writeNumber((BigInteger) value); else if (value instanceof Byte || value instanceof Short || value instanceof Integer) out.writeNumber(value.intValue()); else if (value instanceof Long) out.writeNumber(value.longValue()); else out.writeNumber(canonical(new BigDecimal(value.toString()))); }
    private String canonical(BigDecimal value) throws IOException { BigDecimal normalized = value.stripTrailingZeros(); if (normalized.signum() == 0) return "0"; long precision = normalized.precision(), scale = normalized.scale(), sign = normalized.signum() < 0 ? 1L : 0L; long plainLength = scale <= 0 ? sign + precision - scale : sign + (precision > scale ? precision + 1L : scale + 2L); if (plainLength > MAX_BYTES) throw new IOException("JSON numeric expansion limit exceeded"); return normalized.toPlainString(); }
    private void object(JsonGenerator out, Map<?, ?> values, State state, int depth) throws IOException { state.enter(values, depth); try { state.children(values.size()); List<String> keys = new ArrayList<String>(values.size()); for (Object key : values.keySet()) { if (!(key instanceof String)) throw new IllegalArgumentException("JSON map key must be a string"); keys.add((String) key); } Collections.sort(keys); out.writeStartObject(); for (String key : keys) { out.writeFieldName(key); writeValue(out, values.get(key), state, depth + 1); } out.writeEndObject(); } finally { state.leave(values); } }
    private void array(JsonGenerator out, Collection<?> values, State state, int depth) throws IOException { state.enter(values, depth); try { state.children(values.size()); out.writeStartArray(); for (Object value : values) writeValue(out, value, state, depth + 1); out.writeEndArray(); } finally { state.leave(values); } }

    private static final class State {
        private int nodes; private final IdentityHashMap<Object, Boolean> active = new IdentityHashMap<Object, Boolean>();
        void node(int depth) throws IOException { if (depth > MAX_DEPTH) throw new IOException("JSON export exceeds depth limit"); if (++nodes > MAX_NODES) throw new IOException("JSON export exceeds node limit"); }
        void children(int count) throws IOException { if (count > MAX_NODES - nodes) throw new IOException("JSON export exceeds node limit"); }
        void enter(Object value, int depth) throws IOException { node(depth); if (active.put(value, Boolean.TRUE) != null) throw new IOException("JSON export contains a cycle"); }
        void leave(Object value) { active.remove(value); }
    }
    private static final class BoundedOutputStream extends OutputStream {
        private final int maximum; private final ByteArrayOutputStream output = new ByteArrayOutputStream(8192); private int size;
        BoundedOutputStream(int maximum) { this.maximum = maximum; }
        @Override public void write(int value) throws IOException { if (size == maximum) throw new IOException("JSON export exceeds UTF-8 byte limit"); output.write(value); size++; }
        @Override public void write(byte[] buffer, int offset, int length) throws IOException { if (length > maximum - size) throw new IOException("JSON export exceeds UTF-8 byte limit"); output.write(buffer, offset, length); size += length; }
        byte[] bytes() { return output.toByteArray(); }
    }
}
