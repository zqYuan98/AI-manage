package com.ailab.system.report.exporter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailab.system.config.LabProperties;
import com.ailab.system.report.FakeLibreOfficeMain;
import com.ailab.system.report.ProcessTestSupport;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class LibreOfficeProcessRunnerProcessTreeTest {
    @Test
    void firstSnapshotFailureStillReclaimsTheStartedRootProcess() throws Exception {
        Path temp = Files.createTempDirectory("snapshot fail ");
        LabProperties properties = new LabProperties();
        properties.setTempDirectory(temp.toString());
        LibreOfficeProcessRunner.ProcessTreeController failing = token ->
                new LibreOfficeProcessRunner.ProcessTreeSession() {
                    @Override public List<LibreOfficeProcessRunner.ProcessIdentity> snapshot(Process process)
                            throws ReportExportException {
                        try { ProcessTestSupport.awaitFile(temp.resolve("root.pid"), 10L); }
                        catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                        throw new ReportExportException("snapshot failed", true);
                    }
                    @Override public void terminate(Process process,
                            List<LibreOfficeProcessRunner.ProcessIdentity> tracked) {
                        throw new AssertionError("tracked termination must not run");
                    }
                };
        LibreOfficeProcessRunner runner = new LibreOfficeProcessRunner(properties,
                Arrays.asList(ProcessTestSupport.javaExecutable(), "-cp",
                        System.getProperty("java.class.path"), FakeLibreOfficeMain.class.getName()),
                null, failing);
        assertThrows(ReportExportException.class, () -> runner.convert(new byte[] {1}, "snapshotfail"));
        long pid = ProcessTestSupport.readPid(temp.resolve("root.pid"));
        ProcessTestSupport.awaitDead(pid, 10L);
        assertTrue(!ProcessTestSupport.isAlive(pid));
    }

    @Test
    void reusedPidWithDifferentStartTimeIsNeitherKilledNorReportedAsASurvivor() throws Exception {
        LibreOfficeProcessRunner.ProcessIdentity original = identity(41, 100L, 1, "token");
        LibreOfficeProcessRunner.ProcessIdentity reused = identity(41, 200L, 1, "unrelated");
        ScriptedInventory inventory = new ScriptedInventory(
                empty(), map(reused), map(reused), map(reused), map(reused));
        RecordingTerminator terminator = new RecordingTerminator();
        LibreOfficeProcessRunner.OshiProcessTreeSession session = session("token", inventory, terminator,
                new ManualClock());

        session.terminate(DEAD_PROCESS, Collections.singletonList(original));

        assertEquals(0, terminator.killed.size());
    }

    @Test
    void identityRefreshFailureStopsTerminationWithTypedFailClosedError() throws Exception {
        final LibreOfficeProcessRunner.ProcessIdentity original = identity(42, 100L, 1, "token");
        LibreOfficeProcessRunner.ProcessInventory inventory = new LibreOfficeProcessRunner.ProcessInventory() {
            @Override public Map<Integer, LibreOfficeProcessRunner.ProcessIdentity> snapshot() {
                return empty();
            }
            @Override public LibreOfficeProcessRunner.ProcessIdentity current(int pid)
                    throws ReportExportException {
                throw new ReportExportException("identity unavailable", true);
            }
            @Override public String commandLine(LibreOfficeProcessRunner.ProcessIdentity identity) {
                return identity.commandLine;
            }
        };
        LibreOfficeProcessRunner.OshiProcessTreeSession session = session("token", inventory,
                new RecordingTerminator(), new ManualClock());

        ReportExportException error = assertThrows(ReportExportException.class,
                () -> session.terminate(DEAD_PROCESS, Collections.singletonList(original)));

        assertTrue(error.isRetryable());
        assertTrue(error.getMessage().contains("identity unavailable"));
    }

    @Test
    void snapshotBuildsPpidClosureWhenOnlyRootContainsTheToken() throws Exception {
        LibreOfficeProcessRunner.ProcessIdentity root = identity(10, 100L, 1, "office unique-token");
        LibreOfficeProcessRunner.ProcessIdentity child = identity(11, 110L, 10, "helper");
        LibreOfficeProcessRunner.ProcessIdentity grandchild = identity(12, 120L, 11, "worker");
        ScriptedInventory inventory = new ScriptedInventory(empty(), map(root, child, grandchild));
        LibreOfficeProcessRunner.OshiProcessTreeSession session = session("unique-token", inventory,
                new RecordingTerminator(), new ManualClock());

        List<LibreOfficeProcessRunner.ProcessIdentity> found = session.snapshot(DEAD_PROCESS);

        assertEquals(Arrays.asList(root, child, grandchild), found);
    }

    @Test
    void identityAppearingOnlyInFinalVerificationIsKilledBeforeTypedSurvivorFailure() throws Exception {
        LibreOfficeProcessRunner.ProcessIdentity late = identity(77, 700L, 1, "late-token");
        ScriptedInventory inventory = new ScriptedInventory(empty(), empty(), map(late));
        RecordingTerminator terminator = new RecordingTerminator();
        LibreOfficeProcessRunner.OshiProcessTreeSession session = session("late-token", inventory,
                terminator, new DeadlineClock());

        ReportExportException failure = assertThrows(ReportExportException.class,
                () -> session.terminate(DEAD_PROCESS,
                        Collections.<LibreOfficeProcessRunner.ProcessIdentity>emptyList()));

        assertTrue(failure.isRetryable());
        assertEquals(Collections.singletonList(late), terminator.killed);
    }

    @Test
    void sharedControllerKeepsSessionsIndependentAfterBothOpenBeforeEitherSnapshots() throws Exception {
        final LibreOfficeProcessRunner.ProcessIdentity a = identity(101, 1000L, 1, "token-a");
        final LibreOfficeProcessRunner.ProcessIdentity b = identity(202, 2000L, 1, "token-b");
        LibreOfficeProcessRunner.ProcessInventoryFactory factory = token ->
                new ScriptedInventory(empty(), map("token-a".equals(token) ? a : b));
        LibreOfficeProcessRunner.OshiProcessTreeController controller =
                new LibreOfficeProcessRunner.OshiProcessTreeController(factory,
                        new RecordingTerminator(), new ManualClock());
        CountDownLatch opened = new CountDownLatch(2);
        CountDownLatch snapshot = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<List<LibreOfficeProcessRunner.ProcessIdentity>> first = pool.submit(() -> {
                LibreOfficeProcessRunner.ProcessTreeSession session = controller.open("token-a");
                opened.countDown(); snapshot.await(5L, TimeUnit.SECONDS);
                return session.snapshot(DEAD_PROCESS);
            });
            Future<List<LibreOfficeProcessRunner.ProcessIdentity>> second = pool.submit(() -> {
                LibreOfficeProcessRunner.ProcessTreeSession session = controller.open("token-b");
                opened.countDown(); snapshot.await(5L, TimeUnit.SECONDS);
                return session.snapshot(DEAD_PROCESS);
            });
            assertTrue(opened.await(5L, TimeUnit.SECONDS));
            snapshot.countDown();
            assertEquals(Collections.singletonList(a), first.get(5L, TimeUnit.SECONDS));
            assertEquals(Collections.singletonList(b), second.get(5L, TimeUnit.SECONDS));
        } finally {
            snapshot.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void commandTerminatorDrainsLargeStdoutAndStderrConcurrently() throws Exception {
        LibreOfficeProcessRunner.KillCommandFactory commands = ignored -> Arrays.asList(
                ProcessTestSupport.javaExecutable(), "-cp", System.getProperty("java.class.path"),
                FakeLibreOfficeMain.class.getName(), "terminator-output");
        LibreOfficeProcessRunner.CommandProcessTerminator terminator =
                new LibreOfficeProcessRunner.CommandProcessTerminator(commands);

        terminator.terminate(identity(123, 1230L, 1, "token"));
    }

    private LibreOfficeProcessRunner.OshiProcessTreeSession session(String token,
            LibreOfficeProcessRunner.ProcessInventory inventory,
            LibreOfficeProcessRunner.ProcessTerminator terminator,
            LibreOfficeProcessRunner.ProcessClock clock) throws ReportExportException {
        return new LibreOfficeProcessRunner.OshiProcessTreeSession(token, inventory, terminator, clock);
    }

    private LibreOfficeProcessRunner.ProcessIdentity identity(int pid, long start, int parent, String command) {
        return new LibreOfficeProcessRunner.ProcessIdentity(pid, start, parent, command);
    }

    private static Map<Integer, LibreOfficeProcessRunner.ProcessIdentity> empty() {
        return Collections.emptyMap();
    }

    private static Map<Integer, LibreOfficeProcessRunner.ProcessIdentity> map(
            LibreOfficeProcessRunner.ProcessIdentity... identities) {
        Map<Integer, LibreOfficeProcessRunner.ProcessIdentity> result = new LinkedHashMap<Integer, LibreOfficeProcessRunner.ProcessIdentity>();
        for (LibreOfficeProcessRunner.ProcessIdentity identity : identities) result.put(identity.pid, identity);
        return result;
    }

    private static final class ScriptedInventory implements LibreOfficeProcessRunner.ProcessInventory {
        private final Queue<Map<Integer, LibreOfficeProcessRunner.ProcessIdentity>> snapshots =
                new ArrayDeque<Map<Integer, LibreOfficeProcessRunner.ProcessIdentity>>();
        private final Map<Integer, LibreOfficeProcessRunner.ProcessIdentity> current =
                new LinkedHashMap<Integer, LibreOfficeProcessRunner.ProcessIdentity>();

        @SafeVarargs ScriptedInventory(Map<Integer, LibreOfficeProcessRunner.ProcessIdentity>... values) {
            snapshots.addAll(Arrays.asList(values));
        }

        @Override public Map<Integer, LibreOfficeProcessRunner.ProcessIdentity> snapshot() {
            Map<Integer, LibreOfficeProcessRunner.ProcessIdentity> value = snapshots.isEmpty()
                    ? new LinkedHashMap<Integer, LibreOfficeProcessRunner.ProcessIdentity>(current)
                    : snapshots.remove();
            current.clear();
            current.putAll(value);
            return new LinkedHashMap<Integer, LibreOfficeProcessRunner.ProcessIdentity>(value);
        }

        @Override public LibreOfficeProcessRunner.ProcessIdentity current(int pid) {
            return current.get(pid);
        }

        @Override public String commandLine(LibreOfficeProcessRunner.ProcessIdentity identity) {
            return identity.commandLine;
        }
    }

    private static final class RecordingTerminator implements LibreOfficeProcessRunner.ProcessTerminator {
        final List<LibreOfficeProcessRunner.ProcessIdentity> killed =
                new ArrayList<LibreOfficeProcessRunner.ProcessIdentity>();
        @Override public void terminate(LibreOfficeProcessRunner.ProcessIdentity identity) {
            killed.add(identity);
        }
    }

    private static class ManualClock implements LibreOfficeProcessRunner.ProcessClock {
        long now;
        @Override public long nanoTime() { return now; }
        @Override public void sleep(long millis) { now += TimeUnit.MILLISECONDS.toNanos(millis); }
    }

    private static final class DeadlineClock extends ManualClock {
        int reads;
        @Override public long nanoTime() {
            return reads++ == 0 ? 0L : TimeUnit.SECONDS.toNanos(6L);
        }
    }

    private static final Process DEAD_PROCESS = new Process() {
        @Override public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public int waitFor() { return 0; }
        @Override public int exitValue() { return 0; }
        @Override public void destroy() { }
    };
}
