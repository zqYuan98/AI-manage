package com.ailab.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailab.system.domain.LabReminder;
import com.ailab.system.dto.LabAccessContext;
import com.ailab.system.dto.ReminderCandidate;
import com.ailab.system.mapper.LabDashboardMapper;
import com.ailab.system.service.impl.LabAccessServiceImpl;
import com.ailab.system.service.impl.LabReminderServiceImpl;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ruoyi.common.exception.ServiceException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LabReminderServiceTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-15T01:00:00Z"), ZONE);

    @Mock private LabDashboardMapper mapper;
    @Mock private LabAccessService access;
    private LabReminderService service;

    @BeforeEach
    void setUp() { service = new LabReminderServiceImpl(mapper, access, CLOCK); }

    @Test
    void blockEscalationUsesInclusiveSevenAndFourteenDayBoundariesAndEpisodeRecipientKeys() {
        ReminderCandidate day7 = block(11L, 1, "2026-08-08T01:00:00Z", 101L, "OWNER");
        ReminderCandidate day14 = block(12L, 2, "2026-08-01T01:00:00Z", 901L, "MANAGER");
        ReminderCandidate tooEarly = block(13L, 1, "2026-08-09T01:00:00Z", 103L, "OWNER");
        when(mapper.selectOpenBlockReminderCandidates()).thenReturn(Arrays.asList(day7, day14, tooEarly));
        when(mapper.insertReminderIfAbsent(any(LabReminder.class))).thenReturn(1);

        assertEquals(2, service.scanBlocks());

        ArgumentCaptor<LabReminder> captor = ArgumentCaptor.forClass(LabReminder.class);
        verify(mapper, org.mockito.Mockito.times(2)).insertReminderIfAbsent(captor.capture());
        assertEquals("BLOCK:11:1:WARNING:101:2026-08-15", captor.getAllValues().get(0).getIdempotencyKey());
        assertEquals("WARNING", captor.getAllValues().get(0).getReminderLevel());
        assertEquals("BLOCK:12:2:CRITICAL:901:2026-08-15", captor.getAllValues().get(1).getIdempotencyKey());
        assertEquals("CRITICAL", captor.getAllValues().get(1).getReminderLevel());
    }

    @Test
    void fourteenDayBlockKeepsDailyOwnerWarningAndAddsManagerCriticalWithoutDuplicates() {
        ReminderCandidate owner = block(21L, 4, "2026-07-20T01:00:00Z", 102L, "OWNER");
        ReminderCandidate manager = block(21L, 4, "2026-07-20T01:00:00Z", 901L, "MANAGER");
        when(mapper.selectOpenBlockReminderCandidates()).thenReturn(Arrays.asList(owner, manager));
        when(mapper.insertReminderIfAbsent(any(LabReminder.class))).thenReturn(1, 0);

        assertEquals(1, service.scanBlocks());

        ArgumentCaptor<LabReminder> captor = ArgumentCaptor.forClass(LabReminder.class);
        verify(mapper, org.mockito.Mockito.times(2)).insertReminderIfAbsent(captor.capture());
        assertTrue(captor.getAllValues().stream().anyMatch(r -> r.getIdempotencyKey().contains(":WARNING:102:")));
        assertTrue(captor.getAllValues().stream().anyMatch(r -> r.getIdempotencyKey().contains(":CRITICAL:901:")));
    }

    @Test
    void pendingScanRunsOnlyInLastThreeDaysAndPreservesReadableFieldSummary() {
        Clock monthEnd = Clock.fixed(Instant.parse("2026-08-29T01:00:00Z"), ZONE);
        service = new LabReminderServiceImpl(mapper, access, monthEnd);
        ReminderCandidate missing = new ReminderCandidate();
        missing.setTaskId(31L); missing.setRecipientId(103L); missing.setAudience("OWNER");
        missing.setMissingFields("计划完成日期、交付物、结果说明");
        when(mapper.selectPendingTaskReminderCandidates("2026-08", false)).thenReturn(Collections.singletonList(missing));
        when(mapper.insertReminderIfAbsent(any(LabReminder.class))).thenReturn(1);

        assertEquals(1, service.scanPendingTasks());

        ArgumentCaptor<LabReminder> captor = ArgumentCaptor.forClass(LabReminder.class);
        verify(mapper).insertReminderIfAbsent(captor.capture());
        assertEquals("PENDING:31:103:2026-08-29", captor.getValue().getIdempotencyKey());
        assertTrue(captor.getValue().getReminderContent().contains("计划完成日期、交付物、结果说明"));
    }

    @Test
    void pendingScanEscalatesToManagersOneDayBeforeCloseAndDoesNothingOutsideWindow() {
        Clock oneDay = Clock.fixed(Instant.parse("2026-08-30T01:00:00Z"), ZONE);
        service = new LabReminderServiceImpl(mapper, access, oneDay);
        when(mapper.selectPendingTaskReminderCandidates("2026-08", true)).thenReturn(Collections.<ReminderCandidate>emptyList());
        assertEquals(0, service.scanPendingTasks());
        verify(mapper).selectPendingTaskReminderCandidates("2026-08", true);

        service = new LabReminderServiceImpl(mapper, access,
                Clock.fixed(Instant.parse("2026-08-20T01:00:00Z"), ZONE));
        assertEquals(0, service.scanPendingTasks());
        verify(mapper, never()).selectPendingTaskReminderCandidates("2026-08", false);
    }

    @Test
    void listScopeComesOnlyFromTrustedAccessContext() {
        LabAccessContext lead = context(2L, 102L, LabAccessServiceImpl.LEAD, "algorithm");
        when(access.context(2L)).thenReturn(lead);
        when(mapper.selectReminderList(eq(lead), eq(Boolean.TRUE))).thenReturn(Collections.<LabReminder>emptyList());

        service.listReminders(true, 2L);

        verify(mapper).selectReminderList(lead, true);
    }

    @Test
    void listResolvesTrustedScopeBeforeRestoringPageHelperSoReminderTotalsArePreserved() {
        LabAccessContext member = context(3L, 103L, LabAccessServiceImpl.MEMBER, "algorithm");
        PageHelper.startPage(3, 20, "reminder_date desc");
        try {
            when(access.context(3L)).thenAnswer(invocation -> {
                assertNull(PageHelper.getLocalPage(), "access lookup must not consume the reminder page request");
                return member;
            });
            when(mapper.selectReminderList(member, true)).thenAnswer(invocation -> {
                Page<?> request = PageHelper.getLocalPage();
                assertEquals(3, request.getPageNum()); assertEquals(20, request.getPageSize());
                assertEquals("reminder_date desc", request.getOrderBy());
                Page<LabReminder> result = new Page<LabReminder>(3, 20); result.setTotal(41L); return result;
            });

            List<LabReminder> result = service.listReminders(true, 3L);

            assertTrue(result instanceof Page); assertEquals(41L, ((Page<?>) result).getTotal());
        } finally {
            PageHelper.clearPage();
        }
    }

    @Test
    void readMutationsAreRecipientOnlyEvenForManagerAndUseOptimisticVersion() {
        LabAccessContext manager = context(1L, 901L, LabAccessServiceImpl.MANAGER, "manage");
        when(access.context(1L)).thenReturn(manager);
        when(mapper.markReminderRead(55L, 901L, 3, Date.from(CLOCK.instant()), "1")).thenReturn(1);

        service.markRead(55L, 3, 1L);
        verify(mapper).markReminderRead(55L, 901L, 3, Date.from(CLOCK.instant()), "1");

        when(mapper.markReminderRead(56L, 901L, 0, Date.from(CLOCK.instant()), "1")).thenReturn(0);
        assertThrows(ServiceException.class, () -> service.markRead(56L, 0, 1L));
    }

    @Test
    void markAllReadCanOnlyTargetAuthenticatedRecipient() {
        LabAccessContext member = context(3L, 103L, LabAccessServiceImpl.MEMBER, "algorithm");
        when(access.context(3L)).thenReturn(member);
        when(mapper.markAllRemindersRead(103L, Date.from(CLOCK.instant()), "3")).thenReturn(4);
        assertEquals(4, service.markAllRead(3L));
        verify(mapper).markAllRemindersRead(103L, Date.from(CLOCK.instant()), "3");
    }

    private ReminderCandidate block(Long taskId, int episode, String start, Long recipient, String audience) {
        ReminderCandidate value = new ReminderCandidate(); value.setTaskId(taskId); value.setEpisodeNo(episode);
        value.setBlockStartTime(Date.from(Instant.parse(start))); value.setRecipientId(recipient); value.setAudience(audience);
        value.setTaskTitle("blocked-" + taskId); return value;
    }

    private LabAccessContext context(Long user, Long member, String role, String line) {
        LabAccessContext value = new LabAccessContext(); value.setUserId(user); value.setMemberId(member);
        value.setRoleKey(role); value.setBizLine(line); return value;
    }
}
