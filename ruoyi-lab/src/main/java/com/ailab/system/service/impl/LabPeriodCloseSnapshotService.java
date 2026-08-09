package com.ailab.system.service.impl;

import com.ailab.system.constant.LabConstants;
import com.ailab.system.domain.LabPeriodCloseFact;
import com.ailab.system.domain.LabPeriodCloseSnapshot;
import com.ailab.system.domain.LabTask;
import com.ailab.system.domain.LabTaskBlockEvent;
import com.ailab.system.domain.LabCollaborationRecord;
import com.ailab.system.domain.LabMember;
import com.ailab.system.mapper.LabPeriodCloseSnapshotMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 创建不可变月结修订与类型化事实。 */
@Service
public class LabPeriodCloseSnapshotService {
    private static final String CALCULATION_VERSION = "PERIOD_CLOSE_V1";
    private static final ObjectMapper JSON = new ObjectMapper();
    private final LabPeriodCloseSnapshotMapper mapper;
    private final LabTaskStateRepairService repairQueue;
    private final Clock clock;

    @Autowired
    public LabPeriodCloseSnapshotService(LabPeriodCloseSnapshotMapper mapper, LabTaskStateRepairService repairQueue) {
        this(mapper, repairQueue, Clock.systemDefaultZone());
    }

    public LabPeriodCloseSnapshotService(LabPeriodCloseSnapshotMapper mapper, Clock clock) {
        this(mapper, null, clock);
    }

    public LabPeriodCloseSnapshotService(LabPeriodCloseSnapshotMapper mapper,
            LabTaskStateRepairService repairQueue, Clock clock) {
        if (mapper == null || clock == null) { throw new IllegalArgumentException("月结快照依赖不能为空"); }
        this.mapper = mapper;
        this.repairQueue = repairQueue;
        this.clock = clock;
    }

    public Long latestFormalRevisionId(String period) {
        if (isBlank(period)) { throw new ServiceException("关期月份不能为空"); }
        return mapper.selectLatestFormalRevisionId(period);
    }

    @Transactional
    public LabPeriodCloseSnapshot close(String period, int periodVersion, Long formalRevisionId,
            int performanceRevision, Long actorId, List<LabTask> tasks) {
        return close(period, periodVersion, formalRevisionId, performanceRevision, actorId, tasks,
                Collections.<LabCollaborationRecord>emptyList(), Collections.<LabMember>emptyList());
    }

    @Transactional
    public LabPeriodCloseSnapshot close(String period, int periodVersion, Long formalRevisionId,
            int performanceRevision, Long actorId, List<LabTask> tasks,
            List<LabCollaborationRecord> collaborations, List<LabMember> members) {
        List<LabTaskBlockEvent> openBlocks = lockOpenBlocks(tasks, actorId);
        return close(period, periodVersion, formalRevisionId, performanceRevision, actorId, tasks,
                openBlocks, collaborations, members);
    }

    public List<LabTaskBlockEvent> lockOpenBlocks(List<LabTask> tasks, Long actorId) {
        if (tasks == null || actorId == null) {
            throw new ServiceException("月结阻塞锁缺少必要字段");
        }
        List<Long> taskIds = new ArrayList<Long>();
        Map<Long, LabTask> byId = new HashMap<Long, LabTask>();
        for (LabTask task : tasks) {
            if (task == null || task.getId() == null || !mapper.taskMatchesLastEvent(task.getId())) {
                if (task != null && task.getId() != null && repairQueue != null) {
                    repairQueue.queueCurrentEventMismatch(task, actorId);
                }
                throw new ServiceException("任务当前状态与最后审计事件不一致，已进入修复队列");
            }
            taskIds.add(task.getId());
            byId.put(task.getId(), task);
        }
        List<LabTaskBlockEvent> blocks = mapper.selectOpenBlocksForTaskIdsForUpdate(taskIds);
        if (blocks == null) blocks = Collections.emptyList();
        Set<Long> blockedTasks = new HashSet<Long>();
        for (LabTaskBlockEvent block : blocks) {
            LabTask task = block == null ? null : byId.get(block.getTaskId());
            if (task == null || block.getId() == null || !blockedTasks.add(block.getTaskId())) {
                throw new ServiceException("开放阻塞 episode 不唯一，不能关期");
            }
            if (isTerminal(task)) {
                if (repairQueue != null) repairQueue.queueTerminalOpenBlock(task, actorId);
                throw new ServiceException("终态任务仍有开放阻塞，已进入修复队列");
            }
        }
        return new ArrayList<LabTaskBlockEvent>(blocks);
    }

    public LabPeriodCloseSnapshot close(String period, int periodVersion, Long formalRevisionId,
            int performanceRevision, Long actorId, List<LabTask> tasks, List<LabTaskBlockEvent> openBlocks,
            List<LabCollaborationRecord> collaborations, List<LabMember> members) {
        if (isBlank(period) || periodVersion < 0 || performanceRevision < 0 || actorId == null || tasks == null) {
            throw new ServiceException("月结快照缺少必要字段");
        }
        if (openBlocks == null || collaborations == null || members == null) {
            throw new ServiceException("月结支持事实不能为空");
        }
        LabPeriodCloseSnapshot snapshot = new LabPeriodCloseSnapshot();
        snapshot.setPeriod(period);
        Integer maximum = mapper.selectMaxRevision(period);
        snapshot.setRevisionNo(maximum == null ? 1 : maximum + 1);
        snapshot.setPeriodVersion(periodVersion);
        snapshot.setFormalRevisionId(formalRevisionId);
        snapshot.setPerformanceRevision(performanceRevision);
        snapshot.setClosedBy(actorId);
        snapshot.setClosedTime(Date.from(clock.instant()));
        snapshot.setCalculationVersion(CALCULATION_VERSION);
        snapshot.setDelFlag(LabConstants.NO);
        snapshot.setCreateBy(String.valueOf(actorId));
        if (mapper.insertSnapshot(snapshot) != 1 || snapshot.getId() == null) {
            throw new ServiceException("月结修订写入失败");
        }
        for (LabTask task : tasks) {
            insertFact(snapshot.getId(), LabConstants.TASK_LEVEL_WEEK.equals(task.getTaskLevel())
                    ? "WEEKLY_COMMITMENT" : "MONTH_RESULT", task.getId(), snapshot(task), actorId);
        }
        Date cutoff = snapshot.getClosedTime();
        Set<Long> clearedTasks = new HashSet<Long>();
        for (LabTaskBlockEvent block : openBlocks) {
            insertFact(snapshot.getId(), "OPEN_BLOCK_AT_CUTOFF", block.getId(), json(block), actorId);
            if (mapper.closeOpenBlockForPeriod(block.getId(), actorId, cutoff, String.valueOf(actorId)) != 1) {
                throw new ServiceException("关期阻塞 episode 已并发变化");
            }
            if (clearedTasks.add(block.getTaskId())
                    && mapper.clearTaskBlockForPeriod(block.getTaskId(), String.valueOf(actorId)) != 1) {
                throw new ServiceException("关期阻塞状态已并发变化");
            }
        }
        for (LabTask task : tasks) {
            if (LabConstants.TASK_LEVEL_WEEK.equals(task.getTaskLevel())) {
                List<com.ailab.system.domain.LabTaskExecutionEvent> events = mapper.selectExecutionEvents(task.getId());
                if (events != null && !events.isEmpty()) insertFact(snapshot.getId(), "WEEK_EXECUTION_EVENTS",
                        task.getId(), json(events), actorId);
            } else {
                List<com.ailab.system.domain.LabTaskWorkflowEvent> events = mapper.selectWorkflowEvents(task.getId());
                if (events != null && !events.isEmpty()) insertFact(snapshot.getId(), "MONTH_WORKFLOW_EVENTS",
                        task.getId(), json(events), actorId);
            }
        }
        for (LabCollaborationRecord collaboration : collaborations) {
            if (collaboration != null && collaboration.getId() != null) {
                insertFact(snapshot.getId(), "COLLABORATION", collaboration.getId(), json(collaboration), actorId);
            }
        }
        for (LabMember member : members) {
            if (member != null && member.getId() != null) {
                insertFact(snapshot.getId(), "MEMBER_IDENTITY", member.getId(), json(member), actorId);
            }
        }
        return snapshot;
    }

    private boolean isTerminal(LabTask task) {
        if (LabConstants.TASK_LEVEL_WEEK.equals(task.getTaskLevel())) {
            return LabConstants.EXECUTION_SELF_DONE.equals(task.getExecutionStatus())
                    || LabConstants.EXECUTION_SELF_UNDONE.equals(task.getExecutionStatus())
                    || LabConstants.EXECUTION_CANCELLED.equals(task.getExecutionStatus());
        }
        return LabConstants.WORKFLOW_CONFIRMED.equals(task.getWorkflowStatus());
    }

    private void insertFact(Long snapshotId, String factType, Long businessId, String factJson, Long actorId) {
        LabPeriodCloseFact fact = new LabPeriodCloseFact();
        fact.setCloseSnapshotId(snapshotId); fact.setFactType(factType); fact.setBusinessId(businessId);
        fact.setFactJson(factJson); fact.setDelFlag(LabConstants.NO); fact.setCreateBy(String.valueOf(actorId));
        if (mapper.insertFact(fact) != 1) { throw new ServiceException("月结事实写入失败"); }
    }

    private String snapshot(LabTask task) {
        java.util.LinkedHashMap<String, Object> value = new java.util.LinkedHashMap<String, Object>();
        value.put("taskId", task.getId());
        value.put("parentId", task.getParentId());
        value.put("goalId", task.getGoalId());
        value.put("milestoneId", task.getMilestoneId());
        value.put("taskLevel", task.getTaskLevel());
        value.put("period", task.getPeriod());
        value.put("bizLine", task.getBizLine());
        value.put("taskType", task.getTaskType());
        value.put("title", task.getTitle());
        value.put("ownerId", task.getOwnerId());
        value.put("deptId", task.getDeptId());
        value.put("planDate", task.getPlanDate());
        value.put("deliverable", task.getDeliverable());
        value.put("perfWeight", task.getPerfWeight());
        value.put("goalWeight", task.getGoalWeight());
        value.put("workflowStatus", task.getWorkflowStatus());
        value.put("executionStatus", task.getExecutionStatus());
        value.put("resultStatus", task.getResultStatus());
        value.put("actualFinishTime", task.getActualFinishTime());
        value.put("resultDesc", task.getResultDesc());
        value.put("failReason", task.getFailReason());
        value.put("nextAction", task.getNextAction());
        value.put("taskVersion", task.getVersion());
        value.put("executionVersion", task.getExecutionVersion());
        value.put("evidence", task.getEvidenceList());
        boolean accepted = LabConstants.TASK_LEVEL_MONTH.equals(task.getTaskLevel())
                && LabConstants.WORKFLOW_CONFIRMED.equals(task.getWorkflowStatus());
        value.put("accepted", accepted);
        value.put("closeResultStatus", accepted ? task.getResultStatus() : LabConstants.RESULT_UNDONE);
        return json(value);
    }

    private String json(Object value) {
        try { return JSON.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new ServiceException("月结事实序列化失败"); }
    }

    private boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }
}
