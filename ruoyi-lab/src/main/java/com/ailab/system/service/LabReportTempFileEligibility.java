package com.ailab.system.service;

import java.nio.file.Path;

/**
 * Report-job lifecycle gate for temporary-file cleanup.
 *
 * <p>The path is relative to the configured report temporary directory. Implementations must return
 * {@code false} for unknown and non-terminal jobs; absence of an implementation is fail-closed.</p>
 */
public interface LabReportTempFileEligibility {
    boolean isDeletionEligible(Path relativePath);
}
