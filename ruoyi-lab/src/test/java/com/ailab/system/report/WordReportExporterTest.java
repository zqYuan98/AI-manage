package com.ailab.system.report;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
import org.junit.jupiter.api.Test;

class WordReportExporterTest {
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
    }

    @Test
    void rejectsHugeCanonicalTextBeforeBuildingTheDocumentPackage() {
        ReportData value = new ReportData(report().getContext(), "t", 1,
                Collections.singletonList(new ReportSectionData("x", "TEXT", "x", Collections.<Map<String,Object>>emptyList(), map("text", repeat("界", 400000)))), Collections.<String,Object>emptyMap());
        assertThrows(java.io.IOException.class, () -> new WordReportExporter().export(value));
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
