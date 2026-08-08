package com.ailab.system.service;

/** Optional bridge implemented by the report engine when Task 12 is installed. */
public interface LabReportRecoveryWorker {
    int recoverInterruptedJobs();
}
