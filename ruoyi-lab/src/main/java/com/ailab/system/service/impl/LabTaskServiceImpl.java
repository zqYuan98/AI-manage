package com.ailab.system.service.impl;

import com.ailab.system.constant.LabConstants;
import com.ailab.system.domain.LabGoal;
import com.ailab.system.domain.LabTask;
import com.ailab.system.domain.LabTaskBlockEvent;
import com.ailab.system.domain.LabTaskEvidence;
import com.ailab.system.domain.LabTaskQualityGate;
import com.ailab.system.dto.FieldValidationError;
import com.ailab.system.dto.TaskSubmitCommand;
import com.ailab.system.exception.LabValidationException;
import com.ailab.system.mapper.LabGoalMapper;
import com.ailab.system.mapper.LabTaskEvidenceMapper;
import com.ailab.system.mapper.LabTaskMapper;
import com.ailab.system.service.LabTaskService;
import com.ailab.system.service.TaskWorkflowService;
import com.ailab.system.util.LabPeriodUtils;
import com.ruoyi.common.annotation.DataScope;
import com.ruoyi.common.exception.ServiceException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LabTaskServiceImpl implements LabTaskService {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private final LabTaskMapper taskMapper;
    private final LabTaskEvidenceMapper evidenceMapper;
    private final LabGoalMapper goalMapper;
    private final TaskWorkflowService workflowService;
    private final Clock clock;

    @Autowired
    public LabTaskServiceImpl(LabTaskMapper taskMapper, LabTaskEvidenceMapper evidenceMapper,
            LabGoalMapper goalMapper, TaskWorkflowService workflowService) {
        this(taskMapper, evidenceMapper, goalMapper, workflowService, Clock.systemDefaultZone());
    }

    public LabTaskServiceImpl(LabTaskMapper taskMapper, LabTaskEvidenceMapper evidenceMapper,
            LabGoalMapper goalMapper, TaskWorkflowService workflowService, Clock clock) {
        this.taskMapper = taskMapper;
        this.evidenceMapper = evidenceMapper;
        this.goalMapper = goalMapper;
        this.workflowService = workflowService;
        this.clock = clock;
    }

    @Override
    @DataScope(deptAlias = "t", userAlias = "u", permission = "lab:task:list")
    public List<LabTask> listTasks(LabTask query) {
        return taskMapper.selectTaskList(query == null ? new LabTask() : query);
    }

    @Override
    public LabTask getTask(Long id) {
        LabTask task = taskMapper.selectTaskById(id);
        if (task == null) throw new ServiceException("Task does not exist");
        task.setEvidenceList(evidenceMapper.selectEvidenceByTaskId(id));
        return task;
    }

    @Override
    @Transactional
    public int createTask(LabTask task, Long actorId) {
        validateTaskConnections(task);
        task.setId(null);
        task.setWorkflowStatus(LabConstants.WORKFLOW_DRAFT);
        task.setResultStatus(LabConstants.RESULT_DOING);
        task.setActualFinishTime(null);
        task.setResultDesc(null);
        task.setFailReason(null);
        task.setNextAction(null);
        task.setBlockFlag(LabConstants.NO);
        task.setBlockStartTime(null);
        task.setPeriodLockFlag(LabConstants.NO);
        task.setVersion(0);
        task.setDelFlag(LabConstants.NO);
        task.setCreateBy(actor(actorId));
        return taskMapper.insertTask(task);
    }

    @Override
    @Transactional
    public int updateTask(LabTask task, Long actorId) {
        requireIdentity(task);
        LabTask stored = taskMapper.selectTaskById(task.getId());
        if (stored == null) throw new ServiceException("Task does not exist");
        if (!stored.getVersion().equals(task.getVersion())) throw optimisticConflict();
        requireMutable(stored);
        validateTaskConnections(task);
        preserveServerState(task, stored);
        task.setUpdateBy(actor(actorId));
        if (taskMapper.updateTask(task) != 1) throw optimisticConflict();
        return 1;
    }

    @Override
    @Transactional
    public int deleteTask(Long id, Integer version, Long actorId) {
        LabTask stored = taskMapper.selectTaskById(id);
        if (stored == null) throw new ServiceException("Task does not exist");
        if (version == null || !version.equals(stored.getVersion())) throw optimisticConflict();
        requireMutable(stored);
        if (!taskMapper.selectTasksByParentId(id).isEmpty()) throw new ServiceException("Delete weekly child tasks first");
        if (taskMapper.deleteTask(id, version, actor(actorId)) != 1) throw optimisticConflict();
        return 1;
    }

    @Override
    @Transactional
    public int activateMonthlyPlan(Long ownerId, String period, Long actorId) {
        if (ownerId == null || blank(period)) throw new ServiceException("Owner and month period are required");
        LabPeriodUtils.parseMonth(period);
        List<LabTask> tasks = taskMapper.selectKeyMonthTasksByOwnerPeriod(ownerId, period);
        BigDecimal total = BigDecimal.ZERO;
        for (LabTask task : tasks) total = total.add(zero(task.getPerfWeight()));
        if (tasks.isEmpty() || total.compareTo(ONE_HUNDRED) != 0) {
            throw new ServiceException("Monthly key-task performance weights must total 100 before plan activation");
        }
        for (LabTask task : tasks) {
            List<FieldValidationError> errors = workflowService.activatePlan(task);
            requireValid(errors);
        }
        for (LabTask task : tasks) {
            task.setUpdateBy(actor(actorId));
            if (taskMapper.updateTask(task) != 1) throw optimisticConflict();
        }
        return tasks.size();
    }

    @Override
    @Transactional
    public void activateTask(Long id, Integer version, Long actorId) {
        LabTask task = loadVersioned(id, version);
        if (!LabConstants.TASK_LEVEL_WEEK.equals(task.getTaskLevel())) {
            throw new ServiceException("Monthly key tasks must be activated through owner-period plan activation");
        }
        requireOwner(task, member(actorId));
        LabTask parent = taskMapper.selectTaskById(task.getParentId());
        if (parent == null || !LabConstants.WORKFLOW_ACTIVE.equals(parent.getWorkflowStatus())
                || LabConstants.YES.equals(parent.getPeriodLockFlag())) {
            throw new ServiceException("Weekly task requires an active, unlocked month task");
        }
        requireValid(workflowService.activatePlan(task));
        saveWorkflowTask(task, actorId);
    }

    @Override
    @Transactional
    public void submitResult(Long id, Integer version, TaskSubmitCommand command, Long actorId) {
        LabTask task = loadVersioned(id, version);
        Long actorMemberId = member(actorId);
        requireOwner(task, actorMemberId);
        requireValid(workflowService.submitResult(task, command, actorMemberId));
        for (LabTaskEvidence item : task.getEvidenceList()) {
            if (item != null && item.getId() == null) {
                item.setCreateBy(actor(actorId));
                if (evidenceMapper.insertEvidence(item) != 1) throw new ServiceException("Evidence could not be saved");
            }
        }
        saveWorkflowTask(task, actorId);
    }

    @Override
    @Transactional
    public void withdrawResult(Long id, Integer version, Long actorId) {
        LabTask task = loadVersioned(id, version);
        requireOwner(task, member(actorId));
        workflowService.withdraw(task);
        saveWorkflowTask(task, actorId);
    }

    @Override
    @Transactional
    public void reviewPass(Long id, Integer version, TaskSubmitCommand command, Long actorId) {
        LabTask task = loadVersioned(id, version);
        Long actorMemberId = member(actorId);
        requireValid(workflowService.reviewPass(task, command, actorMemberId));
        for (Long evidenceId : command.getApprovedEvidenceIds()) {
            if (evidenceMapper.approveEvidence(evidenceId, id, actorMemberId, Date.from(clock.instant()),
                    command.getEvidenceAuditComment(), actor(actorId)) != 1) {
                throw new ServiceException("Selected evidence changed; refresh and retry");
            }
        }
        saveWorkflowTask(task, actorId);
    }

    @Override
    @Transactional
    public void reviewReturn(Long id, Integer version, TaskSubmitCommand command, Long actorId) {
        LabTask task = loadVersioned(id, version);
        workflowService.reviewReturn(task, command, member(actorId));
        saveWorkflowTask(task, actorId);
    }

    @Override
    @Transactional
    public void reopenTask(Long id, Integer version, String reason, Long actorId) {
        LabTask task = loadVersioned(id, version);
        workflowService.managerReopen(task, member(actorId), reason);
        task.setRemark(reason);
        saveWorkflowTask(task, actorId);
    }

    @Override
    public BigDecimal calculateMonthProgress(Long monthTaskId) {
        LabTask month = taskMapper.selectTaskById(monthTaskId);
        if (month == null || !LabConstants.TASK_LEVEL_MONTH.equals(month.getTaskLevel())) {
            throw new ServiceException("Monthly progress requires a month task");
        }
        List<LabTask> weeks = taskMapper.selectTasksByParentId(monthTaskId);
        if (weeks.isEmpty()) return BigDecimal.ZERO.setScale(2);
        int completed = 0;
        for (LabTask week : weeks) {
            if (LabConstants.WORKFLOW_CONFIRMED.equals(week.getWorkflowStatus()) && isCompleted(week.getResultStatus())) completed++;
        }
        return new BigDecimal(completed).multiply(ONE_HUNDRED)
                .divide(new BigDecimal(weeks.size()), 2, RoundingMode.HALF_UP);
    }

    @Override public List<LabTaskEvidence> listEvidence(Long taskId) { getTask(taskId); return evidenceMapper.selectEvidenceByTaskId(taskId); }

    @Override
    @Transactional
    public LabTaskEvidence addEvidence(Long taskId, LabTaskEvidence evidence, Long actorId) {
        LabTask task = getTask(taskId);
        if (LabConstants.WORKFLOW_CONFIRMED.equals(task.getWorkflowStatus())) throw new ServiceException("Confirmed task evidence is immutable");
        if (evidence == null || blank(evidence.getEvidenceType()) || blank(evidence.getEvidenceTitle()) || blank(evidence.getEvidenceUrl())) {
            throw new ServiceException("Evidence type, title and URL are required");
        }
        evidence.setId(null); evidence.setTaskId(taskId); evidence.setSubmitterId(member(actorId));
        evidence.setSubmitTime(Date.from(clock.instant())); evidence.setAuditStatus(LabConstants.EVIDENCE_AUDIT_PENDING);
        evidence.setAuditorId(null); evidence.setAuditTime(null); evidence.setAuditComment(null);
        evidence.setDelFlag(LabConstants.NO); evidence.setCreateBy(actor(actorId));
        if (evidenceMapper.insertEvidence(evidence) != 1) {
            throw new ServiceException("Evidence could not be saved because the task changed or became read-only");
        }
        return evidence;
    }

    @Override
    @Transactional
    public int deleteEvidence(Long taskId, Long evidenceId, Long actorId) {
        requireMutable(getTask(taskId));
        if (evidenceMapper.deleteEvidence(evidenceId, taskId, actor(actorId)) != 1) {
            throw new ServiceException("Only pending evidence belonging to this task can be deleted");
        }
        return 1;
    }

    @Override public List<LabTaskQualityGate> listQualityGates(Long taskId) { getTask(taskId); return taskMapper.selectQualityGates(taskId); }

    @Override
    public LabTaskQualityGate getQualityGate(Long id) {
        LabTaskQualityGate gate = taskMapper.selectQualityGateById(id);
        if (gate == null) throw new ServiceException("Quality gate does not exist");
        return gate;
    }

    @Override
    @Transactional
    public LabTaskQualityGate addQualityGate(LabTaskQualityGate gate, Long actorId) {
        validateGate(gate); getTask(gate.getTaskId());
        gate.setId(null); gate.setGateStatus("PENDING"); gate.setCheckerId(null); gate.setCheckTime(null);
        gate.setCheckResult(null); gate.setDelFlag(LabConstants.NO); gate.setCreateBy(actor(actorId));
        taskMapper.insertQualityGate(gate); return gate;
    }

    @Override
    @Transactional
    public int updateQualityGate(LabTaskQualityGate gate, Long actorId) {
        validateGate(gate);
        LabTaskQualityGate stored = taskMapper.selectQualityGateById(gate.getId());
        if (stored == null || "PASSED".equals(stored.getGateStatus())) throw new ServiceException("Passed or missing quality gate cannot be edited");
        gate.setTaskId(stored.getTaskId());
        gate.setGateStatus(stored.getGateStatus()); gate.setCheckerId(stored.getCheckerId()); gate.setCheckTime(stored.getCheckTime());
        gate.setCheckResult(stored.getCheckResult()); gate.setDelFlag(stored.getDelFlag()); gate.setUpdateBy(actor(actorId));
        return taskMapper.updateQualityGate(gate);
    }

    @Override
    @Transactional
    public int deleteQualityGate(Long id, Long actorId) {
        LabTaskQualityGate stored = taskMapper.selectQualityGateById(id);
        if (stored == null || "PASSED".equals(stored.getGateStatus())) throw new ServiceException("Passed or missing quality gate cannot be deleted");
        return taskMapper.deleteQualityGate(id, actor(actorId));
    }

    @Override
    @Transactional
    public void passQualityGate(Long gateId, Long approvedEvidenceId, String result, Long actorId) {
        LabTaskQualityGate gate = taskMapper.selectQualityGateById(gateId);
        LabTaskEvidence evidence = evidenceMapper.selectEvidenceById(approvedEvidenceId);
        if (gate == null || evidence == null || !gate.getTaskId().equals(evidence.getTaskId())
                || !LabConstants.EVIDENCE_AUDIT_APPROVED.equals(evidence.getAuditStatus()) || "2".equals(evidence.getDelFlag())) {
            throw new ServiceException("Quality gate requires explicit approved evidence from the same task");
        }
        if (taskMapper.markQualityGatePassed(gateId, member(actorId), Date.from(clock.instant()), result, actor(actorId)) != 1) {
            throw new ServiceException("Quality gate is already passed or changed");
        }
    }

    @Override
    @Transactional
    public LabTaskBlockEvent blockTask(Long taskId, Integer version, String type, String reason, Long actorId) {
        LabTask task = loadVersioned(taskId, version);
        requireMutable(task);
        if (LabConstants.YES.equals(task.getBlockFlag()) || taskMapper.selectOpenBlockEvent(taskId) != null) {
            throw new ServiceException("Task is already blocked");
        }
        if (blank(type) || blank(reason)) throw new ServiceException("Block type and reason are required");
        Date now = Date.from(clock.instant());
        task.setBlockFlag(LabConstants.YES); task.setBlockStartTime(now); saveWorkflowTask(task, actorId);
        LabTaskBlockEvent event = new LabTaskBlockEvent(); event.setTaskId(taskId); event.setBlockType(type);
        event.setBlockReason(reason); event.setBlockStartTime(now); event.setBlockStatus("OPEN");
        event.setDelFlag(LabConstants.NO); event.setCreateBy(actor(actorId)); taskMapper.insertBlockEvent(event); return event;
    }

    @Override
    @Transactional
    public void unblockTask(Long taskId, Integer version, String resolution, Long actorId) {
        LabTask task = loadVersioned(taskId, version);
        LabTaskBlockEvent event = taskMapper.selectOpenBlockEvent(taskId);
        if (!LabConstants.YES.equals(task.getBlockFlag()) || event == null) throw new ServiceException("Task is not blocked");
        if (blank(resolution)) throw new ServiceException("Block resolution is required");
        Date now = Date.from(clock.instant());
        task.setBlockFlag(LabConstants.NO); task.setBlockStartTime(null); saveWorkflowTask(task, actorId);
        if (taskMapper.closeBlockEvent(event.getId(), member(actorId), now, resolution, actor(actorId)) != 1) {
            throw new ServiceException("Open block episode changed; refresh and retry");
        }
    }

    @Override public List<LabTaskBlockEvent> listBlockEvents(Long taskId) { getTask(taskId); return taskMapper.selectBlockEvents(taskId); }

    private LabTask loadVersioned(Long id, Integer version) {
        LabTask task = getTask(id);
        if (version == null || !version.equals(task.getVersion())) throw optimisticConflict();
        return task;
    }

    private void saveWorkflowTask(LabTask task, Long actorId) {
        task.setUpdateBy(actor(actorId));
        if (taskMapper.updateTask(task) != 1) throw optimisticConflict();
    }

    private void validateTaskConnections(LabTask task) {
        if (task == null || blank(task.getTaskLevel()) || blank(task.getPeriod())) throw new ServiceException("Task level and period are required");
        String ownerBizLine = task.getOwnerId() == null ? null : taskMapper.selectMemberBizLineById(task.getOwnerId());
        if (ownerBizLine == null || !ownerBizLine.equals(task.getBizLine())) {
            throw new ServiceException("Task business line must match the active owner's responsible scope");
        }
        if (LabConstants.TASK_LEVEL_WEEK.equals(task.getTaskLevel())) {
            LabTask parent = taskMapper.selectTaskById(task.getParentId());
            if (parent == null || !LabConstants.TASK_LEVEL_MONTH.equals(parent.getTaskLevel())) throw new ServiceException("Weekly task must belong to a month task");
            if (!same(parent.getGoalId(), task.getGoalId()) || !same(parent.getMilestoneId(), task.getMilestoneId())) {
                throw new ServiceException("Weekly task must inherit annual goal and milestone links from its month task");
            }
            LabPeriodUtils.PeriodRange month = LabPeriodUtils.parseMonth(parent.getPeriod());
            LabPeriodUtils.PeriodRange week = LabPeriodUtils.parseWeek(task.getPeriod());
            if (week.getStartDate().isBefore(month.getStartDate()) || week.getEndDate().isAfter(month.getEndDate())) {
                throw new ServiceException("Weekly task period must be fully contained in its month task period");
            }
            return;
        }
        if (!LabConstants.TASK_LEVEL_MONTH.equals(task.getTaskLevel())) throw new ServiceException("Task level must be month or week");
        LabPeriodUtils.parseMonth(task.getPeriod());
        LabGoal annual = goalMapper.selectGoalById(task.getGoalId());
        LabGoal milestone = goalMapper.selectGoalById(task.getMilestoneId());
        if (annual == null || milestone == null || !"YEAR".equals(annual.getGoalLevel())
                || !"QUARTER".equals(milestone.getGoalLevel()) || !annual.getId().equals(milestone.getParentId())) {
            throw new ServiceException("Month task must link a quarterly milestone under its annual goal");
        }
        LocalDate monthStart = LabPeriodUtils.parseMonth(task.getPeriod()).getStartDate();
        int quarter = (monthStart.getMonthValue() - 1) / 3 + 1;
        if (!milestone.getYear().equals(monthStart.getYear())
                || !milestone.getPeriod().equals(monthStart.getYear() + "Q" + quarter)) {
            throw new ServiceException("Month task period must belong to its quarterly milestone");
        }
    }

    private void preserveServerState(LabTask task, LabTask stored) {
        task.setWorkflowStatus(stored.getWorkflowStatus()); task.setResultStatus(stored.getResultStatus());
        task.setActualFinishTime(stored.getActualFinishTime()); task.setResultDesc(stored.getResultDesc());
        task.setFailReason(stored.getFailReason()); task.setNextAction(stored.getNextAction());
        task.setBlockFlag(stored.getBlockFlag()); task.setBlockStartTime(stored.getBlockStartTime());
        task.setPeriodLockFlag(stored.getPeriodLockFlag()); task.setDelFlag(stored.getDelFlag());
    }

    private void validateGate(LabTaskQualityGate gate) {
        if (gate == null || gate.getTaskId() == null || blank(gate.getGateNo()) || blank(gate.getGateName())) {
            throw new ServiceException("Quality gate task, number and name are required");
        }
    }

    private void requireIdentity(LabTask task) {
        if (task == null || task.getId() == null || task.getVersion() == null) throw new ServiceException("Task id and version are required");
    }

    private void requireMutable(LabTask task) {
        if (LabConstants.WORKFLOW_CONFIRMED.equals(task.getWorkflowStatus())) {
            throw new ServiceException("Confirmed task is immutable; reopen it before editing");
        }
        if (LabConstants.YES.equals(task.getPeriodLockFlag())) {
            throw new ServiceException("Period-locked task is read-only");
        }
    }

    private void requireOwner(LabTask task, Long actorId) {
        if (actorId == null || !actorId.equals(task.getOwnerId())) throw new ServiceException("Only the task owner can submit or withdraw this result");
    }

    private Long member(Long userId) {
        if (userId == null) throw new ServiceException("Authenticated actor is required");
        Long memberId = taskMapper.selectMemberIdByUserId(userId);
        if (memberId == null) throw new ServiceException("Authenticated user is not an active lab member");
        return memberId;
    }

    private void requireValid(List<FieldValidationError> errors) {
        if (errors == null || errors.isEmpty()) return;
        throw new LabValidationException(errors);
    }

    private boolean isCompleted(String status) {
        return LabConstants.RESULT_EXCEEDED.equals(status) || LabConstants.RESULT_ONTIME.equals(status) || LabConstants.RESULT_DELAYED.equals(status);
    }
    private boolean same(Object left, Object right) { return left == null ? right == null : left.equals(right); }
    private String actor(Long id) { if (id == null) throw new ServiceException("Authenticated actor is required"); return String.valueOf(id); }
    private ServiceException optimisticConflict() { return new ServiceException("Record was changed by another user; refresh and retry"); }
    private BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
