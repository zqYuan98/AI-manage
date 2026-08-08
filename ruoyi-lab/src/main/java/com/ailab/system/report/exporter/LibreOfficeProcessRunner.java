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
    private final LabProperties properties; private final List<String> executable;
    public LibreOfficeProcessRunner(LabProperties properties) { this(properties, Arrays.asList(properties.getLibreOfficeExecutable())); }
    /** Test seam for a Java fake executable; production still passes one soffice executable argument. */
    public LibreOfficeProcessRunner(LabProperties properties, List<String> executable) {
        if (properties == null || executable == null || executable.isEmpty()) throw new IllegalArgumentException("LibreOffice configuration is required");
        this.properties = properties; this.executable = new ArrayList<String>(executable);
    }
    public byte[] convert(byte[] word, String name) throws ReportExportException {
        if (word == null || word.length == 0) throw new ReportExportException("Word input is required", false);
        Path root = null; ExecutorService readers = null;
        try {
            Path configured = Paths.get(properties.getTempDirectory()).toAbsolutePath().normalize(); Files.createDirectories(configured);
            root = Files.createTempDirectory(configured, "lo-").toAbsolutePath().normalize(); requireInside(configured, root);
            Path profile = Files.createDirectory(root.resolve("profile")); Path input = root.resolve(safeName(name) + ".docx"); Path out = Files.createDirectory(root.resolve("out"));
            Files.write(input, word, StandardOpenOption.CREATE_NEW);
            List<String> command = new ArrayList<String>(executable); command.add("--headless"); command.add("--nologo"); command.add("--nodefault"); command.add("--nofirststartwizard"); command.add("--nolockcheck"); command.add("-env:UserInstallation=" + profile.toUri().toASCIIString()); command.add("--convert-to"); command.add("pdf"); command.add("--outdir"); command.add(out.toString()); command.add(input.toString());
            Process process;
            try { process = new ProcessBuilder(command).directory(root.toFile()).start(); } catch (IOException ex) { throw new ReportExportException("LibreOffice executable is unavailable", true, ex); }
            readers = Executors.newFixedThreadPool(2); Future<String> stdout = readers.submit(() -> bounded(process.getInputStream())); Future<String> stderr = readers.submit(() -> bounded(process.getErrorStream()));
            long timeout = Math.max(1L, properties.getConversionTimeoutSeconds());
            if (!process.waitFor(timeout, TimeUnit.SECONDS)) { terminate(process); throw new ReportExportException("LibreOffice conversion timed out", true); }
            String error = stderr.get(5, TimeUnit.SECONDS); String output = stdout.get(5, TimeUnit.SECONDS);
            if (process.exitValue() != 0) throw new ReportExportException("LibreOffice conversion failed: " + compact(error, output), true);
            Path pdf = out.resolve(safeName(name) + ".pdf").normalize(); requireInside(out, pdf);
            if (!Files.isRegularFile(pdf)) throw new ReportExportException("LibreOffice produced no PDF output", true);
            byte[] bytes = Files.readAllBytes(pdf); long maximum = Math.max(1024L, properties.getMaxUploadSizeBytes());
            if (bytes.length > maximum || !pdf(bytes)) throw new ReportExportException("LibreOffice produced an invalid PDF", true); return bytes;
        } catch (ReportExportException ex) { throw ex;
        } catch (Exception ex) { throw new ReportExportException("LibreOffice conversion failed", true, ex);
        } finally { if (readers != null) readers.shutdownNow(); if (root != null) delete(root); }
    }
    private String bounded(InputStream input) throws IOException { try (InputStream stream=input; ByteArrayOutputStream out=new ByteArrayOutputStream()) { byte[] buffer=new byte[4096]; for(int n;(n=stream.read(buffer))>=0;) if(out.size()<MAX_LOG) out.write(buffer,0,Math.min(n,MAX_LOG-out.size())); return new String(out.toByteArray(), StandardCharsets.UTF_8); } }
    private void terminate(Process process) { process.destroy(); try { if (!process.waitFor(2, TimeUnit.SECONDS)) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); } } catch (InterruptedException ex) { Thread.currentThread().interrupt(); process.destroyForcibly(); } }
    private void requireInside(Path parent, Path child) throws ReportExportException { if (!child.toAbsolutePath().normalize().startsWith(parent.toAbsolutePath().normalize())) throw new ReportExportException("Unsafe conversion path", false); }
    private String safeName(String raw) { String value = raw == null ? "report" : raw.replaceAll("[^A-Za-z0-9._-]", "_"); value = value.replaceAll("^\\.+", ""); return value.isEmpty() ? "report" : value.length() > 80 ? value.substring(0,80) : value; }
    private boolean pdf(byte[] value) { return value.length >= 5 && value[0]=='%' && value[1]=='P' && value[2]=='D' && value[3]=='F' && value[4]=='-'; }
    private String compact(String stderr, String stdout) { String value=(stderr==null?"":stderr)+(stdout==null?"":" "+stdout); return value.replaceAll("[\\r\\n]+", " ").substring(0, Math.min(512, value.length())); }
    private void delete(Path root) { try { Files.walkFileTree(root, new SimpleFileVisitor<Path>() { @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException { Files.deleteIfExists(file); return FileVisitResult.CONTINUE; } @Override public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException { if (error != null) throw error; Files.deleteIfExists(directory); return FileVisitResult.CONTINUE; } }); } catch (IOException ignored) { } }
}
