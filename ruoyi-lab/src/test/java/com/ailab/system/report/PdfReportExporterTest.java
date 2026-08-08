package com.ailab.system.report;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailab.system.config.LabProperties;
import com.ailab.system.report.exporter.PdfReportExporter;
import com.ailab.system.report.exporter.ReportExportException;
import com.ailab.system.report.exporter.LibreOfficeProcessRunner;
import java.util.Arrays;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PdfReportExporterTest {
    @Test
    void rejectsMissingLibreOfficeAsRetryableTypedFailure() throws Exception {
        LabProperties properties = new LabProperties();
        Path temp = Files.createTempDirectory("pdf exporter test ");
        properties.setTempDirectory(temp.toString());
        properties.setLibreOfficeExecutable(temp.resolve("missing soffice").toString());
        PdfReportExporter exporter = new PdfReportExporter(properties);
        ReportExportException error = assertThrows(ReportExportException.class,
                () -> exporter.exportFromWord(new byte[] {1, 2, 3}, "monthly report"));
        assertTrue(error.isRetryable());
    }

    @Test
    void runnerUsesArgumentListHandlesFakeSuccessFailureAndCleansIsolatedWork() throws Exception {
        Path temp = Files.createTempDirectory("pdf paths with spaces "); LabProperties properties = new LabProperties(); properties.setTempDirectory(temp.toString()); properties.setConversionTimeoutSeconds(1);
        LibreOfficeProcessRunner runner = new LibreOfficeProcessRunner(properties, Arrays.asList(javaExecutable(), "-cp", System.getProperty("java.class.path"), FakeLibreOfficeMain.class.getName()));
        byte[] pdf = runner.convert(new byte[] {1}, "safe report"); String captured = new String(pdf, java.nio.charset.StandardCharsets.US_ASCII); assertTrue(captured.startsWith("%PDF-")); assertTrue(captured.contains("-env:UserInstallation=file:")); assertTrue(captured.contains("pdf paths with spaces"));
        ReportExportException failed = assertThrows(ReportExportException.class, () -> runner.convert(new byte[] {1}, "nonzero")); assertTrue(failed.isRetryable());
        ReportExportException missing = assertThrows(ReportExportException.class, () -> runner.convert(new byte[] {1}, "missing")); assertTrue(missing.isRetryable());
        ReportExportException timeout = assertThrows(ReportExportException.class, () -> runner.convert(new byte[] {1}, "timeout")); assertTrue(timeout.isRetryable());
        ReportExportException tree = assertThrows(ReportExportException.class, () -> runner.convert(new byte[] {1}, "tree")); assertTrue(tree.isRetryable());
        long child = Long.parseLong(new String(Files.readAllBytes(temp.resolve("child.pid")), java.nio.charset.StandardCharsets.US_ASCII));
        Object optional = Class.forName("java.lang.ProcessHandle").getMethod("of", long.class).invoke(null, Long.valueOf(child));
        Object handle = optional.getClass().getMethod("orElse", Object.class).invoke(optional, new Object[] {null});
        assertTrue(handle == null || !((Boolean) Class.forName("java.lang.ProcessHandle").getMethod("isAlive").invoke(handle)).booleanValue());
        Files.deleteIfExists(temp.resolve("child.pid"));
        try (java.util.stream.Stream<Path> entries = Files.list(temp)) { java.util.List<Path> remaining = entries.collect(java.util.stream.Collectors.toList()); assertTrue(remaining.isEmpty(), String.valueOf(remaining)); }
    }

    @Test
    void cleanupFailureAfterSuccessfulConversionIsTypedAndDoesNotReturnThePdf() throws Exception {
        Path temp = Files.createTempDirectory("pdf cleanup seam "); LabProperties properties = new LabProperties(); properties.setTempDirectory(temp.toString());
        LibreOfficeProcessRunner runner = new LibreOfficeProcessRunner(properties, Arrays.asList(javaExecutable(), "-cp", System.getProperty("java.class.path"), FakeLibreOfficeMain.class.getName()), root -> { throw new java.io.IOException("locked"); });
        ReportExportException error = assertThrows(ReportExportException.class, () -> runner.convert(new byte[] {1}, "success"));
        assertTrue(error.getMessage().contains("cleanup"));
    }

    @Test
    void realLibreOfficeSmokeWhenAnAbsoluteExecutableIsAvailable() throws Exception {
        LabProperties properties = new LabProperties(); java.nio.file.Path executable = locateOffice(properties.getLibreOfficeExecutable());
        org.junit.jupiter.api.Assumptions.assumeTrue(executable != null, "LibreOffice executable was not found from configured path or PATH"); properties.setLibreOfficeExecutable(executable.toString());
        Path temp = Files.createTempDirectory("real libreoffice smoke "); properties.setTempDirectory(temp.toString());
        com.ailab.system.report.model.ReportData report = new com.ailab.system.report.model.ReportData(new com.ailab.system.report.model.ReportContext("2026-08", "实验室", 1L, java.time.Instant.EPOCH, java.util.Collections.<String,Object>emptyMap()), "t", 1, java.util.Collections.<com.ailab.system.report.model.ReportSectionData>emptyList(), java.util.Collections.<String,Object>emptyMap());
        byte[] pdf = new LibreOfficeProcessRunner(properties).convert(new com.ailab.system.report.exporter.WordReportExporter().export(report), "smoke");
        assertTrue(pdf.length > 5 && pdf[0] == '%');
    }
    private String javaExecutable() { String executable = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win") ? "java.exe" : "java"; return java.nio.file.Paths.get(System.getProperty("java.home"), "bin", executable).toString(); }
    private java.nio.file.Path locateOffice(String configured) { java.nio.file.Path direct = java.nio.file.Paths.get(configured); if (direct.isAbsolute() && Files.isExecutable(direct)) return direct; String[] candidates = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win") ? new String[] {configured, "soffice.exe", "soffice"} : new String[] {configured, "soffice"}; for (String folder : System.getenv("PATH").split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) for (String name : candidates) { java.nio.file.Path candidate = java.nio.file.Paths.get(folder, name); if (Files.isExecutable(candidate)) return candidate; } return null; }
}
