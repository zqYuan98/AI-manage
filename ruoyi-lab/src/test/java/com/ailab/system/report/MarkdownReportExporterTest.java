package com.ailab.system.report;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailab.system.report.exporter.JsonReportExporter;
import com.ailab.system.report.exporter.MarkdownReportExporter;
import com.ailab.system.report.config.ReportSectionConfig;
import com.ailab.system.report.model.ReportContext;
import com.ailab.system.report.model.ReportData;
import com.ailab.system.report.model.ReportSectionData;
import com.ailab.system.report.renderer.ChartSectionRenderer;
import com.ailab.system.domain.LabReportSection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MarkdownReportExporterTest {
    @Test
    void jsonAndMarkdownAreUtf8LfDeterministicAndDoNotLeakSensitiveMetadata() throws Exception {
        ReportData data = report();
        JsonReportExporter json = new JsonReportExporter(); MarkdownReportExporter markdown = new MarkdownReportExporter();
        byte[] jsonFirst = json.export(data); byte[] markdownFirst = markdown.export(data);
        assertArrayEquals(jsonFirst, json.export(data)); assertArrayEquals(markdownFirst, markdown.export(data));
        String jsonText = new String(jsonFirst, StandardCharsets.UTF_8); String markdownText = new String(markdownFirst, StandardCharsets.UTF_8);
        assertTrue(jsonText.startsWith("{\"context\":")); assertTrue(jsonText.contains("2026-08-08T00:00:00Z"));
        assertTrue(jsonText.indexOf("\"context\"") < jsonText.indexOf("\"templateCode\"")); assertTrue(jsonText.indexOf("\"sections\"") < jsonText.indexOf("\"metadata\""));
        assertTrue(markdownText.startsWith("# 人工智能实验室月报\n")); assertTrue(markdownText.contains("\\|")); assertTrue(markdownText.contains("暂无数据")); assertTrue(markdownText.contains("## 明细")); assertTrue(markdownText.contains("## 统计")); assertTrue(markdownText.contains("## 说明")); assertTrue(markdownText.contains("## 人工补充")); assertTrue(markdownText.contains("## 分组")); assertTrue(markdownText.contains("## 图表"));
        assertFalse(markdownText.contains("lab:secret")); assertFalse(markdownText.contains("<script>")); assertTrue(markdownText.endsWith("\n")); assertFalse(markdownText.contains("\r"));
    }

    @Test
    void exportersRejectNonFiniteNumbersAndMarkdownNormalizesUntrustedBlockFields() throws Exception {
        Map<String, Object> row = new LinkedHashMap<String, Object>(); row.put("n", new BigInteger("12345678901234567890"));
        ReportData numeric = new ReportData(new ReportContext("2026-08", "实验室", 1L, Instant.parse("2026-08-08T00:00:00Z"), Collections.<String, Object>emptyMap()), "t", 1,
                Collections.singletonList(new ReportSectionData("x", "TABLE", "x", Collections.singletonList(row), Collections.<String, Object>emptyMap())), Collections.<String, Object>emptyMap());
        assertTrue(new String(new JsonReportExporter().export(numeric), StandardCharsets.UTF_8).contains("12345678901234567890"));
        row.put("n", Double.NaN);
        ReportData nonFinite = new ReportData(numeric.getContext(), "t", 1, Collections.singletonList(new ReportSectionData("x", "TABLE", "x", Collections.singletonList(row), Collections.<String, Object>emptyMap())), Collections.<String, Object>emptyMap());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> new JsonReportExporter().export(nonFinite));
        ReportData title = new ReportData(numeric.getContext(), "t", 1, Collections.singletonList(new ReportSectionData("x", "TEXT", "标题\n---", Collections.<Map<String, Object>>emptyList(), Collections.<String, Object>singletonMap("text", "- 注入\n```\n1. injected\n    code\n\tcode"))), Collections.<String, Object>emptyMap());
        String markdown = new String(new MarkdownReportExporter().export(title), StandardCharsets.UTF_8);
        assertFalse(markdown.contains("\n---\n")); assertFalse(markdown.contains("\n```\n")); assertFalse(markdown.contains("\n1. injected")); assertFalse(markdown.contains("\n    code")); assertFalse(markdown.contains("\n\tcode")); assertTrue(markdown.contains("\u00a0\u00a0\u00a0\u00a0code"));
    }

    @Test
    void jsonCanonicalizesEquivalentBigDecimalsWithoutLosingLargePrecision() throws Exception {
        ReportContext context = report().getContext();
        ReportData first = decimalReport(context, new BigDecimal("1000000000000000000000000000000000000.00"));
        ReportData second = decimalReport(context, new BigDecimal("1000000000000000000000000000000000000"));
        JsonReportExporter exporter = new JsonReportExporter();
        assertArrayEquals(exporter.export(first), exporter.export(second));
        assertArrayEquals(exporter.export(decimalReport(context, new BigDecimal("1.0"))), exporter.export(decimalReport(context, new BigDecimal("1.00"))));
        assertArrayEquals(exporter.export(decimalReport(context, new BigDecimal("1.00"))), exporter.export(decimalReport(context, BigDecimal.ONE)));
        assertTrue(new String(exporter.export(first), StandardCharsets.UTF_8).contains("1000000000000000000000000000000000000"));
        assertTrue(new String(exporter.export(decimalReport(context, new BigDecimal("-0.00"))), StandardCharsets.UTF_8).contains("\"n\":0"));
    }

    @Test
    void markdownTableEmptyAndChartPortableDataAreExplicit() throws Exception {
        Map<String, Object> table = new LinkedHashMap<String, Object>(); table.put("headers", Arrays.asList("名称")); table.put("alignments", Arrays.asList("left"));
        ReportSectionData chart = renderedChart();
        ReportData report = new ReportData(report().getContext(), "t", 1, Arrays.asList(new ReportSectionData("t", "TABLE", "空表", Collections.<Map<String, Object>>emptyList(), table), chart), Collections.<String, Object>emptyMap());
        String markdown = new String(new MarkdownReportExporter().export(report), StandardCharsets.UTF_8);
        assertTrue(markdown.contains("暂无数据")); assertTrue(markdown.contains("一月")); assertTrue(markdown.contains("二月")); assertTrue(markdown.contains("1")); assertTrue(markdown.contains("data:image/png;base64,"));
    }

    @Test
    void markdownRejectsWrongSizeFakeHugeOrTruncatedPngPayloads() throws Exception {
        for (String png : Arrays.asList("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL0WQAAAABJRU5ErkJggg==", fakePngHeader(100000, 100000), Base64.getEncoder().encodeToString(new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10}))) {
            Map<String, Object> summary = new LinkedHashMap<String, Object>(); summary.put("categories", Collections.singletonList("x")); summary.put("values", Collections.singletonList(1)); summary.put("series", Collections.emptyList()); summary.put("pngBase64", png);
            ReportData value = new ReportData(report().getContext(), "t", 1, Collections.singletonList(new ReportSectionData("c", "CHART", "图", Collections.<Map<String, Object>>emptyList(), summary)), Collections.<String, Object>emptyMap());
            assertFalse(new String(new MarkdownReportExporter().export(value), StandardCharsets.UTF_8).contains("data:image/png;base64,"));
        }
    }

    @Test
    void markdownRejectsOversizeInputAtTheUtf8AppendBoundary() throws Exception {
        Map<String, Object> huge = Collections.<String, Object>singletonMap("text", repeat("界", 800000));
        ReportData oversized = new ReportData(report().getContext(), "t", 1, Collections.singletonList(new ReportSectionData("x", "TEXT", "x", Collections.<Map<String, Object>>emptyList(), huge)), Collections.<String, Object>emptyMap());
        IOException markdown = org.junit.jupiter.api.Assertions.assertThrows(IOException.class, () -> new MarkdownReportExporter().export(oversized));
        assertTrue(markdown.getMessage().contains("UTF-8 byte limit"));
    }

    @Test
    void jsonRejectsDepthAndNodeBudgetsBeforeMaterializingTheWholeDocument() throws Exception {
        ReportData deep = new ReportData(report().getContext(), "t", 1, Collections.<ReportSectionData>emptyList(), Collections.<String, Object>singletonMap("deep", nested(63)));
        org.junit.jupiter.api.Assertions.assertThrows(IOException.class, () -> new JsonReportExporter().export(deep));
        List<Object> nodes = new ArrayList<Object>(); for (int i = 0; i < 99995; i++) nodes.add(null);
        ReportData many = new ReportData(report().getContext(), "t", 1, Collections.<ReportSectionData>emptyList(), Collections.<String, Object>singletonMap("nodes", nodes));
        org.junit.jupiter.api.Assertions.assertThrows(IOException.class, () -> new JsonReportExporter().export(many));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> new ReportData(report().getContext(), "t", 1, Collections.<ReportSectionData>emptyList(), Collections.<String, Object>singletonMap("deep", nested(80))));
        List<Object> tooMany = new ArrayList<Object>(); for (int i = 0; i < 100001; i++) tooMany.add(null);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> new ReportData(report().getContext(), "t", 1, Collections.<ReportSectionData>emptyList(), Collections.<String, Object>singletonMap("nodes", tooMany)));
    }

    @Test
    void jsonRejectsOversizeUtf8OutputAndModelCyclesWithControlledErrors() throws Exception {
        ReportData oversized = new ReportData(report().getContext(), "t", 1, Collections.<ReportSectionData>emptyList(), Collections.<String, Object>singletonMap("text", repeat("界", 800000)));
        IOException json = org.junit.jupiter.api.Assertions.assertThrows(IOException.class, () -> new JsonReportExporter().export(oversized)); assertTrue(json.getMessage().contains("UTF-8 byte limit"));
        Map<String, Object> cycle = new LinkedHashMap<String, Object>(); cycle.put("self", cycle);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> new ReportData(report().getContext(), "t", 1, Collections.<ReportSectionData>emptyList(), cycle));
    }

    @Test
    void jsonRejectsHugeBigDecimalPlainExpansionBeforeMaterializingIt() throws Exception {
        ReportData value = decimalReport(report().getContext(), new BigDecimal("1E+3000000"));
        IOException error = org.junit.jupiter.api.Assertions.assertThrows(IOException.class, () -> new JsonReportExporter().export(value));
        assertTrue(error.getMessage().contains("numeric expansion limit"));
    }

    private ReportData decimalReport(ReportContext context, BigDecimal value) {
        return new ReportData(context, "t", 1, Collections.singletonList(new ReportSectionData("x", "TABLE", "x", Collections.singletonList(Collections.<String, Object>singletonMap("n", value)), Collections.<String, Object>emptyMap())), Collections.<String, Object>emptyMap());
    }

    private ReportSectionData renderedChart() {
        LabReportSection raw = new LabReportSection(); raw.setSectionCode("c"); raw.setSectionName("图"); raw.setSectionType("CHART"); raw.setDataSource("GOAL_PROGRESS"); raw.setSortNo(1); raw.setManualFlag("0"); raw.setVisibleFlag("1"); raw.setQueryConfigJson("{\"filters\":[]}"); raw.setRenderConfigJson("{\"chart\":\"bar\"}"); raw.setStyleConfigJson("{}");
        List<Map<String, Object>> rows = Arrays.asList(row("label", "一月", "value", 1), row("label", "二月", "value", 2));
        return new ChartSectionRenderer().render(report().getContext(), new ReportSectionConfig(raw), new ReportSectionData("c", "CHART", "图", rows, Collections.<String, Object>emptyMap()));
    }

    private Object nested(int depth) { Object value = "leaf"; for (int i = 0; i < depth; i++) value = Collections.<String, Object>singletonMap("x", value); return value; }
    private String repeat(String value, int count) { StringBuilder result = new StringBuilder(value.length() * count); for (int i = 0; i < count; i++) result.append(value); return result.toString(); }
    private String fakePngHeader(int width, int height) { ByteBuffer buffer = ByteBuffer.allocate(33); buffer.put(new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10}); buffer.putInt(13); buffer.put(new byte[] {73, 72, 68, 82}); buffer.putInt(width); buffer.putInt(height); buffer.put(new byte[] {8, 2, 0, 0, 0}); buffer.putInt(0); return Base64.getEncoder().encodeToString(buffer.array()); }
    private Map<String, Object> row(Object... values) { Map<String, Object> result = new LinkedHashMap<String, Object>(); for (int i = 0; i < values.length; i += 2) result.put(String.valueOf(values[i]), values[i + 1]); return result; }

    private ReportData report() {
        ReportContext context = new ReportContext("2026-08", "人工智能实验室", 7L, Instant.parse("2026-08-08T00:00:00Z"), Collections.<String, Object>emptyMap());
        Map<String, Object> row = new LinkedHashMap<String, Object>(); row.put("owner", "张三|<script>"); row.put("count", 2);
        Map<String, Object> tableSummary = new LinkedHashMap<String, Object>(); tableSummary.put("headers", Arrays.asList("负责人", "数量")); tableSummary.put("alignments", Arrays.asList("left", "right"));
        Map<String, Object> textSummary = new LinkedHashMap<String, Object>(); textSummary.put("text", "*说明*\n# 不应成为标题");
        Map<String, Object> manualSummary = new LinkedHashMap<String, Object>(); manualSummary.put("text", "暂无人工填写内容");
        Map<String, Object> groupSummary = new LinkedHashMap<String, Object>(); groupSummary.put("groups", Collections.emptyList());
        Map<String, Object> chartSummary = new LinkedHashMap<String, Object>(); chartSummary.put("categories", Arrays.asList("一月")); chartSummary.put("series", Collections.emptyList());
        return new ReportData(context, "monthly", 1, Arrays.asList(
                new ReportSectionData("table", "TABLE", "明细", Collections.singletonList(row), tableSummary),
                new ReportSectionData("stat", "STAT", "统计", Collections.<Map<String, Object>>emptyList(), Collections.<String, Object>emptyMap()),
                new ReportSectionData("text", "TEXT", "说明", Collections.<Map<String, Object>>emptyList(), textSummary),
                new ReportSectionData("manual", "MANUAL", "人工补充", Collections.<Map<String, Object>>emptyList(), manualSummary),
                new ReportSectionData("group", "GROUP_TEXT", "分组", Collections.<Map<String, Object>>emptyList(), groupSummary),
                new ReportSectionData("chart", "CHART", "图表", Collections.<Map<String, Object>>emptyList(), chartSummary)),
                Collections.<String, Object>singletonMap("sensitivePermission", "lab:secret"));
    }
}
