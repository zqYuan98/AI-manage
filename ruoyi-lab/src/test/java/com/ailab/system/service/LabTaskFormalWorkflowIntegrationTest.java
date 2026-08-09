package com.ailab.system.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailab.system.constant.LabConstants;
import com.ailab.system.domain.LabTask;
import com.ailab.system.dto.LabAccessContext;
import com.ailab.system.dto.TaskSubmitCommand;
import com.ailab.system.mapper.LabGoalMapper;
import com.ailab.system.mapper.LabTaskEvidenceMapper;
import com.ailab.system.mapper.LabTaskMapper;
import com.ailab.system.service.impl.LabFormalAcceptanceService;
import com.ailab.system.service.impl.LabTaskServiceImpl;
import com.ailab.system.service.impl.LabTaskWorkflowEventService;
import java.time.Clock;
import java.util.Collections;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LabTaskFormalWorkflowIntegrationTest {
    private LabTaskMapper taskMapper;
    private LabTaskWorkflowEventService events;
    private LabFormalAcceptanceService acceptance;
    private TaskWorkflowService workflow;
    private LabTaskService service;

    @BeforeEach
    void setUp() {
        taskMapper = mock(LabTaskMapper.class);
        LabTaskEvidenceMapper evidence = mock(LabTaskEvidenceMapper.class);
        when(evidence.selectEvidenceByTaskId(9L)).thenReturn(Collections.emptyList());
        LabAccessService access = mock(LabAccessService.class);
        LabAccessContext context = new LabAccessContext();
        context.setMemberId(8L); context.setRoleKey("lab_manager"); context.setBizLine("manage");
        when(access.context(88L)).thenReturn(context);
        workflow = mock(TaskWorkflowService.class);
        events = mock(LabTaskWorkflowEventService.class);
        acceptance = mock(LabFormalAcceptanceService.class);
        service = new LabTaskServiceImpl(taskMapper, evidence, mock(LabGoalMapper.class), workflow, access,
                events, acceptance, Clock.systemUTC());
    }

    @Test
    void confirmationWritesCurrentRowAuditEventAndFormalFactInOneCallChain() {
        LabTask task = monthly(9L, LabConstants.WORKFLOW_PENDING_REVIEW, 4);
        when(taskMapper.selectTaskById(9L)).thenReturn(task);
        when(taskMapper.updateTask(task)).thenReturn(1);
        doAnswer(invocation -> { task.setWorkflowStatus(LabConstants.WORKFLOW_CONFIRMED); return Collections.emptyList(); })
                .when(workflow).reviewPass(eq(task), any(TaskSubmitCommand.class), eq(8L));
        TaskSubmitCommand command = new TaskSubmitCommand(); command.setReviewerComment("验收通过");

        service.reviewPass(9L, 4, command, 88L);

        verify(events).append(task, LabConstants.WORKFLOW_PENDING_REVIEW, LabConstants.WORKFLOW_CONFIRMED,
                8L, "CONFIRM", "验收通过");
        verify(acceptance).accept(task, 8L, "验收通过", 4);
    }

    @Test
    void returnKeepsReasonInAppendOnlyEventAfterResettingCurrentFacts() {
        LabTask task = monthly(9L, LabConstants.WORKFLOW_PENDING_REVIEW, 4);
        when(taskMapper.selectTaskById(9L)).thenReturn(task);
        when(taskMapper.updateTask(task)).thenReturn(1);
        doAnswer(invocation -> { task.setWorkflowStatus(LabConstants.WORKFLOW_ACTIVE); return null; })
                .when(workflow).reviewReturn(eq(task), any(TaskSubmitCommand.class), eq(8L));
        TaskSubmitCommand command = new TaskSubmitCommand(); command.setReviewerComment("补充验收材料");

        service.reviewReturn(9L, 4, command, 88L);

        verify(events).append(task, LabConstants.WORKFLOW_PENDING_REVIEW, LabConstants.WORKFLOW_ACTIVE,
                8L, "RETURN", "补充验收材料");
    }

    @Test
    void activationEventKeepsTheDraftToActiveTransition() {
        LabTask task=monthly(9L,LabConstants.WORKFLOW_DRAFT,0);task.setTaskType(LabConstants.TASK_TYPE_KEY);
        task.setPerfWeight(new BigDecimal("100"));
        when(taskMapper.lockMemberForUpdate(7L)).thenReturn("algorithm");
        when(taskMapper.selectKeyMonthTasksByOwnerPeriodForUpdate(7L,"2026-08"))
                .thenReturn(Collections.singletonList(task));
        when(taskMapper.updateTask(task)).thenReturn(1);
        when(workflow.activatePlan(task)).thenAnswer(invocation->{task.setWorkflowStatus(LabConstants.WORKFLOW_ACTIVE);return Collections.emptyList();});

        service.activateMonthlyPlan(7L,"2026-08",88L);

        verify(events).append(task,LabConstants.WORKFLOW_DRAFT,LabConstants.WORKFLOW_ACTIVE,
                8L,"ACTIVATE","激活月度结果");
    }

    private LabTask monthly(Long id, String workflowStatus, int version) {
        LabTask task = new LabTask(); task.setId(id); task.setTaskLevel(LabConstants.TASK_LEVEL_MONTH);
        task.setPeriod("2026-08"); task.setBizLine("algorithm"); task.setOwnerId(7L);
        task.setWorkflowStatus(workflowStatus); task.setResultStatus(LabConstants.RESULT_ONTIME);
        task.setPeriodLockFlag(LabConstants.NO); task.setVersion(version); return task;
    }
}
