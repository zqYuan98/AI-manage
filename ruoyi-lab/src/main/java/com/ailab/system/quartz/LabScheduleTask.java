package com.ailab.system.quartz;

import com.ailab.system.config.LabProperties;
import com.ailab.system.mapper.LabDashboardMapper;
import com.ailab.system.mapper.LabReportMapper;
import com.ailab.system.report.ReportArtifactStore;
import com.ailab.system.service.LabPerformanceService;
import com.ailab.system.service.LabReminderService;
import com.ailab.system.service.LabReportRecoveryWorker;
import com.ailab.system.service.LabReportTempFileEligibility;
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
import java.util.ArrayList;
import java.util.Comparator;
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
    private final LabReportTempFileEligibility tempFileEligibility;
    private final LabReportMapper reportMapper;
    private final ReportArtifactStore artifactStore;

    @Autowired
    public LabScheduleTask(LabReminderService reminders, LabPerformanceService performance,
            LabDashboardMapper mapper, LabProperties properties, Optional<LabReportRecoveryWorker> recoveryWorker,
            Optional<LabReportTempFileEligibility> tempFileEligibility,LabReportMapper reportMapper,ReportArtifactStore artifactStore) {
        this(reminders, performance, mapper, properties, Clock.system(ZoneId.of("Asia/Shanghai")),
                recoveryWorker.orElse(null), tempFileEligibility.orElse(null),reportMapper,artifactStore);
    }

    public LabScheduleTask(LabReminderService reminders, LabPerformanceService performance,
            LabDashboardMapper mapper, LabProperties properties, Clock clock, LabReportRecoveryWorker reportRecoveryWorker) {
        this(reminders, performance, mapper, properties, clock, reportRecoveryWorker, null);
    }

    public LabScheduleTask(LabReminderService reminders, LabPerformanceService performance,
            LabDashboardMapper mapper, LabProperties properties, Clock clock, LabReportRecoveryWorker reportRecoveryWorker,
            LabReportTempFileEligibility tempFileEligibility) {
        this(reminders,performance,mapper,properties,clock,reportRecoveryWorker,tempFileEligibility,null,null);
    }

    public LabScheduleTask(LabReminderService reminders, LabPerformanceService performance,
            LabDashboardMapper mapper, LabProperties properties, Clock clock, LabReportRecoveryWorker reportRecoveryWorker,
            LabReportTempFileEligibility tempFileEligibility,LabReportMapper reportMapper,ReportArtifactStore artifactStore) {
        this.reminders = reminders; this.performance = performance; this.mapper = mapper;
        this.properties = properties; this.clock = clock; this.reportRecoveryWorker = reportRecoveryWorker;
        this.tempFileEligibility = tempFileEligibility;this.reportMapper=reportMapper;this.artifactStore=artifactStore;
    }

    public void scanBlocks() {
        try { LOG.info("AI Lab block reminder scan inserted {} rows", reminders.scanBlocks()); }
        catch (RuntimeException error) { LOG.error("AI Lab block reminder scan failed", error); throw error; }
    }

    public void scanPendingTasks() {
        try { LOG.info("AI Lab pending-task scan inserted {} rows", reminders.scanPendingTasks()); }
        catch (RuntimeException error) { LOG.error("AI Lab pending-task scan failed", error); throw error; }
    }

    public void closeDuePeriods() {
        LocalDate today = LocalDate.now(clock);
        if (today.getDayOfMonth() != 1) return;
        String period = YearMonth.from(today).minusMonths(1).toString();
        try {
            List<Long> managerUsers = mapper.selectActiveManagerUserIds();
            if (managerUsers == null) managerUsers = Collections.emptyList();
            if (managerUsers.isEmpty()) {
                throw new ServiceException("AI Lab scheduled close requires an enabled manager mapped to an active lab member");
            }
            performance.closePeriod(period, "scheduled month close", managerUsers.get(0));
            LOG.info("AI Lab period {} closed or already closed", period);
        } catch (RuntimeException error) {
            LOG.error("AI Lab period {} close failed", period, error);
            throw error;
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
            if (tempFileEligibility == null) {
                LOG.warn("AI Lab report temporary cleanup eligibility is not installed; cleanup skipped fail-closed");
            } else {
            final Instant cutoff = clock.instant().minus(TEMP_RETENTION_HOURS, ChronoUnit.HOURS);
            List<Path> directories=new ArrayList<Path>();try(Stream<Path> paths=Files.walk(realTemp)){Path[] values=paths.filter(path->Files.isDirectory(path)&&!path.equals(realTemp)).toArray(Path[]::new);for(Path directory:values){Path realDirectory=directory.toRealPath();if(!realDirectory.startsWith(realTemp))throw new ServiceException("Refusing to clean a temporary directory outside the configured directory");Path relative=realTemp.relativize(realDirectory);if(Files.getLastModifiedTime(realDirectory).toInstant().isBefore(cutoff)&&tempFileEligibility.isDeletionEligible(relative))directories.add(realDirectory);}}
            int deleted = 0;
            try (Stream<Path> paths = Files.walk(realTemp)) {
                Path[] files = paths.filter(path -> Files.isRegularFile(path)).toArray(Path[]::new);
                for (Path file : files) {
                    Path realFile = file.toRealPath();
                    if (!realFile.startsWith(realTemp)) throw new ServiceException("Refusing to clean a temporary file outside the configured directory");
                    Path relativeFile = realTemp.relativize(realFile);
                    if (Files.getLastModifiedTime(realFile).toInstant().isBefore(cutoff)
                            && tempFileEligibility.isDeletionEligible(relativeFile) && Files.deleteIfExists(realFile)) deleted++;
                }
            }
            Collections.sort(directories,new Comparator<Path>(){@Override public int compare(Path left,Path right){return Integer.compare(right.getNameCount(),left.getNameCount());}});for(Path directory:directories){try{if(Files.deleteIfExists(directory))deleted++;}catch(java.nio.file.DirectoryNotEmptyException ignored){/* another owned residue remains */}}
            LOG.info("AI Lab report temporary cleanup removed {} files", deleted);
            }
            if(artifactStore!=null&&reportMapper!=null){int deleted=artifactStore.cleanOrphanRuns(reportMapper.selectReferencedReportArtifactPaths(),clock.instant().minus(7,ChronoUnit.DAYS));LOG.info("AI Lab report archive reconciliation removed {} orphan artifact files",deleted);}
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
        catch (RuntimeException error) { LOG.error("AI Lab report recovery failed", error); throw error; }
    }

    private Path configuredPath(String value, String label) {
        if (value == null || value.trim().isEmpty()) throw new ServiceException(label + " is required");
        return Paths.get(value).toAbsolutePath().normalize();
    }
}
