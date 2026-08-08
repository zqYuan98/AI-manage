package com.ailab.system.report.exporter;

import com.ailab.system.config.LabProperties;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

/** Executes LibreOffice without a shell and with one disposable profile per conversion. */
public final class LibreOfficeProcessRunner {
    private static final int MAX_LOG = 64 * 1024;
    private static final int MAX_COMMAND_LINE_ATTEMPTS = 3;
    private static final long COMMAND_LINE_STARTUP_WINDOW_NANOS = TimeUnit.SECONDS.toNanos(5L);
    private static final long TERMINATION_DEADLINE_NANOS = TimeUnit.SECONDS.toNanos(5L);
    private static final long TERMINATION_GRACE_MILLIS = 200L;
    private static final int TERMINATION_QUIET_ROUNDS = 5;
    private static final long TOKEN_SCAN_MIN_AGE_MILLIS = 250L;

    private final LabProperties properties;
    private final List<String> executable;
    private final Cleanup cleanup;
    private final ProcessTreeController processes;

    public interface Cleanup {
        void clean(Path root) throws IOException;
    }

    public LibreOfficeProcessRunner(LabProperties properties) {
        this(properties, Arrays.asList(properties.getLibreOfficeExecutable()));
    }

    /** Test seam for a Java fake executable; production still passes one soffice executable argument. */
    public LibreOfficeProcessRunner(LabProperties properties, List<String> executable) {
        this(properties, executable, null);
    }

    public LibreOfficeProcessRunner(LabProperties properties, List<String> executable, Cleanup cleanup) {
        this(properties, executable, cleanup, new OshiProcessTreeController(executable));
    }

    LibreOfficeProcessRunner(LabProperties properties, List<String> executable, Cleanup cleanup,
            ProcessTreeController processes) {
        if (properties == null || executable == null || executable.isEmpty() || processes == null)
            throw new IllegalArgumentException("LibreOffice configuration is required");
        this.properties = properties;
        this.executable = new ArrayList<String>(executable);
        this.cleanup = cleanup == null ? new Cleanup() {
            @Override public void clean(Path root) throws IOException { delete(root); }
        } : cleanup;
        this.processes = processes;
    }

    public byte[] convert(byte[] word, String name) throws ReportExportException {
        if (word == null || word.length == 0)
            throw new ReportExportException("Word input is required", false);
        Path root = null;
        ExecutorService readers = null;
        Process process = null;
        ProcessTreeSession session = null;
        List<ProcessIdentity> tracked = null;
        boolean terminationDone = false;
        ReportExportException primary = null;
        try {
            Path configured = Paths.get(properties.getTempDirectory()).toAbsolutePath().normalize();
            Files.createDirectories(configured);
            root = Files.createTempDirectory(configured, "lo-").toAbsolutePath().normalize();
            requireInside(configured, root);
            Path profile = Files.createDirectory(root.resolve("profile"));
            Path input = root.resolve(safeName(name) + ".docx");
            Path out = Files.createDirectory(root.resolve("out"));
            String processToken = profile.toUri().toASCIIString();
            session = processes.open(processToken);
            Files.write(input, word, StandardOpenOption.CREATE_NEW);

            List<String> command = new ArrayList<String>(executable);
            command.add("--headless");
            command.add("--nologo");
            command.add("--nodefault");
            command.add("--nofirststartwizard");
            command.add("--nolockcheck");
            command.add("-env:UserInstallation=" + profile.toUri().toASCIIString());
            command.add("--convert-to");
            command.add("pdf");
            command.add("--outdir");
            command.add(out.toString());
            command.add(input.toString());
            try {
                process = new ProcessBuilder(command).directory(root.toFile()).start();
                try {
                    tracked = session.bindRoot(process, processToken);
                    Thread.sleep(100L);
                    tracked.addAll(session.snapshot(process));
                } catch (ReportExportException ex) {
                    try {
                        session.reclaimUnknown(process);
                        terminationDone = true;
                    }
                    catch (ReportExportException reclaimFailure) { ex.addSuppressed(reclaimFailure); }
                    throw ex;
                }
            } catch (ReportExportException ex) {
                throw ex;
            } catch (IOException ex) {
                throw new ReportExportException("LibreOffice executable is unavailable", true, ex);
            }

            final Process started = process;
            readers = Executors.newFixedThreadPool(2);
            Future<String> stdout = readers.submit(() -> bounded(started.getInputStream(), MAX_LOG));
            Future<String> stderr = readers.submit(() -> bounded(started.getErrorStream(), MAX_LOG));
            long timeout = Math.max(1L, properties.getConversionTimeoutSeconds());
            if (!process.waitFor(timeout, TimeUnit.SECONDS)) {
                session.terminate(process, tracked);
                terminationDone = true;
                throw new ReportExportException("LibreOffice conversion timed out", true);
            }
            String error = stderr.get(5L, TimeUnit.SECONDS);
            String output = stdout.get(5L, TimeUnit.SECONDS);
            if (process.exitValue() != 0)
                throw new ReportExportException("LibreOffice conversion failed: " + compact(error, output), true);
            Path pdf = out.resolve(safeName(name) + ".pdf").normalize();
            requireInside(out, pdf);
            if (!Files.isRegularFile(pdf, LinkOption.NOFOLLOW_LINKS))
                throw new ReportExportException("LibreOffice produced no PDF output", true);
            long maximum = Math.max(1024L, properties.getMaxUploadSizeBytes());
            byte[] bytes = readBoundedPdf(pdf, maximum);
            if (!pdf(bytes))
                throw new ReportExportException("LibreOffice produced an invalid PDF", true);
            return bytes;
        } catch (ReportExportException ex) {
            primary = ex;
            throw ex;
        } catch (Exception ex) {
            primary = new ReportExportException("LibreOffice conversion failed", true, ex);
            throw primary;
        } finally {
            ReportExportException finishing = primary;
            boolean restoreInterrupt = false;
            if (process != null && !terminationDone) {
                try {
                    if (session == null) emergencyTerminate(process);
                    else session.terminate(process, tracked);
                } catch (ReportExportException ex) {
                    finishing = merge(finishing, ex);
                }
            }
            finishing = closeProcessStreams(process, finishing);
            if (readers != null) {
                readers.shutdownNow();
                try {
                    if (!readers.awaitTermination(5L, TimeUnit.SECONDS))
                        finishing = merge(finishing, new ReportExportException(
                                "Process output readers did not stop", true));
                } catch (InterruptedException interrupted) {
                    restoreInterrupt = true;
                    finishing = merge(finishing, new ReportExportException(
                            "Interrupted while closing process output readers", true, interrupted));
                }
            }
            if (session != null) {
                try { session.close(); }
                catch (ReportExportException closeFailure) {
                    finishing = merge(finishing, closeFailure);
                }
            }
            if (root != null) {
                try {
                    cleanupWithRetry(root);
                } catch (IOException ex) {
                    finishing = merge(finishing, new ReportExportException(
                            "Report conversion cleanup failed; temporary data may remain: "
                                    + String.valueOf(ex.getMessage()), true, ex));
                }
            }
            if (restoreInterrupt) Thread.currentThread().interrupt();
            if (primary == null && finishing != null) throw finishing;
        }
    }

    interface ProcessTreeController {
        ProcessTreeSession open(String token) throws ReportExportException;
    }

    interface ProcessTreeSession {
        default List<ProcessIdentity> bindRoot(Process process, String token)
                throws ReportExportException { return snapshot(process); }
        List<ProcessIdentity> snapshot(Process process) throws ReportExportException;
        void terminate(Process process, List<ProcessIdentity> tracked) throws ReportExportException;
        default void reclaimUnknown(Process process) throws ReportExportException {
            terminate(process, Collections.<ProcessIdentity>emptyList());
        }
        default void close() throws ReportExportException { }
    }

    interface ProcessInventory {
        Map<Integer, ProcessIdentity> snapshot() throws ReportExportException;
        ProcessIdentity current(int pid) throws ReportExportException;
        String commandLine(ProcessIdentity identity) throws ReportExportException;
    }

    interface ProcessInventoryFactory {
        ProcessInventory create(String token) throws ReportExportException;
    }

    interface ProcessLease {
        ProcessIdentity identity();
        boolean isAlive() throws ReportExportException;
        void terminate() throws ReportExportException;
        void close() throws ReportExportException;
    }

    interface ProcessLeaseProvider {
        void verifySupported() throws ReportExportException;
        ProcessLease acquire(ProcessIdentity identity, ProcessInventory inventory)
                throws ReportExportException;
    }

    interface ProcessClock {
        long nanoTime();
        void sleep(long millis) throws InterruptedException;
    }

    /** Stable identity prevents a recycled PID from being mistaken for the process originally observed. */
    static final class ProcessIdentity {
        final int pid;
        final long startTime;
        final int parentPid;
        final String commandLine;
        final String executable;

        ProcessIdentity(int pid, long startTime, int parentPid, String commandLine) {
            this(pid, startTime, parentPid, commandLine, "");
        }

        ProcessIdentity(int pid, long startTime, int parentPid, String commandLine,
                String executable) {
            if (pid <= 0 || startTime <= 0L)
                throw new IllegalArgumentException("A positive process id and start time are required");
            this.pid = pid;
            this.startTime = startTime;
            this.parentPid = parentPid;
            this.commandLine = commandLine == null ? "" : commandLine;
            this.executable = executable == null ? "" : executable;
        }

        boolean sameExecution(ProcessIdentity other) {
            return other != null && pid == other.pid && startTime == other.startTime;
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ProcessIdentity)) return false;
            return sameExecution((ProcessIdentity) other);
        }

        @Override public int hashCode() {
            return 31 * pid + (int) (startTime ^ (startTime >>> 32));
        }

        @Override public String toString() {
            return "pid=" + pid + "@" + startTime + ",ppid=" + parentPid;
        }
    }

    /** A shared controller is stateless; every conversion receives an isolated identity session. */
    static final class OshiProcessTreeController implements ProcessTreeController {
        private final ProcessInventoryFactory inventories;
        private final ProcessLeaseProvider leases;
        private final ProcessClock clock;
        private final String expectedExecutable;
        private final int ownerPid;

        OshiProcessTreeController(List<String> command) {
            this(defaultInventories(), nativeLeases(), new SystemProcessClock(),
                    executableName(command == null || command.isEmpty() ? "" : command.get(0)),
                    currentProcessId());
        }

        OshiProcessTreeController(ProcessInventoryFactory inventories, ProcessLeaseProvider leases,
                ProcessClock clock) {
            this(inventories, leases, clock, "", 0);
        }

        OshiProcessTreeController(ProcessInventoryFactory inventories, ProcessLeaseProvider leases,
                ProcessClock clock, String expectedExecutable, int ownerPid) {
            if (inventories == null || leases == null || clock == null)
                throw new IllegalArgumentException("Process tracking collaborators are required");
            this.inventories = inventories;
            this.leases = leases;
            this.clock = clock;
            this.expectedExecutable = expectedExecutable == null ? "" : expectedExecutable;
            this.ownerPid = ownerPid;
        }

        @Override public ProcessTreeSession open(String token) throws ReportExportException {
            if (expectedExecutable.length() > 0 && ownerPid <= 0)
                throw new ReportExportException("Cannot identify the process that launches LibreOffice", true);
            leases.verifySupported();
            return new OshiProcessTreeSession(token, inventories.create(token), leases, clock,
                    expectedExecutable, ownerPid);
        }

        private static ProcessInventoryFactory defaultInventories() {
            return new ProcessInventoryFactory() {
                @Override public ProcessInventory create(String token) throws ReportExportException {
                    return new OshiProcessInventory();
                }
            };
        }
    }

    static final class OshiProcessTreeSession implements ProcessTreeSession {
        private final String token;
        private final ProcessInventory inventory;
        private final ProcessLeaseProvider leaseProvider;
        private final ProcessClock clock;
        private final String expectedExecutable;
        private final int ownerPid;
        private final Set<ProcessIdentity> baseline;
        private final Set<ProcessIdentity> roots = new LinkedHashSet<ProcessIdentity>();
        private final Set<ProcessIdentity> inspected = new HashSet<ProcessIdentity>();
        private final Map<ProcessIdentity, CommandLineAttempt> attempts =
                new LinkedHashMap<ProcessIdentity, CommandLineAttempt>();
        private final Map<ProcessIdentity, Long> firstObservedForTokenScan =
                new LinkedHashMap<ProcessIdentity, Long>();
        private final Map<ProcessIdentity, ProcessLease> leases =
                new LinkedHashMap<ProcessIdentity, ProcessLease>();

        OshiProcessTreeSession(String token, ProcessInventory inventory, ProcessLeaseProvider leaseProvider,
                ProcessClock clock, String expectedExecutable, int ownerPid) throws ReportExportException {
            if (token == null || token.length() == 0 || inventory == null || leaseProvider == null || clock == null)
                throw new IllegalArgumentException("Process-tree session configuration is required");
            this.token = token;
            this.inventory = inventory;
            this.leaseProvider = leaseProvider;
            this.clock = clock;
            this.expectedExecutable = normalizeExecutable(expectedExecutable);
            this.ownerPid = ownerPid;
            leaseProvider.verifySupported();
            this.baseline = new HashSet<ProcessIdentity>(inventory.snapshot().values());
        }

        @Override public List<ProcessIdentity> bindRoot(Process process, String expectedToken)
                throws ReportExportException {
            if (!token.equals(expectedToken))
                throw new ReportExportException("Process-tree binding token mismatch", false);
            ReportExportException lastFailure = null;
            long started = clock.nanoTime();
            long deadline = started > Long.MAX_VALUE - COMMAND_LINE_STARTUP_WINDOW_NANOS
                    ? Long.MAX_VALUE : started + COMMAND_LINE_STARTUP_WINDOW_NANOS;
            do {
                Map<Integer, ProcessIdentity> current = inventory.snapshot();
                List<ProcessIdentity> matches = new ArrayList<ProcessIdentity>();
                for (ProcessIdentity identity : current.values()) {
                    if (baseline.contains(identity) || !expected(identity)
                            || ownerPid > 0 && identity.parentPid != ownerPid) continue;
                    try {
                        if (tokenMatches(identity, true)) matches.add(identity);
                    } catch (ReportExportException failure) {
                        lastFailure = failure;
                    }
                }
                if (matches.size() > 1)
                    throw new ReportExportException("Cannot safely bind a unique LibreOffice root process", true);
                if (matches.size() == 1) {
                    ProcessIdentity root = matches.get(0);
                    if (ensureLease(root) != null) {
                        roots.add(root);
                        return new ArrayList<ProcessIdentity>(Collections.singletonList(root));
                    }
                }
                if (clock.nanoTime() < deadline) {
                    try { clock.sleep(50L); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new ReportExportException("Interrupted while binding LibreOffice root", true,
                                interrupted);
                    }
                }
            } while (clock.nanoTime() < deadline);
            if (lastFailure != null) throw lastFailure;
            throw new ReportExportException("Cannot safely bind the started LibreOffice root process", true);
        }

        @Override public List<ProcessIdentity> snapshot(Process ignored) throws ReportExportException {
            return snapshot(ignored, false);
        }

        private List<ProcessIdentity> snapshot(Process ignored, boolean forceTokenScan)
                throws ReportExportException {
            Map<Integer, ProcessIdentity> current = inventory.snapshot();
            LinkedHashSet<ProcessIdentity> found = new LinkedHashSet<ProcessIdentity>();
            addLiveRoots(found);
            expandPpidClosure(current, found);
            for (ProcessIdentity identity : current.values()) {
                if (!baseline.contains(identity) && !roots.contains(identity) && !inspected.contains(identity)
                        && !found.contains(identity) && expected(identity)
                        && (forceTokenScan || matureForTokenScan(identity))) {
                    inspectNewIdentity(identity);
                    if (forceTokenScan) {
                        while (!roots.contains(identity) && !inspected.contains(identity)
                                && attempts.containsKey(identity)) inspectNewIdentity(identity);
                    }
                }
            }
            found.clear();
            addLiveRoots(found);
            expandPpidClosure(current, found);
            return new ArrayList<ProcessIdentity>(found);
        }

        private void addLiveRoots(Set<ProcessIdentity> found) throws ReportExportException {
            for (ProcessIdentity root : roots) {
                ProcessLease lease = leases.get(root);
                if (lease != null && lease.isAlive()) found.add(root);
            }
        }

        private void expandPpidClosure(Map<Integer, ProcessIdentity> current,
                Set<ProcessIdentity> found) throws ReportExportException {
            boolean changed;
            do {
                changed = false;
                for (ProcessIdentity candidate : current.values()) {
                    if (!found.contains(candidate) && hasParent(found, candidate)
                            && ensureLease(candidate) != null) {
                        found.add(candidate);
                        changed = true;
                    }
                }
            } while (changed);
        }

        private boolean tokenMatches(ProcessIdentity identity, boolean rootBinding)
                throws ReportExportException {
            long now = clock.nanoTime();
            CommandLineAttempt attempt = attempts.get(identity);
            if (attempt == null) {
                attempt = new CommandLineAttempt(now);
                attempts.put(identity, attempt);
            } else if (outsideStartupWindow(attempt, now)) {
                throw commandLineUnavailable(identity, attempt, "startup window expired");
            }
            attempt.count++;
            String commandLine = null;
            try {
                commandLine = inventory.commandLine(identity);
                attempt.lastFailure = null;
            } catch (ReportExportException temporary) {
                attempt.lastFailure = temporary;
            }
            if (commandLine != null && commandLine.trim().length() > 0) {
                attempts.remove(identity);
                inspected.add(identity);
                return commandLine.contains(token);
            }
            if (attempt.count >= MAX_COMMAND_LINE_ATTEMPTS)
                throw commandLineUnavailable(identity, attempt,
                        rootBinding ? "root binding retry budget exhausted" : "retry budget exhausted");
            return false;
        }

        private void inspectNewIdentity(ProcessIdentity identity) throws ReportExportException {
            if (tokenMatches(identity, false)) {
                ProcessLease lease = ensureLease(identity);
                if (lease != null) roots.add(identity);
            }
        }

        private boolean expected(ProcessIdentity identity) {
            if (expectedExecutable.length() == 0) return true;
            return expectedExecutable.equals(normalizeExecutable(identity.executable));
        }

        private boolean matureForTokenScan(ProcessIdentity identity) {
            if (expectedExecutable.length() == 0) return true;
            long now = clock.nanoTime();
            Long first = firstObservedForTokenScan.get(identity);
            if (first == null) {
                firstObservedForTokenScan.put(identity, Long.valueOf(now));
                return false;
            }
            return now >= first.longValue()
                    && now - first.longValue() >= TimeUnit.MILLISECONDS.toNanos(TOKEN_SCAN_MIN_AGE_MILLIS);
        }

        private boolean outsideStartupWindow(CommandLineAttempt attempt, long now) {
            return now >= attempt.firstSeenNanos
                    && now - attempt.firstSeenNanos >= COMMAND_LINE_STARTUP_WINDOW_NANOS;
        }

        private ReportExportException commandLineUnavailable(ProcessIdentity identity,
                CommandLineAttempt attempt, String reason) {
            String message = "Cannot safely identify newly started process " + identity
                    + ": command line unavailable after " + attempt.count + " attempts; " + reason;
            return attempt.lastFailure == null
                    ? new ReportExportException(message, true)
                    : new ReportExportException(message, true, attempt.lastFailure);
        }

        private ProcessLease ensureLease(ProcessIdentity identity) throws ReportExportException {
            ProcessLease existing = leases.get(identity);
            if (existing != null) return existing;
            ProcessLease acquired = leaseProvider.acquire(identity, inventory);
            if (acquired != null) leases.put(identity, acquired);
            return acquired;
        }

        private static final class CommandLineAttempt {
            final long firstSeenNanos;
            int count;
            ReportExportException lastFailure;

            CommandLineAttempt(long firstSeenNanos) { this.firstSeenNanos = firstSeenNanos; }
        }

        @Override public void terminate(Process root, List<ProcessIdentity> tracked)
                throws ReportExportException {
            LinkedHashSet<ProcessIdentity> known = new LinkedHashSet<ProcessIdentity>();
            if (tracked != null) {
                known.addAll(tracked);
                for (ProcessIdentity identity : tracked) ensureLease(identity);
            }
            long started = clock.nanoTime();
            long deadline = started > Long.MAX_VALUE - TERMINATION_DEADLINE_NANOS
                    ? Long.MAX_VALUE : started + TERMINATION_DEADLINE_NANOS;
            int quietRounds = 0;
            try {
                while (clock.nanoTime() < deadline) {
                    boolean live = terminationRound(root, known);
                    if (!live) {
                        if (++quietRounds >= TERMINATION_QUIET_ROUNDS) return;
                    } else quietRounds = 0;
                }
                finalTerminationRound(root, known);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                if (root != null) root.destroyForcibly();
                throw new ReportExportException("Interrupted while terminating LibreOffice", true, ex);
            }
        }

        private boolean terminationRound(Process root, LinkedHashSet<ProcessIdentity> known)
                throws ReportExportException, InterruptedException {
            known.addAll(snapshot(root));
            killAll(known);
            stopRoot(root);
            clock.sleep(TERMINATION_GRACE_MILLIS);
            known.addAll(snapshot(root));
            return rootAlive(root) || !liveIdentities(known).isEmpty();
        }

        /** Deadline handling performs a full fresh kill/grace/verify and kills late discoveries once. */
        private void finalTerminationRound(Process root, LinkedHashSet<ProcessIdentity> known)
                throws ReportExportException, InterruptedException {
            known.addAll(snapshot(root, true));
            killAll(known);
            stopRoot(root);
            clock.sleep(TERMINATION_GRACE_MILLIS);
            known.addAll(snapshot(root, true));
            List<ProcessIdentity> survivors = liveIdentities(known);
            boolean rootSurvived = rootAlive(root);
            if (survivors.isEmpty() && !rootSurvived) return;
            killAll(survivors);
            stopRoot(root);
            throw new ReportExportException(
                    "LibreOffice process tree remained unstable after termination deadline: "
                            + details(survivors, rootSurvived), true);
        }

        private void killAll(Iterable<ProcessIdentity> identities) throws ReportExportException {
            List<ProcessIdentity> ordered = new ArrayList<ProcessIdentity>();
            for (ProcessIdentity identity : identities) if (!ordered.contains(identity)) ordered.add(identity);
            Collections.sort(ordered, (left, right) -> Integer.compare(depth(right, ordered), depth(left, ordered)));
            for (ProcessIdentity identity : ordered) {
                ProcessLease lease = ensureLease(identity);
                if (lease != null && lease.isAlive()) lease.terminate();
            }
        }

        private int depth(ProcessIdentity identity, List<ProcessIdentity> identities) {
            int depth = 0;
            ProcessIdentity cursor = identity;
            Set<ProcessIdentity> seen = new HashSet<ProcessIdentity>();
            while (seen.add(cursor)) {
                ProcessIdentity parent = null;
                for (ProcessIdentity candidate : identities)
                    if (candidate.pid == cursor.parentPid && cursor.startTime >= candidate.startTime) {
                        parent = candidate;
                        break;
                    }
                if (parent == null) break;
                depth++;
                cursor = parent;
            }
            return depth;
        }

        private List<ProcessIdentity> liveIdentities(Iterable<ProcessIdentity> identities)
                throws ReportExportException {
            List<ProcessIdentity> live = new ArrayList<ProcessIdentity>();
            for (ProcessIdentity identity : identities) {
                ProcessLease lease = leases.get(identity);
                if (lease != null && lease.isAlive()) live.add(identity);
            }
            return live;
        }

        private boolean hasParent(Set<ProcessIdentity> found, ProcessIdentity candidate) {
            for (ProcessIdentity possible : found)
                if (possible.pid == candidate.parentPid && candidate.startTime >= possible.startTime) return true;
            return false;
        }

        @Override public void reclaimUnknown(Process root) throws ReportExportException {
            ReportExportException failure = null;
            try { stopRoot(root); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                failure = new ReportExportException("Interrupted while reclaiming LibreOffice root", true,
                        interrupted);
            }
            try {
                terminate(root, Collections.<ProcessIdentity>emptyList());
            } catch (ReportExportException scanFailure) {
                if (failure == null) failure = scanFailure;
                else failure.addSuppressed(scanFailure);
            }
            if (failure != null) throw failure;
        }

        @Override public void close() throws ReportExportException {
            ReportExportException failure = null;
            for (ProcessLease lease : leases.values()) {
                try { lease.close(); }
                catch (ReportExportException closeFailure) {
                    if (failure == null) failure = closeFailure;
                    else failure.addSuppressed(closeFailure);
                }
            }
            leases.clear();
            if (failure != null) throw failure;
        }

        private void stopRoot(Process root) throws InterruptedException {
            if (root == null || !root.isAlive()) return;
            root.destroy();
            if (!root.waitFor(1L, TimeUnit.SECONDS)) {
                root.destroyForcibly();
                root.waitFor(1L, TimeUnit.SECONDS);
            }
        }

        private boolean rootAlive(Process root) { return root != null && root.isAlive(); }

        private String details(List<ProcessIdentity> survivors, boolean rootSurvived) {
            return "rootAlive=" + rootSurvived + ", identities=" + survivors;
        }
    }

    /** OSHI inventory supplies Java-8-compatible PID, PPID, command line, and process start time. */
    static final class OshiProcessInventory implements ProcessInventory {
        private final OperatingSystem os;

        OshiProcessInventory() throws ReportExportException {
            try {
                this.os = new SystemInfo().getOperatingSystem();
            } catch (RuntimeException ex) {
                throw new ReportExportException("Cannot inspect operating-system process inventory", true, ex);
            }
        }

        @Override public Map<Integer, ProcessIdentity> snapshot() throws ReportExportException {
            try {
                Map<Integer, ProcessIdentity> result = new LinkedHashMap<Integer, ProcessIdentity>();
                for (OSProcess process : os.getProcesses()) {
                    int pid = process.getProcessID();
                    long start = process.getStartTime();
                    if (pid <= 0) continue;
                    if (start <= 0L)
                        throw new ReportExportException(
                                "Cannot read operating-system process identity for pid " + pid, true);
                    result.put(pid, identity(process, ""));
                }
                return result;
            } catch (RuntimeException ex) {
                throw new ReportExportException("Cannot safely inspect LibreOffice process tree", true, ex);
            }
        }

        @Override public ProcessIdentity current(int pid) throws ReportExportException {
            try {
                OSProcess process = refreshed(pid);
                return process == null ? null : identity(process, "");
            } catch (ReportExportException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                throw new ReportExportException(
                        "Cannot read LibreOffice process identity for pid " + pid, true, ex);
            }
        }

        @Override public String commandLine(ProcessIdentity identity) throws ReportExportException {
            try {
                OSProcess process = refreshed(identity.pid);
                if (process == null || !identity.sameExecution(identity(process, ""))) return "";
                String command = process.getCommandLine();
                return command == null ? "" : command;
            } catch (ReportExportException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                throw new ReportExportException(
                        "Cannot read LibreOffice process command line for " + identity, true, ex);
            }
        }

        private OSProcess refreshed(int pid) throws ReportExportException {
            OSProcess process = os.getProcess(pid);
            if (process == null) return null;
            if (!process.updateAttributes()) {
                process = os.getProcess(pid);
                if (process == null) return null;
                if (!process.updateAttributes())
                    throw new ReportExportException(
                            "Cannot refresh LibreOffice process identity for pid " + pid, true);
            }
            if (process.getStartTime() <= 0L)
                throw new ReportExportException(
                        "Cannot read LibreOffice process start time for pid " + pid, true);
            return process;
        }

        private ProcessIdentity identity(OSProcess process, String commandLine) {
            return new ProcessIdentity(process.getProcessID(), process.getStartTime(),
                    process.getParentProcessID(), commandLine, process.getName());
        }
    }

    static final class WindowsProcessLeaseProvider implements ProcessLeaseProvider {
        private static final int PROCESS_TERMINATE = 0x0001;
        private static final int PROCESS_QUERY_LIMITED_INFORMATION = 0x1000;
        private static final int SYNCHRONIZE = 0x00100000;
        private static final int WAIT_OBJECT_0 = 0;
        private static final int WAIT_TIMEOUT = 258;
        private static final int WAIT_FAILED = -1;
        private static final int ERROR_INVALID_PARAMETER = 87;

        @Override public void verifySupported() { }

        @Override public ProcessLease acquire(ProcessIdentity identity, ProcessInventory ignored)
                throws ReportExportException {
            int access = PROCESS_TERMINATE | PROCESS_QUERY_LIMITED_INFORMATION | SYNCHRONIZE;
            WinNT.HANDLE handle = Kernel32.INSTANCE.OpenProcess(access, false, identity.pid);
            if (handle == null || WinBase.INVALID_HANDLE_VALUE.equals(handle)) {
                int error = Kernel32.INSTANCE.GetLastError();
                if (error == ERROR_INVALID_PARAMETER) return null;
                throw windowsFailure("open", identity, error);
            }
            boolean keep = false;
            try {
                WinBase.FILETIME creation = new WinBase.FILETIME();
                WinBase.FILETIME exit = new WinBase.FILETIME();
                WinBase.FILETIME kernel = new WinBase.FILETIME();
                WinBase.FILETIME user = new WinBase.FILETIME();
                if (!Kernel32.INSTANCE.GetProcessTimes(handle, creation, exit, kernel, user))
                    throw windowsFailure("read creation time for", identity,
                            Kernel32.INSTANCE.GetLastError());
                if (creation.toDate().getTime() != identity.startTime) return null;
                keep = true;
                return new WindowsProcessLease(identity, handle);
            } finally {
                if (!keep && !Kernel32.INSTANCE.CloseHandle(handle))
                    throw windowsFailure("close", identity,
                            Kernel32.INSTANCE.GetLastError());
            }
        }

        private ReportExportException windowsFailure(String action, ProcessIdentity identity, int error) {
            return new ReportExportException("Cannot " + action + " stable Windows process handle for "
                    + identity + "; error=" + error, true);
        }

        private static final class WindowsProcessLease implements ProcessLease {
            private final ProcessIdentity identity;
            private WinNT.HANDLE handle;

            WindowsProcessLease(ProcessIdentity identity, WinNT.HANDLE handle) {
                this.identity = identity;
                this.handle = handle;
            }

            @Override public ProcessIdentity identity() { return identity; }

            @Override public boolean isAlive() throws ReportExportException {
                if (handle == null) return false;
                int result = Kernel32.INSTANCE.WaitForSingleObject(handle, 0);
                if (result == WAIT_TIMEOUT) return true;
                if (result == WAIT_OBJECT_0) return false;
                if (result == WAIT_FAILED)
                    throw new ReportExportException("Cannot inspect stable Windows process handle for "
                            + identity + "; error=" + Kernel32.INSTANCE.GetLastError(), true);
                throw new ReportExportException("Unexpected Windows wait result " + result + " for "
                        + identity, true);
            }

            @Override public void terminate() throws ReportExportException {
                if (handle == null || !isAlive()) return;
                if (!Kernel32.INSTANCE.TerminateProcess(handle, 1) && isAlive())
                    throw new ReportExportException("Cannot terminate stable Windows process handle for "
                            + identity + "; error=" + Kernel32.INSTANCE.GetLastError(), true);
                int result = Kernel32.INSTANCE.WaitForSingleObject(handle, 5000);
                if (result != WAIT_OBJECT_0 && isAlive())
                    throw new ReportExportException("Windows process did not exit after stable-handle termination: "
                            + identity, true);
            }

            @Override public void close() throws ReportExportException {
                if (handle == null) return;
                WinNT.HANDLE closing = handle;
                handle = null;
                if (!Kernel32.INSTANCE.CloseHandle(closing))
                    throw new ReportExportException("Cannot close stable Windows process handle for "
                            + identity + "; error=" + Kernel32.INSTANCE.GetLastError(), true);
            }
        }
    }

    static final class LinuxPidfdLeaseProvider implements ProcessLeaseProvider {
        private static final long SYS_PIDFD_SEND_SIGNAL = 424L;
        private static final long SYS_PIDFD_OPEN = 434L;
        private static final int ESRCH = 3;
        private static final int SIGKILL = 9;
        private LinuxLibC libc;
        private boolean verified;

        interface LinuxLibC extends Library {
            long syscall(long number, Object... arguments);
            int close(int descriptor);
        }

        @Override public synchronized void verifySupported() throws ReportExportException {
            if (verified) return;
            String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
            if (!(architecture.equals("amd64") || architecture.equals("x86_64")
                    || architecture.equals("aarch64") || architecture.equals("arm64")))
                throw new ReportExportException("Linux pidfd is unsupported on architecture " + architecture,
                        true);
            try {
                if (libc == null) libc = Native.load("c", LinuxLibC.class);
            } catch (RuntimeException unavailable) {
                throw new ReportExportException("Linux pidfd native library is unavailable", true, unavailable);
            } catch (LinkageError unavailable) {
                throw new ReportExportException("Linux pidfd native library is unavailable", true, unavailable);
            }
            int descriptor = pidfdOpen(currentProcessId());
            if (descriptor < 0)
                throw linuxFailure("probe pidfd_open", Native.getLastError());
            try {
                if (pidfdSignal(libc, descriptor, 0) != 0)
                    throw linuxFailure("probe pidfd_send_signal", Native.getLastError());
            } finally {
                if (libc.close(descriptor) != 0)
                    throw linuxFailure("close pidfd probe", Native.getLastError());
            }
            verified = true;
        }

        @Override public ProcessLease acquire(ProcessIdentity identity, ProcessInventory inventory)
                throws ReportExportException {
            verifySupported();
            int descriptor = pidfdOpen(identity.pid);
            if (descriptor < 0) {
                int error = Native.getLastError();
                if (error == ESRCH) return null;
                throw linuxFailure("open pidfd for " + identity, error);
            }
            boolean keep = false;
            try {
                ProcessIdentity current = inventory.current(identity.pid);
                if (!identity.sameExecution(current)) return null;
                if (pidfdSignal(libc, descriptor, 0) != 0) {
                    int error = Native.getLastError();
                    if (error == ESRCH) return null;
                    throw linuxFailure("validate pidfd for " + identity, error);
                }
                keep = true;
                return new LinuxPidfdLease(identity, descriptor, libc);
            } finally {
                if (!keep && libc.close(descriptor) != 0)
                    throw linuxFailure("close rejected pidfd for " + identity,
                            Native.getLastError());
            }
        }

        private int pidfdOpen(int pid) {
            return (int) libc.syscall(SYS_PIDFD_OPEN, Integer.valueOf(pid), Integer.valueOf(0));
        }

        private static int pidfdSignal(LinuxLibC libc, int descriptor, int signal) {
            return (int) libc.syscall(SYS_PIDFD_SEND_SIGNAL, Integer.valueOf(descriptor),
                    Integer.valueOf(signal), Pointer.NULL, Integer.valueOf(0));
        }

        private static ReportExportException linuxFailure(String action, int error) {
            return new ReportExportException("Cannot safely " + action + "; errno=" + error, true);
        }

        private static final class LinuxPidfdLease implements ProcessLease {
            private final ProcessIdentity identity;
            private final LinuxLibC libc;
            private int descriptor;

            LinuxPidfdLease(ProcessIdentity identity, int descriptor, LinuxLibC libc) {
                this.identity = identity;
                this.descriptor = descriptor;
                this.libc = libc;
            }

            @Override public ProcessIdentity identity() { return identity; }

            @Override public boolean isAlive() throws ReportExportException {
                if (descriptor < 0) return false;
                if (pidfdSignal(libc, descriptor, 0) == 0) return true;
                int error = Native.getLastError();
                if (error == ESRCH) return false;
                throw linuxFailure("inspect pidfd for " + identity, error);
            }

            @Override public void terminate() throws ReportExportException {
                if (descriptor < 0 || !isAlive()) return;
                if (pidfdSignal(libc, descriptor, SIGKILL) != 0) {
                    int error = Native.getLastError();
                    if (error != ESRCH) throw linuxFailure("signal pidfd for " + identity, error);
                }
            }

            @Override public void close() throws ReportExportException {
                if (descriptor < 0) return;
                int closing = descriptor;
                descriptor = -1;
                if (libc.close(closing) != 0)
                    throw linuxFailure("close pidfd for " + identity, Native.getLastError());
            }
        }
    }

    static final class UnsupportedProcessLeaseProvider implements ProcessLeaseProvider {
        private final String platform;
        UnsupportedProcessLeaseProvider(String platform) { this.platform = platform; }
        @Override public void verifySupported() throws ReportExportException {
            throw new ReportExportException("Stable process handles are unsupported on " + platform, true);
        }
        @Override public ProcessLease acquire(ProcessIdentity identity, ProcessInventory inventory)
                throws ReportExportException {
            verifySupported();
            return null;
        }
    }

    static final class SystemProcessClock implements ProcessClock {
        @Override public long nanoTime() { return System.nanoTime(); }
        @Override public void sleep(long millis) throws InterruptedException { Thread.sleep(millis); }
    }

    private static ProcessLeaseProvider nativeLeases() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return new WindowsProcessLeaseProvider();
        if (os.contains("linux")) return new LinuxPidfdLeaseProvider();
        return new UnsupportedProcessLeaseProvider(os.length() == 0 ? "unknown platform" : os);
    }

    private static int currentProcessId() {
        try { return new SystemInfo().getOperatingSystem().getProcessId(); }
        catch (RuntimeException failure) { return 0; }
    }

    private static String executableName(String raw) {
        if (raw == null || raw.length() == 0) return "";
        try {
            Path file = Paths.get(raw).getFileName();
            return normalizeExecutable(file == null ? raw : file.toString());
        } catch (RuntimeException invalidPath) {
            return normalizeExecutable(raw);
        }
    }

    private static String normalizeExecutable(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        int slash = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'));
        if (slash >= 0) normalized = normalized.substring(slash + 1);
        if (normalized.endsWith(".exe") || normalized.endsWith(".bin"))
            normalized = normalized.substring(0, normalized.length() - 4);
        return normalized;
    }

    private static String bounded(InputStream input, int maximum) throws IOException {
        try (InputStream stream = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            for (int count; (count = stream.read(buffer)) >= 0;) {
                if (out.size() < maximum)
                    out.write(buffer, 0, Math.min(count, maximum - out.size()));
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String compactLogs(String stderr, String stdout) {
        String value = (stderr == null ? "" : stderr) + (stdout == null ? "" : " " + stdout);
        value = value.replaceAll("[\\r\\n]+", " ");
        return value.substring(0, Math.min(512, value.length()));
    }

    private static ReportExportException merge(ReportExportException primary,
            ReportExportException additional) {
        if (primary == null) return additional;
        if (primary != additional) primary.addSuppressed(additional);
        return primary;
    }

    private static ReportExportException closeProcessStreams(Process process,
            ReportExportException primary) {
        if (process == null) return primary;
        primary = closeStream(process.getOutputStream(), "stdin", primary);
        primary = closeStream(process.getInputStream(), "stdout", primary);
        return closeStream(process.getErrorStream(), "stderr", primary);
    }

    private static ReportExportException closeStream(Closeable stream, String name,
            ReportExportException primary) {
        if (stream == null) return primary;
        try { stream.close(); }
        catch (IOException failure) {
            return merge(primary, new ReportExportException(
                    "Cannot close LibreOffice " + name + " stream", true, failure));
        }
        return primary;
    }

    private void cleanupWithRetry(Path root) throws IOException {
        IOException failure = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                cleanup.clean(root);
                return;
            } catch (IOException cleanupFailure) {
                if (failure == null) failure = cleanupFailure;
                else failure.addSuppressed(cleanupFailure);
                if (attempt + 1 < 3) {
                    try { Thread.sleep(100L); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        throw failure == null ? new IOException("Temporary cleanup failed") : failure;
    }

    private void emergencyTerminate(Process process) throws ReportExportException {
        try {
            process.destroy();
            if (!process.waitFor(2L, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                if (!process.waitFor(5L, TimeUnit.SECONDS) || process.isAlive())
                    throw new ReportExportException(
                            "LibreOffice root process survived emergency termination", true);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new ReportExportException("Interrupted while terminating LibreOffice", true, ex);
        }
    }

    private void requireInside(Path parent, Path child) throws ReportExportException {
        if (!child.toAbsolutePath().normalize().startsWith(parent.toAbsolutePath().normalize()))
            throw new ReportExportException("Unsafe conversion path", false);
    }

    private String safeName(String raw) {
        String value = raw == null ? "report" : raw.replaceAll("[^A-Za-z0-9._-]", "_");
        value = value.replaceAll("^\\.+", "");
        if (value.isEmpty()) return "report";
        return value.length() > 80 ? value.substring(0, 80) : value;
    }

    private byte[] readBoundedPdf(Path pdf, long maximum) throws ReportExportException {
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    pdf, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            long size = attributes.size();
            if (!attributes.isRegularFile() || size < 32L || size > maximum
                    || size > Integer.MAX_VALUE - 8L)
                throw new ReportExportException("LibreOffice produced an invalid PDF", true);
            Set<OpenOption> options = new HashSet<OpenOption>();
            options.add(StandardOpenOption.READ);
            options.add(LinkOption.NOFOLLOW_LINKS);
            try (SeekableByteChannel channel = Files.newByteChannel(pdf, options);
                    InputStream input = Channels.newInputStream(channel);
                    ByteArrayOutputStream out = new ByteArrayOutputStream((int) size)) {
                byte[] buffer = new byte[8192];
                long read = 0L;
                for (int count; (count = input.read(buffer)) >= 0;) {
                    if (count == 0) continue;
                    read += count;
                    if (read > maximum || read > Integer.MAX_VALUE - 8L)
                        throw new ReportExportException("LibreOffice PDF exceeds byte limit", true);
                    out.write(buffer, 0, count);
                }
                return out.toByteArray();
            }
        } catch (ReportExportException failure) {
            throw failure;
        } catch (IOException failure) {
            throw new ReportExportException("Cannot safely read LibreOffice PDF output", true, failure);
        }
    }

    private boolean pdf(byte[] value) {
        byte[] header = "%PDF-".getBytes(StandardCharsets.US_ASCII);
        byte[] eofMarker = "%%EOF".getBytes(StandardCharsets.US_ASCII);
        byte[] startMarker = "startxref".getBytes(StandardCharsets.US_ASCII);
        if (!matchesAt(value, header, 0)) return false;
        int eof = lastIndexOf(value, eofMarker, value.length - eofMarker.length);
        if (eof < 0 || !whitespace(value, eof + eofMarker.length, value.length)) return false;
        int start = lastIndexOf(value, startMarker, eof - startMarker.length);
        if (start < 0) return false;
        int cursor = start + startMarker.length;
        while (cursor < eof && white(value[cursor])) cursor++;
        long offset = 0L;
        int digits = 0;
        while (cursor < eof && value[cursor] >= '0' && value[cursor] <= '9') {
            if (offset > (Long.MAX_VALUE - 9L) / 10L) return false;
            offset = offset * 10L + value[cursor++] - '0';
            digits++;
        }
        if (digits == 0 || offset < header.length || offset >= start || offset > Integer.MAX_VALUE)
            return false;
        int xref = (int) offset;
        byte[] xrefMarker = "xref".getBytes(StandardCharsets.US_ASCII);
        byte[] trailerMarker = "trailer".getBytes(StandardCharsets.US_ASCII);
        return matchesAt(value, xrefMarker, xref)
                && indexOf(value, trailerMarker, xref + xrefMarker.length, start) >= 0;
    }

    private boolean matchesAt(byte[] value, byte[] expected, int offset) {
        if (offset < 0 || offset > value.length - expected.length) return false;
        for (int i = 0; i < expected.length; i++) if (value[offset + i] != expected[i]) return false;
        return true;
    }

    private int lastIndexOf(byte[] value, byte[] target, int from) {
        for (int offset = Math.min(from, value.length - target.length); offset >= 0; offset--)
            if (matchesAt(value, target, offset)) return offset;
        return -1;
    }

    private int indexOf(byte[] value, byte[] target, int from, int limit) {
        for (int offset = Math.max(0, from); offset <= limit - target.length; offset++)
            if (matchesAt(value, target, offset)) return offset;
        return -1;
    }

    private boolean whitespace(byte[] value, int from, int limit) {
        for (int i = from; i < limit; i++) if (!white(value[i])) return false;
        return true;
    }

    private boolean white(byte value) {
        return value == 0 || value == 9 || value == 10 || value == 12 || value == 13 || value == 32;
    }

    private String compact(String stderr, String stdout) {
        return compactLogs(stderr, stdout);
    }

    private void delete(Path root) throws IOException {
        IOException failure = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                    @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        Files.deleteIfExists(file);
                        return FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult postVisitDirectory(Path directory, IOException error)
                            throws IOException {
                        if (error != null) throw error;
                        Files.deleteIfExists(directory);
                        return FileVisitResult.CONTINUE;
                    }
                });
                return;
            } catch (IOException ex) {
                failure = ex;
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw failure == null ? new IOException("Temporary cleanup failed") : failure;
    }
}
