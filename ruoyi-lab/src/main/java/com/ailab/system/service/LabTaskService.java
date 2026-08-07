package com.ailab.system.service;

import com.ailab.system.domain.LabTask;
import com.ailab.system.domain.LabTaskBlockEvent;
import com.ailab.system.domain.LabTaskEvidence;
import com.ailab.system.domain.LabTaskQualityGate;
import com.ailab.system.dto.TaskSubmitCommand;
import java.math.BigDecimal;
import java.util.List;

public interface LabTaskService {
    List<LabTask> listTasks(LabTask query, Long actorId);
    LabTask getTask(Long id, Long actorId);
    int createTask(LabTask task, Long actorId);
    int updateTask(LabTask task, Long actorId);
    int deleteTask(Long id, Integer version, Long actorId);
    int activateMonthlyPlan(Long ownerId, String period, Long actorId);
    void activateTask(Long id, Integer version, Long actorId);
    void submitResult(Long id, Integer version, TaskSubmitCommand command, Long actorId);
    void withdrawResult(Long id, Integer version, Long actorId);
    void reviewPass(Long id, Integer version, TaskSubmitCommand command, Long actorId);
    void reviewReturn(Long id, Integer version, TaskSubmitCommand command, Long actorId);
    void reopenTask(Long id, Integer version, String reason, Long actorId);
    BigDecimal calculateMonthProgress(Long monthTaskId, Long actorId);

    List<LabTaskEvidence> listEvidence(Long taskId, Long actorId);
    LabTaskEvidence addEvidence(Long taskId, LabTaskEvidence evidence, Long actorId);
    int deleteEvidence(Long taskId, Long evidenceId, Long actorId);
    List<LabTaskQualityGate> listQualityGates(Long taskId, Long actorId);
    LabTaskQualityGate getQualityGate(Long id, Long actorId);
    LabTaskQualityGate addQualityGate(LabTaskQualityGate gate, Long actorId);
    int updateQualityGate(LabTaskQualityGate gate, Long actorId);
    int deleteQualityGate(Long id, Long actorId);
    void passQualityGate(Long gateId, Long approvedEvidenceId, String result, Long actorId);
    LabTaskBlockEvent blockTask(Long taskId, Integer version, String type, String reason, Long actorId);
    void unblockTask(Long taskId, Integer version, String resolution, Long actorId);
    List<LabTaskBlockEvent> listBlockEvents(Long taskId, Long actorId);
}
