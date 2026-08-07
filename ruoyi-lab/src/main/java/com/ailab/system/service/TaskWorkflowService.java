package com.ailab.system.service;

import com.ailab.system.domain.LabTask;
import com.ailab.system.dto.FieldValidationError;
import com.ailab.system.dto.TaskSubmitCommand;
import java.util.List;

/** Stateless state-machine operations for a task aggregate. */
public interface TaskWorkflowService {
    List<FieldValidationError> activatePlan(LabTask task);

    List<FieldValidationError> submitResult(LabTask task, TaskSubmitCommand command);

    void withdraw(LabTask task);

    List<FieldValidationError> reviewPass(LabTask task, TaskSubmitCommand command, Long actorId);

    void reviewReturn(LabTask task, TaskSubmitCommand command, Long actorId);

    void managerReopen(LabTask task, Long managerId, String reason);
}
