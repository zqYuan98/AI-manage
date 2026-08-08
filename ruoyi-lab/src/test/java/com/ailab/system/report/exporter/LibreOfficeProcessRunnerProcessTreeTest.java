package com.ailab.system.report.exporter;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailab.system.config.LabProperties;
import com.ailab.system.report.FakeLibreOfficeMain;
import com.ailab.system.report.ProcessTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class LibreOfficeProcessRunnerProcessTreeTest {
    @Test
    void firstSnapshotFailureStillReclaimsTheStartedRootProcess() throws Exception {
        Path temp = Files.createTempDirectory("snapshot fail "); LabProperties properties = new LabProperties(); properties.setTempDirectory(temp.toString());
        LibreOfficeProcessRunner.ProcessTreeController failing = token -> new LibreOfficeProcessRunner.ProcessTreeSession() {
            @Override public List<Object> snapshot(Process process) throws ReportExportException { try { ProcessTestSupport.awaitFile(temp.resolve("root.pid"), 10L); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); } throw new ReportExportException("snapshot failed", true); }
            @Override public void terminate(Process process, List<Object> tracked) { throw new AssertionError("tracked termination must not run"); }
        };
        LibreOfficeProcessRunner runner = new LibreOfficeProcessRunner(properties, Arrays.asList(ProcessTestSupport.javaExecutable(), "-cp", System.getProperty("java.class.path"), FakeLibreOfficeMain.class.getName()), null, failing);
        assertThrows(ReportExportException.class, () -> runner.convert(new byte[] {1}, "snapshotfail"));
        long pid = ProcessTestSupport.readPid(temp.resolve("root.pid"));
        ProcessTestSupport.awaitDead(pid, 10L);
        assertTrue(!ProcessTestSupport.isAlive(pid));
    }
}
