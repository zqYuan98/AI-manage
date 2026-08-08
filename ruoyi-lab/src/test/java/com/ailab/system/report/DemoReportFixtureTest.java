package com.ailab.system.report;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailab.system.config.LabProperties;
import com.ailab.system.domain.LabReportSection;
import com.ailab.system.report.config.ReportSectionConfig;
import com.ailab.system.report.exporter.JsonReportExporter;
import com.ailab.system.report.exporter.MarkdownReportExporter;
import com.ailab.system.report.exporter.PdfReportExporter;
import com.ailab.system.report.exporter.WordReportExporter;
import com.ailab.system.report.model.ReportContext;
import com.ailab.system.report.model.ReportData;
import com.ailab.system.report.model.ReportSectionData;
import com.ailab.system.report.renderer.ChartSectionRenderer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Golden demo artifacts built from the deterministic rows seeded by sql/ailab.sql. */
class DemoReportFixtureTest {
    private static final String FILE = "2026-07-ai-lab-monthly-report";
    private static final Path SAMPLES = Paths.get("..", "samples").toAbsolutePath().normalize();

    @Test
    void fixtureFactsMatchTheDeterministicSqlSeed() throws Exception {
        String sql = new String(Files.readAllBytes(Paths.get("..", "sql", "ailab.sql")), StandardCharsets.UTF_8);
        int taskInsert = sql.indexOf("INSERT INTO `lab_task`");
        String tasks = sql.substring(taskInsert, sql.indexOf(';', taskInsert));
        assertFalse(tasks.contains("'2026-07'"), "July task section must render the seeded empty state");
        assertTrue(sql.contains("'2026-07',1,'1',92.50") && sql.contains("'2026-07',1,'1',88.00")
                && sql.contains("'2026-07',1,'1',76.00"));
        assertTrue(sql.contains("'Improve model quality'") && sql.contains("'Expand serving capability'")
                && sql.contains("'Accept accelerator cluster'"));
        assertTrue(sql.contains("'2026-07','ALL','MANUAL_NOTE'")
                && sql.contains("'July performance calibration completed'")
                && sql.contains("'Delivery evidence and cross-line reviews were closed'")
                && sql.contains("'Prepare Q3 milestone execution'"));
        ReportData report = demoReport();
        assertTrue(report.getSections().get(0).getRows().isEmpty());
        assertEquals(3, report.getSections().get(1).getRows().size());
    }

    @Test
    void trackedJsonMarkdownAndWordAreExactProductionExporterOutput() throws Exception {
        ReportData report = demoReport();
        byte[] json = new JsonReportExporter().export(report);
        byte[] markdown = new MarkdownReportExporter().export(report);
        byte[] word = new WordReportExporter().export(report);
        if (Boolean.getBoolean("ailab.samples.generate")) writeCore(json, markdown, word);

        assertArrayEquals(json, Files.readAllBytes(SAMPLES.resolve(FILE + ".json")));
        assertArrayEquals(markdown, Files.readAllBytes(SAMPLES.resolve(FILE + ".md")));
        assertDocxPackageEquals(word, Files.readAllBytes(SAMPLES.resolve(FILE + ".docx")));
        assertTrue(json.length > 100 && json[0] == '{');
        String text = new String(markdown, StandardCharsets.UTF_8);
        assertTrue(text.contains("人工智能实验室月报"));
        assertTrue(text.contains("Algorithm Lead") && text.contains("92\\.50"));
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(word))) {
            StringBuilder content = new StringBuilder();
            document.getParagraphs().forEach(value -> content.append(value.getText()).append('\n'));
            document.getTables().forEach(table -> table.getRows().forEach(row ->
                    row.getTableCells().forEach(cell -> content.append(cell.getText()).append('\n'))));
            assertTrue(content.toString().contains("人工智能实验室月报"));
            assertTrue(content.toString().contains("绩效概览"));
            assertEquals(1, document.getTables().size());
            assertEquals(1, document.getAllPictures().size());
        }
    }

    @Test
    void trackedPdfIsValidAndCanBeRegeneratedWhenLibreOfficeIsAvailable() throws Exception {
        Path pdfFile = SAMPLES.resolve(FILE + ".pdf");
        Path office = locateOffice();
        byte[] generated = null;
        if (Boolean.getBoolean("ailab.samples.generate") && office != null) {
            Files.createDirectories(SAMPLES);
            generated = exportPdf(office);
            Files.write(pdfFile, generated);
        }
        byte[] tracked = Files.readAllBytes(pdfFile);
        assertPdf(tracked);
        Assumptions.assumeTrue(office != null,
                "LibreOffice unavailable; tracked PDF magic is verified but live conversion is skipped");
        if (generated == null) generated = exportPdf(office);
        assertPdf(generated);
    }

    @Test
    void pdfValidationRejectsAFalseCrossReference() {
        byte[] invalid = new byte[1200];
        Arrays.fill(invalid, (byte) ' ');
        byte[] prefix = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(prefix, 0, invalid, 0, prefix.length);
        byte[] suffix = "\nstartxref\n9\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(suffix, 0, invalid, invalid.length - suffix.length, suffix.length);
        assertFalse(validPdf(invalid));
    }

    private static byte[] exportPdf(Path office) throws Exception {
        LabProperties properties = new LabProperties();
        properties.setLibreOfficeExecutable(office.toString());
        properties.setTempDirectory(Paths.get(System.getProperty("java.io.tmpdir"), "ailab-demo-report-temp").toString());
        properties.setConversionTimeoutSeconds(120L);
        return new PdfReportExporter(properties).export(demoReport());
    }

    static ReportData demoReport() {
        ReportContext context = new ReportContext("2026-07", "ALL", 30001L,
                Instant.parse("2026-08-03T09:01:00Z"), Collections.<String,Object>emptyMap());
        List<ReportSectionData> sections = new ArrayList<ReportSectionData>();
        sections.add(new ReportSectionData("TASK_TABLE", "TABLE", "任务交付",
                Collections.<Map<String,Object>>emptyList(),
                tableSummary(Arrays.asList("业务线", "负责人", "交付物"),
                        Arrays.asList("bizLine", "owner", "deliverable"),
                        Arrays.asList("left", "left", "left"))));
        sections.add(new ReportSectionData("SCORE_STAT", "STAT", "绩效概览",
                Arrays.asList(
                        row("member", "Algorithm Lead", "score", new BigDecimal("92.50"), "status", "CALIBRATED"),
                        row("member", "Platform Lead", "score", new BigDecimal("88.00"), "status", "CALIBRATED"),
                        row("member", "Platform Engineer", "score", new BigDecimal("76.00"), "status", "PENDING")),
                tableSummary(Arrays.asList("成员", "得分", "校准状态"),
                        Arrays.asList("member", "score", "status"),
                        Arrays.asList("left", "right", "center"))));
        sections.add(new ReportSectionData("MANAGER_TEXT", "TEXT", "目标进展",
                Collections.<Map<String,Object>>emptyList(),
                map("text", "Build dependable AI platform · 62% · Platform and model work in progress")));
        sections.add(new ReportSectionData("MANUAL_NOTE", "MANUAL", "管理小结",
                Collections.<Map<String,Object>>emptyList(),
                map("text", "July performance calibration completed\n\nDelivery evidence and cross-line reviews were closed\n\nPrepare Q3 milestone execution")));
        List<Map<String,Object>> groups = Collections.emptyList();
        sections.add(new ReportSectionData("LINE_GROUP", "GROUP_TEXT", "业务线进展",
                Collections.<Map<String,Object>>emptyList(), map("groups", groups)));
        sections.add(renderChart(context));
        Map<String,Object> metadata = map("header", map("title", "人工智能实验室月报", "logo", "ai-lab"),
                "style", map("theme", "blue", "font", "Microsoft YaHei"),
                "source", "sql/ailab.sql deterministic demo rows", "sensitivePermission", "lab:report:sensitive");
        return new ReportData(context, "standard_month", 1, sections, metadata);
    }

    private static Map<String,Object> tableSummary(List<String> headers, List<String> fields, List<String> alignments) {
        return map("headers", headers, "fields", fields, "alignments", alignments,
                "widths", Arrays.asList("25%", "30%", "45%"));
    }
    private static ReportSectionData renderChart(ReportContext context) {
        LabReportSection definition = new LabReportSection();
        definition.setId(30006L); definition.setTemplateId(30001L); definition.setSectionCode("PROGRESS_CHART");
        definition.setSectionName("三季度目标进度"); definition.setSectionType("CHART"); definition.setSortNo(60);
        definition.setDataSource("GOAL_PROGRESS"); definition.setQueryConfigJson("{}");
        definition.setRenderConfigJson("{\"chart\":\"bar\"}"); definition.setStyleConfigJson("{}");
        definition.setManualFlag("0"); definition.setVisibleFlag("1"); definition.setSensitiveFlag("0");
        definition.setVersion(1); definition.setDelFlag("0");
        List<Map<String,Object>> rows = Arrays.asList(
                row("goalTitle", "Improve model quality", "progressRate", 75),
                row("goalTitle", "Expand serving capability", "progressRate", 58),
                row("goalTitle", "Accept accelerator cluster", "progressRate", 90));
        ReportSectionData source = new ReportSectionData("PROGRESS_CHART", "CHART", "三季度目标进度",
                rows, Collections.<String,Object>emptyMap());
        return new ChartSectionRenderer().render(context, new ReportSectionConfig(definition), source);
    }
    private static Map<String,Object> row(Object... values) { return map(values); }
    private static Map<String,Object> map(Object... values) {
        Map<String,Object> result = new LinkedHashMap<String,Object>();
        for (int index = 0; index < values.length; index += 2)
            result.put(String.valueOf(values[index]), values[index + 1]);
        return result;
    }
    private static void writeCore(byte[] json, byte[] markdown, byte[] word) throws Exception {
        Files.createDirectories(SAMPLES);
        Files.write(SAMPLES.resolve(FILE + ".json"), json);
        Files.write(SAMPLES.resolve(FILE + ".md"), markdown);
        Files.write(SAMPLES.resolve(FILE + ".docx"), word);
    }
    private static void assertPdf(byte[] value) {
        assertTrue(validPdf(value), "PDF header/xref/trailer/startxref/EOF structure must be valid");
    }

    private static boolean validPdf(byte[] value) {
        byte[] header = "%PDF-".getBytes(StandardCharsets.US_ASCII);
        byte[] eofMarker = "%%EOF".getBytes(StandardCharsets.US_ASCII);
        byte[] startMarker = "startxref".getBytes(StandardCharsets.US_ASCII);
        if (value.length <= 1000 || !matchesAt(value, header, 0)) return false;
        int eof = lastIndexOf(value, eofMarker, value.length - eofMarker.length);
        if (eof < 0 || !whitespace(value, eof + eofMarker.length, value.length)) return false;
        int start = lastIndexOf(value, startMarker, eof - startMarker.length);
        if (start < 0) return false;
        int cursor = start + startMarker.length;
        while (cursor < eof && whiteByte(value[cursor])) cursor++;
        long offset = 0L;
        int digits = 0;
        while (cursor < eof && value[cursor] >= '0' && value[cursor] <= '9') {
            if (offset > (Long.MAX_VALUE - 9L) / 10L) return false;
            offset = offset * 10L + value[cursor++] - '0';
            digits++;
        }
        if (digits == 0 || offset < header.length || offset >= start || offset > Integer.MAX_VALUE) return false;
        int xref = (int) offset;
        byte[] xrefMarker = "xref".getBytes(StandardCharsets.US_ASCII);
        byte[] trailerMarker = "trailer".getBytes(StandardCharsets.US_ASCII);
        return matchesAt(value, xrefMarker, xref)
                && indexOf(value, trailerMarker, xref + xrefMarker.length, start) >= 0;
    }

    private static boolean matchesAt(byte[] value, byte[] expected, int offset) {
        if (offset < 0 || offset > value.length - expected.length) return false;
        for (int i = 0; i < expected.length; i++) if (value[offset + i] != expected[i]) return false;
        return true;
    }

    private static int lastIndexOf(byte[] value, byte[] target, int from) {
        for (int offset = Math.min(from, value.length - target.length); offset >= 0; offset--)
            if (matchesAt(value, target, offset)) return offset;
        return -1;
    }

    private static int indexOf(byte[] value, byte[] target, int from, int limit) {
        for (int offset = Math.max(0, from); offset <= limit - target.length; offset++)
            if (matchesAt(value, target, offset)) return offset;
        return -1;
    }

    private static boolean whitespace(byte[] value, int from, int limit) {
        for (int index = from; index < limit; index++) if (!whiteByte(value[index])) return false;
        return true;
    }

    private static boolean whiteByte(byte value) {
        return value == 0 || value == 9 || value == 10 || value == 12 || value == 13 || value == 32;
    }
    private static void assertDocxPackageEquals(byte[] expected, byte[] actual) throws Exception {
        Map<String,byte[]> expectedEntries = docxEntries(expected);
        Map<String,byte[]> actualEntries = docxEntries(actual);
        assertEquals(expectedEntries.keySet(), actualEntries.keySet());
        for (String name : expectedEntries.keySet())
            assertArrayEquals(expectedEntries.get(name), actualEntries.get(name), name);
    }
    private static Map<String,byte[]> docxEntries(byte[] value) throws Exception {
        Map<String,byte[]> entries = new TreeMap<String,byte[]>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(value))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                for (int count; (count = zip.read(buffer)) >= 0;) bytes.write(buffer, 0, count);
                assertTrue(entries.put(entry.getName(), bytes.toByteArray()) == null, "duplicate DOCX entry");
            }
        }
        return entries;
    }
    private static Path locateOffice() {
        String configured = System.getProperty("ailab.demo.libreoffice");
        if (configured == null || configured.trim().isEmpty()) configured = System.getenv("AILAB_LIBREOFFICE");
        if (configured != null && Files.isRegularFile(Paths.get(configured))) return Paths.get(configured).toAbsolutePath();
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            Path oosplash = Paths.get("/usr/lib/libreoffice/program/oosplash");
            if (Files.isRegularFile(oosplash)) return oosplash;
        }
        String path = System.getenv("PATH"); if (path == null) return null;
        String[] names = System.getProperty("os.name", "").toLowerCase().contains("win")
                ? new String[] {"soffice.exe", "soffice.com"} : new String[] {"soffice", "libreoffice"};
        for (String directory : path.split(java.io.File.pathSeparator)) for (String name : names) {
            Path candidate = Paths.get(directory, name); if (Files.isRegularFile(candidate)) return candidate.toAbsolutePath();
        }
        return null;
    }
}
