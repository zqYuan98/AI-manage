package com.ailab.system.report.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Canonical server-owned shape for the only three editable management narratives. */
public final class ReportManualSummaryContract {
    private static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final java.util.Set<String> FIELDS = new java.util.LinkedHashSet<String>(
            Arrays.asList("bizLineSummary", "reasonAnalysis", "nextStep"));
    private static final int MAX_FIELD_CHARS = 1200;

    private ReportManualSummaryContract() { }

    public static Canonical parse(String source) {
        try {
            JsonNode root = JSON.readTree(source);
            if (root == null || !root.isObject() || root.size() != FIELDS.size()) throw invalid();
            Iterator<String> names = root.fieldNames();
            while (names.hasNext()) if (!FIELDS.contains(names.next())) throw invalid();
            Map<String, String> values = new LinkedHashMap<String, String>();
            for (String field : FIELDS) {
                JsonNode value = root.get(field);
                if (value == null || !value.isTextual()) throw invalid();
                String text = value.asText().trim();
                if (text.isEmpty() || text.length() > MAX_FIELD_CHARS) throw invalid();
                values.put(field, text);
            }
            return new Canonical(JSON.writeValueAsString(values), values.get("bizLineSummary") + "\n\n"
                    + values.get("reasonAnalysis") + "\n\n" + values.get("nextStep"));
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalid();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Report summary requires bizLineSummary, reasonAnalysis and nextStep");
    }

    public static final class Canonical {
        private final String json;
        private final String text;
        private Canonical(String json, String text) { this.json = json; this.text = text; }
        public String getJson() { return json; }
        public String getText() { return text; }
    }
}
