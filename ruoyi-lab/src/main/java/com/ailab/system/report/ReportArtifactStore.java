package com.ailab.system.report;

import com.ailab.system.config.LabProperties;
import com.ruoyi.common.exception.ServiceException;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Contained artifact storage with isolated temporary directories and atomic publication. */
@Component
public class ReportArtifactStore {
    private final Path outputRoot; private final Path tempRoot;
    public ReportArtifactStore(LabProperties properties) {
        if (properties == null) throw new IllegalArgumentException("Lab report properties are required");
        outputRoot = configured(properties.getOutputDirectory(), "Report output directory");
        tempRoot = configured(properties.getTempDirectory(), "Report temporary directory");
        if (tempRoot.equals(outputRoot) || !tempRoot.startsWith(outputRoot)) {
            throw new ServiceException("Report temporary directory must be a child of the output directory");
        }
    }

    public String publish(Long reportId, String safeName, String format, byte[] bytes) {
        if (reportId == null || reportId.longValue() <= 0 || bytes == null) throw new ServiceException("Artifact identity and bytes are required");
        if (safeName == null || !safeName.matches("[A-Za-z0-9_-]{1,96}")) throw new ServiceException("Unsafe report artifact name");
        String extension = extension(format); if (bytes.length > 50L * 1024L * 1024L) throw new ServiceException("Report artifact exceeds the storage limit");
        try {
            Files.createDirectories(outputRoot); Files.createDirectories(tempRoot);
            Path realOutput = outputRoot.toRealPath(); Path realTemp = tempRoot.toRealPath();
            if (!realTemp.startsWith(realOutput)) throw new ServiceException("Report temporary directory escapes the output directory");
            Path temporaryDirectory = contained(tempRoot, "report-" + reportId); Files.createDirectories(temporaryDirectory);
            Path archiveDirectory = contained(outputRoot, "archive/report-" + reportId); Files.createDirectories(archiveDirectory);
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

    /** Deletes only the deterministic target for an artifact whose DB status is not SUCCESS. */
    public void discardOrphan(Long reportId, String safeName, String format) {
        if (reportId == null || reportId.longValue() <= 0 || safeName == null
                || !safeName.matches("[A-Za-z0-9_-]{1,96}")) {
            throw new ServiceException("Artifact identity is invalid");
        }
        deleteUncommitted("archive/report-" + reportId + "/" + safeName + "." + extension(format));
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
