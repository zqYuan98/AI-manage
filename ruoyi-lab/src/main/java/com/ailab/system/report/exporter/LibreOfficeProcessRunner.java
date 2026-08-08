package com.ailab.system.report.exporter;

import com.ailab.system.config.LabProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

/** Executes LibreOffice without a shell and with one disposable profile per conversion. */
public final class LibreOfficeProcessRunner {
    private static final int MAX_LOG = 64 * 1024;
    private static final int MAX_TERMINATOR_LOG = 2048;
    private static final int MAX_COMMAND_LINE_ATTEMPTS = 3;
    private static final long COMMAND_LINE_STARTUP_WINDOW_NANOS = TimeUnit.SECONDS.toNanos(5L);
    private static final long TERMINATION_DEADLINE_NANOS = TimeUnit.SECONDS.toNanos(5L);
    private static final long TERMINATION_GRACE_MILLIS = 200L;

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
        this(properties, executable, cleanup, new OshiProcessTreeController());
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
            session = processes.open(profile.toUri().toASCIIString());
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
                    tracked = session.snapshot(process);
                    Thread.sleep(100L);
                    tracked.addAll(session.snapshot(process));
                } catch (ReportExportException ex) {
                    emergencyTerminate(process);
                    throw ex;
                }
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
            if (!Files.isRegularFile(pdf))
                throw new ReportExportException("LibreOffice produced no PDF output", true);
            byte[] bytes = Files.readAllBytes(pdf);
            long maximum = Math.max(1024L, properties.getMaxUploadSizeBytes());
            if (bytes.length > maximum || !pdf(bytes))
                throw new ReportExportException("LibreOffice produced an invalid PDF", true);
            return bytes;
        } catch (ReportExportException ex) {
            primary = ex;
            throw ex;
        } catch (Exception ex) {
            primary = new ReportExportException("LibreOffice conversion failed", true, ex);
            throw primary;
        } finally {
            if (readers != null) readers.shutdownNow();
            if (process != null && !terminationDone) {
                try {
                    if (tracked == null || session == null) emergencyTerminate(process);
                    else session.terminate(process, tracked);
                } catch (ReportExportException ex) {
                    if (primary != null) primary.addSuppressed(ex);
                    else throw ex;
                }
            }
            if (root != null) {
                try {
                    cleanup.clean(root);
                } catch (IOException ex) {
                    if (primary != null) primary.addSuppressed(ex);
                    else throw new ReportExportException(
                            "Report conversion cleanup failed; temporary data may remain", true, ex);
                }
            }
        }
    }

    interface ProcessTreeController {
        ProcessTreeSession open(String token) throws ReportExportException;
    }

    interface ProcessTreeSession {
        List<ProcessIdentity> snapshot(Process process) throws ReportExportException;
        void terminate(Process process, List<ProcessIdentity> tracked) throws ReportExportException;
    }

    interface ProcessInventory {
        Map<Integer, ProcessIdentity> snapshot() throws ReportExportException;
        ProcessIdentity current(int pid) throws ReportExportException;
        String commandLine(ProcessIdentity identity) throws ReportExportException;
    }

    interface ProcessInventoryFactory {
        ProcessInventory create(String token) throws ReportExportException;
    }

    interface ProcessTerminator {
        void terminate(ProcessIdentity identity) throws ReportExportException;
    }

    interface KillCommandFactory {
        List<String> command(ProcessIdentity identity);
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

        ProcessIdentity(int pid, long startTime, int parentPid, String commandLine) {
            if (pid <= 0 || startTime <= 0L)
                throw new IllegalArgumentException("A positive process id and start time are required");
            this.pid = pid;
            this.startTime = startTime;
            this.parentPid = parentPid;
            this.commandLine = commandLine == null ? "" : commandLine;
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
        private final ProcessTerminator terminator;
        private final ProcessClock clock;

        OshiProcessTreeController() {
            this(new ProcessInventoryFactory() {
                @Override public ProcessInventory create(String token) throws ReportExportException {
                    return new OshiProcessInventory();
                }
            }, new CommandProcessTerminator(), new SystemProcessClock());
        }

        OshiProcessTreeController(ProcessInventoryFactory inventories, ProcessTerminator terminator,
                ProcessClock clock) {
            if (inventories == null || terminator == null || clock == null)
                throw new IllegalArgumentException("Process tracking collaborators are required");
            this.inventories = inventories;
            this.terminator = terminator;
            this.clock = clock;
        }

        @Override public ProcessTreeSession open(String token) throws ReportExportException {
            return new OshiProcessTreeSession(token, inventories.create(token), terminator, clock);
        }
    }

    static final class OshiProcessTreeSession implements ProcessTreeSession {
        private final String token;
        private final ProcessInventory inventory;
        private final ProcessTerminator terminator;
        private final ProcessClock clock;
        private final Set<ProcessIdentity> baseline;
        private final Set<ProcessIdentity> roots = new LinkedHashSet<ProcessIdentity>();
        private final Set<ProcessIdentity> inspected = new HashSet<ProcessIdentity>();
        private final Map<ProcessIdentity, CommandLineAttempt> attempts =
                new LinkedHashMap<ProcessIdentity, CommandLineAttempt>();

        OshiProcessTreeSession(String token, ProcessInventory inventory, ProcessTerminator terminator,
                ProcessClock clock) throws ReportExportException {
            if (token == null || token.length() == 0 || inventory == null || terminator == null || clock == null)
                throw new IllegalArgumentException("Process-tree session configuration is required");
            this.token = token;
            this.inventory = inventory;
            this.terminator = terminator;
            this.clock = clock;
            this.baseline = new HashSet<ProcessIdentity>(inventory.snapshot().values());
        }

        @Override public List<ProcessIdentity> snapshot(Process ignored) throws ReportExportException {
            Map<Integer, ProcessIdentity> current = inventory.snapshot();
            for (ProcessIdentity identity : current.values()) {
                if (!baseline.contains(identity) && !roots.contains(identity) && !inspected.contains(identity))
                    inspectNewIdentity(identity);
            }
            LinkedHashSet<ProcessIdentity> found = new LinkedHashSet<ProcessIdentity>();
            for (ProcessIdentity root : roots) {
                ProcessIdentity now = current.get(root.pid);
                if (root.sameExecution(now)) found.add(now);
            }
            boolean changed;
            do {
                changed = false;
                for (ProcessIdentity candidate : current.values()) {
                    if (!found.contains(candidate) && hasParent(found, candidate)) {
                        found.add(candidate);
                        changed = true;
                    }
                }
            } while (changed);
            return new ArrayList<ProcessIdentity>(found);
        }

        private void inspectNewIdentity(ProcessIdentity identity) throws ReportExportException {
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
                if (commandLine.contains(token)) roots.add(identity);
                return;
            }
            if (attempt.count >= MAX_COMMAND_LINE_ATTEMPTS)
                throw commandLineUnavailable(identity, attempt, "retry budget exhausted");
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

        private static final class CommandLineAttempt {
            final long firstSeenNanos;
            int count;
            ReportExportException lastFailure;

            CommandLineAttempt(long firstSeenNanos) {
                this.firstSeenNanos = firstSeenNanos;
            }
        }

        @Override public void terminate(Process root, List<ProcessIdentity> tracked)
                throws ReportExportException {
            LinkedHashSet<ProcessIdentity> known = new LinkedHashSet<ProcessIdentity>();
            if (tracked != null) known.addAll(tracked);
            long started = clock.nanoTime();
            long deadline = started > Long.MAX_VALUE - TERMINATION_DEADLINE_NANOS
                    ? Long.MAX_VALUE : started + TERMINATION_DEADLINE_NANOS;
            int quietRounds = 0;
            try {
                while (clock.nanoTime() < deadline) {
                    boolean live = terminationRound(root, known);
                    if (!live) {
                        if (++quietRounds >= 2) return;
                    } else {
                        quietRounds = 0;
                    }
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
            killAllCurrent(known);
            stopRoot(root);
            clock.sleep(TERMINATION_GRACE_MILLIS);
            known.addAll(snapshot(root));
            return rootAlive(root) || !liveIdentities(known).isEmpty();
        }

        /** Deadline handling performs one complete kill/grace/verify round before returning or failing. */
        private void finalTerminationRound(Process root, LinkedHashSet<ProcessIdentity> known)
                throws ReportExportException, InterruptedException {
            known.addAll(snapshot(root));
            killAllCurrent(known);
            stopRoot(root);
            clock.sleep(TERMINATION_GRACE_MILLIS);

            List<ProcessIdentity> verification = snapshot(root);
            known.addAll(verification);
            List<ProcessIdentity> survivors = liveIdentities(known);
            boolean rootSurvived = rootAlive(root);
            if (survivors.isEmpty() && !rootSurvived) return;

            // A process first observed by final verification is still killed once before typed failure.
            killAllCurrent(survivors);
            stopRoot(root);
            throw new ReportExportException(
                    "LibreOffice process tree remained unstable after termination deadline: "
                            + details(survivors, rootSurvived), true);
        }

        private void killAllCurrent(Iterable<ProcessIdentity> identities) throws ReportExportException {
            for (ProcessIdentity identity : identities) killIfCurrent(identity);
        }

        private void killIfCurrent(ProcessIdentity identity) throws ReportExportException {
            ProcessIdentity current = inventory.current(identity.pid);
            if (!identity.sameExecution(current)) return;
            try {
                terminator.terminate(identity);
            } catch (ReportExportException failure) {
                ProcessIdentity after = inventory.current(identity.pid);
                if (identity.sameExecution(after)) throw failure;
            }
        }

        private List<ProcessIdentity> liveIdentities(Iterable<ProcessIdentity> identities)
                throws ReportExportException {
            List<ProcessIdentity> live = new ArrayList<ProcessIdentity>();
            for (ProcessIdentity identity : identities) {
                ProcessIdentity current = inventory.current(identity.pid);
                if (identity.sameExecution(current)) live.add(identity);
            }
            return live;
        }

        private boolean hasParent(Set<ProcessIdentity> found, ProcessIdentity candidate) {
            for (ProcessIdentity possible : found) {
                if (possible.pid == candidate.parentPid && candidate.startTime >= possible.startTime) return true;
            }
            return false;
        }

        private void stopRoot(Process root) throws InterruptedException {
            if (root == null || !root.isAlive()) return;
            root.destroy();
            if (!root.waitFor(1L, TimeUnit.SECONDS)) {
                root.destroyForcibly();
                root.waitFor(1L, TimeUnit.SECONDS);
            }
        }

        private boolean rootAlive(Process root) {
            return root != null && root.isAlive();
        }

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
                    process.getParentProcessID(), commandLine);
        }
    }

    /** Runs a fixed platform command without a shell and drains both pipes before waiting can deadlock. */
    static final class CommandProcessTerminator implements ProcessTerminator {
        private final KillCommandFactory commands;

        CommandProcessTerminator() {
            this(new KillCommandFactory() {
                @Override public List<String> command(ProcessIdentity identity) {
                    List<String> argv = new ArrayList<String>();
                    if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
                        argv.add("taskkill");
                        argv.add("/PID");
                        argv.add(String.valueOf(identity.pid));
                        argv.add("/T");
                        argv.add("/F");
                    } else {
                        argv.add("/bin/kill");
                        argv.add("-KILL");
                        argv.add(String.valueOf(identity.pid));
                    }
                    return argv;
                }
            });
        }

        CommandProcessTerminator(KillCommandFactory commands) {
            if (commands == null) throw new IllegalArgumentException("Kill command factory is required");
            this.commands = commands;
        }

        @Override public void terminate(ProcessIdentity identity) throws ReportExportException {
            if (identity == null) throw new ReportExportException("Missing process identity", true);
            Process killer = null;
            ExecutorService drains = null;
            try {
                List<String> command = commands.command(identity);
                if (command == null || command.isEmpty())
                    throw new ReportExportException("Missing safe process-tree terminator command", true);
                killer = new ProcessBuilder(new ArrayList<String>(command)).start();
                final Process started = killer;
                drains = Executors.newFixedThreadPool(2);
                Future<String> stdout = drains.submit(() -> bounded(started.getInputStream(), MAX_TERMINATOR_LOG));
                Future<String> stderr = drains.submit(() -> bounded(started.getErrorStream(), MAX_TERMINATOR_LOG));
                if (!killer.waitFor(5L, TimeUnit.SECONDS)) {
                    killer.destroyForcibly();
                    throw new ReportExportException("Process-tree terminator timed out", true);
                }
                String out = stdout.get(2L, TimeUnit.SECONDS);
                String err = stderr.get(2L, TimeUnit.SECONDS);
                if (killer.exitValue() != 0)
                    throw new ReportExportException("Process-tree terminator exit=" + killer.exitValue()
                            + " for " + identity + ": " + compactLogs(err, out), true);
            } catch (ReportExportException ex) {
                throw ex;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                if (killer != null) killer.destroyForcibly();
                throw new ReportExportException("Interrupted while terminating LibreOffice", true, ex);
            } catch (IOException ex) {
                throw new ReportExportException("Cannot start safe process-tree terminator", true, ex);
            } catch (ExecutionException ex) {
                throw new ReportExportException("Cannot read process-tree terminator output", true, ex.getCause());
            } catch (TimeoutException ex) {
                if (killer != null) killer.destroyForcibly();
                throw new ReportExportException("Timed out draining process-tree terminator output", true, ex);
            } finally {
                if (drains != null) drains.shutdownNow();
            }
        }
    }

    static final class SystemProcessClock implements ProcessClock {
        @Override public long nanoTime() { return System.nanoTime(); }
        @Override public void sleep(long millis) throws InterruptedException { Thread.sleep(millis); }
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

    private boolean pdf(byte[] value) {
        return value.length >= 5 && value[0] == '%' && value[1] == 'P' && value[2] == 'D'
                && value[3] == 'F' && value[4] == '-';
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
