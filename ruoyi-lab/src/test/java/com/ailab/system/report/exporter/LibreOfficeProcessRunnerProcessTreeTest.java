package com.ailab.system.report.exporter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import java.util.concurrent.atomic.AtomicInteger;
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
                        try { ProcessTestSupport.awaitPid(temp.resolve("root.pid"), 10L); }
                        catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                        throw new ReportExportException("snapshot failed", true);
                    }
                    @Override public void terminate(Process process,
                            List<LibreOfficeProcessRunner.ProcessIdentity> tracked) {
                        throw new AssertionError("tracked termination must not run");
                    }
                    @Override public void reclaimUnknown(Process process) throws ReportExportException {
                        process.destroy();
                        try {
                            if (!process.waitFor(2L, TimeUnit.SECONDS)) process.destroyForcibly();
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new ReportExportException("reclaim interrupted", true, interrupted);
                        }
                    }
                };
        LibreOfficeProcessRunner runner = new LibreOfficeProcessRunner(properties,
                Arrays.asList(ProcessTestSupport.javaExecutable(), "-cp",
                        System.getProperty("java.class.path"), FakeLibreOfficeMain.class.getName()),
                null, failing);
        assertThrows(ReportExportException.class, () -> runner.convert(new byte[] {1}, "snapshotfail"));
        long pid = ProcessTestSupport.awaitPid(temp.resolve("root.pid"), 10L);
        ProcessTestSupport.awaitDead(pid, 10L);
        assertTrue(!ProcessTestSupport.isAlive(pid));
    }

    @Test
    void reusedPidWithDifferentStartTimeIsNeitherKilledNorReportedAsASurvivor() throws Exception {
        LibreOfficeProcessRunner.ProcessIdentity original = identity(41, 100L, 1, "token");
        LibreOfficeProcessRunner.ProcessIdentity reused = identity(41, 200L, 1, "unrelated");
        ScriptedInventory inventory = new ScriptedInventory(
                empty(), map(reused), map(reused), map(reused), map(reused));
        RecordingLeaseProvider terminator = new RecordingLeaseProvider();
        LibreOfficeProcessRunner.OshiProcessTreeSession session = session("token", inventory, terminator,
                new ManualClock());

        session.terminate(DEAD_PROCESS, Collections.singletonList(original));

        assertEquals(0, terminator.terminated.size());
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
                new RecordingLeaseProvider(), new ManualClock());

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
                new RecordingLeaseProvider(), new ManualClock());

        List<LibreOfficeProcessRunner.ProcessIdentity> found = session.snapshot(DEAD_PROCESS);

        assertEquals(Arrays.asList(root, child, grandchild), found);
    }

    @Test
    void snapshotRetriesAnEmptyCommandLineAndFindsTheSameIdentityOnTheNextSnapshot() throws Exception {
        LibreOfficeProcessRunner.ProcessIdentity root = identity(13, 130L, 1, "");
        CommandLineInventory inventory = new CommandLineInventory(root, "", "office retry-token");
        LibreOfficeProcessRunner.OshiProcessTreeSession session = session("retry-token", inventory,
                new RecordingLeaseProvider(), new ManualClock());

        assertTrue(session.snapshot(DEAD_PROCESS).isEmpty());
        assertEquals(Collections.singletonList(root), session.snapshot(DEAD_PROCESS));
    }

    @Test
    void snapshotFailsClosedAfterThreeEmptyCommandLineAttempts() throws Exception {
        LibreOfficeProcessRunner.ProcessIdentity root = identity(14, 140L, 1, "");
        CommandLineInventory inventory = new CommandLineInventory(root, "", "", "");
        LibreOfficeProcessRunner.OshiProcessTreeSession session = session("never-visible-token", inventory,
                new RecordingLeaseProvider(), new ManualClock());

        assertTrue(session.snapshot(DEAD_PROCESS).isEmpty());
        assertTrue(session.snapshot(DEAD_PROCESS).isEmpty());
        ReportExportException failure = assertThrows(ReportExportException.class,
                () -> session.snapshot(DEAD_PROCESS));

        assertTrue(failure.isRetryable());
        assertTrue(failure.getMessage().contains("pid=14@140"));
        assertTrue(failure.getMessage().contains("3"));
    }

    @Test
    void snapshotRetriesATemporaryCommandLineReadFailureAndCanThenMatch() throws Exception {
        LibreOfficeProcessRunner.ProcessIdentity root = identity(15, 150L, 1, "");
        ReportExportException temporary = new ReportExportException("WMI temporarily unavailable", true);
        CommandLineInventory inventory = new CommandLineInventory(root, temporary, "office exception-token");
        LibreOfficeProcessRunner.OshiProcessTreeSession session = session("exception-token", inventory,
                new RecordingLeaseProvider(), new ManualClock());

        assertTrue(session.snapshot(DEAD_PROCESS).isEmpty());
        assertEquals(Collections.singletonList(root), session.snapshot(DEAD_PROCESS));
    }

    @Test
    void snapshotFailsClosedWhenAnUnresolvedCommandLineOutlivesTheStartupWindow() throws Exception {
        LibreOfficeProcessRunner.ProcessIdentity root = identity(16, 160L, 1, "");
        CommandLineInventory inventory = new CommandLineInventory(root, "", "office window-token");
        ManualClock clock = new ManualClock();
        LibreOfficeProcessRunner.OshiProcessTreeSession session = session("window-token", inventory,
                new RecordingLeaseProvider(), clock);
        assertTrue(session.snapshot(DEAD_PROCESS).isEmpty());

        clock.sleep(6000L);
        ReportExportException failure = assertThrows(ReportExportException.class,
                () -> session.snapshot(DEAD_PROCESS));

        assertTrue(failure.isRetryable());
        assertTrue(failure.getMessage().contains("startup window"));
    }

    @Test
    void identityAppearingOnlyInFinalVerificationIsKilledBeforeTypedSurvivorFailure() throws Exception {
        LibreOfficeProcessRunner.ProcessIdentity late = identity(77, 700L, 1, "late-token");
        ScriptedInventory inventory = new ScriptedInventory(empty(), empty(), map(late));
        RecordingLeaseProvider terminator = new RecordingLeaseProvider();
        LibreOfficeProcessRunner.OshiProcessTreeSession session = session("late-token", inventory,
                terminator, new DeadlineClock());

        ReportExportException failure = assertThrows(ReportExportException.class,
                () -> session.terminate(DEAD_PROCESS,
                        Collections.<LibreOfficeProcessRunner.ProcessIdentity>emptyList()));

        assertTrue(failure.isRetryable());
        assertEquals(Collections.singletonList(late), terminator.terminated);
    }

    @Test
    void sharedControllerKeepsSessionsIndependentAfterBothOpenBeforeEitherSnapshots() throws Exception {
        final LibreOfficeProcessRunner.ProcessIdentity a = identity(101, 1000L, 1, "token-a");
        final LibreOfficeProcessRunner.ProcessIdentity b = identity(202, 2000L, 1, "token-b");
        LibreOfficeProcessRunner.ProcessInventoryFactory factory = token ->
                new ScriptedInventory(empty(), map("token-a".equals(token) ? a : b));
        LibreOfficeProcessRunner.OshiProcessTreeController controller =
                new LibreOfficeProcessRunner.OshiProcessTreeController(factory,
                        new RecordingLeaseProvider(), new ManualClock());
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
    void stableLeaseTerminatesTheObservedExecutionAfterItsPidIsReused() throws Exception {
        LibreOfficeProcessRunner.ProcessIdentity original =
                identity(301, 3000L, 9, "office stable-token", "soffice");
        LibreOfficeProcessRunner.ProcessIdentity replacement =
                identity(301, 4000L, 9, "unrelated", "soffice");
        ScriptedInventory inventory = new ScriptedInventory(empty(), map(original), map(replacement));
        RecordingLeaseProvider leases = new RecordingLeaseProvider();
        LibreOfficeProcessRunner.OshiProcessTreeSession session = new LibreOfficeProcessRunner.OshiProcessTreeSession(
                "stable-token", inventory, leases, new ManualClock(), "soffice", 9);

        session.bindRoot(DEAD_PROCESS, "stable-token");
        session.terminate(DEAD_PROCESS, Collections.singletonList(original));

        assertEquals(Collections.singletonList(original), leases.terminated);
        assertTrue(!leases.acquired.contains(replacement), "replacement execution must never be leased");
    }

    @Test
    void unrelatedNewExecutableWithUnreadableCommandLineDoesNotBlockRootBinding() throws Exception {
        LibreOfficeProcessRunner.ProcessIdentity unrelated =
                identity(401, 4100L, 9, "", "other-program");
        LibreOfficeProcessRunner.ProcessIdentity root =
                identity(402, 4200L, 9, "office root-token", "soffice");
        final ScriptedInventory delegate = new ScriptedInventory(empty(), map(unrelated, root));
        LibreOfficeProcessRunner.ProcessInventory inventory = new LibreOfficeProcessRunner.ProcessInventory() {
            @Override public Map<Integer, LibreOfficeProcessRunner.ProcessIdentity> snapshot()
                    throws ReportExportException { return delegate.snapshot(); }
            @Override public LibreOfficeProcessRunner.ProcessIdentity current(int pid)
                    throws ReportExportException { return delegate.current(pid); }
            @Override public String commandLine(LibreOfficeProcessRunner.ProcessIdentity identity)
                    throws ReportExportException {
                if (identity.equals(unrelated))
                    throw new ReportExportException("unrelated command line is inaccessible", true);
                return identity.commandLine;
            }
        };
        LibreOfficeProcessRunner.OshiProcessTreeSession session = new LibreOfficeProcessRunner.OshiProcessTreeSession(
                "root-token", inventory, new RecordingLeaseProvider(), new ManualClock(), "soffice", 9);

        assertEquals(Collections.singletonList(root), session.bindRoot(DEAD_PROCESS, "root-token"));
    }

    @Test
    void terminationUsesStableLeasesFromGrandchildToRoot() throws Exception {
        LibreOfficeProcessRunner.ProcessIdentity root = identity(501, 5000L, 9, "office order-token");
        LibreOfficeProcessRunner.ProcessIdentity child = identity(502, 5100L, 501, "helper");
        LibreOfficeProcessRunner.ProcessIdentity grandchild = identity(503, 5200L, 502, "worker");
        ScriptedInventory inventory = new ScriptedInventory(empty(), map(root, child, grandchild));
        RecordingLeaseProvider leases = new RecordingLeaseProvider();
        LibreOfficeProcessRunner.OshiProcessTreeSession session = new LibreOfficeProcessRunner.OshiProcessTreeSession(
                "order-token", inventory, leases, new ManualClock(), "soffice", 9);
        List<LibreOfficeProcessRunner.ProcessIdentity> tracked =
                session.bindRoot(DEAD_PROCESS, "order-token");
        tracked.addAll(session.snapshot(DEAD_PROCESS));

        session.terminate(DEAD_PROCESS, tracked);

        assertEquals(Arrays.asList(grandchild, child, root), leases.terminated);
    }

    @Test
    void ppidDescendantsNeverRequireCommandLineInspection() throws Exception {
        LibreOfficeProcessRunner.ProcessIdentity root = identity(551, 5500L, 9, "office ppid-token");
        LibreOfficeProcessRunner.ProcessIdentity child = identity(552, 5600L, 551, "");
        LibreOfficeProcessRunner.ProcessIdentity grandchild = identity(553, 5700L, 552, "");
        final Map<Integer, LibreOfficeProcessRunner.ProcessIdentity> processes = map(root, child, grandchild);
        final AtomicInteger descendantQueries = new AtomicInteger();
        LibreOfficeProcessRunner.ProcessInventory inventory = new LibreOfficeProcessRunner.ProcessInventory() {
            private boolean baseline = true;
            @Override public Map<Integer, LibreOfficeProcessRunner.ProcessIdentity> snapshot() {
                if (baseline) { baseline = false; return empty(); }
                return processes;
            }
            @Override public LibreOfficeProcessRunner.ProcessIdentity current(int pid) {
                return processes.get(pid);
            }
            @Override public String commandLine(LibreOfficeProcessRunner.ProcessIdentity identity)
                    throws ReportExportException {
                if (identity.equals(root)) return identity.commandLine;
                descendantQueries.incrementAndGet();
                throw new ReportExportException("descendant command line denied", true);
            }
        };
        ManualClock clock = new ManualClock();
        LibreOfficeProcessRunner.OshiProcessTreeSession session = new LibreOfficeProcessRunner.OshiProcessTreeSession(
                "ppid-token", inventory, new RecordingLeaseProvider(), clock, "soffice", 9);
        session.bindRoot(DEAD_PROCESS, "ppid-token");
        clock.sleep(300L);

        assertEquals(Arrays.asList(root, child, grandchild), session.snapshot(DEAD_PROCESS));
        clock.sleep(300L);
        assertEquals(Arrays.asList(root, child, grandchild), session.snapshot(DEAD_PROCESS));
        assertEquals(0, descendantQueries.get());
    }

    @Test
    void baselineTokenCollisionCannotReplaceTheNewRootStartIdentity() throws Exception {
        LibreOfficeProcessRunner.ProcessIdentity old =
                identity(601, 6000L, 9, "office collision-token");
        LibreOfficeProcessRunner.ProcessIdentity root =
                identity(601, 7000L, 9, "office collision-token");
        ScriptedInventory inventory = new ScriptedInventory(map(old), map(root));
        RecordingLeaseProvider leases = new RecordingLeaseProvider();
        LibreOfficeProcessRunner.OshiProcessTreeSession session = new LibreOfficeProcessRunner.OshiProcessTreeSession(
                "collision-token", inventory, leases, new ManualClock(), "soffice", 9);

        assertEquals(Collections.singletonList(root), session.bindRoot(DEAD_PROCESS, "collision-token"));
        assertEquals(Collections.singletonList(root), leases.acquired);
    }

    @Test
    void windowsNativeLeaseValidatesAndTerminatesTheExactOpenedHandle() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win"));
        Path temp = Files.createTempDirectory("windows native lease ");
        Path pidFile = temp.resolve("child.pid");
        Process process = new ProcessBuilder(ProcessTestSupport.javaExecutable(), "-cp",
                System.getProperty("java.class.path"), FakeLibreOfficeMain.class.getName(),
                "pid-sleeper", pidFile.toString()).start();
        LibreOfficeProcessRunner.ProcessLease lease = null;
        try {
            int pid = (int) ProcessTestSupport.awaitPid(pidFile, 10L);
            LibreOfficeProcessRunner.OshiProcessInventory inventory =
                    new LibreOfficeProcessRunner.OshiProcessInventory();
            LibreOfficeProcessRunner.ProcessIdentity identity = inventory.current(pid);
            assertNotNull(identity);
            LibreOfficeProcessRunner.ProcessIdentity bulkIdentity = inventory.snapshot().get(pid);
            assertNotNull(bulkIdentity);
            assertEquals(ProcessTestSupport.currentPid(), bulkIdentity.parentPid);
            assertTrue(bulkIdentity.executable.toLowerCase(java.util.Locale.ROOT).contains("java"),
                    "bulk inventory executable was " + bulkIdentity.executable);
            assertTrue(inventory.commandLine(bulkIdentity).contains(pidFile.toString()));
            lease = new LibreOfficeProcessRunner.WindowsProcessLeaseProvider().acquire(identity, inventory);
            assertNotNull(lease, "OSHI and GetProcessTimes creation times must identify the same execution");
            assertTrue(lease.isAlive());
            lease.terminate();
            ProcessTestSupport.awaitDead(pid, 10L);
            assertTrue(!lease.isAlive());
        } finally {
            if (lease != null) lease.close();
            if (process.isAlive()) process.destroyForcibly();
            process.waitFor(5L, TimeUnit.SECONDS);
            Files.deleteIfExists(pidFile);
            Files.deleteIfExists(temp);
        }
    }

    @Test
    void terminationFailureStillRunsCleanupAndSuppressesItsFailure() throws Exception {
        Path temp = Files.createTempDirectory("termination cleanup ");
        LabProperties properties = new LabProperties();
        properties.setTempDirectory(temp.toString());
        AtomicInteger cleanups = new AtomicInteger();
        LibreOfficeProcessRunner.ProcessTreeController controller = token ->
                new LibreOfficeProcessRunner.ProcessTreeSession() {
                    @Override public List<LibreOfficeProcessRunner.ProcessIdentity> snapshot(Process process) {
                        return new ArrayList<LibreOfficeProcessRunner.ProcessIdentity>();
                    }
                    @Override public void terminate(Process process,
                            List<LibreOfficeProcessRunner.ProcessIdentity> tracked)
                            throws ReportExportException {
                        throw new ReportExportException("native termination failed", true);
                    }
                };
        LibreOfficeProcessRunner runner = new LibreOfficeProcessRunner(properties,
                Arrays.asList(ProcessTestSupport.javaExecutable(), "-cp",
                        System.getProperty("java.class.path"), FakeLibreOfficeMain.class.getName()), root -> {
                    cleanups.incrementAndGet();
                    if (Files.exists(root)) deleteTree(root);
                    throw new java.io.IOException("cleanup locked");
                }, controller);

        ReportExportException error = assertThrows(ReportExportException.class,
                () -> runner.convert(new byte[] {1}, "success"));

        assertTrue(error.getMessage().contains("termination failed"));
        assertTrue(cleanups.get() >= 1, "cleanup must run even after termination failure");
        assertTrue(error.getSuppressed().length == 1);
        assertTrue(error.getSuppressed()[0].getMessage().contains("cleanup"));
        try (java.util.stream.Stream<Path> entries = Files.list(temp)) {
            assertTrue(!entries.findAny().isPresent());
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
                    java.io.IOException failure) throws java.io.IOException {
                if (failure != null) throw failure;
                Files.delete(directory);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    private LibreOfficeProcessRunner.OshiProcessTreeSession session(String token,
            LibreOfficeProcessRunner.ProcessInventory inventory,
            LibreOfficeProcessRunner.ProcessLeaseProvider leases,
            LibreOfficeProcessRunner.ProcessClock clock) throws ReportExportException {
        return new LibreOfficeProcessRunner.OshiProcessTreeSession(
                token, inventory, leases, clock, "", 0);
    }

    private LibreOfficeProcessRunner.ProcessIdentity identity(int pid, long start, int parent, String command) {
        return identity(pid, start, parent, command, "soffice");
    }

    private LibreOfficeProcessRunner.ProcessIdentity identity(int pid, long start, int parent,
            String command, String executable) {
        return new LibreOfficeProcessRunner.ProcessIdentity(pid, start, parent, command, executable);
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

    private static final class CommandLineInventory implements LibreOfficeProcessRunner.ProcessInventory {
        private final LibreOfficeProcessRunner.ProcessIdentity identity;
        private final Queue<Object> commandLines = new ArrayDeque<Object>();
        private boolean baseline = true;

        CommandLineInventory(LibreOfficeProcessRunner.ProcessIdentity identity, Object... commandLines) {
            this.identity = identity;
            this.commandLines.addAll(Arrays.asList(commandLines));
        }

        @Override public Map<Integer, LibreOfficeProcessRunner.ProcessIdentity> snapshot() {
            if (baseline) {
                baseline = false;
                return empty();
            }
            return map(identity);
        }

        @Override public LibreOfficeProcessRunner.ProcessIdentity current(int pid) {
            return pid == identity.pid ? identity : null;
        }

        @Override public String commandLine(LibreOfficeProcessRunner.ProcessIdentity ignored)
                throws ReportExportException {
            Object result = commandLines.isEmpty() ? "" : commandLines.remove();
            if (result instanceof ReportExportException) throw (ReportExportException) result;
            return (String) result;
        }
    }

    private static final class RecordingLeaseProvider
            implements LibreOfficeProcessRunner.ProcessLeaseProvider {
        final List<LibreOfficeProcessRunner.ProcessIdentity> acquired =
                new ArrayList<LibreOfficeProcessRunner.ProcessIdentity>();
        final List<LibreOfficeProcessRunner.ProcessIdentity> terminated =
                new ArrayList<LibreOfficeProcessRunner.ProcessIdentity>();

        @Override public void verifySupported() { }

        @Override public LibreOfficeProcessRunner.ProcessLease acquire(
                LibreOfficeProcessRunner.ProcessIdentity identity,
                LibreOfficeProcessRunner.ProcessInventory inventory) throws ReportExportException {
            LibreOfficeProcessRunner.ProcessIdentity current = inventory.current(identity.pid);
            if (!identity.sameExecution(current)) return null;
            acquired.add(identity);
            return new LibreOfficeProcessRunner.ProcessLease() {
                private boolean alive = true;
                @Override public LibreOfficeProcessRunner.ProcessIdentity identity() { return identity; }
                @Override public boolean isAlive() { return alive; }
                @Override public void terminate() {
                    if (alive) terminated.add(identity);
                    alive = false;
                }
                @Override public void close() { }
            };
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
