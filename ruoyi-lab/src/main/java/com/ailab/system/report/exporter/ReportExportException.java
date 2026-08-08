package com.ailab.system.report.exporter;

import java.io.IOException;

/** A controlled export failure that lets the caller decide whether a conversion can be retried. */
public final class ReportExportException extends IOException {
    private final boolean retryable;
    private byte[] preservedWord;
    public ReportExportException(String message, boolean retryable) { super(message); this.retryable = retryable; }
    public ReportExportException(String message, boolean retryable, Throwable cause) { super(message, cause); this.retryable = retryable; }
    public boolean isRetryable() { return retryable; }
    public byte[] getPreservedWord() { return preservedWord == null ? null : preservedWord.clone(); }
    ReportExportException preserveWord(byte[] word) {
        if (word != null && preservedWord == null) preservedWord = word.clone();
        return this;
    }
}
