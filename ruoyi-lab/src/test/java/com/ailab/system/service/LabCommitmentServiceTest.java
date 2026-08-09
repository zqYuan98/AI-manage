package com.ailab.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailab.system.constant.LabConstants;
import com.ailab.system.domain.LabTask;
import com.ailab.system.domain.LabTaskBlockEvent;
import com.ailab.system.domain.LabTaskExecutionEvent;
import com.ailab.system.dto.LabAccessContext;
import com.ailab.system.dto.WeeklyCommitmentCommand;
import com.ailab.system.mapper.LabCommitmentMapper;
import com.ailab.system.mapper.LabTaskMapper;
import com.ailab.system.service.impl.LabCommitmentServiceImpl;
import com.ailab.system.service.impl.LabTaskExecutionMigrationService;
import com.ruoyi.common.exception.ServiceException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LabCommitmentServiceTest {
    private final LabCommitmentMapper commitments = mock(LabCommitmentMapper.class);
    private final LabTaskMapper tasks = mock(LabTaskMapper.class);
    private final LabAccessService access = mock(LabAccessService.class);
    private final LabTaskExecutionMigrationService cutover = mock(LabTaskExecutionMigrationService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-09T04:00:00Z"), ZoneOffset.UTC);
    private LabCommitmentService service;

    @BeforeEach
    void setUp() {
        service = new LabCommitmentServiceImpl(commitments, tasks, access, cutover, clock);
        when(access.context(100L)).thenReturn(context(7L));
        when(tasks.insertTask(any(LabTask.class))).thenAnswer(invocation -> {
            LabTask task = invocation.getArgument(0); if (task.getId() == null) task.setId(900L); return 1;
        });
        when(commitments.insertExecutionEvent(any(LabTaskExecutionEvent.class))).thenReturn(1);
        when(commitments.updateExecutionFact(anyLong(), anyInt(), anyString(), anyString(), anyString(),
                nullable(Date.class), nullable(String.class), nullable(String.class), nullable(String.class), anyString())).thenReturn(1);
    }

    @Test
    void createWeeklyCommitmentStartsActive() {
        when(tasks.selectTaskForUpdate(10L)).thenReturn(monthParent());
        WeeklyCommitmentCommand command = command(); command.setParentTaskId(10L);

        LabTask created = service.create(command, 100L);

        assertEquals(LabConstants.EXECUTION_ACTIVE, created.getExecutionStatus());
        assertEquals(LabConstants.TASK_LEVEL_WEEK, created.getTaskLevel());
        assertEquals(7L, created.getOwnerId());
        assertEquals(0, created.getExecutionVersion());
        verify(commitments).insertExecutionEvent(any(LabTaskExecutionEvent.class));
    }

    @Test
    void memberCompletesOwnCommitmentWithoutReviewer() {
        when(tasks.selectTaskForUpdate(20L)).thenReturn(active(20L, 7L));
        WeeklyCommitmentCommand command = command();
        command.setActualFinishTime(Date.from(clock.instant())); command.setResultDescription("已交付");

        service.complete(20L, 0, command, 100L);

        verify(commitments).updateExecutionFact(20L, 0, LabConstants.EXECUTION_ACTIVE,
                LabConstants.EXECUTION_SELF_DONE, LabConstants.RESULT_ONTIME, command.getActualFinishTime(),
                "已交付", null, null, "100");
    }

    @Test
    void memberCannotCompleteAnotherMembersCommitment() {
        when(tasks.selectTaskForUpdate(20L)).thenReturn(active(20L, 8L));
        WeeklyCommitmentCommand command = command();
        command.setActualFinishTime(Date.from(clock.instant())); command.setResultDescription("已交付");

        assertThrows(ServiceException.class, () -> service.complete(20L, 0, command, 100L));
    }

    @Test
    void completeRequiresFinishTimeAndResultDescription() {
        when(tasks.selectTaskForUpdate(20L)).thenReturn(active(20L, 7L));
        assertThrows(ServiceException.class, () -> service.complete(20L, 0, new WeeklyCommitmentCommand(), 100L));
    }

    @Test
    void undoneRequiresReasonAndNextAction() {
        when(tasks.selectTaskForUpdate(20L)).thenReturn(active(20L, 7L));
        assertThrows(ServiceException.class, () -> service.markUndone(20L, 0, new WeeklyCommitmentCommand(), 100L));
    }

    @Test
    void memberCannotCancelActiveCommitment() {
        when(tasks.selectTaskForUpdate(20L)).thenReturn(active(20L, 7L));
        doThrow(new ServiceException("仅管理者可取消承诺")).when(access).requireManager(100L);
        WeeklyCommitmentCommand command = command(); command.setReason("范围变化");
        assertThrows(ServiceException.class, () -> service.cancel(20L, 0, command, 100L));
    }

    @Test
    void managerCancellationRequiresScopeChangeReason() {
        when(tasks.selectTaskForUpdate(20L)).thenReturn(active(20L, 7L));
        assertThrows(ServiceException.class,
                () -> service.cancel(20L, 0, new WeeklyCommitmentCommand(), 100L));
    }

    @Test
    void correctionResetsCurrentResultFieldsButKeepsEventHistory() {
        LabTask task = active(20L, 7L); task.setExecutionStatus(LabConstants.EXECUTION_SELF_DONE);
        task.setResultStatus(LabConstants.RESULT_DELAYED); task.setResultDesc("旧结果");
        task.setActualFinishTime(Date.from(clock.instant())); when(tasks.selectTaskForUpdate(20L)).thenReturn(task);
        WeeklyCommitmentCommand command = new WeeklyCommitmentCommand(); command.setReason("纠正误报");

        service.correct(20L, 0, command, 100L);

        verify(commitments).updateExecutionFact(20L, 0, LabConstants.EXECUTION_SELF_DONE,
                LabConstants.EXECUTION_ACTIVE, LabConstants.RESULT_DOING, null, null, null, null, "100");
        ArgumentCaptor<LabTaskExecutionEvent> event = ArgumentCaptor.forClass(LabTaskExecutionEvent.class);
        verify(commitments).insertExecutionEvent(event.capture());
        assertEquals(LabConstants.RESULT_DELAYED, event.getValue().getResultStatus());
    }

    @Test
    void carryCreatesExactlyOneNewCommitmentAndKeepsOriginalUndone() {
        LabTask old = active(20L, 7L); old.setExecutionStatus(LabConstants.EXECUTION_SELF_UNDONE);
        old.setResultStatus(LabConstants.RESULT_UNDONE); when(tasks.selectTaskForUpdate(20L)).thenReturn(old);
        when(commitments.selectCarriedCommitment(20L, "2026-W33")).thenReturn(null);
        WeeklyCommitmentCommand command = command(); command.setPeriod("2026-W33");

        LabTask carried = service.carry(20L, 0, command, 100L);

        assertEquals(20L, carried.getCarriedFromId());
        assertEquals(LabConstants.EXECUTION_ACTIVE, carried.getExecutionStatus());
        assertEquals(LabConstants.EXECUTION_SELF_UNDONE, old.getExecutionStatus());
        verify(tasks).insertTask(carried);
    }

    @Test
    void terminalTransitionClosesOpenBlockInSameTransaction() {
        when(tasks.selectTaskForUpdate(20L)).thenReturn(active(20L, 7L));
        LabTaskBlockEvent block = new LabTaskBlockEvent(); block.setId(33L); block.setTaskId(20L); block.setBlockStatus("OPEN");
        when(tasks.selectOpenBlockEvent(20L)).thenReturn(block);
        when(tasks.closeBlockEvent(anyLong(), anyLong(), any(Date.class), anyString(), anyString())).thenReturn(1);
        WeeklyCommitmentCommand command = command(); command.setFailReason("依赖未就绪"); command.setNextAction("下周继续");

        service.markUndone(20L, 0, command, 100L);

        verify(tasks).closeBlockEvent(33L, 7L, Date.from(clock.instant()), "承诺终态自动关闭阻塞", "100");
    }

    private static WeeklyCommitmentCommand command() {
        WeeklyCommitmentCommand command = new WeeklyCommitmentCommand(); command.setTitle("完成算法验证");
        command.setDeliverable("验证记录"); command.setPlanDate(Date.from(Instant.parse("2026-08-09T00:00:00Z")));
        command.setPeriod("2026-W32"); return command;
    }
    private static LabTask monthParent() {
        LabTask task = active(10L, 7L); task.setTaskLevel(LabConstants.TASK_LEVEL_MONTH); task.setPeriod("2026-08");
        task.setWorkflowStatus(LabConstants.WORKFLOW_ACTIVE);
        task.setGoalId(1L); task.setMilestoneId(2L); task.setBizLine("algorithm"); return task;
    }
    private static LabTask active(Long id, Long owner) {
        LabTask task = new LabTask(); task.setId(id); task.setOwnerId(owner); task.setTaskLevel(LabConstants.TASK_LEVEL_WEEK);
        task.setPeriod("2026-W32"); task.setPlanDate(Date.from(Instant.parse("2026-08-09T00:00:00Z")));
        task.setExecutionStatus(LabConstants.EXECUTION_ACTIVE); task.setExecutionVersion(0); task.setResultStatus(LabConstants.RESULT_DOING);
        task.setPeriodLockFlag(LabConstants.NO); task.setDelFlag(LabConstants.NO); return task;
    }
    private static LabAccessContext context(Long memberId) {
        LabAccessContext context = new LabAccessContext(); context.setUserId(100L); context.setMemberId(memberId);
        context.setRoleKey("lab_member"); context.setBizLine("algorithm"); return context;
    }
}
