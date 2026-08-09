package com.ailab.system.service.impl;

import com.ailab.system.constant.LabConstants;
import com.ailab.system.domain.LabPeriodCloseFact;
import com.ailab.system.domain.LabPeriodCloseSnapshot;
import com.ailab.system.domain.LabTask;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 创建不可变月结修订与类型化事实。 */
@Service
public class LabPeriodCloseSnapshotService {
    private static final String CALCULATION_VERSION = "PERIOD_CLOSE_V1";
    private static final ObjectMapper JSON = new ObjectMapper();
    private final LabPeriodCloseSnapshotMapper mapper;
    private final Clock clock;

    public LabPeriodCloseSnapshotService(LabPeriodCloseSnapshotMapper mapper) {
        this(mapper, Clock.systemDefaultZone());
    }

    public LabPeriodCloseSnapshotService(LabPeriodCloseSnapshotMapper mapper, Clock clock) {
        if (mapper == null || clock == null) { throw new IllegalArgumentException("月结快照依赖不能为空"); }
        this.mapper = mapper;
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
        if (isBlank(period) || periodVersion < 0 || performanceRevision < 0 || actorId == null || tasks == null) {
            throw new ServiceException("月结快照缺少必要字段");
        }
        if (collaborations == null || members == null) {
            throw new ServiceException("月结支持事实不能为空");
        }
        for (LabTask task : tasks) {
            if (task == null || task.getId() == null || !mapper.taskMatchesLastEvent(task.getId())) {
                throw new ServiceException("任务当前状态与最后审计事件不一致，不能关期");
            }
            if (mapper.hasOpenBlock(task.getId())) {
                throw new ServiceException("存在未关闭阻塞的任务不能进入关期快照");
            }
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
