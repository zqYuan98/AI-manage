package com.ailab.system.report;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailab.system.report.exporter.JsonReportExporter;
import com.ailab.system.report.exporter.MarkdownReportExporter;
import com.ailab.system.report.model.ReportContext;
import com.ailab.system.report.model.ReportData;
import com.ailab.system.report.model.ReportSectionData;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
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
        ReportData title = new ReportData(numeric.getContext(), "t", 1, Collections.singletonList(new ReportSectionData("x", "TEXT", "标题\n---", Collections.<Map<String, Object>>emptyList(), Collections.<String, Object>singletonMap("text", "- 注入\n```\n1. injected\n    code"))), Collections.<String, Object>emptyMap());
        String markdown = new String(new MarkdownReportExporter().export(title), StandardCharsets.UTF_8);
        assertFalse(markdown.contains("\n---\n")); assertFalse(markdown.contains("\n```\n")); assertFalse(markdown.contains("\n1. injected")); assertFalse(markdown.contains("\n    code")); assertTrue(markdown.contains("\u00a0\u00a0\u00a0\u00a0code"));
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
        Map<String, Object> chart = new LinkedHashMap<String, Object>(); chart.put("categories", Arrays.asList("一月", "二月")); chart.put("series", Collections.singletonList(Collections.<String, Object>singletonMap("values", Arrays.asList(1, 2)))); chart.put("values", Arrays.asList(1, 2)); chart.put("pngBase64", "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL0WQAAAABJRU5ErkJggg==");
        ReportData report = new ReportData(report().getContext(), "t", 1, Arrays.asList(new ReportSectionData("t", "TABLE", "空表", Collections.<Map<String, Object>>emptyList(), table), new ReportSectionData("c", "CHART", "图", Collections.<Map<String, Object>>emptyList(), chart)), Collections.<String, Object>emptyMap());
        String markdown = new String(new MarkdownReportExporter().export(report), StandardCharsets.UTF_8);
        assertTrue(markdown.contains("暂无数据")); assertTrue(markdown.contains("一月")); assertTrue(markdown.contains("二月")); assertTrue(markdown.contains("1")); assertTrue(markdown.contains("data:image/png;base64,iVBORw0KGgo"));
    }

    private ReportData decimalReport(ReportContext context, BigDecimal value) {
        return new ReportData(context, "t", 1, Collections.singletonList(new ReportSectionData("x", "TABLE", "x", Collections.singletonList(Collections.<String, Object>singletonMap("n", value)), Collections.<String, Object>emptyMap())), Collections.<String, Object>emptyMap());
    }

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
