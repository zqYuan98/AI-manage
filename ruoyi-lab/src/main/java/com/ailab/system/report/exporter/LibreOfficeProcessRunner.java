package com.ailab.system.report.exporter;

import com.ailab.system.config.LabProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** Executes LibreOffice without a shell and with one disposable profile per conversion. */
public final class LibreOfficeProcessRunner {
    private static final int MAX_LOG = 64 * 1024;
    private final LabProperties properties; private final List<String> executable; private final Cleanup cleanup;
    private final ProcessTreeController processes;
    public interface Cleanup { void clean(Path root) throws IOException; }
    public LibreOfficeProcessRunner(LabProperties properties) { this(properties, Arrays.asList(properties.getLibreOfficeExecutable())); }
    /** Test seam for a Java fake executable; production still passes one soffice executable argument. */
    public LibreOfficeProcessRunner(LabProperties properties, List<String> executable) { this(properties, executable, null); }
    public LibreOfficeProcessRunner(LabProperties properties, List<String> executable, Cleanup cleanup) {
        this(properties, executable, cleanup, new ReflectiveProcessTreeController());
    }
    LibreOfficeProcessRunner(LabProperties properties, List<String> executable, Cleanup cleanup, ProcessTreeController processes) {
        if (properties == null || executable == null || executable.isEmpty()) throw new IllegalArgumentException("LibreOffice configuration is required");
        this.properties = properties; this.executable = new ArrayList<String>(executable); this.cleanup = cleanup == null ? new Cleanup() { @Override public void clean(Path root) throws IOException { delete(root); } } : cleanup; this.processes = processes;
    }
    public byte[] convert(byte[] word, String name) throws ReportExportException {
        if (word == null || word.length == 0) throw new ReportExportException("Word input is required", false);
        Path root = null; ExecutorService readers = null; Process process = null; List<Object> tracked = null; ReportExportException primary = null;
        try {
            Path configured = Paths.get(properties.getTempDirectory()).toAbsolutePath().normalize(); Files.createDirectories(configured);
            root = Files.createTempDirectory(configured, "lo-").toAbsolutePath().normalize(); requireInside(configured, root);
            Path profile = Files.createDirectory(root.resolve("profile")); Path input = root.resolve(safeName(name) + ".docx"); Path out = Files.createDirectory(root.resolve("out"));
            Files.write(input, word, StandardOpenOption.CREATE_NEW);
            List<String> command = new ArrayList<String>(executable); command.add("--headless"); command.add("--nologo"); command.add("--nodefault"); command.add("--nofirststartwizard"); command.add("--nolockcheck"); command.add("-env:UserInstallation=" + profile.toUri().toASCIIString()); command.add("--convert-to"); command.add("pdf"); command.add("--outdir"); command.add(out.toString()); command.add(input.toString());
            try { process = new ProcessBuilder(command).directory(root.toFile()).start(); tracked = processes.snapshot(process); Thread.sleep(100L); tracked.addAll(processes.snapshot(process)); } catch (IOException ex) { throw new ReportExportException("LibreOffice executable is unavailable", true, ex); }
            final Process started = process;
            readers = Executors.newFixedThreadPool(2); Future<String> stdout = readers.submit(() -> bounded(started.getInputStream())); Future<String> stderr = readers.submit(() -> bounded(started.getErrorStream()));
            long timeout = Math.max(1L, properties.getConversionTimeoutSeconds());
            if (!process.waitFor(timeout, TimeUnit.SECONDS)) { processes.terminate(process, tracked); throw new ReportExportException("LibreOffice conversion timed out", true); }
            String error = stderr.get(5, TimeUnit.SECONDS); String output = stdout.get(5, TimeUnit.SECONDS);
            if (process.exitValue() != 0) throw new ReportExportException("LibreOffice conversion failed: " + compact(error, output), true);
            Path pdf = out.resolve(safeName(name) + ".pdf").normalize(); requireInside(out, pdf);
            if (!Files.isRegularFile(pdf)) throw new ReportExportException("LibreOffice produced no PDF output", true);
            byte[] bytes = Files.readAllBytes(pdf); long maximum = Math.max(1024L, properties.getMaxUploadSizeBytes());
            if (bytes.length > maximum || !pdf(bytes)) throw new ReportExportException("LibreOffice produced an invalid PDF", true); return bytes;
        } catch (ReportExportException ex) { primary = ex; throw ex;
        } catch (Exception ex) { primary = new ReportExportException("LibreOffice conversion failed", true, ex); throw primary;
        } finally { if (readers != null) readers.shutdownNow(); if (process != null && tracked != null) try { processes.terminate(process, tracked); } catch (ReportExportException ex) { if (primary != null) primary.addSuppressed(ex); else throw ex; } if (root != null) try { cleanup.clean(root); } catch (IOException ex) { if (primary != null) primary.addSuppressed(ex); else throw new ReportExportException("Report conversion cleanup failed; temporary data may remain", true, ex); } }
    }
    private String bounded(InputStream input) throws IOException { try (InputStream stream=input; ByteArrayOutputStream out=new ByteArrayOutputStream()) { byte[] buffer=new byte[4096]; for(int n;(n=stream.read(buffer))>=0;) if(out.size()<MAX_LOG) out.write(buffer,0,Math.min(n,MAX_LOG-out.size())); return new String(out.toByteArray(), StandardCharsets.UTF_8); } }
    interface ProcessTreeController { List<Object> snapshot(Process process) throws ReportExportException; void terminate(Process process, List<Object> tracked) throws ReportExportException; }
    /** Uses ProcessHandle only through reflection so the module remains Java-8 source compatible. */
    static final class ReflectiveProcessTreeController implements ProcessTreeController {
        public List<Object> snapshot(Process process) throws ReportExportException { if (Boolean.getBoolean("ailab.report.test.forceJava8Fallback")) return fallbackSnapshot(process); try { Class<?> type = Class.forName("java.lang.ProcessHandle"); Object root = Process.class.getMethod("toHandle").invoke(process); Object stream = type.getMethod("descendants").invoke(root); try { List<Object> values = new ArrayList<Object>(); java.util.Iterator<?> iterator = ((Stream<?>) stream).iterator(); while (iterator.hasNext()) values.add(iterator.next()); return values; } finally { ((Stream<?>) stream).close(); } } catch (ClassNotFoundException ex) { return fallbackSnapshot(process); } catch (Exception ex) { throw new ReportExportException("Cannot safely inspect LibreOffice process tree", true, ex); } }
        public void terminate(Process process, List<Object> tracked) throws ReportExportException { if (Boolean.getBoolean("ailab.report.test.forceJava8Fallback")) { fallbackTerminate(process, tracked); return; } try { Class<?> type = Class.forName("java.lang.ProcessHandle"); if (tracked == null) throw new ReportExportException("Cannot safely inspect LibreOffice process tree", true); List<Object> all = new ArrayList<Object>(tracked); all.addAll(snapshot(process)); destroy(type, all, false); if (process.isAlive()) process.destroy(); waitDead(type, all, 1500L); if (process.isAlive()) process.waitFor(1500L, TimeUnit.MILLISECONDS); destroy(type, all, true); if (process.isAlive()) process.destroyForcibly(); waitDead(type, all, 3000L); if (process.isAlive() || alive(type, all)) throw new ReportExportException("LibreOffice process tree survived termination", true); } catch (ReportExportException ex) { throw ex; } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new ReportExportException("Interrupted while terminating LibreOffice", true, ex); } catch (ClassNotFoundException ex) { fallbackTerminate(process, tracked); } catch (Exception ex) { throw new ReportExportException("Cannot safely terminate LibreOffice process tree", true, ex); } }
        private static void destroy(Class<?> type, List<Object> values, boolean force) throws Exception { for (int i = values.size() - 1; i >= 0; i--) if (((Boolean) type.getMethod("isAlive").invoke(values.get(i))).booleanValue()) type.getMethod(force ? "destroyForcibly" : "destroy").invoke(values.get(i)); }
        private static void waitDead(Class<?> type, List<Object> values, long millis) throws Exception { long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis); while (alive(type, values) && System.nanoTime() < end) Thread.sleep(25L); }
        private static boolean alive(Class<?> type, List<Object> values) throws Exception { for (Object value : values) if (((Boolean) type.getMethod("isAlive").invoke(value)).booleanValue()) return true; return false; }
        private static List<Object> fallbackSnapshot(Process process) throws ReportExportException { List<Object> result = new ArrayList<Object>(); result.add(Long.valueOf(pid(process))); return result; }
        private static void fallbackTerminate(Process process, List<Object> tracked) throws ReportExportException { long pid = tracked == null || tracked.isEmpty() || !(tracked.get(0) instanceof Long) ? -1L : ((Long) tracked.get(0)).longValue(); if (pid <= 0L) throw new ReportExportException("Cannot safely inspect LibreOffice process tree", true); String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT); List<String> argv = new ArrayList<String>(); if (os.contains("win")) { argv.add("taskkill"); argv.add("/PID"); argv.add(String.valueOf(pid)); argv.add("/T"); argv.add("/F"); } else { Path pkill = Files.isExecutable(Paths.get("/usr/bin/pkill")) ? Paths.get("/usr/bin/pkill") : Paths.get("/bin/pkill"); if (!Files.isExecutable(pkill)) throw new ReportExportException("No safe Java-8 process-tree terminator is available", true); argv.add(pkill.toString()); argv.add("-KILL"); argv.add("-P"); argv.add(String.valueOf(pid)); }
            try { Process killer = new ProcessBuilder(argv).start(); if (!killer.waitFor(5, TimeUnit.SECONDS)) { killer.destroyForcibly(); throw new ReportExportException("Process-tree terminator timed out", true); } if (killer.exitValue() != 0) throw new ReportExportException("Process-tree terminator failed", true); process.destroyForcibly(); if (!process.waitFor(5, TimeUnit.SECONDS) || process.isAlive()) throw new ReportExportException("LibreOffice process tree survived termination", true); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new ReportExportException("Interrupted while terminating LibreOffice", true, ex); } catch (IOException ex) { throw new ReportExportException("Cannot start safe process-tree terminator", true, ex); } }
        private static long pid(Process process) throws ReportExportException { try { Object value = Process.class.getMethod("pid").invoke(process); long pid = ((Long) value).longValue(); if (pid <= 0L) throw new ReportExportException("Invalid LibreOffice process identifier", true); return pid; } catch (ReportExportException ex) { throw ex; } catch (Exception ex) { throw new ReportExportException("Cannot obtain LibreOffice process identifier", true, ex); } }
    }
    private void requireInside(Path parent, Path child) throws ReportExportException { if (!child.toAbsolutePath().normalize().startsWith(parent.toAbsolutePath().normalize())) throw new ReportExportException("Unsafe conversion path", false); }
    private String safeName(String raw) { String value = raw == null ? "report" : raw.replaceAll("[^A-Za-z0-9._-]", "_"); value = value.replaceAll("^\\.+", ""); return value.isEmpty() ? "report" : value.length() > 80 ? value.substring(0,80) : value; }
    private boolean pdf(byte[] value) { return value.length >= 5 && value[0]=='%' && value[1]=='P' && value[2]=='D' && value[3]=='F' && value[4]=='-'; }
    private String compact(String stderr, String stdout) { String value=(stderr==null?"":stderr)+(stdout==null?"":" "+stdout); return value.replaceAll("[\\r\\n]+", " ").substring(0, Math.min(512, value.length())); }
    private void delete(Path root) throws IOException { IOException failure = null; for (int attempt = 0; attempt < 5; attempt++) { try { Files.walkFileTree(root, new SimpleFileVisitor<Path>() { @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException { Files.deleteIfExists(file); return FileVisitResult.CONTINUE; } @Override public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException { if (error != null) throw error; Files.deleteIfExists(directory); return FileVisitResult.CONTINUE; } }); return; } catch (IOException ex) { failure = ex; try { Thread.sleep(100L); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; } } } throw failure == null ? new IOException("Temporary cleanup failed") : failure; }
}
