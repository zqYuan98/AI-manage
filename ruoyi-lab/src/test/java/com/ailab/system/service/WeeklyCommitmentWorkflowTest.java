package com.ailab.system.service;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ailab.system.constant.LabConstants;
import com.ailab.system.domain.LabTask;
import com.ailab.system.dto.TaskSubmitCommand;
import com.ailab.system.service.impl.TaskWorkflowServiceImpl;
import com.ruoyi.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

class WeeklyCommitmentWorkflowTest {
    private final TaskWorkflowService workflow = new TaskWorkflowServiceImpl();

    @Test
    void formalMonthlyWorkflowRejectsWeeklyTasks() {
        LabTask week = new LabTask(); week.setTaskLevel(LabConstants.TASK_LEVEL_WEEK);
        week.setWorkflowStatus(LabConstants.WORKFLOW_ACTIVE); week.setResultStatus(LabConstants.RESULT_DOING);
        assertThrows(ServiceException.class, () -> workflow.submitResult(week, new TaskSubmitCommand(), 7L));
        week.setWorkflowStatus(LabConstants.WORKFLOW_PENDING_REVIEW);
        assertThrows(ServiceException.class, () -> workflow.reviewPass(week, new TaskSubmitCommand(), 8L));
        assertThrows(ServiceException.class, () -> workflow.reviewReturn(week, new TaskSubmitCommand(), 8L));
    }
}
