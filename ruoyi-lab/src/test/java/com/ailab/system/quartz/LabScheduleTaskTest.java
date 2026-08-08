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
    void closeIsolatesFailureWhileResolvingTheTrustedManager() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-01T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
        when(mapper.selectActiveManagerUserIds()).thenThrow(new IllegalStateException("database unavailable"));
        LabScheduleTask task = task(clock, properties(directory.resolve("reports"), directory.resolve("reports/tmp")));

        assertDoesNotThrow(task::closeDuePeriods);
    }

    @Test
    void cleanupRemovesOnlyOldFilesInsideConfiguredReportTempDirectory() throws Exception {
        Path output = Files.createDirectories(directory.resolve("reports"));
        Path temp = Files.createDirectories(output.resolve("tmp"));
        Path oldTemp = Files.write(temp.resolve("old.tmp"), new byte[] { 1 });
        Path freshTemp = Files.write(temp.resolve("fresh.tmp"), new byte[] { 2 });
        Path archive = Files.write(output.resolve("report.pdf"), new byte[] { 3 });
        Files.setLastModifiedTime(oldTemp, FileTime.from(Instant.parse("2026-08-30T00:00:00Z")));
        Files.setLastModifiedTime(freshTemp, FileTime.from(Instant.parse("2026-09-01T01:30:00Z")));
        LabScheduleTask task = task(Clock.fixed(Instant.parse("2026-09-01T02:00:00Z"), ZoneId.of("Asia/Shanghai")), properties(output, temp));

        task.cleanReportTempFiles();

        assertFalse(Files.exists(oldTemp)); assertTrue(Files.exists(freshTemp)); assertTrue(Files.exists(archive));
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
