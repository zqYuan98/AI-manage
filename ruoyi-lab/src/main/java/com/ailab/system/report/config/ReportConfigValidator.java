package com.ailab.system.report.config;

import com.ailab.system.domain.LabReportInstance;
import com.ailab.system.domain.LabReportSection;
import com.ailab.system.domain.LabReportTemplate;
import com.ailab.system.report.provider.ReportConfigValidatorIds;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strict whitelist validation used identically for REST saves and template imports. */
public final class ReportConfigValidator {
    private static final int MAX_JSON_CHARS = 16000, MAX_JSON_DEPTH = 8, MAX_FILTERS = 10, MAX_COLUMNS = 10, MAX_STRING = 500;
    private static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Set<String> TYPES = set("TABLE", "STAT", "TEXT", "MANUAL", "GROUP_TEXT", "CHART");
    private static final Set<String> OPERATORS = set("EQ", "NE", "IN", "GTE", "LTE", "BETWEEN");
    private static final Set<String> ALL_FIELDS = set("period", "bizLine", "owner", "ownerId", "memberId", "status", "resultStatus", "taskLevel", "planDate", "title", "deliverable", "nextAction", "coordination", "block", "goalLevel", "progressRate", "assetStage", "assetType", "iprStage", "iprType", "result", "sectionCode");
    private static final Map<String, Set<String>> COMPATIBLE = compatible();

    public void validateSection(LabReportSection section) {
        if (section == null || !TYPES.contains(section.getSectionType())) throw invalid("Unknown report section type");
        String provider = section.getDataSource();
        if ("MANUAL".equals(section.getSectionType())) { if (provider != null && !provider.trim().isEmpty()) throw invalid("MANUAL sections cannot have a provider"); }
        else if (provider == null || !COMPATIBLE.get(section.getSectionType()).contains(provider)) throw invalid("Provider is not compatible with section type");
        validateQuery(parse(section.getQueryConfigJson(), "query configuration"));
        validateRender(parse(section.getRenderConfigJson(), "render configuration"));
        if ("PERF_SUMMARY".equals(provider) || nonBlank(section.getSensitivePermission())) section.setSensitiveFlag("1");
    }

    public void validateForSave(String serializedSection) { validateSerialized(serializedSection); }
    public void validateForImport(String serializedSection) { validateSerialized(serializedSection); }

    private void validateSerialized(String input) {
        JsonNode root = parse(input, "section"); requireObject(root, "section"); assertOnly(root, set("sectionType", "dataSource", "queryConfig", "renderConfig", "sensitivePermission"), "section");
        LabReportSection section = new LabReportSection(); section.setSectionType(text(root, "sectionType", true)); section.setDataSource(text(root, "dataSource", false));
        section.setQueryConfigJson(root.has("queryConfig") ? root.get("queryConfig").toString() : "{}"); section.setRenderConfigJson(root.has("renderConfig") ? root.get("renderConfig").toString() : "{}"); section.setSensitivePermission(text(root, "sensitivePermission", false)); validateSection(section);
    }

    public LabReportTemplate nextRevisionForPublishedEdit(LabReportTemplate current, int expectedVersion) {
        if (current == null || current.getVersion() == null || current.getVersion().intValue() != expectedVersion) throw new IllegalStateException("Template has changed; reload before saving");
        if (!"ENABLED".equals(current.getStatus())) throw new IllegalStateException("Only published templates create a new revision");
        LabReportTemplate copy = copy(current); copy.setId(null); copy.setRevisionNo(current.getRevisionNo() + 1); copy.setVersion(0); copy.setLatestFlag("1"); copy.setDefaultFlag("0"); return copy;
    }
    public LabReportTemplate saveAsNewFamily(LabReportTemplate source, String code) {
        if (source == null || !nonBlank(code) || !code.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid template code");
        LabReportTemplate copy = copy(source); copy.setId(null); copy.setTemplateCode(code); copy.setRevisionNo(1); copy.setVersion(0); copy.setLatestFlag("1"); copy.setDefaultFlag("0"); return copy;
    }
    public void assertHistoricalInstancePinned(LabReportInstance instance, List<LabReportTemplate> templates) {
        if (instance == null || !nonBlank(instance.getTemplateCode()) || instance.getTemplateRevision() == null) throw new IllegalStateException("Report instance has no template revision");
        for (LabReportTemplate template : templates) if (instance.getTemplateCode().equals(template.getTemplateCode()) && instance.getTemplateRevision().equals(template.getRevisionNo())) return;
        throw new IllegalStateException("Historical report template revision is unavailable");
    }

    private void validateQuery(JsonNode node) {
        requireObject(node, "query configuration"); assertOnly(node, set("filters", "sort", "groupBy", "limit"), "query configuration");
        if (node.has("filters")) { if (!node.get("filters").isArray() || node.get("filters").size() > MAX_FILTERS) throw invalid("Invalid filters"); for (JsonNode filter : node.get("filters")) validateFilter(filter); }
        if (node.has("sort")) allowedField(node.get("sort"), "sort"); if (node.has("groupBy")) allowedField(node.get("groupBy"), "groupBy"); if (node.has("limit") && (!node.get("limit").canConvertToInt() || node.get("limit").asInt() < 1 || node.get("limit").asInt() > 1000)) throw invalid("Invalid limit");
    }
    private void validateFilter(JsonNode filter) {
        requireObject(filter, "filter"); assertOnly(filter, set("field", "operator", "value"), "filter"); allowedField(filter.get("field"), "filter field");
        if (!filter.has("operator") || !filter.get("operator").isTextual() || !OPERATORS.contains(filter.get("operator").asText())) throw invalid("Unknown filter operator");
        if (!filter.has("value") || !simpleValue(filter.get("value"))) throw invalid("Invalid filter value");
        String operator = filter.get("operator").asText(); JsonNode value = filter.get("value");
        if ("BETWEEN".equals(operator) && (!value.isArray() || value.size() != 2 || !scalar(value.get(0)) || !scalar(value.get(1)))) throw invalid("BETWEEN requires exactly two scalar values");
        if ("IN".equals(operator) && (!value.isArray() || value.size() == 0 || value.size() > 20)) throw invalid("Invalid IN filter");
        if (!"BETWEEN".equals(operator) && !"IN".equals(operator) && !scalar(value)) throw invalid("Scalar operator requires a scalar value");
    }
    private void validateRender(JsonNode node) {
        requireObject(node, "render configuration"); assertOnly(node, set("columns", "limit", "template", "metrics", "chart", "groupBy", "placeholder"), "render configuration");
        if (node.has("columns")) { if (!node.get("columns").isArray() || node.get("columns").size() > MAX_COLUMNS) throw invalid("Invalid columns"); for (JsonNode column : node.get("columns")) validateColumn(column); }
        if (node.has("limit") && (!node.get("limit").canConvertToInt() || node.get("limit").asInt() < 1 || node.get("limit").asInt() > 1000)) throw invalid("Invalid render limit");
        for (String name : Arrays.asList("template", "chart", "placeholder")) if (node.has(name) && (!node.get(name).isTextual() || node.get(name).asText().length() > MAX_STRING)) throw invalid("Invalid " + name);
        if (node.has("metrics")) { if (!node.get("metrics").isArray() || node.get("metrics").size() == 0 || node.get("metrics").size() > 10) throw invalid("Invalid metrics"); for (JsonNode metric : node.get("metrics")) if (!metric.isTextual() || metric.asText().length() > 64) throw invalid("Invalid metric"); }
        if (node.has("groupBy")) allowedField(node.get("groupBy"), "groupBy");
    }
    private void validateColumn(JsonNode column) { if (column.isTextual()) { allowedField(column, "column"); return; } requireObject(column, "column"); assertOnly(column, set("field", "label", "align", "width"), "column"); allowedField(column.get("field"), "column field"); if (column.has("label") && (!column.get("label").isTextual() || column.get("label").asText().length() > 100)) throw invalid("Invalid column label"); if (column.has("align") && (!column.get("align").isTextual() || !set("LEFT", "CENTER", "RIGHT").contains(column.get("align").asText()))) throw invalid("Invalid column alignment"); if (column.has("width") && (!column.get("width").isTextual() || !column.get("width").asText().matches("([1-9][0-9]{0,2}%|[1-9][0-9]{0,3}px)"))) throw invalid("Invalid column width"); }
    private void allowedField(JsonNode value, String name) { if (value == null || !value.isTextual() || !ALL_FIELDS.contains(value.asText())) throw invalid("Unknown " + name); }
    private JsonNode parse(String source, String name) { try { if (source == null || source.trim().isEmpty()) return JSON.readTree("{}"); if (source.length() > MAX_JSON_CHARS) throw invalid("Configuration too large"); JsonNode node = JSON.readTree(source); if (node == null || depth(node, 0) > MAX_JSON_DEPTH) throw invalid("Configuration nesting is too deep"); return node; } catch (JsonProcessingException ex) { throw invalid("Invalid " + name); } }
    private int depth(JsonNode node, int current) { int maximum = current; Iterator<JsonNode> children = node.elements(); while (children.hasNext()) maximum = Math.max(maximum, depth(children.next(), current + 1)); return maximum; }
    private boolean simpleValue(JsonNode value) { if (scalar(value)) return true; if (value.isArray() && value.size() <= 20) for (JsonNode child : value) if (!scalar(child)) return false; return value.isArray(); }
    private boolean scalar(JsonNode value) { return value != null && ((value.isTextual() && value.asText().length() <= MAX_STRING) || value.isNumber() || value.isBoolean() || value.isNull()); }
    private String text(JsonNode root, String key, boolean required) { if (!root.has(key)) { if (required) throw invalid("Missing " + key); return null; } if (!root.get(key).isTextual() || root.get(key).asText().length() > MAX_STRING) throw invalid("Invalid " + key); return root.get(key).asText(); }
    private void requireObject(JsonNode node, String name) { if (node == null || !node.isObject()) throw invalid("Invalid " + name); }
    private void assertOnly(JsonNode object, Set<String> allowed, String name) { Iterator<String> names = object.fieldNames(); while (names.hasNext()) if (!allowed.contains(names.next())) throw invalid("Unknown " + name + " field"); }
    private static Set<String> set(String... values) { return Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(values))); }
    private static boolean nonBlank(String value) { return value != null && !value.trim().isEmpty(); }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
    private static Map<String, Set<String>> compatible() { Map<String, Set<String>> map = new HashMap<String, Set<String>>(); map.put("TABLE", set("TASK_DETAIL", "TASK_UNDONE", "TASK_NEXT", "TASK_COORD", "TASK_BLOCK", "ASSET_SUMMARY", "IPR_SUMMARY", "MANUAL_SUMMARY")); map.put("STAT", set("GOAL_PROGRESS", "TASK_STAT", "ASSET_SUMMARY", "IPR_SUMMARY", "PERF_SUMMARY")); map.put("TEXT", set("GOAL_PROGRESS", "TASK_NEXT", "TASK_BLOCK", "MANUAL_SUMMARY")); map.put("GROUP_TEXT", set("TASK_DETAIL", "TASK_COORD", "GOAL_PROGRESS", "MANUAL_SUMMARY")); map.put("CHART", set("GOAL_PROGRESS", "TASK_STAT", "PERF_SUMMARY")); return Collections.unmodifiableMap(map); }
    private static LabReportTemplate copy(LabReportTemplate source) { LabReportTemplate copy = new LabReportTemplate(); copy.setTemplateCode(source.getTemplateCode()); copy.setTemplateName(source.getTemplateName()); copy.setReportType(source.getReportType()); copy.setPeriodType(source.getPeriodType()); copy.setRevisionNo(source.getRevisionNo()); copy.setLatestFlag(source.getLatestFlag()); copy.setDefaultFlag(source.getDefaultFlag()); copy.setStatus(source.getStatus()); copy.setHeaderJson(source.getHeaderJson()); copy.setStyleJson(source.getStyleJson()); return copy; }

    /** Pure state model; persistence transactions will perform its replacement atomically in Task 12. */
    public static final class TemplateFamily {
        private final List<LabReportTemplate> revisions = new ArrayList<LabReportTemplate>();
        public TemplateFamily(List<LabReportTemplate> current) { if (current != null) for (LabReportTemplate item : current) revisions.add(copy(item)); assertInvariant(); }
        public void publishAsDefault(LabReportTemplate candidate) { if (candidate == null || !"ENABLED".equals(candidate.getStatus())) throw new IllegalStateException("Default template must be enabled"); for (LabReportTemplate item : revisions) if (candidate.getReportType().equals(item.getReportType())) { item.setDefaultFlag("0"); item.setLatestFlag("0"); } LabReportTemplate copy = copy(candidate); copy.setDefaultFlag("1"); copy.setLatestFlag("1"); revisions.add(copy); assertInvariant(); }
        public int defaultLatestEnabledCount(String reportType) { int count = 0; for (LabReportTemplate item : revisions) if (reportType.equals(item.getReportType()) && item.isDefaultTemplate() && item.isLatest() && "ENABLED".equals(item.getStatus())) count++; return count; }
        public List<LabReportTemplate> snapshot() { List<LabReportTemplate> copy = new ArrayList<LabReportTemplate>(); for (LabReportTemplate item : revisions) copy.add(ReportConfigValidator.copy(item)); return Collections.unmodifiableList(copy); }
        private void assertInvariant() { Map<String, Integer> counts = new LinkedHashMap<String, Integer>(); for (LabReportTemplate item : revisions) { String type = item.getReportType(); if (!counts.containsKey(type)) counts.put(type, 0); if (item.isDefaultTemplate() && item.isLatest() && "ENABLED".equals(item.getStatus())) counts.put(type, counts.get(type) + 1); } for (Integer count : counts.values()) if (count.intValue() != 1) throw new IllegalStateException("Exactly one default latest enabled template is required per report type"); }
    }
}
