package com.ailab.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailab.system.constant.LabConstants;
import com.ailab.system.domain.LabTask;
import com.ailab.system.dto.LabAccessContext;
import com.ailab.system.dto.MonthlyCarryCommand;
import com.ailab.system.mapper.LabGoalMapper;
import com.ailab.system.mapper.LabTaskEvidenceMapper;
import com.ailab.system.mapper.LabTaskMapper;
import com.ailab.system.service.impl.LabFormalAcceptanceService;
import com.ailab.system.service.impl.LabTaskServiceImpl;
import com.ailab.system.service.impl.LabTaskWorkflowEventService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LabMonthlyResultCarryTest {
    private LabTaskMapper tasks;
    private LabTaskWorkflowEventService events;
    private LabTaskService service;

    @BeforeEach
    void setUp() {
        tasks=mock(LabTaskMapper.class);events=mock(LabTaskWorkflowEventService.class);
        LabTaskEvidenceMapper evidence=mock(LabTaskEvidenceMapper.class);
        when(evidence.selectEvidenceByTaskId(9L)).thenReturn(Collections.emptyList());
        LabAccessService access=mock(LabAccessService.class);LabAccessContext context=new LabAccessContext();
        context.setMemberId(8L);context.setRoleKey("lab_manager");when(access.context(88L)).thenReturn(context);
        service=new LabTaskServiceImpl(tasks,evidence,mock(LabGoalMapper.class),mock(TaskWorkflowService.class),access,
                events,mock(LabFormalAcceptanceService.class),Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"),ZoneOffset.UTC));
    }

    @Test
    void confirmedUndoneMonthCreatesOneDraftNextMonthWithoutChangingSource() {
        LabTask source=source();when(tasks.selectTaskById(9L)).thenReturn(source);
        when(tasks.selectCarriedTask(9L,"2026-09")).thenReturn(null);
        when(tasks.insertTask(any(LabTask.class))).thenAnswer(invocation->{LabTask row=invocation.getArgument(0);row.setId(20L);return 1;});
        MonthlyCarryCommand command=new MonthlyCarryCommand();command.setPlanDate(Date.from(Instant.parse("2026-09-25T00:00:00Z")));
        command.setReason("继续完成验收材料");

        LabTask carried=service.carryMonthlyResult(9L,4,command,88L);

        assertNotSame(source,carried);assertEquals(Long.valueOf(9L),carried.getCarriedFromId());
        assertEquals("2026-09",carried.getPeriod());assertEquals(LabConstants.WORKFLOW_DRAFT,carried.getWorkflowStatus());
        assertEquals(LabConstants.WORKFLOW_CONFIRMED,source.getWorkflowStatus());assertEquals(LabConstants.RESULT_UNDONE,source.getResultStatus());
        verify(events).append(source,LabConstants.WORKFLOW_CONFIRMED,LabConstants.WORKFLOW_CONFIRMED,8L,"CARRY","继续完成验收材料");
    }

    @Test
    void repeatedCarryReturnsExistingTargetAndDoesNotDuplicateHistory() {
        LabTask source=source();LabTask existing=source();existing.setId(20L);existing.setPeriod("2026-09");existing.setCarriedFromId(9L);
        when(tasks.selectTaskById(9L)).thenReturn(source);when(tasks.selectCarriedTask(9L,"2026-09")).thenReturn(existing);
        MonthlyCarryCommand command=new MonthlyCarryCommand();command.setPlanDate(Date.from(Instant.parse("2026-09-25T00:00:00Z")));command.setReason("继续处理");

        assertEquals(Long.valueOf(20L),service.carryMonthlyResult(9L,4,command,88L).getId());
        verify(tasks,never()).insertTask(any(LabTask.class));verify(events,never()).append(any(),any(),any(),any(),any(),any());
    }

    private LabTask source(){LabTask task=new LabTask();task.setId(9L);task.setTaskLevel("month");task.setPeriod("2026-08");
        task.setBizLine("algorithm");task.setTaskType("key");task.setTitle("完成算法验收");task.setOwnerId(7L);
        task.setPlanDate(Date.from(Instant.parse("2026-08-28T00:00:00Z")));task.setDeliverable("验收记录");
        task.setPerfWeight(new BigDecimal("60"));task.setGoalWeight(new BigDecimal("40"));
        task.setWorkflowStatus(LabConstants.WORKFLOW_CONFIRMED);task.setResultStatus(LabConstants.RESULT_UNDONE);
        task.setPeriodLockFlag(LabConstants.NO);task.setVersion(4);return task;}
}
