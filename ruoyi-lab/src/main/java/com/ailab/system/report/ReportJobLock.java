package com.ailab.system.report;

/** Short distributed guard; database job state remains the durable duplicate defense. */
public interface ReportJobLock {
    String tryAcquire(Long reportId, String step);
    default boolean renew(Long reportId, String step, String token) { return true; }
    void release(Long reportId, String step, String token);
}
