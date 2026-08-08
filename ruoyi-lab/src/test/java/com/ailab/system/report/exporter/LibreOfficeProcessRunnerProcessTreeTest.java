package com.ailab.system.report.exporter;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailab.system.config.LabProperties;
import com.ailab.system.report.FakeLibreOfficeMain;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class LibreOfficeProcessRunnerProcessTreeTest {
    @Test
    void firstSnapshotFailureStillReclaimsTheStartedRootProcess() throws Exception {
        Path temp = Files.createTempDirectory("snapshot fail "); LabProperties properties = new LabProperties(); properties.setTempDirectory(temp.toString());
        LibreOfficeProcessRunner.ProcessTreeController failing = new LibreOfficeProcessRunner.ProcessTreeController() {
            @Override public void prepare(String token) { }
            @Override public List<Object> snapshot(Process process) throws ReportExportException { try { Thread.sleep(200L); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); } throw new ReportExportException("snapshot failed", true); }
            @Override public void terminate(Process process, List<Object> tracked) { throw new AssertionError("tracked termination must not run"); }
        };
        LibreOfficeProcessRunner runner = new LibreOfficeProcessRunner(properties, Arrays.asList(javaExecutable(), "-cp", System.getProperty("java.class.path"), FakeLibreOfficeMain.class.getName()), null, failing);
        assertThrows(ReportExportException.class, () -> runner.convert(new byte[] {1}, "snapshotfail"));
        long pid = Long.parseLong(new String(Files.readAllBytes(temp.resolve("root.pid")), StandardCharsets.US_ASCII));
        Object optional = Class.forName("java.lang.ProcessHandle").getMethod("of", long.class).invoke(null, Long.valueOf(pid)); Object handle = optional.getClass().getMethod("orElse", Object.class).invoke(optional, new Object[] {null});
        assertTrue(handle == null || !((Boolean) Class.forName("java.lang.ProcessHandle").getMethod("isAlive").invoke(handle)).booleanValue());
    }
    private String javaExecutable() { return Paths.get(System.getProperty("java.home"), "bin", System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win") ? "java.exe" : "java").toString(); }
}
