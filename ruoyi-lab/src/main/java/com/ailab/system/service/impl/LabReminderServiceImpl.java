package com.ailab.system.service.impl;

import com.ailab.system.constant.LabConstants;
import com.ailab.system.domain.LabReminder;
import com.ailab.system.dto.LabAccessContext;
import com.ailab.system.dto.ReminderCandidate;
import com.ailab.system.mapper.LabDashboardMapper;
import com.ailab.system.service.LabAccessService;
import com.ailab.system.service.LabReminderService;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ruoyi.common.exception.ServiceException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LabReminderServiceImpl implements LabReminderService {
    private final LabDashboardMapper mapper;
    private final LabAccessService access;
    private final Clock clock;

    @Autowired
    public LabReminderServiceImpl(LabDashboardMapper mapper, LabAccessService access) {
        this(mapper, access, Clock.system(ZoneId.of("Asia/Shanghai")));
    }

    public LabReminderServiceImpl(LabDashboardMapper mapper, LabAccessService access, Clock clock) {
        this.mapper = mapper; this.access = access; this.clock = clock;
    }

    @Override
    @Transactional
    public int scanBlocks() {
        LocalDate today = LocalDate.now(clock);
        int inserted = 0;
        for (ReminderCandidate candidate : safe(mapper.selectOpenBlockReminderCandidates())) {
            if (candidate == null || candidate.getBlockStartTime() == null || candidate.getRecipientId() == null
                    || candidate.getTaskId() == null || candidate.getEpisodeNo() == null) continue;
            long days = ChronoUnit.DAYS.between(localDate(candidate.getBlockStartTime()), today);
            String level;
            if ("MANAGER".equals(candidate.getAudience())) {
                if (days < 14) continue;
                level = "CRITICAL";
            } else {
                if (days < 7) continue;
                level = "WARNING";
            }
            LabReminder reminder = reminder(candidate, "BLOCK", level, today);
            reminder.setTitle("任务阻塞" + days + "天：" + text(candidate.getTaskTitle(), "未命名任务"));
            reminder.setReminderContent("任务持续阻塞" + days + "天，请检查阻塞原因、协调责任人与下一步行动。");
            reminder.setIdempotencyKey("BLOCK:" + candidate.getTaskId() + ":" + candidate.getEpisodeNo() + ":"
                    + level + ":" + candidate.getRecipientId() + ":" + today);
            inserted += insertIdempotently(reminder);
        }
        return inserted;
    }

    @Override
    @Transactional
    public int scanPendingTasks() {
        LocalDate today = LocalDate.now(clock);
        YearMonth month = YearMonth.from(today);
        int remainingAfterToday = month.atEndOfMonth().getDayOfMonth() - today.getDayOfMonth();
        if (remainingAfterToday > 2) return 0;
        boolean managerEscalation = remainingAfterToday <= 1;
        String period = month.toString();
        int inserted = 0;
        for (ReminderCandidate candidate : safe(mapper.selectPendingTaskReminderCandidates(period, managerEscalation))) {
            if (candidate == null || candidate.getTaskId() == null || candidate.getRecipientId() == null) continue;
            LabReminder reminder = reminder(candidate, "PENDING_TASK", managerEscalation && "MANAGER".equals(candidate.getAudience())
                    ? "WARNING" : "INFO", today);
            reminder.setTitle(("MANAGER".equals(candidate.getAudience()) ? "月末待填汇总：" : "月末任务待填：")
                    + text(candidate.getTaskTitle(), "未命名任务"));
            reminder.setReminderContent("尚缺必填项：" + text(candidate.getMissingFields(), "待补充字段") + "。周期关闭前请完成维护。");
            reminder.setIdempotencyKey("PENDING:" + candidate.getTaskId() + ":" + candidate.getRecipientId() + ":" + today);
            inserted += insertIdempotently(reminder);
        }
        return inserted;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LabReminder> listReminders(Boolean unreadOnly, Long actorUserId) {
        Page<?> requestedPage = PageHelper.getLocalPage();
        if (requestedPage != null) PageHelper.clearPage();
        LabAccessContext scope = access.context(actorUserId);
        if (requestedPage != null) {
            Page<?> restored = PageHelper.startPage(requestedPage.getPageNum(), requestedPage.getPageSize(), requestedPage.getOrderBy());
            restored.setReasonable(requestedPage.getReasonable());
            restored.setPageSizeZero(requestedPage.getPageSizeZero());
        }
        return safeReminders(mapper.selectReminderList(scope, unreadOnly));
    }

    @Override
    @Transactional
    public void markRead(Long id, Integer version, Long actorUserId) {
        if (id == null || version == null) throw new ServiceException("Reminder id and version are required");
        LabAccessContext actor = access.context(actorUserId);
        int rows = mapper.markReminderRead(id, actor.getMemberId(), version, Date.from(clock.instant()), actor(actorUserId));
        if (rows != 1) throw new ServiceException("Reminder is not owned by the actor or changed concurrently");
    }

    @Override
    @Transactional
    public int markAllRead(Long actorUserId) {
        LabAccessContext actor = access.context(actorUserId);
        return mapper.markAllRemindersRead(actor.getMemberId(), Date.from(clock.instant()), actor(actorUserId));
    }

    private LabReminder reminder(ReminderCandidate candidate, String type, String level, LocalDate today) {
        LabReminder value = new LabReminder();
        value.setTaskId(candidate.getTaskId()); value.setBusinessType("TASK"); value.setBusinessId(candidate.getTaskId());
        value.setEpisodeNo(candidate.getEpisodeNo()); value.setRecipientId(candidate.getRecipientId());
        value.setReminderType(type); value.setReminderLevel(level);
        value.setReminderDate(Date.from(today.atStartOfDay(clock.getZone()).toInstant()));
        value.setReadFlag(LabConstants.NO); value.setSendTime(Date.from(clock.instant())); value.setVersion(0);
        value.setDelFlag(LabConstants.NO); value.setCreateBy("system");
        return value;
    }

    private int insertIdempotently(LabReminder reminder) {
        int rows = mapper.insertReminderIfAbsent(reminder);
        if (rows < 0 || rows > 1) throw new ServiceException("Invalid reminder idempotency result");
        return rows;
    }

    private LocalDate localDate(Date value) { return value.toInstant().atZone(clock.getZone()).toLocalDate(); }
    private String actor(Long actorUserId) { return String.valueOf(actorUserId); }
    private String text(String value, String fallback) { return value == null || value.trim().isEmpty() ? fallback : value.trim(); }
    private List<ReminderCandidate> safe(List<ReminderCandidate> value) { return value == null ? Collections.<ReminderCandidate>emptyList() : value; }
    private List<LabReminder> safeReminders(List<LabReminder> value) { return value == null ? Collections.<LabReminder>emptyList() : value; }
}
