package com.ailab.system.report;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailab.system.config.LabProperties;
import com.ailab.system.report.exporter.LibreOfficeProcessRunner;
import com.ailab.system.report.exporter.PdfReportExporter;
import com.ailab.system.report.exporter.ReportExportException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
        assertArrayEquals(new byte[] {1, 2, 3}, error.getPreservedWord());
    }

    @Test
    void standardPdfExportFailurePreservesTheSuccessfulWordArtifactForPersistence() throws Exception {
        LabProperties properties = new LabProperties();
        Path temp = Files.createTempDirectory("pdf preserves word ");
        properties.setTempDirectory(temp.toString());
        properties.setLibreOfficeExecutable(temp.resolve("missing soffice").toString());
        PdfReportExporter exporter = new PdfReportExporter(properties);
        com.ailab.system.report.model.ReportData report = new com.ailab.system.report.model.ReportData(
                new com.ailab.system.report.model.ReportContext("2026-08", "lab", 1L,
                        java.time.Instant.EPOCH, java.util.Collections.<String,Object>emptyMap()),
                "t", 1, java.util.Collections.<com.ailab.system.report.model.ReportSectionData>emptyList(),
                java.util.Collections.<String,Object>emptyMap());

        ReportExportException error = assertThrows(ReportExportException.class,
                () -> exporter.export(report));
        byte[] preserved = error.getPreservedWord();

        assertTrue(preserved.length > 4 && preserved[0] == 'P' && preserved[1] == 'K');
        preserved[0] = 0;
        assertTrue(error.getPreservedWord()[0] == 'P', "preserved Word must be defensively copied");
    }

    @Test
    void runnerUsesOrderedArgumentsBoundsLogsValidatesOutputAndCleansIsolatedWork() throws Exception {
        Path temp = Files.createTempDirectory("pdf paths with spaces ");
        LabProperties properties = properties(temp, 1L);
        LibreOfficeProcessRunner runner = runner(properties);
        byte[] pdf = runner.convert(new byte[] {1}, "safe report");
        String captured = new String(pdf, StandardCharsets.US_ASCII);
        assertTrue(captured.startsWith("%PDF-"));
        assertTrue(captured.contains("-env:UserInstallation=file:"));
        assertTrue(captured.contains("pdf paths with spaces"));
        assertTrue(captured.indexOf("--headless") < captured.indexOf("--nologo")
                && captured.indexOf("--nologo") < captured.indexOf("--nodefault")
                && captured.indexOf("--nodefault") < captured.indexOf("--nofirststartwizard")
                && captured.indexOf("--nofirststartwizard") < captured.indexOf("--nolockcheck")
                && captured.indexOf("--nolockcheck") < captured.indexOf("--convert-to"));

        ReportExportException failed = assertThrows(ReportExportException.class,
                () -> runner.convert(new byte[] {1}, "nonzero"));
        assertTrue(failed.isRetryable());
        assertTrue(failed.getMessage().length() <= 600, failed.getMessage());
        assertTrue(assertThrows(ReportExportException.class,
                () -> runner.convert(new byte[] {1}, "missing")).isRetryable());
        assertTrue(assertThrows(ReportExportException.class,
                () -> runner.convert(new byte[] {1}, "invalid")).isRetryable());
        assertTrue(assertThrows(ReportExportException.class,
                () -> runner.convert(new byte[] {1}, "truncated")).isRetryable());
        assertTrue(assertThrows(ReportExportException.class,
                () -> runner.convert(new byte[] {1}, "bad-xref")).isRetryable());
        properties.setMaxUploadSizeBytes(1024L);
        assertTrue(assertThrows(ReportExportException.class,
                () -> runner.convert(new byte[] {1}, "oversize")).isRetryable());
        properties.setMaxUploadSizeBytes(50L * 1024L * 1024L);
        assertTrue(runner.convert(new byte[] {1}, "../../safe-name").length > 5);
        assertTrue(assertThrows(ReportExportException.class,
                () -> runner.convert(new byte[] {1}, "timeout")).isRetryable());
        assertEmpty(temp);
    }

    @Test
    void ppidClosureKillsDescendantsWhenOnlyTheRootContainsTheSessionToken() throws Exception {
        Path temp = Files.createTempDirectory("pdf ppid closure ");
        ReportExportException error = assertThrows(ReportExportException.class,
                () -> runner(properties(temp, 4L)).convert(new byte[] {1}, "ppid-tree"));
        assertTrue(error.isRetryable());
        assertTreeDead(temp, "ppid-tree");
        removeTreeFiles(temp, "ppid-tree");
        assertEmpty(temp);
    }

    @Test
    void rootFirstExitStillKillsTheTokenBearingOrphanAndItsDescendant() throws Exception {
        Path temp = Files.createTempDirectory("pdf orphan closure ");
        ReportExportException error = assertThrows(ReportExportException.class,
                () -> runner(properties(temp, 10L)).convert(new byte[] {1}, "orphan"));
        assertTrue(error.isRetryable());
        assertTreeDead(temp, "orphan");
        removeTreeFiles(temp, "orphan");
        assertEmpty(temp);
    }

    @Test
    void concurrentConversionsKeepInterleavedProcessTreeSessionsIsolated() throws Exception {
        Path temp = Files.createTempDirectory("pdf concurrent trees ");
        LabProperties properties = properties(temp, 4L);
        LibreOfficeProcessRunner shared = runner(properties);
        Process sentinel = fakeProcess("sleeper").start();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<byte[]> first = pool.submit(() -> shared.convert(new byte[] {1}, "barrier-a-tree"));
            Future<byte[]> second = pool.submit(() -> shared.convert(new byte[] {1}, "barrier-b-tree"));
            assertRetryableFailure(first);
            assertRetryableFailure(second);
            assertTrue(sentinel.isAlive(), "an unrelated pre-existing process must not be killed");
            assertTreeDead(temp, "barrier-a-tree");
            assertTreeDead(temp, "barrier-b-tree");
            removeTreeFiles(temp, "barrier-a-tree");
            removeTreeFiles(temp, "barrier-b-tree");
            assertEmpty(temp);
        } finally {
            pool.shutdownNow();
            sentinel.destroyForcibly();
            sentinel.waitFor(5L, TimeUnit.SECONDS);
        }
    }

    @Test
    void terminationRescansAndKillsATokenBearingProcessSpawnedAfterTheFirstKill() throws Exception {
        Path temp = Files.createTempDirectory("pdf late spawn ");
        LabProperties properties = properties(temp, 1L);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        Process broker = null;
        try {
            Future<byte[]> conversion = pool.submit(() -> runner(properties).convert(new byte[] {1}, "late-tree"));
            ProcessTestSupport.awaitPid(temp.resolve("late-tree.root.pid"), 10L);
            ProcessTestSupport.awaitFile(temp.resolve("late-tree.token"), 10L);
            broker = fakeProcess("late-broker", temp.toString(), "late-tree").start();
            assertRetryableFailure(conversion);
            assertTrue(broker.waitFor(10L, TimeUnit.SECONDS));
            assertTrue(broker.exitValue() == 0);
            long late = ProcessTestSupport.awaitPid(temp.resolve("late-tree.late.pid"), 10L);
            ProcessTestSupport.awaitDead(late, 10L);
            removeTreeFiles(temp, "late-tree");
            assertEmpty(temp);
        } finally {
            pool.shutdownNow();
            if (broker != null && broker.isAlive()) broker.destroyForcibly();
        }
    }

    @Test
    void cleanupFailureAfterSuccessfulConversionIsTypedAndDoesNotReturnThePdf() throws Exception {
        Path temp = Files.createTempDirectory("pdf cleanup seam ");
        LabProperties properties = properties(temp, 30L);
        LibreOfficeProcessRunner runner = new LibreOfficeProcessRunner(properties, fakeCommand(), root -> {
            deleteTree(root);
            throw new java.io.IOException("locked");
        });
        ReportExportException error = assertThrows(ReportExportException.class,
                () -> runner.convert(new byte[] {1}, "success"));
        assertTrue(error.getMessage().contains("cleanup"));
        assertEmpty(temp);
    }

    @Test
    void primaryConversionFailureRetainsCleanupFailureAsSuppressedContext() throws Exception {
        Path temp = Files.createTempDirectory("pdf cleanup suppressed ");
        LabProperties properties = properties(temp, 30L);
        LibreOfficeProcessRunner runner = new LibreOfficeProcessRunner(properties, fakeCommand(), root -> {
            deleteTree(root);
            throw new java.io.IOException("cleanup locked");
        });
        ReportExportException error = assertThrows(ReportExportException.class,
                () -> runner.convert(new byte[] {1}, "nonzero"));
        assertTrue(error.getSuppressed().length == 1);
        assertTrue(error.getSuppressed()[0].getMessage().contains("cleanup locked"));
        assertEmpty(temp);
    }

    @Test
    void realLibreOfficeSmokeWhenAnExecutableIsActuallyAvailable() throws Exception {
        LabProperties properties = new LabProperties();
        Path executable = locateOffice(properties.getLibreOfficeExecutable());
        org.junit.jupiter.api.Assumptions.assumeTrue(executable != null,
                "LibreOffice executable was not found from configured path or PATH");
        properties.setLibreOfficeExecutable(executable.toString());
        Path temp = Files.createTempDirectory("real libreoffice smoke ");
        properties.setTempDirectory(temp.toString());
        com.ailab.system.report.model.ReportData report = new com.ailab.system.report.model.ReportData(
                new com.ailab.system.report.model.ReportContext("2026-08", "lab", 1L,
                        java.time.Instant.EPOCH, java.util.Collections.<String,Object>emptyMap()),
                "t", 1, java.util.Collections.<com.ailab.system.report.model.ReportSectionData>emptyList(),
                java.util.Collections.<String,Object>emptyMap());
        byte[] pdf = new LibreOfficeProcessRunner(properties).convert(
                new com.ailab.system.report.exporter.WordReportExporter().export(report), "smoke");
        assertTrue(pdf.length > 5 && pdf[0] == '%');
    }

    private LabProperties properties(Path temp, long timeoutSeconds) {
        LabProperties properties = new LabProperties();
        properties.setTempDirectory(temp.toString());
        properties.setConversionTimeoutSeconds(timeoutSeconds);
        return properties;
    }

    private LibreOfficeProcessRunner runner(LabProperties properties) {
        return new LibreOfficeProcessRunner(properties, fakeCommand());
    }

    private java.util.List<String> fakeCommand() {
        return Arrays.asList(ProcessTestSupport.javaExecutable(), "-cp",
                System.getProperty("java.class.path"), FakeLibreOfficeMain.class.getName());
    }

    private ProcessBuilder fakeProcess(String... args) {
        java.util.List<String> command = new java.util.ArrayList<String>(fakeCommand());
        command.addAll(Arrays.asList(args));
        return new ProcessBuilder(command);
    }

    private void assertRetryableFailure(Future<byte[]> future) throws Exception {
        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> future.get(20L, TimeUnit.SECONDS));
        assertTrue(failure.getCause() instanceof ReportExportException);
        assertTrue(((ReportExportException) failure.getCause()).isRetryable());
    }

    private void assertTreeDead(Path base, String tag) throws Exception {
        for (String role : Arrays.asList("root", "child", "grandchild")) {
            Path file = base.resolve(tag + "." + role + ".pid");
            ProcessTestSupport.awaitDead(ProcessTestSupport.awaitPid(file, 10L), 10L);
        }
    }

    private void removeTreeFiles(Path base, String tag) throws Exception {
        for (String suffix : Arrays.asList("root.pid", "child.pid", "grandchild.pid", "late.pid", "token", "ready"))
            Files.deleteIfExists(base.resolve(tag + "." + suffix));
    }

    private void assertEmpty(Path directory) throws Exception {
        try (java.util.stream.Stream<Path> entries = Files.list(directory)) {
            assertTrue(!entries.findAny().isPresent(), "unexpected files remain in " + directory);
        }
    }

    private void deleteTree(Path root) throws java.io.IOException {
        Files.walkFileTree(root, new java.nio.file.SimpleFileVisitor<Path>() {
            @Override public java.nio.file.FileVisitResult visitFile(Path file,
                    java.nio.file.attribute.BasicFileAttributes attrs) throws java.io.IOException {
                Files.delete(file);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
            @Override public java.nio.file.FileVisitResult postVisitDirectory(Path directory,
                    java.io.IOException error) throws java.io.IOException {
                if (error != null) throw error;
                Files.delete(directory);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    private Path locateOffice(String configured) {
        Path direct = java.nio.file.Paths.get(configured);
        if (direct.isAbsolute() && Files.isExecutable(direct)) return direct;
        String[] candidates = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win")
                ? new String[] {configured, "soffice.exe", "soffice"} : new String[] {configured, "soffice"};
        String path = System.getenv("PATH");
        if (path == null) return null;
        for (String folder : path.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
            if (folder.length() == 0) continue;
            for (String name : candidates) {
                Path candidate = java.nio.file.Paths.get(folder, name);
                if (Files.isExecutable(candidate)) return candidate;
            }
        }
        return null;
    }
}
