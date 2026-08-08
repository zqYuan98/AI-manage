package com.ailab.system.quartz;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailab.system.config.LabProperties;
import com.ailab.system.mapper.LabDashboardMapper;
import com.ailab.system.service.LabPerformanceService;
import com.ailab.system.service.LabReminderService;
import com.ailab.system.service.LabReportRecoveryWorker;
import com.ailab.system.service.LabReportTempFileEligibility;
import com.ruoyi.common.exception.ServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LabScheduleTaskTest {
    @Mock private LabReminderService reminders;
    @Mock private LabPerformanceService performance;
    @Mock private LabDashboardMapper mapper;
    @Mock private LabReportRecoveryWorker recoveryWorker;
    @TempDir Path directory;

    @Test
    void parameterlessFacadeDelegatesScansAndClosesPreviousMonthOnFirstDayWithTrustedManager() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-01T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
        when(mapper.selectActiveManagerUserIds()).thenReturn(Collections.singletonList(39101L));
        LabScheduleTask task = task(clock, properties(directory.resolve("reports"), directory.resolve("reports/tmp")));

        task.scanBlocks(); task.scanPendingTasks(); task.closeDuePeriods();

        verify(reminders).scanBlocks(); verify(reminders).scanPendingTasks();
        verify(performance).closePeriod("2026-08", "scheduled month close", 39101L);
    }

    @Test
    void closeDoesNothingOffFirstDayAndRecoveryWithoutWorkerIsObservableButNonFailing() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-02T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
        LabScheduleTask task = task(clock, properties(directory.resolve("reports"), directory.resolve("reports/tmp")));
        task.closeDuePeriods();
        assertDoesNotThrow(task::recoverReportJobs);
    }

    @Test
    void criticalSchedulerFailuresPropagateSoQuartzCanAlertAndRetry() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-01T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
        when(mapper.selectActiveManagerUserIds()).thenThrow(new IllegalStateException("database unavailable"));
        LabScheduleTask task = task(clock, properties(directory.resolve("reports"), directory.resolve("reports/tmp")));

        assertThrows(IllegalStateException.class, task::closeDuePeriods);

        when(reminders.scanBlocks()).thenThrow(new IllegalStateException("block scan unavailable"));
        assertThrows(IllegalStateException.class, task::scanBlocks);
        when(reminders.scanPendingTasks()).thenThrow(new IllegalStateException("pending scan unavailable"));
        assertThrows(IllegalStateException.class, task::scanPendingTasks);

        LabScheduleTask recovery = new LabScheduleTask(reminders, performance, mapper,
                properties(directory.resolve("reports"), directory.resolve("reports/tmp")), clock, recoveryWorker);
        when(recoveryWorker.recoverInterruptedJobs()).thenThrow(new IllegalStateException("recovery unavailable"));
        assertThrows(IllegalStateException.class, recovery::recoverReportJobs);
    }

    @Test
    void cleanupFailsClosedWithoutReportJobLifecycleEligibility() throws Exception {
        Path output = Files.createDirectories(directory.resolve("reports"));
        Path temp = Files.createDirectories(output.resolve("tmp"));
        Path oldTemp = Files.write(temp.resolve("old.tmp"), new byte[] { 1 });
        Path freshTemp = Files.write(temp.resolve("fresh.tmp"), new byte[] { 2 });
        Path archive = Files.write(output.resolve("report.pdf"), new byte[] { 3 });
        Files.setLastModifiedTime(oldTemp, FileTime.from(Instant.parse("2026-08-30T00:00:00Z")));
        Files.setLastModifiedTime(freshTemp, FileTime.from(Instant.parse("2026-09-01T01:30:00Z")));
        LabScheduleTask task = task(Clock.fixed(Instant.parse("2026-09-01T02:00:00Z"), ZoneId.of("Asia/Shanghai")), properties(output, temp));

        task.cleanReportTempFiles();

        assertTrue(Files.exists(oldTemp), "an unknown or running job temporary file must never be deleted by age alone");
        assertTrue(Files.exists(freshTemp)); assertTrue(Files.exists(archive));
    }

    @Test
    void cleanupDeletesOnlyOldFilesExplicitlyEligibleByTerminalReportJobLifecycle() throws Exception {
        Path output = Files.createDirectories(directory.resolve("reports"));
        Path temp = Files.createDirectories(output.resolve("tmp"));
        Path terminal = Files.write(temp.resolve("terminal-job.tmp"), new byte[] { 1 });
        Path running = Files.write(temp.resolve("running-job.tmp"), new byte[] { 2 });
        Files.setLastModifiedTime(terminal, FileTime.from(Instant.parse("2026-08-30T00:00:00Z")));
        Files.setLastModifiedTime(running, FileTime.from(Instant.parse("2026-08-30T00:00:00Z")));
        LabReportTempFileEligibility eligibility = relative -> relative.toString().startsWith("terminal-job");
        LabScheduleTask task = new LabScheduleTask(reminders, performance, mapper, properties(output, temp),
                Clock.fixed(Instant.parse("2026-09-01T02:00:00Z"), ZoneId.of("Asia/Shanghai")), null, eligibility);

        task.cleanReportTempFiles();

        assertFalse(Files.exists(terminal));
        assertTrue(Files.exists(running), "non-terminal jobs are never age-only cleanup candidates");
    }

    @Test
    void cleanupRejectsTempDirectoryOutsideReportRoot() throws Exception {
        Path output = Files.createDirectories(directory.resolve("reports"));
        Path outside = Files.createDirectories(directory.resolve("outside"));
        Path valuable = Files.write(outside.resolve("valuable.txt"), new byte[] { 9 });
        LabScheduleTask task = task(Clock.systemUTC(), properties(output, outside));
        assertThrows(ServiceException.class, task::cleanReportTempFiles);
        assertTrue(Files.exists(valuable));
    }

    private LabScheduleTask task(Clock clock, LabProperties properties) {
        return new LabScheduleTask(reminders, performance, mapper, properties, clock, null);
    }
    private LabProperties properties(Path output, Path temp) {
        LabProperties properties = new LabProperties(); properties.setOutputDirectory(output.toString()); properties.setTempDirectory(temp.toString()); return properties;
    }
}
