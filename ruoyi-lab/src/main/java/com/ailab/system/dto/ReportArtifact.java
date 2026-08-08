package com.ailab.system.dto;

import java.nio.file.Path;

/** Authorized server-side artifact; controllers never accept or expose an arbitrary path. */
public final class ReportArtifact {
    private final Path path; private final String fileName; private final String contentType;
    public ReportArtifact(Path path, String fileName, String contentType) {
        this.path = path; this.fileName = fileName; this.contentType = contentType;
    }
    public Path getPath() { return path; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
}
