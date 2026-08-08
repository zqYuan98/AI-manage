package com.ailab.system.report;

import com.ailab.system.config.LabProperties;
import com.ruoyi.common.exception.ServiceException;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.LinkOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** Contained artifact storage with isolated temporary directories and atomic publication. */
@Component
public class ReportArtifactStore {
    private static final Pattern RUN_ARTIFACT=Pattern.compile("^(archive/report-[0-9]+/runs/[A-Za-z0-9_-]{16,128})/[^/]+$");
    private final Path outputRoot; private final Path tempRoot;
    public ReportArtifactStore(LabProperties properties) {
        if (properties == null) throw new IllegalArgumentException("Lab report properties are required");
        outputRoot = configured(properties.getOutputDirectory(), "Report output directory");
        tempRoot = configured(properties.getTempDirectory(), "Report temporary directory");
        if (tempRoot.equals(outputRoot) || !tempRoot.startsWith(outputRoot)) {
            throw new ServiceException("Report temporary directory must be a child of the output directory");
        }
    }

    public String publish(Long reportId, Long jobId, String runToken, String safeName, String format, byte[] bytes) {
        if (reportId == null || reportId.longValue() <= 0 || jobId == null || jobId.longValue() <= 0 || bytes == null) throw new ServiceException("Artifact identity and bytes are required");
        if (runToken == null || !runToken.matches("[A-Za-z0-9_-]{16,128}")) throw new ServiceException("Artifact run identity is invalid");
        if (safeName == null || !safeName.matches("[A-Za-z0-9_-]{1,96}")) throw new ServiceException("Unsafe report artifact name");
        String extension = extension(format); if (bytes.length > 50L * 1024L * 1024L) throw new ServiceException("Report artifact exceeds the storage limit");
        try {
            Files.createDirectories(outputRoot); Files.createDirectories(tempRoot);
            Path realOutput = outputRoot.toRealPath(); Path realTemp = tempRoot.toRealPath();
            if (!realTemp.startsWith(realOutput)) throw new ServiceException("Report temporary directory escapes the output directory");
            Path temporaryDirectory = contained(tempRoot, "report-" + reportId + "-job-" + jobId + "-run-" + runToken); Files.createDirectories(temporaryDirectory);
            Path archiveDirectory = contained(outputRoot, "archive/report-" + reportId + "/runs/" + runToken); Files.createDirectories(archiveDirectory);
            realDirectory(temporaryDirectory, realTemp); realDirectory(archiveDirectory, realOutput);
            Path target = contained(archiveDirectory, safeName + "." + extension);
            if (Files.exists(target)) throw new ServiceException("Successful report artifacts are immutable");
            Path temporary = Files.createTempFile(temporaryDirectory, safeName + "-", ".part");
            boolean published = false;
            try {
                Files.write(temporary, bytes);
                try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException ex) { throw new ServiceException("Atomic report artifact publication is unavailable"); }
                published = true;
            } finally { if (!published) Files.deleteIfExists(temporary); }
            return outputRoot.relativize(target).toString().replace('\\', '/');
        } catch (ServiceException ex) { throw ex; }
        catch (IOException ex) { throw new ServiceException("Could not publish report artifact"); }
    }

    public Path resolve(String relativePath, String format) {
        if (relativePath == null || relativePath.length() > 1000 || relativePath.indexOf('\0') >= 0) throw new ServiceException("Report artifact path is invalid");
        Path raw = Paths.get(relativePath); if (raw.isAbsolute()) throw new ServiceException("Report artifact path must be relative");
        Path resolved = contained(outputRoot, relativePath);
        if (!resolved.getFileName().toString().toLowerCase(Locale.ROOT).endsWith("." + extension(format)) || !Files.isRegularFile(resolved)) {
            throw new ServiceException("Report artifact is not available");
        }
        try {
            Path realOutput = outputRoot.toRealPath(); Path real = resolved.toRealPath();
            if (!real.startsWith(realOutput)) throw new ServiceException("Report artifact path escapes the configured directory");
            return real;
        } catch (ServiceException ex) { throw ex; }
        catch (IOException ex) { throw new ServiceException("Report artifact is not available"); }
    }

    public byte[] read(String relativePath, String format) {
        try { return Files.readAllBytes(resolve(relativePath, format)); }
        catch (IOException ex) { throw new ServiceException("Could not read report artifact"); }
    }

    /** Removes only an artifact published by the current operation before its DB success marker. */
    public void deleteUncommitted(String relativePath) {
        if (relativePath == null) return;
        Path raw = Paths.get(relativePath); if (raw.isAbsolute()) throw new ServiceException("Report artifact path must be relative");
        Path resolved = contained(outputRoot, relativePath);
        try {
            if (Files.exists(resolved)) {
                Path realOutput = outputRoot.toRealPath(); Path real = resolved.toRealPath();
                if (!real.startsWith(realOutput)) throw new ServiceException("Refusing to clean an artifact outside the output directory");
                Files.deleteIfExists(real);
            }
        } catch (ServiceException ex) { throw ex; }
        catch (IOException ex) { throw new ServiceException("Could not clean an uncommitted report artifact"); }
    }

    /** Deletes old unreferenced files, then removes empty immutable run directories. */
    public int cleanOrphanRuns(List<String> referencedPaths, Instant cutoff) {
        if(cutoff==null)throw new ServiceException("Report artifact cleanup cutoff is required");
        Set<String> retained=new HashSet<String>();for(String reference:referencedPaths==null?Collections.<String>emptyList():referencedPaths){if(reference==null)continue;Path raw=Paths.get(reference);if(raw.isAbsolute())throw new ServiceException("Stored report artifact path must be relative");Path resolved=contained(outputRoot,reference);String normalized=outputRoot.relativize(resolved).toString().replace('\\','/');if(RUN_ARTIFACT.matcher(normalized).matches())retained.add(normalized);}
        Path archive=contained(outputRoot,"archive");if(!Files.exists(archive))return 0;
        try{Path realOutput=outputRoot.toRealPath();Path realArchive=archive.toRealPath();if(!realArchive.startsWith(realOutput))throw new ServiceException("Report archive escapes the output directory");List<Path> files=new ArrayList<Path>();List<Path> runs=new ArrayList<Path>();try(Stream<Path> paths=Files.walk(realArchive,4)){Path[] values=paths.toArray(Path[]::new);for(Path value:values){if(Files.isSymbolicLink(value))throw new ServiceException("Refusing to clean an unsafe report archive run");if(Files.isRegularFile(value,LinkOption.NOFOLLOW_LINKS)){String relative=realOutput.relativize(value).toString().replace('\\','/');if(RUN_ARTIFACT.matcher(relative).matches()&&!retained.contains(relative)&&Files.getLastModifiedTime(value,LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(cutoff))files.add(value);}else if(Files.isDirectory(value,LinkOption.NOFOLLOW_LINKS)){String relative=realOutput.relativize(value).toString().replace('\\','/');if(RUN_ARTIFACT.matcher(relative+"/artifact").matches()&&Files.getLastModifiedTime(value,LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(cutoff))runs.add(value);}}}
            for(Path file:files){if(!file.toRealPath().startsWith(realOutput))throw new ServiceException("Refusing to clean an unsafe report archive artifact");Files.deleteIfExists(file);}Collections.sort(runs,Comparator.reverseOrder());for(Path run:runs)try{Files.deleteIfExists(run);}catch(java.nio.file.DirectoryNotEmptyException ignored){/* referenced artifacts remain */}return files.size();
        }catch(ServiceException ex){throw ex;}catch(IOException ex){throw new ServiceException("Could not reconcile report artifact archives");}
    }

    private Path contained(Path root, String child) {
        Path resolved = root.resolve(child).toAbsolutePath().normalize();
        if (!resolved.startsWith(root)) throw new ServiceException("Report storage path escapes the configured directory");
        return resolved;
    }
    private void realDirectory(Path directory, Path realRoot) throws IOException {
        if (!directory.toRealPath().startsWith(realRoot)) throw new ServiceException("Report storage directory escapes the configured root");
    }
    private String extension(String format) {
        if ("JSON".equals(format)) return "json"; if ("MARKDOWN".equals(format)) return "md";
        if ("WORD".equals(format)) return "docx"; if ("PDF".equals(format)) return "pdf";
        throw new ServiceException("Unsupported report artifact format");
    }
    private static Path configured(String value, String label) {
        if (value == null || value.trim().isEmpty()) throw new ServiceException(label + " is required");
        return Paths.get(value).toAbsolutePath().normalize();
    }
}
