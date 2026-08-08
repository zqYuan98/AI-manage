package com.ailab.system.quartz;

import com.ailab.system.config.LabProperties;
import com.ailab.system.mapper.LabDashboardMapper;
import com.ailab.system.service.LabPerformanceService;
import com.ailab.system.service.LabReminderService;
import com.ailab.system.service.LabReportRecoveryWorker;
import com.ruoyi.common.exception.ServiceException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Parameterless Quartz facade referenced by the seeded RuoYi jobs. */
@Component("labScheduleTask")
public class LabScheduleTask {
    private static final Logger LOG = LoggerFactory.getLogger(LabScheduleTask.class);
    private static final long TEMP_RETENTION_HOURS = 24L;
    private final LabReminderService reminders;
    private final LabPerformanceService performance;
    private final LabDashboardMapper mapper;
    private final LabProperties properties;
    private final Clock clock;
    private final LabReportRecoveryWorker reportRecoveryWorker;

    @Autowired
    public LabScheduleTask(LabReminderService reminders, LabPerformanceService performance,
            LabDashboardMapper mapper, LabProperties properties, Optional<LabReportRecoveryWorker> recoveryWorker) {
        this(reminders, performance, mapper, properties, Clock.system(ZoneId.of("Asia/Shanghai")),
                recoveryWorker.orElse(null));
    }

    public LabScheduleTask(LabReminderService reminders, LabPerformanceService performance,
            LabDashboardMapper mapper, LabProperties properties, Clock clock, LabReportRecoveryWorker reportRecoveryWorker) {
        this.reminders = reminders; this.performance = performance; this.mapper = mapper;
        this.properties = properties; this.clock = clock; this.reportRecoveryWorker = reportRecoveryWorker;
    }

    public void scanBlocks() {
        try { LOG.info("AI Lab block reminder scan inserted {} rows", reminders.scanBlocks()); }
        catch (RuntimeException error) { LOG.error("AI Lab block reminder scan failed", error); }
    }

    public void scanPendingTasks() {
        try { LOG.info("AI Lab pending-task scan inserted {} rows", reminders.scanPendingTasks()); }
        catch (RuntimeException error) { LOG.error("AI Lab pending-task scan failed", error); }
    }

    public void closeDuePeriods() {
        LocalDate today = LocalDate.now(clock);
        if (today.getDayOfMonth() != 1) return;
        String period = YearMonth.from(today).minusMonths(1).toString();
        try {
            List<Long> managerUsers = mapper.selectActiveManagerUserIds();
            if (managerUsers == null) managerUsers = Collections.emptyList();
            if (managerUsers.isEmpty()) {
                LOG.error("AI Lab scheduled close skipped: no enabled manager user is mapped to an active lab member");
                return;
            }
            performance.closePeriod(period, "scheduled month close", managerUsers.get(0));
            LOG.info("AI Lab period {} closed or already closed", period);
        } catch (RuntimeException error) {
            LOG.error("AI Lab period {} close failed; later jobs remain isolated", period, error);
        }
    }

    public void cleanReportTempFiles() {
        Path output = configuredPath(properties.getOutputDirectory(), "Report output directory");
        Path temp = configuredPath(properties.getTempDirectory(), "Report temporary directory");
        try {
            Files.createDirectories(output);
            Files.createDirectories(temp);
            Path realOutput = output.toRealPath();
            Path realTemp = temp.toRealPath();
            if (realTemp.equals(realOutput) || !realTemp.startsWith(realOutput)) {
                throw new ServiceException("Report temporary directory must be a child of the report output directory");
            }
            final Instant cutoff = clock.instant().minus(TEMP_RETENTION_HOURS, ChronoUnit.HOURS);
            int deleted = 0;
            try (Stream<Path> paths = Files.walk(realTemp)) {
                Path[] files = paths.filter(path -> Files.isRegularFile(path)).toArray(Path[]::new);
                for (Path file : files) {
                    Path realFile = file.toRealPath();
                    if (!realFile.startsWith(realTemp)) throw new ServiceException("Refusing to clean a temporary file outside the configured directory");
                    if (Files.getLastModifiedTime(realFile).toInstant().isBefore(cutoff) && Files.deleteIfExists(realFile)) deleted++;
                }
            }
            LOG.info("AI Lab report temporary cleanup removed {} files", deleted);
        } catch (ServiceException error) {
            throw error;
        } catch (IOException error) {
            throw new ServiceException("Could not safely clean the report temporary directory: " + error.getMessage());
        }
    }

    public void recoverReportJobs() {
        if (reportRecoveryWorker == null) {
            LOG.warn("AI Lab report recovery worker is not installed yet; recovery scan skipped");
            return;
        }
        try { LOG.info("AI Lab report recovery resumed {} jobs", reportRecoveryWorker.recoverInterruptedJobs()); }
        catch (RuntimeException error) { LOG.error("AI Lab report recovery failed", error); }
    }

    private Path configuredPath(String value, String label) {
        if (value == null || value.trim().isEmpty()) throw new ServiceException(label + " is required");
        return Paths.get(value).toAbsolutePath().normalize();
    }
}
