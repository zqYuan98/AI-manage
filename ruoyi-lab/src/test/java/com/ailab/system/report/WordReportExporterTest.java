package com.ailab.system.report;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ailab.system.report.exporter.WordReportExporter;
import com.ailab.system.report.model.ReportContext;
import com.ailab.system.report.model.ReportData;
import com.ailab.system.report.model.ReportSectionData;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;

class WordReportExporterTest {
    @Test
    void duplicateDisplayHeadersStillResolveRowsByFieldIdentity() throws Exception {
        ReportSectionData table = new ReportSectionData("table", "TABLE", "table",
                Collections.singletonList(map("a", "A", "b", "B")),
                map("fields", Arrays.asList("a", "b"), "headers", Arrays.asList("值", "值")));
        ReportData value = new ReportData(report().getContext(), "t", 1,
                Collections.singletonList(table), Collections.<String,Object>emptyMap());

        String document = xmlEntries(new WordReportExporter().export(value)).get("word/document.xml");
        org.w3c.dom.NodeList rows = xml(document).getElementsByTagNameNS(
                "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "tr");
        org.w3c.dom.NodeList cells = ((org.w3c.dom.Element) rows.item(1)).getElementsByTagNameNS(
                "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "tc");

        assertEquals("A", cells.item(0).getTextContent());
        assertEquals("B", cells.item(1).getTextContent());
    }

    @Test
    void tableGeometryAndCellMarginsAreValidSiblingOoxmlElements() throws Exception {
        ReportSectionData table = new ReportSectionData("table", "TABLE", "table",
                Collections.singletonList(map("a", "A", "b", "B", "c", "C")),
                map("fields", Arrays.asList("a", "b", "c"),
                        "headers", Arrays.asList("甲", "乙", "丙"),
                        "widths", Arrays.asList("20%", "30%", "50%")));
        ReportData value = new ReportData(report().getContext(), "t", 1,
                Collections.singletonList(table), Collections.<String,Object>emptyMap());

        org.w3c.dom.Document document = xml(xmlEntries(new WordReportExporter().export(value))
                .get("word/document.xml"));
        String namespace = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
        org.w3c.dom.NodeList gridColumns = document.getElementsByTagNameNS(namespace, "gridCol");
        org.w3c.dom.NodeList cellWidths = document.getElementsByTagNameNS(namespace, "tcW");
        org.w3c.dom.NodeList margins = document.getElementsByTagNameNS(namespace, "tcMar");

        assertEquals(3, gridColumns.getLength());
        assertEquals(6, cellWidths.getLength());
        assertEquals(6, margins.getLength());
        assertEquals(1, document.getElementsByTagNameNS(namespace, "sectPr").getLength());
        assertEquals(1, document.getElementsByTagNameNS(namespace, "pgSz").getLength());
        assertEquals(1, document.getElementsByTagNameNS(namespace, "pgMar").getLength());
        assertEquals(0, document.getElementsByTagNameNS(namespace, "pStyle").getLength(),
                "direct run formatting keeps LibreOffice from flattening tables on unresolved custom pStyle");
        org.w3c.dom.Element firstCellProperties = (org.w3c.dom.Element) document
                .getElementsByTagNameNS(namespace, "tcPr").item(0);
        java.util.List<String> propertyOrder = new java.util.ArrayList<String>();
        for (int child = 0; child < firstCellProperties.getChildNodes().getLength(); child++) {
            org.w3c.dom.Node node = firstCellProperties.getChildNodes().item(child);
            if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) propertyOrder.add(node.getLocalName());
        }
        assertEquals(Arrays.asList("tcW", "shd", "tcMar", "vAlign"), propertyOrder);
        for (int index = 0; index < margins.getLength(); index++) {
            org.w3c.dom.NodeList children = margins.item(index).getChildNodes();
            java.util.List<String> names = new java.util.ArrayList<String>();
            for (int child = 0; child < children.getLength(); child++)
                if (children.item(child).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE)
                    names.add(children.item(child).getLocalName());
            assertEquals(Arrays.asList("top", "left", "bottom", "right"), names);
        }
        org.w3c.dom.NodeList paragraphs = document.getElementsByTagNameNS(namespace, "p");
        for (int index = 0; index < paragraphs.getLength(); index++) {
            int propertyBlocks = 0; org.w3c.dom.NodeList children = paragraphs.item(index).getChildNodes();
            for (int child = 0; child < children.getLength(); child++)
                if (children.item(child).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
                        && "pPr".equals(children.item(child).getLocalName())) propertyBlocks++;
            assertTrue(propertyBlocks <= 1, "a paragraph must have at most one pPr block");
        }
    }

    @Test
    void writesAStableSafeOoxmlPackageForEveryCanonicalSectionType() throws Exception {
        WordReportExporter exporter = new WordReportExporter();
        ReportData report = report();
        byte[] first = exporter.export(report);
        byte[] second = exporter.export(report);
        assertArrayEquals(first, second);

        Map<String, String> entries = xmlEntries(first);
        String document = entries.get("word/document.xml");
        String styles = entries.get("word/styles.xml");
        String rels = entries.get("word/_rels/document.xml.rels");
        assertTrue(entries.containsKey("[Content_Types].xml"));
        assertTrue(entries.keySet().stream().anyMatch(name -> name.startsWith("word/media/image") && name.endsWith(".png")));
        assertTrue(document.contains("人工智能实验室月报"));
        assertTrue(document.contains("明细") && document.contains("统计") && document.contains("说明")
                && document.contains("人工补充") && document.contains("分组") && document.contains("图表"));
        assertTrue(document.contains("暂无数据"));
        assertTrue(document.contains("w:keepNext"));
        assertTrue(document.contains("w:tblHeader"));
        assertTrue(document.contains("w:shd"));
        assertTrue(document.contains("w:tcMar"));
        assertTrue(document.contains("w:vAlign"));
        assertTrue(document.contains("w:jc w:val=\"right\""));
        assertTrue(styles.contains("Microsoft YaHei"));
        assertTrue(styles.contains("w:sz w:val=\"30\"") && styles.contains("w:sz w:val=\"24\"")
                && styles.contains("w:sz w:val=\"21\"") && styles.contains("w:sz w:val=\"17\""));
        assertTrue(rels.contains("image"));
        assertFalse(rels.contains("TargetMode=\"External\""));
        assertFalse(document.contains("lab:secret"));
        org.w3c.dom.NodeList runs = xml(document).getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "r");
        boolean tableSize = false, bodySize = false;
        for (int i = 0; i < runs.getLength(); i++) {
            org.w3c.dom.Element properties = first((org.w3c.dom.Element) runs.item(i), "rPr");
            assertTrue(count(properties, "rFonts") == 1 && count(properties, "sz") == 1 && count(properties, "szCs") == 1);
            String size = first(properties, "sz").getAttributeNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "val");
            tableSize |= "17".equals(size); bodySize |= "21".equals(size);
        }
        assertTrue(tableSize && bodySize);
        org.w3c.dom.NodeList extents = xml(document).getElementsByTagNameNS(
                "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing", "extent");
        assertEquals(1, extents.getLength());
        long contentWidthEmu = 9360L * 635L;
        assertTrue(Long.parseLong(((org.w3c.dom.Element) extents.item(0)).getAttribute("cx")) <= contentWidthEmu,
                "embedded chart must fit inside the Letter page content width");
    }

    @Test
    void rejectsHugeCanonicalTextBeforeBuildingTheDocumentPackage() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new ReportData(report().getContext(), "t", 1,
                        Collections.singletonList(new ReportSectionData("x", "TEXT", "x", Collections.<Map<String,Object>>emptyList(), map("text", repeat("界", 400000)))),
                        Collections.<String,Object>emptyMap()));
        assertTrue(error.getMessage().contains("text byte limit"));
    }

    @Test
    void rejectsTheActualHeaderAndRowCellGridBeforePoiAllocatesIt() {
        java.util.List<String> headers = new java.util.ArrayList<String>(); for (int i = 0; i < 50001; i++) headers.add("c" + i);
        ReportData value = new ReportData(report().getContext(), "t", 1, Collections.singletonList(new ReportSectionData("x", "TABLE", "x", Collections.singletonList(map("c0", "v")), map("headers", headers))), Collections.<String,Object>emptyMap());
        assertThrows(java.io.IOException.class, () -> new WordReportExporter().export(value));
    }

    @Test
    void cumulativeTableStatAndChartCellsAreRejectedBeforePoiAllocatesThem() {
        java.util.List<String> headers = new java.util.ArrayList<String>();
        for (int i = 0; i < 100; i++) headers.add("c" + i);
        java.util.List<Map<String,Object>> rows = repeatedRows(499);
        ReportData value = new ReportData(report().getContext(), "t", 1, Arrays.asList(
                new ReportSectionData("table", "TABLE", "table", rows, map("headers", headers)),
                new ReportSectionData("stat", "STAT", "stat", rows, map("headers", headers)),
                new ReportSectionData("chart", "CHART", "chart", Collections.<Map<String,Object>>emptyList(),
                        map("categories", Collections.singletonList("x"), "values", Collections.singletonList(1)))),
                Collections.<String,Object>emptyMap());
        java.io.IOException failure = assertThrows(java.io.IOException.class, () -> invokePreflight(value));
        assertTrue(failure.getMessage().contains("cell limit"));
    }

    @Test
    void cumulativeRenderedRowsIncludeEveryTableHeaderBeforePoiAllocatesThem() {
        java.util.List<Map<String,Object>> rows = repeatedRows(3333);
        java.util.List<String> categories = new java.util.ArrayList<String>();
        for (int i = 0; i < 3333; i++) categories.add("c" + i);
        ReportData value = new ReportData(report().getContext(), "t", 1, Arrays.asList(
                new ReportSectionData("table", "TABLE", "table", rows,
                        map("headers", Collections.singletonList("value"))),
                new ReportSectionData("stat", "STAT", "stat", rows,
                        map("headers", Collections.singletonList("value"))),
                new ReportSectionData("chart", "CHART", "chart", Collections.<Map<String,Object>>emptyList(),
                        map("categories", categories, "values", Collections.emptyList()))),
                Collections.<String,Object>emptyMap());
        java.io.IOException failure = assertThrows(java.io.IOException.class, () -> invokePreflight(value));
        assertTrue(failure.getMessage().contains("row limit"));
    }

    @Test
    void statTextRowsDoNotConsumeATableRowBudgetBecauseNoTableIsRendered() {
        ReportData value = new ReportData(report().getContext(), "t", 1,
                Collections.singletonList(new ReportSectionData("stat", "STAT", "stat",
                        repeatedRows(10001), map("text", "summary"))),
                Collections.<String,Object>emptyMap());
        assertDoesNotThrow(() -> invokePreflight(value));
    }

    @Test
    void groupTextEmptyMapsConsumeOneGlobalGroupAndTwoGlobalParagraphsEach() {
        java.util.List<Map<String,Object>> first = new java.util.ArrayList<Map<String,Object>>(
                Collections.nCopies(2500, Collections.<String,Object>emptyMap()));
        java.util.List<Map<String,Object>> second = new java.util.ArrayList<Map<String,Object>>(
                Collections.nCopies(2500, Collections.<String,Object>emptyMap()));
        ReportData value = new ReportData(report().getContext(), "t", 1, Arrays.asList(
                new ReportSectionData("g1", "GROUP_TEXT", "g1", Collections.<Map<String,Object>>emptyList(),
                        map("groups", first)),
                new ReportSectionData("g2", "GROUP_TEXT", "g2", Collections.<Map<String,Object>>emptyList(),
                        map("groups", second))), Collections.<String,Object>emptyMap());

        java.io.IOException failure = assertThrows(java.io.IOException.class, () -> invokePreflight(value));

        assertTrue(failure.getMessage().contains("paragraph limit"));
    }

    @Test
    void groupTextGroupBudgetIsGlobalEvenForEntriesThatRenderNoParagraphs() {
        java.util.List<Integer> first = new java.util.ArrayList<Integer>(Collections.nCopies(2500, 1));
        java.util.List<Integer> second = new java.util.ArrayList<Integer>(Collections.nCopies(2501, 2));
        ReportData value = new ReportData(report().getContext(), "t", 1, Arrays.asList(
                new ReportSectionData("g1", "GROUP_TEXT", "g1", Collections.<Map<String,Object>>emptyList(),
                        map("groups", first)),
                new ReportSectionData("g2", "GROUP_TEXT", "g2", Collections.<Map<String,Object>>emptyList(),
                        map("groups", second))), Collections.<String,Object>emptyMap());

        java.io.IOException failure = assertThrows(java.io.IOException.class, () -> invokePreflight(value));

        assertTrue(failure.getMessage().contains("group limit"));
    }

    @Test
    void conservativeGroupBudgetAllowsExactlyFiveThousandEntries() {
        java.util.List<Integer> groups = new java.util.ArrayList<Integer>(Collections.nCopies(5000, 1));
        ReportData value = new ReportData(report().getContext(), "t", 1,
                Collections.singletonList(new ReportSectionData("g", "GROUP_TEXT", "g",
                        Collections.<Map<String,Object>>emptyList(), map("groups", groups))),
                Collections.<String,Object>emptyMap());

        assertDoesNotThrow(() -> invokePreflight(value));
    }

    @Test
    void conservativeParagraphBudgetAllowsExactlyTenThousandParagraphs() {
        java.util.List<Map<String,Object>> groups = new java.util.ArrayList<Map<String,Object>>(
                Collections.nCopies(4997, Collections.<String,Object>emptyMap()));
        ReportData value = new ReportData(report().getContext(), "t", 1, Arrays.asList(
                new ReportSectionData("g", "GROUP_TEXT", "g", Collections.<Map<String,Object>>emptyList(),
                        map("groups", groups)),
                new ReportSectionData("table", "TABLE", "table", Collections.singletonList(map("a", "A")),
                        map("fields", Collections.singletonList("a"), "headers", Collections.singletonList("value")))),
                Collections.<String,Object>emptyMap());

        assertDoesNotThrow(() -> invokePreflight(value));
    }

    @Test
    void conservativeParagraphBudgetRejectsTheNextRenderedParagraphBeforePoi() {
        java.util.List<Map<String,Object>> groups = new java.util.ArrayList<Map<String,Object>>(
                Collections.nCopies(4998, Collections.<String,Object>emptyMap()));
        ReportData value = new ReportData(report().getContext(), "t", 1, Arrays.asList(
                new ReportSectionData("g", "GROUP_TEXT", "g", Collections.<Map<String,Object>>emptyList(),
                        map("groups", groups)),
                new ReportSectionData("text", "TEXT", "text", Collections.<Map<String,Object>>emptyList(),
                        map("text", "body"))), Collections.<String,Object>emptyMap());

        java.io.IOException failure = assertThrows(java.io.IOException.class, () -> invokePreflight(value));
        assertTrue(failure.getMessage().contains("paragraph limit"));
    }

    @Test
    void tableFallbackValuesAreIndexedOncePerRowInsteadOfOncePerMissingHeader() throws Exception {
        CountingValuesMap row = new CountingValuesMap();
        row.put("first", "a");
        row.put("second", "b");

        java.util.List<Object> values = invokeValuesFor(row, Collections.<String>emptyList(), 2);

        assertTrue(row.valuesCalls == 1, "row values must be indexed exactly once");
        assertTrue("a".equals(values.get(0)) && "b".equals(values.get(1)));
    }

    @Test
    void boundedSerializerRefusesBytesBeyondTheConfiguredCap() throws Exception {
        Class<?> type = nested("BoundedOutputStream");
        java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor(java.io.OutputStream.class, int.class); constructor.setAccessible(true);
        java.io.OutputStream stream = (java.io.OutputStream) constructor.newInstance(new ByteArrayOutputStream(), 1);
        java.io.IOException error = assertThrows(java.io.IOException.class, () -> stream.write(new byte[] {1, 2}));
        assertTrue(error.getMessage().contains("byte limit"));
    }

    private Map<String, String> xmlEntries(byte[] bytes) throws Exception {
        Map<String, String> result = new LinkedHashMap<String, String>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                ByteArrayOutputStream value = new ByteArrayOutputStream(); byte[] buffer = new byte[4096];
                for (int read; (read = zip.read(buffer)) > 0;) value.write(buffer, 0, read);
                result.put(entry.getName(), new String(value.toByteArray(), StandardCharsets.UTF_8));
            }
        }
        return result;
    }
    private org.w3c.dom.Document xml(String xml) throws Exception { DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance(); factory.setNamespaceAware(true); return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))); }
    private org.w3c.dom.Element first(org.w3c.dom.Element element, String name) { return (org.w3c.dom.Element) element.getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", name).item(0); }
    private int count(org.w3c.dom.Element element, String name) { return element.getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", name).getLength(); }

    private void invokePreflight(ReportData data) throws Throwable {
        java.lang.reflect.Method method = WordReportExporter.class.getDeclaredMethod("preflight", ReportData.class);
        method.setAccessible(true);
        try { method.invoke(new WordReportExporter(), data); }
        catch (java.lang.reflect.InvocationTargetException error) { throw error.getCause(); }
    }

    private Class<?> nested(String simpleName) {
        for (Class<?> type : WordReportExporter.class.getDeclaredClasses())
            if (simpleName.equals(type.getSimpleName())) return type;
        throw new AssertionError("missing nested type " + simpleName);
    }

    @SuppressWarnings("unchecked")
    private java.util.List<Object> invokeValuesFor(Map<String,Object> row, java.util.List<String> fields,
            int columns)
            throws Exception {
        java.lang.reflect.Method method = WordReportExporter.class.getDeclaredMethod(
                "valuesFor", Map.class, java.util.List.class, int.class);
        method.setAccessible(true);
        return (java.util.List<Object>) method.invoke(new WordReportExporter(), row, fields, columns);
    }

    private static final class CountingValuesMap extends LinkedHashMap<String,Object> {
        int valuesCalls;
        @Override public java.util.Collection<Object> values() {
            valuesCalls++;
            return super.values();
        }
    }

    private java.util.List<Map<String,Object>> repeatedRows(int count) {
        return new java.util.ArrayList<Map<String,Object>>(
                Collections.nCopies(count, map("value", "v")));
    }

    private ReportData report() {
        ReportContext context = new ReportContext("2026-08", "人工智能实验室", 7L,
                Instant.parse("2026-08-08T00:00:00Z"), Collections.<String, Object>emptyMap());
        Map<String, Object> row = map("负责人", "张三", "数量", 2);
        Map<String, Object> table = map("headers", Arrays.asList("负责人", "数量"), "alignments", Arrays.asList("left", "right"));
        Map<String, Object> chart = map("categories", Arrays.asList("一月"), "values", Arrays.asList(1), "pngBase64", trustedPng());
        return new ReportData(context, "monthly", 1, Arrays.asList(
                new ReportSectionData("table", "TABLE", "明细", Collections.singletonList(row), table),
                new ReportSectionData("stat", "STAT", "统计", Collections.<Map<String, Object>>emptyList(), Collections.<String, Object>emptyMap()),
                new ReportSectionData("text", "TEXT", "说明", Collections.<Map<String, Object>>emptyList(), map("text", "按时完成")),
                new ReportSectionData("manual", "MANUAL", "人工补充", Collections.<Map<String, Object>>emptyList(), map("text", "暂无人工填写内容")),
                new ReportSectionData("group", "GROUP_TEXT", "分组", Collections.<Map<String, Object>>emptyList(), map("groups", Collections.emptyList())),
                new ReportSectionData("chart", "CHART", "图表", Collections.<Map<String, Object>>emptyList(), chart)),
                map("sensitivePermission", "lab:secret"));
    }

    private Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < values.length; i += 2) result.put(String.valueOf(values[i]), values[i + 1]);
        return result;
    }
    private String trustedPng() {
        try { BufferedImage image = new BufferedImage(640, 320, BufferedImage.TYPE_INT_RGB); ByteArrayOutputStream out = new ByteArrayOutputStream(); ImageIO.write(image, "png", out); return java.util.Base64.getEncoder().encodeToString(out.toByteArray()); }
        catch (Exception ex) { throw new AssertionError(ex); }
    }
    private String repeat(String value, int count) { StringBuilder out = new StringBuilder(value.length() * count); for (int i = 0; i < count; i++) out.append(value); return out.toString(); }
}
