package com.ailab.system.report.config;

import com.ailab.system.domain.LabReportInstance;
import com.ailab.system.domain.LabReportSection;
import com.ailab.system.domain.LabReportTemplate;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
    private static final int MAX_JSON_CHARS = 16000, MAX_JSON_BYTES = 32000, MAX_JSON_TOKENS = 2000;
    private static final int MAX_JSON_DEPTH = 8, MAX_FILTERS = 10, MAX_COLUMNS = 10, MAX_STRING = 500;
    private static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Set<String> TYPES = ReportConfigCatalog.sectionTypes();
    private static final Set<String> OPERATORS = ReportConfigCatalog.filterOperators();
    private static final Set<String> ALL_FIELDS = ReportConfigCatalog.queryFields();

    public void validateSection(LabReportSection section) {
        if (section == null || !TYPES.contains(section.getSectionType())) throw invalid("Unknown report section type");
        String provider = section.getDataSource();
        if ("MANUAL".equals(section.getSectionType())) { if (provider != null && !provider.trim().isEmpty()) throw invalid("MANUAL sections cannot have a provider"); }
        else if (provider == null || !ReportConfigCatalog.compatibleProviders(section.getSectionType()).contains(provider)) throw invalid("Provider is not compatible with section type");
        validateQuery(parse(section.getQueryConfigJson(), "query configuration"));
        validateRender(parse(section.getRenderConfigJson(), "render configuration"));
        validateSectionStyle(parse(section.getStyleConfigJson(), "section style configuration"));
        if (section.getSensitivePermission() != null && section.getSensitivePermission().length() > 128) throw invalid("Invalid sensitive permission");
        if (ReportConfigCatalog.PERF_SUMMARY.equals(provider) || nonBlank(section.getSensitivePermission())) section.setSensitiveFlag("1");
    }

    /** Updates are checked against the persisted sensitive snapshot, never only against client input. */
    public void validateUpdate(LabReportSection existingPersisted, LabReportSection candidate) {
        if (existingPersisted == null) throw new IllegalArgumentException("Persisted section is required");
        validateSection(candidate);
        if (existingPersisted.isSensitive() && !candidate.isSensitive()) throw new IllegalStateException("Sensitive sections cannot be downgraded");
        if (nonBlank(existingPersisted.getSensitivePermission())
                && !existingPersisted.getSensitivePermission().equals(candidate.getSensitivePermission())) throw new IllegalStateException("Sensitive permission cannot be cleared or changed");
    }

    public void validateForSave(String serializedSection) { validateSerialized(serializedSection); }
    public void validateForImport(String serializedSection) { validateSerialized(serializedSection); }
    public void validateTemplateForSave(String serializedTemplate) { validateSerializedTemplate(serializedTemplate); }
    public void validateTemplateForImport(String serializedTemplate) { validateSerializedTemplate(serializedTemplate); }

    public void validateTemplate(LabReportTemplate template) {
        if (template == null || !validTemplateCode(template.getTemplateCode())) throw invalid("Invalid template code");
        if (!nonBlank(template.getTemplateName()) || template.getTemplateName().length() > 200) throw invalid("Invalid template name");
        if (!ReportConfigCatalog.reportTypes().contains(template.getReportType())) throw invalid("Invalid report type");
        if (template.getRevisionNo() == null || template.getRevisionNo().intValue() < 1) throw invalid("Invalid template revision");
        if (!ReportConfigCatalog.templateStatuses().contains(template.getStatus())) throw invalid("Invalid template status");
        validateTemplateHeader(parse(template.getHeaderJson(), "template header configuration"));
        validateTemplateStyle(parse(template.getStyleJson(), "template style configuration"));
    }

    private void validateSerialized(String input) {
        JsonNode root = parse(input, "section"); requireObject(root, "section"); assertOnly(root, set("sectionType", "dataSource", "queryConfig", "renderConfig", "styleConfig", "sensitivePermission"), "section");
        LabReportSection section = new LabReportSection(); section.setSectionType(text(root, "sectionType", true)); section.setDataSource(text(root, "dataSource", false));
        section.setQueryConfigJson(root.has("queryConfig") ? root.get("queryConfig").toString() : "{}"); section.setRenderConfigJson(root.has("renderConfig") ? root.get("renderConfig").toString() : "{}"); section.setStyleConfigJson(root.has("styleConfig") ? root.get("styleConfig").toString() : "{}"); section.setSensitivePermission(text(root, "sensitivePermission", false)); validateSection(section);
    }

    private void validateSerializedTemplate(String input) {
        JsonNode root = parse(input, "template"); requireObject(root, "template");
        assertOnly(root, set("templateCode", "templateName", "reportType", "revisionNo", "status", "header", "style"), "template");
        LabReportTemplate template = new LabReportTemplate();
        template.setTemplateCode(text(root, "templateCode", true)); template.setTemplateName(text(root, "templateName", true));
        template.setReportType(text(root, "reportType", true)); template.setStatus(text(root, "status", true));
        if (root.has("revisionNo") && (!integralInt(root.get("revisionNo")) || root.get("revisionNo").asInt() < 1)) throw invalid("Invalid template revision");
        template.setRevisionNo(root.has("revisionNo") ? root.get("revisionNo").asInt() : null);
        template.setHeaderJson(root.has("header") ? root.get("header").toString() : "{}");
        template.setStyleJson(root.has("style") ? root.get("style").toString() : "{}");
        validateTemplate(template);
    }

    public LabReportTemplate nextRevisionForPublishedEdit(LabReportTemplate current, int expectedVersion) {
        if (current == null || current.getVersion() == null || current.getVersion().intValue() != expectedVersion) throw new IllegalStateException("Template has changed; reload before saving");
        if (!"ENABLED".equals(current.getStatus())) throw new IllegalStateException("Only published templates create a new revision");
        LabReportTemplate copy = copy(current); copy.setId(null); copy.setRevisionNo(current.getRevisionNo() + 1); copy.setVersion(0); copy.setLatestFlag("1"); copy.setDefaultFlag("0"); return copy;
    }
    public LabReportTemplate saveAsNewFamily(LabReportTemplate source, String code) {
        if (source == null || !validTemplateCode(code)) throw new IllegalArgumentException("Invalid template code");
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
        if (node.has("sort")) allowedField(node.get("sort"), "sort"); if (node.has("groupBy")) allowedField(node.get("groupBy"), "groupBy"); if (node.has("limit") && (!integralInt(node.get("limit")) || node.get("limit").asInt() < 1 || node.get("limit").asInt() > 1000)) throw invalid("Invalid limit");
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
        if (node.has("limit") && (!integralInt(node.get("limit")) || node.get("limit").asInt() < 1 || node.get("limit").asInt() > 1000)) throw invalid("Invalid render limit");
        for (String name : Arrays.asList("template", "chart", "placeholder")) if (node.has(name) && (!node.get(name).isTextual() || node.get(name).asText().length() > MAX_STRING)) throw invalid("Invalid " + name);
        if (node.has("metrics")) { if (!node.get("metrics").isArray() || node.get("metrics").size() == 0 || node.get("metrics").size() > 10) throw invalid("Invalid metrics"); for (JsonNode metric : node.get("metrics")) if (!metric.isTextual() || metric.asText().length() > 64) throw invalid("Invalid metric"); }
        if (node.has("groupBy")) allowedField(node.get("groupBy"), "groupBy");
    }
    private void validateColumn(JsonNode column) { if (column.isTextual()) { allowedField(column, "column"); return; } requireObject(column, "column"); assertOnly(column, set("field", "label", "align", "width"), "column"); allowedField(column.get("field"), "column field"); if (column.has("label") && (!column.get("label").isTextual() || column.get("label").asText().length() > 100)) throw invalid("Invalid column label"); if (column.has("align") && (!column.get("align").isTextual() || !set("LEFT", "CENTER", "RIGHT").contains(column.get("align").asText()))) throw invalid("Invalid column alignment"); if (column.has("width") && (!column.get("width").isTextual() || !column.get("width").asText().matches("([1-9][0-9]{0,2}%|[1-9][0-9]{0,3}px)"))) throw invalid("Invalid column width"); }
    private void validateSectionStyle(JsonNode node) {
        requireObject(node, "section style configuration");
        assertOnly(node, set("titleLevel", "width", "align", "color", "backgroundColor", "fontSize", "bold", "pageBreakBefore", "keepTogether", "padding"), "section style configuration");
        if (node.has("titleLevel") && (!node.get("titleLevel").isTextual() || !node.get("titleLevel").asText().matches("H[1-6]"))) throw invalid("Invalid section title level");
        if (node.has("width") && (!node.get("width").isTextual() || !node.get("width").asText().matches("([1-9][0-9]{0,2}%|[1-9][0-9]{0,3}px)"))) throw invalid("Invalid section width");
        if (node.has("align") && (!node.get("align").isTextual() || !set("LEFT", "CENTER", "RIGHT").contains(node.get("align").asText()))) throw invalid("Invalid section alignment");
        for (String name : Arrays.asList("color", "backgroundColor")) if (node.has(name) && (!node.get(name).isTextual() || !node.get(name).asText().matches("#[0-9A-Fa-f]{6}"))) throw invalid("Invalid section color");
        if (node.has("fontSize") && (!integralInt(node.get("fontSize")) || node.get("fontSize").asInt() < 6 || node.get("fontSize").asInt() > 72)) throw invalid("Invalid section font size");
        for (String name : Arrays.asList("bold", "pageBreakBefore", "keepTogether")) if (node.has(name) && !node.get(name).isBoolean()) throw invalid("Invalid section style flag");
        if (node.has("padding")) {
            JsonNode padding = node.get("padding"); requireObject(padding, "section padding"); assertOnly(padding, set("top", "right", "bottom", "left"), "section padding");
            Iterator<JsonNode> values = padding.elements(); while (values.hasNext()) { JsonNode value = values.next(); if (!integralInt(value) || value.asInt() < 0 || value.asInt() > 100) throw invalid("Invalid section padding"); }
        }
    }
    private void validateTemplateHeader(JsonNode node) {
        requireObject(node, "template header configuration");
        assertOnly(node, set("title", "subtitle", "logo", "periodLabel", "showGeneratedAt"), "template header configuration");
        for (String name : Arrays.asList("title", "subtitle", "periodLabel")) if (node.has(name) && (!node.get(name).isTextual() || node.get(name).asText().length() > 200)) throw invalid("Invalid template header " + name);
        if (node.has("logo") && (!node.get("logo").isTextual() || !node.get("logo").asText().matches("[A-Za-z0-9_-]{1,128}"))) throw invalid("Invalid template header logo");
        if (node.has("showGeneratedAt") && !node.get("showGeneratedAt").isBoolean()) throw invalid("Invalid template header flag");
    }
    private void validateTemplateStyle(JsonNode node) {
        requireObject(node, "template style configuration");
        assertOnly(node, set("theme", "font", "primaryColor", "headingColor", "bodyFontSize", "pageSize", "orientation"), "template style configuration");
        if (node.has("theme") && (!node.get("theme").isTextual() || !node.get("theme").asText().matches("[A-Za-z0-9_-]{1,64}"))) throw invalid("Invalid template theme");
        if (node.has("font") && (!node.get("font").isTextual() || node.get("font").asText().isEmpty() || node.get("font").asText().length() > 100)) throw invalid("Invalid template font");
        for (String name : Arrays.asList("primaryColor", "headingColor")) if (node.has(name) && (!node.get(name).isTextual() || !node.get(name).asText().matches("#[0-9A-Fa-f]{6}"))) throw invalid("Invalid template color");
        if (node.has("bodyFontSize") && (!integralInt(node.get("bodyFontSize")) || node.get("bodyFontSize").asInt() < 6 || node.get("bodyFontSize").asInt() > 72)) throw invalid("Invalid template font size");
        if (node.has("pageSize") && (!node.get("pageSize").isTextual() || !set("A4", "LETTER").contains(node.get("pageSize").asText()))) throw invalid("Invalid template page size");
        if (node.has("orientation") && (!node.get("orientation").isTextual() || !set("PORTRAIT", "LANDSCAPE").contains(node.get("orientation").asText()))) throw invalid("Invalid template orientation");
    }
    private void allowedField(JsonNode value, String name) { if (value == null || !value.isTextual() || !ALL_FIELDS.contains(value.asText())) throw invalid("Unknown " + name); }
    private JsonNode parse(String source, String name) {
        try {
            if (source == null) return JSON.readTree("{}");
            if (source.length() > MAX_JSON_CHARS || source.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES) throw invalid("Invalid " + name + ": configuration too large");
            if (source.trim().isEmpty()) return JSON.readTree("{}");
            validateJsonTokens(source, name);
            JsonNode node = JSON.readTree(source);
            if (node == null) throw invalid("Invalid " + name);
            return node;
        } catch (JsonProcessingException ex) {
            throw invalid("Invalid " + name);
        } catch (IOException ex) {
            throw invalid("Invalid " + name);
        }
    }
    private void validateJsonTokens(String source, String name) throws IOException {
        JsonParser parser = JSON.getFactory().createParser(source);
        int depth = 0, tokens = 0;
        try {
            JsonToken token;
            while ((token = parser.nextToken()) != null) {
                if (++tokens > MAX_JSON_TOKENS) throw invalid("Invalid " + name + ": too many JSON tokens");
                if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
                    if (++depth > MAX_JSON_DEPTH) throw invalid("Invalid " + name + ": nesting is too deep");
                } else if (token == JsonToken.END_OBJECT || token == JsonToken.END_ARRAY) {
                    depth--;
                }
            }
        } finally {
            parser.close();
        }
    }
    private boolean simpleValue(JsonNode value) { if (scalar(value)) return true; if (value.isArray() && value.size() <= 20) for (JsonNode child : value) if (!scalar(child)) return false; return value.isArray(); }
    private boolean scalar(JsonNode value) { return value != null && ((value.isTextual() && value.asText().length() <= MAX_STRING) || value.isNumber() || value.isBoolean() || value.isNull()); }
    private boolean integralInt(JsonNode value) { return value != null && value.isIntegralNumber() && value.canConvertToInt(); }
    private String text(JsonNode root, String key, boolean required) { if (!root.has(key)) { if (required) throw invalid("Missing " + key); return null; } if (!root.get(key).isTextual() || root.get(key).asText().length() > MAX_STRING) throw invalid("Invalid " + key); return root.get(key).asText(); }
    private void requireObject(JsonNode node, String name) { if (node == null || !node.isObject()) throw invalid("Invalid " + name); }
    private void assertOnly(JsonNode object, Set<String> allowed, String name) { Iterator<String> names = object.fieldNames(); while (names.hasNext()) if (!allowed.contains(names.next())) throw invalid("Unknown " + name + " field"); }
    private static Set<String> set(String... values) { return Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(values))); }
    private static boolean nonBlank(String value) { return value != null && !value.trim().isEmpty(); }
    private static boolean validTemplateCode(String value) { return value != null && value.matches("[A-Za-z0-9_-]{1,64}"); }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
    private static LabReportTemplate copy(LabReportTemplate source) {
        LabReportTemplate copy = new LabReportTemplate();
        copy.setId(source.getId()); copy.setTemplateCode(source.getTemplateCode()); copy.setTemplateName(source.getTemplateName());
        copy.setPeriodType(source.getPeriodType()); copy.setRevisionNo(source.getRevisionNo()); copy.setLatestFlag(source.getLatestFlag());
        copy.setDefaultFlag(source.getDefaultFlag()); copy.setStatus(source.getStatus()); copy.setHeaderJson(source.getHeaderJson());
        copy.setStyleJson(source.getStyleJson()); copy.setVersion(source.getVersion()); copy.setDelFlag(source.getDelFlag());
        copy.setCreateBy(source.getCreateBy()); copy.setCreateTime(copyDate(source.getCreateTime())); copy.setUpdateBy(source.getUpdateBy());
        copy.setUpdateTime(copyDate(source.getUpdateTime())); copy.setRemark(source.getRemark());
        return copy;
    }
    private static java.util.Date copyDate(java.util.Date value) { return value == null ? null : new java.util.Date(value.getTime()); }

    /** Pure state model; persistence transactions will perform its replacement atomically in Task 12. */
    public static final class TemplateFamily {
        private final List<LabReportTemplate> revisions = new ArrayList<LabReportTemplate>();
        /** Current revisions must be supplied in strictly ascending revision order within each template code. */
        public TemplateFamily(List<LabReportTemplate> current) { if (current != null) for (LabReportTemplate item : current) revisions.add(copy(item)); assertStructure(revisions); assertInvariant(revisions); }
        public void publishAsDefault(LabReportTemplate candidate, int expectedVersion) {
            if (candidate == null || !"ENABLED".equals(candidate.getStatus()) || !validTemplateCode(candidate.getTemplateCode()) || !ReportConfigCatalog.reportTypes().contains(candidate.getReportType())) throw new IllegalStateException("Default template must be enabled and named");
            if (candidate.getId() != null || candidate.getVersion() == null || candidate.getVersion().intValue() != 0) throw new IllegalStateException("Published revision must be an unsaved version-zero template");
            int maximumRevision = 0; LabReportTemplate latest = null;
            for (LabReportTemplate item : revisions) if (candidate.getTemplateCode().equals(item.getTemplateCode())) {
                if (!candidate.getReportType().equals(item.getReportType())) throw new IllegalStateException("A template family cannot change report type");
                maximumRevision = Math.max(maximumRevision, item.getRevisionNo().intValue()); if (item.isLatest()) latest = item;
            }
            if ((latest == null && expectedVersion != 0) || (latest != null && (latest.getVersion() == null || latest.getVersion().intValue() != expectedVersion))) throw new IllegalStateException("Template has changed; reload before publishing");
            if (candidate.getRevisionNo() == null || candidate.getRevisionNo().intValue() != maximumRevision + 1) throw new IllegalStateException("Published template revision must be consecutive");
            List<LabReportTemplate> proposed = new ArrayList<LabReportTemplate>();
            for (LabReportTemplate item : revisions) proposed.add(copy(item));
            for (LabReportTemplate item : proposed) {
                if (candidate.getReportType().equals(item.getReportType())) item.setDefaultFlag("0");
                if (candidate.getTemplateCode().equals(item.getTemplateCode())) item.setLatestFlag("0");
            }
            LabReportTemplate copy = copy(candidate); copy.setDefaultFlag("1"); copy.setLatestFlag("1"); proposed.add(copy);
            assertStructure(proposed); assertInvariant(proposed);
            revisions.clear(); revisions.addAll(proposed);
        }
        public int defaultLatestEnabledCount(String reportType) { int count = 0; for (LabReportTemplate item : revisions) if (reportType.equals(item.getReportType()) && item.isDefaultTemplate() && item.isLatest() && "ENABLED".equals(item.getStatus())) count++; return count; }
        public List<LabReportTemplate> snapshot() { List<LabReportTemplate> copy = new ArrayList<LabReportTemplate>(); for (LabReportTemplate item : revisions) copy.add(ReportConfigValidator.copy(item)); return Collections.unmodifiableList(copy); }
        private static void assertStructure(List<LabReportTemplate> values) {
            Set<String> revisionKeys = new HashSet<String>(); Map<String, Integer> previousRevision = new HashMap<String, Integer>();
            for (LabReportTemplate item : values) {
                if (item == null || !validTemplateCode(item.getTemplateCode()) || !ReportConfigCatalog.reportTypes().contains(item.getReportType()) || item.getRevisionNo() == null || item.getRevisionNo().intValue() < 1) throw new IllegalStateException("Template revisions must be positive and named");
                String key = item.getTemplateCode() + "\u0000" + item.getRevisionNo();
                if (!revisionKeys.add(key)) throw new IllegalStateException("Duplicate template family revision");
                Integer previous = previousRevision.put(item.getTemplateCode(), item.getRevisionNo());
                if (previous != null && item.getRevisionNo().intValue() <= previous.intValue()) throw new IllegalStateException("Template revisions must be strictly increasing");
            }
        }
        private static void assertInvariant(List<LabReportTemplate> values) {
            Map<String, Integer> defaults = new LinkedHashMap<String, Integer>(); Map<String, Integer> latest = new LinkedHashMap<String, Integer>();
            for (LabReportTemplate item : values) {
                String type = item.getReportType(); String code = item.getTemplateCode();
                if (!defaults.containsKey(type)) defaults.put(type, 0); if (!latest.containsKey(code)) latest.put(code, 0);
                if (item.isLatest()) latest.put(code, latest.get(code) + 1);
                if (item.isDefaultTemplate()) {
                    if (!item.isLatest() || !"ENABLED".equals(item.getStatus())) throw new IllegalStateException("Default must be latest and enabled");
                    defaults.put(type, defaults.get(type) + 1);
                }
            }
            for (Integer count : defaults.values()) if (count.intValue() != 1) throw new IllegalStateException("Exactly one default latest enabled template is required per report type");
            for (Integer count : latest.values()) if (count.intValue() != 1) throw new IllegalStateException("Exactly one latest revision is required per template code");
        }
    }
}
