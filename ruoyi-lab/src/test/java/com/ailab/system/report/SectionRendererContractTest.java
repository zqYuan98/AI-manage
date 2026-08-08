package com.ailab.system.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailab.system.domain.LabReportSection;
import com.ailab.system.report.config.ReportSectionConfig;
import com.ailab.system.report.model.ReportContext;
import com.ailab.system.report.model.ReportSectionData;
import com.ailab.system.report.renderer.ChartSectionRenderer;
import com.ailab.system.report.renderer.GroupTextSectionRenderer;
import com.ailab.system.report.renderer.ManualSectionRenderer;
import com.ailab.system.report.renderer.SectionRenderer;
import com.ailab.system.report.renderer.SectionRendererRegistry;
import com.ailab.system.report.renderer.StatSectionRenderer;
import com.ailab.system.report.renderer.TableSectionRenderer;
import com.ailab.system.report.renderer.TextSectionRenderer;
import java.time.Instant;
import java.time.Duration;
import java.math.BigDecimal;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SectionRendererContractTest {
    private final ReportContext context = new ReportContext("2026-08", "人工智能实验室", 7L,
            Instant.parse("2026-08-08T00:00:00Z"), Collections.<String, Object>emptyMap());

    @Test
    void sixStableRenderersProduceDeterministicImmutableCanonicalPayloads() {
        List<SectionRenderer> renderers = Arrays.<SectionRenderer>asList(new TableSectionRenderer(), new StatSectionRenderer(),
                new TextSectionRenderer(), new ManualSectionRenderer(), new GroupTextSectionRenderer(), new ChartSectionRenderer());
        SectionRendererRegistry registry = new SectionRendererRegistry(renderers);
        assertEquals(6, registry.asMap().size());
        for (String type : Arrays.asList("TABLE", "STAT", "TEXT", "MANUAL", "GROUP_TEXT", "CHART")) {
            SectionRenderer renderer = registry.require(type);
            ReportSectionData first = renderer.render(context, section(type), source(type));
            ReportSectionData second = renderer.render(context, section(type), source(type));
            assertEquals(type, renderer.getId());
            assertEquals(first, second);
            assertEquals(type, first.getSectionType());
            assertThrows(UnsupportedOperationException.class, () -> first.getRows().add(Collections.<String, Object>emptyMap()));
        }
    }

    @Test
    void tableUsesCanonicalColumnsFormatsEmptyValuesAndRejectsUnsafeOrWrongInputs() {
        TableSectionRenderer renderer = new TableSectionRenderer();
        ReportSectionData rendered = renderer.render(context, section("TABLE"), source("TABLE"));
        assertEquals(Arrays.asList("负责人", "计划日期", "完成"), rendered.getSummary().get("headers"));
        assertEquals("\u2014", rendered.getRows().get(1).get("planDate"));
        assertEquals("是", rendered.getRows().get(0).get("status"));
        assertThrows(IllegalArgumentException.class, () -> section("TABLE", "{\"columns\":[\"unknown\"]}"));
        assertThrows(IllegalArgumentException.class, () -> renderer.render(context, section("TABLE"), source("STAT")));
    }

    @Test
    void textManualGroupAndChartHaveSafeEmptyAndNoDroppedNullGroups() {
        TextSectionRenderer text = new TextSectionRenderer();
        assertTrue(String.valueOf(text.render(context, section("TEXT", "{\"template\":\"${context.period}：${summary.total}\"}"), source("TEXT")).getSummary().get("text")).contains("2026-08"));
        assertThrows(IllegalArgumentException.class, () -> text.render(context, section("TEXT", "{\"template\":\"${x?api}\"}"), source("TEXT")));
        ManualSectionRenderer manual = new ManualSectionRenderer();
        assertEquals("暂无人工填写内容", manual.render(context, section("MANUAL"), source("MANUAL")).getSummary().get("text"));
        GroupTextSectionRenderer grouped = new GroupTextSectionRenderer();
        assertEquals(2, ((List<?>) grouped.render(context, section("GROUP_TEXT", "{\"groupBy\":\"owner\"}"), source("GROUP_TEXT")).getSummary().get("groups")).size());
        ChartSectionRenderer chart = new ChartSectionRenderer();
        Object png = chart.render(context, section("CHART"), source("CHART")).getSummary().get("pngBase64");
        assertTrue(String.valueOf(png).length() > 100);
    }

    @Test
    void groupTextHonorsTaskNinePrecomputedSummaryGroupsBeforeRows() {
        Map<String, Object> summary = row("groups", Arrays.asList(row("field", "owner", "key", "研发组", "count", 1)));
        ReportSectionData source = new ReportSectionData("s1", "GROUP_TEXT", "本月进展", Collections.singletonList(row("owner", "研发组")), summary);
        ReportSectionData rendered = new GroupTextSectionRenderer().render(context, section("GROUP_TEXT", "{\"groupBy\":\"owner\"}"), source);
        assertEquals("研发组", ((Map<?, ?>) ((List<?>) rendered.getSummary().get("groups")).get(0)).get("title"));
    }

    @Test
    void chartPngHasFixedBoundsAndByteStableHeadlessOutput() throws Exception {
        ChartSectionRenderer chart = new ChartSectionRenderer();
        String first = String.valueOf(chart.render(context, section("CHART"), source("CHART")).getSummary().get("pngBase64"));
        String second = String.valueOf(chart.render(context, section("CHART"), source("CHART")).getSummary().get("pngBase64"));
        byte[] bytes = Base64.getDecoder().decode(first); BufferedImage image = javax.imageio.ImageIO.read(new ByteArrayInputStream(bytes));
        assertEquals(first, second); assertEquals(640, image.getWidth()); assertEquals(320, image.getHeight()); assertTrue(bytes.length < 512 * 1024);
    }

    @Test
    void chartUsesGoalProgressCanonicalProviderFields() {
        ReportSectionData goalProgress = new ReportSectionData("s1", "CHART", "目标进度", Collections.singletonList(row("goalTitle", "模型训练", "progressRate", 87.5)), Collections.<String, Object>emptyMap());
        ReportSectionData rendered = new ChartSectionRenderer().render(context, section("CHART"), goalProgress);
        assertEquals(Collections.singletonList("模型训练"), rendered.getSummary().get("categories"));
        assertEquals(Collections.singletonList(87.5), rendered.getSummary().get("values"));
    }

    @Test
    void statDerivesDefaultTaskStatAverageAndTopFromCanonicalRows() {
        ReportSectionData taskStats = new ReportSectionData("s1", "STAT", "任务统计", Arrays.asList(row("status", "ONTIME", "total", 4), row("status", "DELAYED", "total", 2)), Collections.<String, Object>emptyMap());
        ReportSectionData rendered = new StatSectionRenderer().render(context, section("STAT", "{\"metrics\":[\"average\",\"top\"]}"), taskStats);
        assertEquals("3", ((Map<?, ?>) ((List<?>) rendered.getSummary().get("metrics")).get(0)).get("value"));
        assertEquals("4", ((Map<?, ?>) ((List<?>) rendered.getSummary().get("metrics")).get(1)).get("value"));
    }

    @Test
    void groupTextUsesTaskNineFieldKeyCountGroupsAgainstSourceRows() {
        List<Map<String, Object>> rows = Arrays.asList(row("bizLine", "算法", "owner", "张三"), row("bizLine", "算法", "owner", "李四"), row("bizLine", null, "owner", "王五"));
        Map<String, Object> summary = row("groups", Arrays.asList(row("field", "bizLine", "key", "算法", "count", 2), row("field", "bizLine", "key", null, "count", 1)));
        ReportSectionData source = new ReportSectionData("s1", "GROUP_TEXT", "协同", rows, summary);
        List<?> groups = (List<?>) new GroupTextSectionRenderer().render(context, section("GROUP_TEXT", "{\"groupBy\":\"bizLine\"}"), source).getSummary().get("groups");
        assertEquals("算法", ((Map<?, ?>) groups.get(0)).get("title")); assertEquals(2, ((List<?>) ((Map<?, ?>) groups.get(0)).get("rows")).size()); assertEquals("（未分组）", ((Map<?, ?>) groups.get(1)).get("title"));
    }

    @Test
    void groupTextMatchesTaskNineStringifiedNumericAndNullKeys() {
        List<Map<String, Object>> rows = Arrays.asList(row("goalId", Long.valueOf(123L)), row("goalId", null));
        Map<String, Object> summary = row("groups", Arrays.asList(row("field", "goalId", "key", "123", "count", 1), row("field", "goalId", "key", "null", "count", 1)));
        ReportSectionData source = new ReportSectionData("s1", "GROUP_TEXT", "协同", rows, summary);
        List<?> groups = (List<?>) new GroupTextSectionRenderer().render(context, section("GROUP_TEXT", "{\"groupBy\":\"goalId\"}"), source).getSummary().get("groups");
        assertEquals(1, ((List<?>) ((Map<?, ?>) groups.get(0)).get("rows")).size());
        assertEquals("（未分组）", ((Map<?, ?>) groups.get(1)).get("title")); assertEquals(1, ((List<?>) ((Map<?, ?>) groups.get(1)).get("rows")).size());
    }

    @Test
    void textRenderingIsByteStableAcrossDefaultLocales() {
        Locale original = Locale.getDefault(); byte[] expected = null;
        try {
            for (Locale locale : Arrays.asList(Locale.US, Locale.GERMANY, Locale.forLanguageTag("tr-TR"))) {
                Locale.setDefault(locale);
                ReportSectionData source = new ReportSectionData("s1", "TEXT", "text", Collections.<Map<String, Object>>emptyList(), row("amount", new BigDecimal("1234.5"), "word", "i"));
                String text = String.valueOf(new TextSectionRenderer().render(context, section("TEXT", "{\"template\":\"${summary.amount} ${summary.word?upper_case}\"}"), source).getSummary().get("text"));
                byte[] actual = text.getBytes(java.nio.charset.StandardCharsets.UTF_8); if (expected == null) expected = actual; else assertArrayEquals(expected, actual);
                assertEquals("1,234.5 I", text);
            }
        } finally { Locale.setDefault(original); }
    }

    @Test
    void chartPngDependsOnlyOnValuesAndNotLabelsOrHostFonts() {
        ChartSectionRenderer renderer = new ChartSectionRenderer();
        ReportSectionData first = new ReportSectionData("s1", "CHART", "chart", Collections.singletonList(row("label", "Alpha", "value", 7)), Collections.<String, Object>emptyMap());
        ReportSectionData second = new ReportSectionData("s1", "CHART", "chart", Collections.singletonList(row("label", "不同字体", "value", 7)), Collections.<String, Object>emptyMap());
        assertEquals(renderer.render(context, section("CHART"), first).getSummary().get("pngBase64"), renderer.render(context, section("CHART"), second).getSummary().get("pngBase64"));
    }

    @Test
    void groupTextRejectsDuplicateOrIncorrectPrecomputedGroupsAndScalesToFiveThousand() {
        GroupTextSectionRenderer renderer = new GroupTextSectionRenderer();
        List<Map<String, Object>> twoRows = Arrays.asList(row("owner", "A"), row("owner", "B"));
        assertThrows(IllegalArgumentException.class, () -> renderer.render(context, section("GROUP_TEXT", "{\"groupBy\":\"owner\"}"), new ReportSectionData("s1", "GROUP_TEXT", "g", twoRows, row("groups", Arrays.asList(row("field", "owner", "key", "A", "count", 1), row("field", "owner", "key", "A", "count", 1))))));
        assertThrows(IllegalArgumentException.class, () -> renderer.render(context, section("GROUP_TEXT", "{\"groupBy\":\"owner\"}"), new ReportSectionData("s1", "GROUP_TEXT", "g", twoRows, row("groups", Collections.singletonList(row("field", "owner", "key", "A", "count", 2))))));
        assertThrows(IllegalArgumentException.class, () -> renderer.render(context, section("GROUP_TEXT", "{\"groupBy\":\"owner\"}"), new ReportSectionData("s1", "GROUP_TEXT", "g", twoRows, row("groups", Collections.singletonList(row("field", "owner", "key", "A", "count", 1.5))))));
        List<Map<String, Object>> rows = new java.util.ArrayList<Map<String, Object>>(); List<Map<String, Object>> groups = new java.util.ArrayList<Map<String, Object>>();
        for (int i = 0; i < 5000; i++) { rows.add(row("owner", "G" + i)); groups.add(row("field", "owner", "key", "G" + i, "count", 1)); }
        ReportSectionData rendered = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> renderer.render(context, section("GROUP_TEXT", "{\"groupBy\":\"owner\"}"), new ReportSectionData("s1", "GROUP_TEXT", "g", rows, row("groups", groups))));
        assertEquals(5000, ((List<?>) rendered.getSummary().get("groups")).size());
    }

    @Test
    void manualPlaceholderAndEmptyStatAreExplicitWithoutInventingZeroMetrics() {
        ReportSectionData manual = new ReportSectionData("s1", "MANUAL", "人工", Collections.<Map<String, Object>>emptyList(), Collections.<String, Object>emptyMap());
        assertEquals("请输入管理说明", new ManualSectionRenderer().render(context, section("MANUAL", "{\"placeholder\":\"请输入管理说明\"}"), manual).getSummary().get("text"));
        ReportSectionData stat = new ReportSectionData("s1", "STAT", "统计", Collections.<Map<String, Object>>emptyList(), Collections.<String, Object>emptyMap());
        ReportSectionData rendered = new StatSectionRenderer().render(context, section("STAT", "{\"metrics\":[\"average\",\"top\"]}"), stat);
        assertEquals(Boolean.TRUE, rendered.getSummary().get("empty")); assertTrue(((List<?>) rendered.getSummary().get("metrics")).isEmpty());
    }

    @Test
    void everyRendererRejectsMismatchedSourceAndCanonicalTableRejectsRawHtml() {
        for (SectionRenderer renderer : Arrays.<SectionRenderer>asList(new TableSectionRenderer(), new StatSectionRenderer(), new TextSectionRenderer(), new ManualSectionRenderer(), new GroupTextSectionRenderer(), new ChartSectionRenderer())) {
            final String wrongType = "TABLE".equals(renderer.getId()) ? "STAT" : "TABLE";
            assertThrows(IllegalArgumentException.class, () -> renderer.render(context, section(renderer.getId()), source(wrongType)));
        }
        assertThrows(IllegalArgumentException.class, () -> new TableSectionRenderer().render(context, section("TABLE", "{\"columns\":[{\"field\":\"owner\",\"label\":\"<b>负责人</b>\"}]}"), source("TABLE")));
        assertThrows(IllegalArgumentException.class, () -> new TableSectionRenderer().render(null, section("TABLE"), source("TABLE")));
        assertThrows(IllegalArgumentException.class, () -> new TableSectionRenderer().render(context, section("TABLE"), new ReportSectionData("other", "TABLE", "other", Collections.<Map<String, Object>>emptyList(), Collections.<String, Object>emptyMap())));
        assertThrows(IllegalArgumentException.class, () -> new ChartSectionRenderer().render(context, section("CHART", "{\"chart\":\"pie\"}"), source("CHART")));
    }

    private ReportSectionData source(String type) {
        List<Map<String, Object>> rows = Arrays.asList(row("owner", "张三", "planDate", "2026-08-01", "status", true, "value", 1.25),
                row("owner", null, "planDate", null, "status", false, "value", 2));
        return new ReportSectionData("s1", type, "本月进展", rows, row("total", 2, "manualText", "", "note", "稳定输出"));
    }

    private ReportSectionConfig section(String type) { return section(type, defaultRender(type)); }
    private ReportSectionConfig section(String type, String render) {
        LabReportSection source = new LabReportSection(); source.setSectionCode("s1"); source.setSectionName("本月进展"); source.setSectionType(type);
        source.setDataSource("MANUAL".equals(type) ? null : provider(type)); source.setSortNo(1); source.setManualFlag("MANUAL".equals(type) ? "1" : "0"); source.setVisibleFlag("1");
        source.setQueryConfigJson("{\"filters\":[]}"); source.setRenderConfigJson(render); source.setStyleConfigJson("{\"titleLevel\":\"H2\"}");
        return new ReportSectionConfig(source);
    }
    private String provider(String type) { if ("TABLE".equals(type)) return "TASK_DETAIL"; if ("STAT".equals(type)) return "TASK_STAT"; if ("TEXT".equals(type) || "CHART".equals(type)) return "GOAL_PROGRESS"; return "TASK_COORD"; }
    private String defaultRender(String type) { if ("TABLE".equals(type)) return "{\"columns\":[{\"field\":\"owner\",\"label\":\"负责人\",\"align\":\"LEFT\"},{\"field\":\"planDate\",\"label\":\"计划日期\",\"align\":\"CENTER\"},{\"field\":\"status\",\"label\":\"完成\",\"align\":\"RIGHT\"}]}"; if ("TEXT".equals(type)) return "{\"template\":\"${context.period}：${summary.total}\"}"; if ("GROUP_TEXT".equals(type)) return "{\"groupBy\":\"owner\"}"; return "{}"; }
    private Map<String, Object> row(Object... values) { Map<String, Object> result = new LinkedHashMap<String, Object>(); for (int i = 0; i < values.length; i += 2) result.put(String.valueOf(values[i]), values[i + 1]); return result; }
}
