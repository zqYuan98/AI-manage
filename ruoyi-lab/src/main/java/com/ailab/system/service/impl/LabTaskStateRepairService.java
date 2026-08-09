package com.ailab.system.service.impl;

import com.ailab.system.constant.LabConstants;
import com.ailab.system.domain.LabTask;
import com.ailab.system.domain.LabTaskMigrationIssue;
import com.ailab.system.mapper.LabCommitmentMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persists a repair item independently so a rejected close cannot roll it back. */
@Service
public class LabTaskStateRepairService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final LabCommitmentMapper mapper;

    public LabTaskStateRepairService(LabCommitmentMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void queueCurrentEventMismatch(LabTask task, Long actorId) {
        queue(task, "CURRENT_EVENT_MISMATCH", actorId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void queueTerminalOpenBlock(LabTask task, Long actorId) {
        queue(task, LabConstants.MIGRATION_TERMINAL_WITH_OPEN_BLOCK, actorId);
    }

    private void queue(LabTask task, String issueCode, Long actorId) {
        if (task == null || task.getId() == null || actorId == null) {
            throw new ServiceException("任务修复项缺少必要字段");
        }
        LabTaskMigrationIssue issue = new LabTaskMigrationIssue();
        issue.setTaskId(task.getId());
        issue.setIssueCode(issueCode);
        issue.setSourceStateJson(state(task));
        issue.setResolutionStatus("OPEN");
        issue.setVersion(0);
        issue.setDelFlag(LabConstants.NO);
        issue.setCreateBy(String.valueOf(actorId));
        int inserted = mapper.insertRepairIssue(issue);
        if (inserted < 0 || inserted > 1) {
            throw new ServiceException("任务修复项写入失败");
        }
    }

    private String state(LabTask task) {
        Map<String, Object> state = new LinkedHashMap<String, Object>();
        state.put("taskId", task.getId());
        state.put("taskLevel", task.getTaskLevel());
        state.put("workflowStatus", task.getWorkflowStatus());
        state.put("executionStatus", task.getExecutionStatus());
        state.put("resultStatus", task.getResultStatus());
        state.put("actualFinishTime", task.getActualFinishTime());
        state.put("periodLockFlag", task.getPeriodLockFlag());
        state.put("taskVersion", task.getVersion());
        state.put("executionVersion", task.getExecutionVersion());
        try {
            return JSON.writeValueAsString(state);
        } catch (JsonProcessingException exception) {
            throw new ServiceException("任务修复项序列化失败");
        }
    }
}
