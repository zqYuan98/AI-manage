package com.ailab.system.service.impl;

import com.ailab.system.constant.LabConstants;
import com.ailab.system.domain.LabGoal;
import com.ailab.system.domain.LabTask;
import com.ailab.system.domain.LabTaskBlockEvent;
import com.ailab.system.domain.LabTaskEvidence;
import com.ailab.system.domain.LabTaskQualityGate;
import com.ailab.system.dto.FieldValidationError;
import com.ailab.system.dto.TaskSubmitCommand;
import com.ailab.system.dto.MonthlyCarryCommand;
import com.ailab.system.dto.ProgressComparison;
import com.ailab.system.exception.LabValidationException;
import com.ailab.system.mapper.LabGoalMapper;
import com.ailab.system.mapper.LabTaskEvidenceMapper;
import com.ailab.system.mapper.LabTaskMapper;
import com.ailab.system.service.LabTaskService;
import com.ailab.system.service.LabAccessService;
import com.ailab.system.service.LabCommitmentCalculationService;
import com.ailab.system.service.TaskWorkflowService;
import com.ailab.system.util.LabPeriodUtils;
import com.ruoyi.common.exception.ServiceException;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LabTaskServiceImpl implements LabTaskService {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final Set<String> QUERY_WORKFLOW_STATUSES = new HashSet<String>(Arrays.asList(
            LabConstants.WORKFLOW_DRAFT, LabConstants.WORKFLOW_ACTIVE,
            LabConstants.WORKFLOW_PENDING_REVIEW, LabConstants.WORKFLOW_CONFIRMED));
    private final LabTaskMapper taskMapper;
    private final LabTaskEvidenceMapper evidenceMapper;
    private final LabGoalMapper goalMapper;
    private final TaskWorkflowService workflowService;
    private final LabAccessService accessService;
    private final LabTaskWorkflowEventService workflowEventService;
    private final LabFormalAcceptanceService formalAcceptanceService;
    private final LabCommitmentCalculationService commitmentCalculations;
    private final LabCommitmentProjectionService commitmentProjection;
    private final Clock clock;

    public LabTaskServiceImpl(LabTaskMapper taskMapper, LabTaskEvidenceMapper evidenceMapper,
            LabGoalMapper goalMapper, TaskWorkflowService workflowService, LabAccessService accessService,
            LabTaskWorkflowEventService workflowEventService, LabFormalAcceptanceService formalAcceptanceService) {
        this(taskMapper, evidenceMapper, goalMapper, workflowService, accessService,
                workflowEventService, formalAcceptanceService, null, Clock.systemDefaultZone());
    }

    @Autowired
    public LabTaskServiceImpl(LabTaskMapper taskMapper, LabTaskEvidenceMapper evidenceMapper,
            LabGoalMapper goalMapper, TaskWorkflowService workflowService, LabAccessService accessService,
            LabTaskWorkflowEventService workflowEventService, LabFormalAcceptanceService formalAcceptanceService,
            LabCommitmentProjectionService commitmentProjection) {
        this(taskMapper, evidenceMapper, goalMapper, workflowService, accessService,
                workflowEventService, formalAcceptanceService, commitmentProjection, Clock.systemDefaultZone());
    }

    /** 兼容聚焦旧任务合同的测试构造入口。 */
    public LabTaskServiceImpl(LabTaskMapper taskMapper, LabTaskEvidenceMapper evidenceMapper,
            LabGoalMapper goalMapper, TaskWorkflowService workflowService, LabAccessService accessService) {
        this(taskMapper, evidenceMapper, goalMapper, workflowService, accessService, null, null,
                null, Clock.systemDefaultZone());
    }

    public LabTaskServiceImpl(LabTaskMapper taskMapper, LabTaskEvidenceMapper evidenceMapper,
            LabGoalMapper goalMapper, TaskWorkflowService workflowService, LabAccessService accessService, Clock clock) {
        this(taskMapper, evidenceMapper, goalMapper, workflowService, accessService, null, null, null, clock);
    }

    public LabTaskServiceImpl(LabTaskMapper taskMapper, LabTaskEvidenceMapper evidenceMapper,
            LabGoalMapper goalMapper, TaskWorkflowService workflowService, LabAccessService accessService,
            LabTaskWorkflowEventService workflowEventService, LabFormalAcceptanceService formalAcceptanceService,
            Clock clock) {
        this(taskMapper, evidenceMapper, goalMapper, workflowService, accessService, workflowEventService,
                formalAcceptanceService, null, clock);
    }

    private LabTaskServiceImpl(LabTaskMapper taskMapper, LabTaskEvidenceMapper evidenceMapper,
            LabGoalMapper goalMapper, TaskWorkflowService workflowService, LabAccessService accessService,
            LabTaskWorkflowEventService workflowEventService, LabFormalAcceptanceService formalAcceptanceService,
            LabCommitmentProjectionService commitmentProjection, Clock clock) {
        this.taskMapper = taskMapper;
        this.evidenceMapper = evidenceMapper;
        this.goalMapper = goalMapper;
        this.workflowService = workflowService;
        this.accessService = accessService;
        this.workflowEventService = workflowEventService;
        this.formalAcceptanceService = formalAcceptanceService;
        this.commitmentCalculations = new LabCommitmentCalculationService();
        this.commitmentProjection = commitmentProjection;
        this.clock = clock;
    }

    @Override
    public List<LabTask> listTasks(LabTask query, Long actorId) {
        Page<?> requestedPage = detachPage();
        LabTask scoped = copyTaskQuery(query);
        validateTaskListQuery(scoped);
        accessService.scopeTaskQuery(scoped, actorId);
        restorePage(requestedPage);
        return taskMapper.selectTaskList(scoped);
    }

    private LabTask copyTaskQuery(LabTask query) {
        LabTask copy = new LabTask();
        if (query != null) {
            BeanUtils.copyProperties(query, copy);
            copy.setWorkflowStatuses(query.getWorkflowStatuses());
        }
        return copy;
    }

    private Page<?> detachPage() {
        Page<?> requested = PageHelper.getLocalPage();
        if (requested != null) PageHelper.clearPage();
        return requested;
    }

    private void restorePage(Page<?> requested) {
        if (requested == null) return;
        Page<?> restored = PageHelper.startPage(requested.getPageNum(), requested.getPageSize(), requested.getOrderBy());
        restored.setReasonable(requested.getReasonable());
        restored.setPageSizeZero(requested.getPageSizeZero());
    }

    private void validateTaskListQuery(LabTask query) {
        if (query.getPeriod() != null && !query.getPeriod().isEmpty()) {
            try { LabPeriodUtils.parse(query.getPeriod()); }
            catch (IllegalArgumentException error) { throw new ServiceException("Task period filter must be a valid YYYY-MM or YYYY-Www value"); }
        }
        if (query.getPeriodTo() != null && !query.getPeriodTo().isEmpty()) {
            try { LabPeriodUtils.parseMonth(query.getPeriodTo()); }
            catch (IllegalArgumentException error) { throw new ServiceException("Task periodTo filter must be a valid YYYY-MM value"); }
        }
        if (query.getTaskLevel() != null && !query.getTaskLevel().isEmpty()
                && !LabConstants.TASK_LEVEL_MONTH.equals(query.getTaskLevel())
                && !LabConstants.TASK_LEVEL_WEEK.equals(query.getTaskLevel())) {
            throw new ServiceException("Task level filter must be month or week");
        }
        for (String status : query.getWorkflowStatuses()) {
            if (!QUERY_WORKFLOW_STATUSES.contains(status)) throw new ServiceException("Unsupported task workflow status filter");
        }
        if (query.getCurrentBlockFlag() != null && !LabConstants.YES.equals(query.getCurrentBlockFlag())
                && !LabConstants.NO.equals(query.getCurrentBlockFlag())) {
            throw new ServiceException("Current block flag filter must be 0 or 1");
        }
        if (Boolean.TRUE.equals(query.getOverdueOrPending()) && query.getAsOf() == null) {
            throw new ServiceException("As-of time is required for overdue task filtering");
        }
    }

    @Override
    public LabTask getTask(Long id, Long actorId) {
        LabTask task = loadTask(id);
        accessService.requireTaskRead(task, actorId);
        return task;
    }

    @Override
    @Transactional
    public int createTask(LabTask task, Long actorId) {
        if (task != null) task.setId(null);
        prevalidateMonthlyGoalTypes(task);
        Map<Long, LabGoal> lockedGoals = lockMonthlyGoalMembership(task);
        Map<Long, String> lockedOwners = lockTaskOwners(task);
        lockKeyTaskCollectionsForCreate(task, lockedGoals);
        Map<Long, LabTask> lockedTasks = lockTaskHierarchyForCreate(task);
        validateTaskConnections(task, actorId, lockedGoals, lockedTasks, lockedOwners);
        requireDefinitionWrite(task, actorId);
        task.setWorkflowStatus(LabConstants.WORKFLOW_DRAFT);
        task.setResultStatus(LabConstants.RESULT_DOING);
        task.setExecutionStatus(LabConstants.TASK_LEVEL_WEEK.equals(task.getTaskLevel())
                ? LabConstants.EXECUTION_PLANNED : null);
        task.setExecutionVersion(0);
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
        LabTask snapshot = taskMapper.selectTaskById(task.getId());
        if (snapshot == null) throw new ServiceException("Task does not exist");
        prevalidateMonthlyGoalTypes(snapshot, task);
        Map<Long, LabGoal> lockedGoals = lockMonthlyGoalMembership(snapshot, task);
        Map<Long, String> lockedOwners = lockTaskOwners(snapshot, task);
        lockKeyTaskCollectionsForUpdate(snapshot, task, lockedGoals);
        Map<Long, LabTask> lockedTasks = lockTaskHierarchyForUpdate(snapshot, task);
        LabTask stored = taskMapper.selectTaskById(task.getId());
        if (stored == null || !stored.getVersion().equals(task.getVersion())) throw optimisticConflict();
        requireContentMutable(stored);
        requireDefinitionWrite(stored, actorId);
        requireDefinitionWrite(task, actorId);
        boolean definitionAudit=requiresDefinitionAudit(stored,task);
        if(definitionAudit&&blank(task.getRemark()))throw new ServiceException("已激活定义变更必须填写原因");
        requireStableActivatedFields(stored, task, lockedGoals);
        requireMonthChildrenStable(stored, task);
        validateTaskConnections(task, actorId, lockedGoals, lockedTasks, lockedOwners);
        preserveServerState(task, stored);
        task.setUpdateBy(actor(actorId));
        if (taskMapper.updateTask(task) != 1) throw optimisticConflict();
        if(definitionAudit)appendDefinitionAudit(task,stored.getWorkflowStatus(),
                member(actorId),"DEFINITION_CHANGE",task.getRemark().trim());
        return 1;
    }

    @Override
    @Transactional
    public int deleteTask(Long id, Integer version, Long actorId) {
        LabTask snapshot = taskMapper.selectTaskById(id);
        if (snapshot == null) throw new ServiceException("Task does not exist");
        Map<Long, LabGoal> lockedGoals = lockMonthlyGoalMembership(snapshot);
        lockKeyTaskCollectionsForDelete(snapshot, lockedGoals);
        lockTaskHierarchyForDelete(snapshot);
        LabTask stored = taskMapper.selectTaskById(id);
        if (stored == null || version == null || !version.equals(stored.getVersion())) throw optimisticConflict();
        requireContentMutable(stored);
        requireDefinitionWrite(stored, actorId);
        if (!LabConstants.WORKFLOW_DRAFT.equals(stored.getWorkflowStatus())) {
            throw new ServiceException("Only draft tasks can be deleted");
        }
        if (!taskMapper.selectTasksByParentIdForUpdate(id).isEmpty()) throw new ServiceException("Delete weekly child tasks first");
        if (taskMapper.deleteTask(id, version, actor(actorId)) != 1) throw optimisticConflict();
        return 1;
    }

    @Override
    @Transactional
    public int activateMonthlyPlan(Long ownerId, String period, Long actorId) {
        if (ownerId == null || blank(period)) throw new ServiceException("Owner and month period are required");
        LabPeriodUtils.parseMonth(period);
        if (taskMapper.lockMemberForUpdate(ownerId) == null) throw new ServiceException("Task owner is not an active lab member");
        List<LabTask> tasks = taskMapper.selectKeyMonthTasksByOwnerPeriodForUpdate(ownerId, period);
        for (LabTask task : tasks) { requireUnlocked(task); accessService.requireMonthlyDefinitionWrite(task, actorId); }
        BigDecimal total = BigDecimal.ZERO;
        for (LabTask task : tasks) total = total.add(zero(task.getPerfWeight()));
        if (tasks.isEmpty() || total.compareTo(ONE_HUNDRED) != 0) {
            throw new ServiceException("Monthly key-task performance weights must total 100 before plan activation");
        }
        for (LabTask task : tasks) accessService.requireEligibleReviewer(task);
        for (LabTask task : tasks) {
            List<FieldValidationError> errors = workflowService.activatePlan(task);
            requireValid(errors);
        }
        for (LabTask task : tasks) {
            task.setUpdateBy(actor(actorId));
            if (taskMapper.updateTask(task) != 1) throw optimisticConflict();
            appendWorkflowEvent(task, LabConstants.WORKFLOW_DRAFT, LabConstants.WORKFLOW_ACTIVE,
                    member(actorId), "ACTIVATE", "激活月度结果");
        }
        return tasks.size();
    }

    @Override
    @Transactional
    public void activateTask(Long id, Integer version, Long actorId) {
        LabTask snapshot = loadTask(id);
        LabTask parent = taskMapper.selectTaskForUpdate(snapshot.getParentId());
        LabTask task = taskMapper.selectTaskForUpdate(id);
        if (task == null || version == null || !version.equals(task.getVersion())) throw optimisticConflict();
        requireUnlocked(task);
        accessService.requireWeeklyWrite(task, actorId);
        if (!LabConstants.TASK_LEVEL_WEEK.equals(task.getTaskLevel())) {
            throw new ServiceException("Monthly key tasks must be activated through owner-period plan activation");
        }
        requireOwner(task, member(actorId));
        if (parent == null || !LabConstants.WORKFLOW_ACTIVE.equals(parent.getWorkflowStatus())
                || LabConstants.YES.equals(parent.getPeriodLockFlag())) {
            throw new ServiceException("Weekly task requires an active, unlocked month task");
        }
        accessService.requireTaskRead(parent, actorId);
        if (!LabConstants.TASK_LEVEL_MONTH.equals(parent.getTaskLevel())
                || !same(parent.getId(), task.getParentId()) || !same(parent.getGoalId(), task.getGoalId())
                || !same(parent.getMilestoneId(), task.getMilestoneId())
                || !same(parent.getBizLine(), task.getBizLine())) {
            throw new ServiceException("Weekly task links must match its current month task");
        }
        LabPeriodUtils.PeriodRange month = LabPeriodUtils.parseMonth(parent.getPeriod());
        LabPeriodUtils.parseWeek(task.getPeriod());
        LocalDate planned = businessDate(task.getPlanDate());
        if (planned == null || planned.isBefore(month.getStartDate()) || planned.isAfter(month.getEndDate())) {
            throw new ServiceException("Weekly task links must match its current month task");
        }
        requireValid(workflowService.activatePlan(task));
        saveWorkflowTask(task, actorId);
    }

    @Override
    @Transactional
    public void submitResult(Long id, Integer version, TaskSubmitCommand command, Long actorId) {
        LabTask task = loadVersioned(id, version);
        String fromStatus = task.getWorkflowStatus();
        requireUnlocked(task);
        accessService.requireTaskWrite(task, actorId);
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
        appendWorkflowEvent(task, fromStatus, task.getWorkflowStatus(), actorMemberId,
                "SUBMIT", task.getResultDesc());
    }

    @Override
    @Transactional
    public void withdrawResult(Long id, Integer version, Long actorId) {
        LabTask task = loadVersioned(id, version);
        String fromStatus = task.getWorkflowStatus();
        requireUnlocked(task);
        accessService.requireTaskWrite(task, actorId);
        requireOwner(task, member(actorId));
        workflowService.withdraw(task);
        saveWorkflowTask(task, actorId);
        appendWorkflowEvent(task, fromStatus, task.getWorkflowStatus(), member(actorId),
                "WITHDRAW", "提交人撤回月度结果");
    }

    @Override
    @Transactional
    public void reviewPass(Long id, Integer version, TaskSubmitCommand command, Long actorId) {
        LabTask task = loadVersioned(id, version);
        String fromStatus = task.getWorkflowStatus();
        requireUnlocked(task);
        accessService.requireTaskReview(task, actorId);
        Long actorMemberId = member(actorId);
        requireValid(workflowService.reviewPass(task, command, actorMemberId));
        for (Long evidenceId : command.getApprovedEvidenceIds()) {
            if (evidenceMapper.approveEvidence(evidenceId, id, actorMemberId, Date.from(clock.instant()),
                    command.getEvidenceAuditComment(), actor(actorId)) != 1) {
                throw new ServiceException("Selected evidence changed; refresh and retry");
            }
        }
        saveWorkflowTask(task, actorId);
        appendWorkflowEvent(task, fromStatus, task.getWorkflowStatus(), actorMemberId,
                "CONFIRM", command.getReviewerComment());
        if (formalAcceptanceService != null) {
            formalAcceptanceService.accept(task, actorMemberId, command.getReviewerComment(), task.getVersion());
        }
    }

    @Override
    @Transactional
    public void reviewReturn(Long id, Integer version, TaskSubmitCommand command, Long actorId) {
        LabTask task = loadVersioned(id, version);
        String fromStatus = task.getWorkflowStatus();
        requireUnlocked(task);
        accessService.requireTaskReview(task, actorId);
        workflowService.reviewReturn(task, command, member(actorId));
        saveWorkflowTask(task, actorId);
        appendWorkflowEvent(task, fromStatus, task.getWorkflowStatus(), member(actorId),
                "RETURN", command.getReviewerComment());
    }

    @Override
    @Transactional
    public void reopenTask(Long id, Integer version, String reason, Long actorId) {
        LabTask task = loadVersioned(id, version);
        String fromStatus = task.getWorkflowStatus();
        requireUnlocked(task);
        accessService.requireManager(actorId);
        workflowService.managerReopen(task, member(actorId), reason);
        task.setRemark(reason);
        saveWorkflowTask(task, actorId);
        appendWorkflowEvent(task, fromStatus, task.getWorkflowStatus(), member(actorId), "REOPEN", reason);
    }

    @Override
    @Transactional
    public LabTask carryMonthlyResult(Long id, Integer version, MonthlyCarryCommand command, Long actorId) {
        LabTask source = loadVersioned(id, version);
        accessService.requireTaskWrite(source, actorId);
        if (!LabConstants.TASK_LEVEL_MONTH.equals(source.getTaskLevel())
                || !LabConstants.WORKFLOW_CONFIRMED.equals(source.getWorkflowStatus())
                || !LabConstants.RESULT_UNDONE.equals(source.getResultStatus())) {
            throw new ServiceException("只有已确认且未完成的月度结果可以转入下月");
        }
        if (command == null || command.getPlanDate() == null || blank(command.getReason())) {
            throw new ServiceException("转入下月必须填写计划日期和原因");
        }
        YearMonth targetMonth = YearMonth.from(LabPeriodUtils.parseMonth(source.getPeriod()).getStartDate()).plusMonths(1);
        String targetPeriod = targetMonth.toString();
        LocalDate targetDate = command.getPlanDate().toInstant().atZone(clock.getZone()).toLocalDate();
        if (!YearMonth.from(targetDate).equals(targetMonth)) {
            throw new ServiceException("转期计划日期必须位于紧接的下一个月份");
        }
        LabTask existing = taskMapper.selectCarriedTask(source.getId(), targetPeriod);
        if (existing != null) return existing;
        LabTask carried = copyForNextMonth(source, targetPeriod, command.getPlanDate(), actorId);
        if (taskMapper.insertTask(carried) != 1) { throw new ServiceException("月度结果转期创建失败"); }
        appendWorkflowEvent(source, LabConstants.WORKFLOW_CONFIRMED, LabConstants.WORKFLOW_CONFIRMED,
                member(actorId), "CARRY", command.getReason().trim());
        return carried;
    }

    private LabTask copyForNextMonth(LabTask source, String targetPeriod, Date planDate, Long actorId) {
        LabTask target = new LabTask();
        target.setParentId(0L); target.setGoalId(source.getGoalId()); target.setMilestoneId(source.getMilestoneId());
        target.setTaskLevel(LabConstants.TASK_LEVEL_MONTH); target.setPeriod(targetPeriod); target.setBizLine(source.getBizLine());
        target.setTaskType(source.getTaskType()); target.setTitle(source.getTitle()); target.setOwnerId(source.getOwnerId());
        target.setDeptId(source.getDeptId()); target.setPlanDate(planDate); target.setDeliverable(source.getDeliverable());
        target.setPerfWeight(source.getPerfWeight()); target.setGoalWeight(source.getGoalWeight());
        target.setWorkflowStatus(LabConstants.WORKFLOW_DRAFT); target.setResultStatus(LabConstants.RESULT_DOING);
        target.setExecutionStatus(null); target.setExecutionVersion(0); target.setCarriedFromId(source.getId());
        target.setAssetId(source.getAssetId()); target.setCoordinationRequired(source.getCoordinationRequired());
        target.setCoordinationOwnerId(source.getCoordinationOwnerId()); target.setCoordinationDeptId(source.getCoordinationDeptId());
        target.setCoordinationContent(source.getCoordinationContent()); target.setCoordinationSupport(source.getCoordinationSupport());
        target.setCoordinationDesc(source.getCoordinationDesc()); target.setBlockFlag(LabConstants.NO);
        target.setPeriodLockFlag(LabConstants.NO); target.setVersion(0); target.setDelFlag(LabConstants.NO);
        target.setCreateBy(actor(actorId)); return target;
    }

    @Override
    public BigDecimal calculateMonthProgress(Long monthTaskId, Long actorId) {
        LabTask month = taskMapper.selectTaskById(monthTaskId);
        if (month == null || !LabConstants.TASK_LEVEL_MONTH.equals(month.getTaskLevel())) {
            throw new ServiceException("Monthly progress requires a month task");
        }
        accessService.requireTaskRead(month, actorId);
        return legacyMonthProgress(taskMapper.selectTasksByParentId(monthTaskId));
    }

    @Override
    public ProgressComparison compareMonthProgress(Long monthTaskId, Date asOf, Long actorId) {
        LabTask month = taskMapper.selectTaskById(monthTaskId);
        if (month == null || !LabConstants.TASK_LEVEL_MONTH.equals(month.getTaskLevel())) {
            throw new ServiceException("Monthly progress requires a month task");
        }
        accessService.requireTaskRead(month, actorId);
        BigDecimal legacy = legacyMonthProgress(taskMapper.selectTasksByParentId(monthTaskId));
        return ProgressComparison.legacyActive(legacy, commitmentProjection == null
                ? commitmentCalculations.calculateMonth(month,
                    taskMapper.selectCommitmentsForCalculation(monthTaskId, asOf), asOf, null, null, false)
                : commitmentProjection.projectMonth(month, asOf));
    }

    private BigDecimal legacyMonthProgress(List<LabTask> weeks) {
        if (weeks.isEmpty()) return BigDecimal.ZERO.setScale(2);
        int confirmed = 0, completed = 0;
        for (LabTask week : weeks) {
            if (LabConstants.WORKFLOW_CONFIRMED.equals(week.getWorkflowStatus())) {
                confirmed++;
                if (isCompleted(week.getResultStatus())) completed++;
            }
        }
        if (confirmed == 0) return BigDecimal.ZERO.setScale(2);
        return new BigDecimal(completed).multiply(ONE_HUNDRED)
                .divide(new BigDecimal(confirmed), 2, RoundingMode.HALF_UP);
    }

    @Override public List<LabTaskEvidence> listEvidence(Long taskId, Long actorId) { getTask(taskId, actorId); return evidenceMapper.selectEvidenceByTaskId(taskId); }

    @Override
    @Transactional
    public LabTaskEvidence addEvidence(Long taskId, LabTaskEvidence evidence, Long actorId) {
        LabTask task = loadTask(taskId);
        requireContentMutable(task);
        accessService.requireTaskWrite(task, actorId);
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
        LabTask task = loadTask(taskId);
        requireContentMutable(task);
        accessService.requireTaskWrite(task, actorId);
        if (evidenceMapper.deleteEvidence(evidenceId, taskId, actor(actorId)) != 1) {
            throw new ServiceException("Only pending evidence belonging to this task can be deleted");
        }
        return 1;
    }

    @Override public List<LabTaskQualityGate> listQualityGates(Long taskId, Long actorId) { getTask(taskId, actorId); return taskMapper.selectQualityGates(taskId); }

    @Override
    public LabTaskQualityGate getQualityGate(Long id, Long actorId) {
        LabTaskQualityGate gate = taskMapper.selectQualityGateById(id);
        if (gate == null) throw new ServiceException("Quality gate does not exist");
        accessService.requireTaskRead(loadTask(gate.getTaskId()), actorId);
        return gate;
    }

    @Override
    @Transactional
    public LabTaskQualityGate addQualityGate(LabTaskQualityGate gate, Long actorId) {
        validateGate(gate); LabTask task = loadTask(gate.getTaskId()); requireContentMutable(task); accessService.requireTaskWrite(task, actorId);
        gate.setId(null); gate.setGateStatus("PENDING"); gate.setCheckerId(null); gate.setCheckTime(null);
        gate.setCheckResult(null); gate.setDelFlag(LabConstants.NO); gate.setCreateBy(actor(actorId));
        if (taskMapper.insertQualityGate(gate) != 1) throw new ServiceException("Quality gate could not be added because the task changed or became read-only");
        return gate;
    }

    @Override
    @Transactional
    public int updateQualityGate(LabTaskQualityGate gate, Long actorId) {
        validateGate(gate);
        LabTaskQualityGate stored = taskMapper.selectQualityGateById(gate.getId());
        if (stored == null || "PASSED".equals(stored.getGateStatus())) throw new ServiceException("Passed or missing quality gate cannot be edited");
        LabTask task = loadTask(stored.getTaskId()); requireContentMutable(task); accessService.requireTaskWrite(task, actorId);
        gate.setTaskId(stored.getTaskId());
        gate.setGateStatus(stored.getGateStatus()); gate.setCheckerId(stored.getCheckerId()); gate.setCheckTime(stored.getCheckTime());
        gate.setCheckResult(stored.getCheckResult()); gate.setDelFlag(stored.getDelFlag()); gate.setUpdateBy(actor(actorId));
        if (taskMapper.updateQualityGate(gate) != 1) throw new ServiceException("Quality gate changed or its task became read-only");
        return 1;
    }

    @Override
    @Transactional
    public int deleteQualityGate(Long id, Long actorId) {
        LabTaskQualityGate stored = taskMapper.selectQualityGateById(id);
        if (stored == null || "PASSED".equals(stored.getGateStatus())) throw new ServiceException("Passed or missing quality gate cannot be deleted");
        LabTask task = loadTask(stored.getTaskId()); requireContentMutable(task); accessService.requireTaskWrite(task, actorId);
        if (taskMapper.deleteQualityGate(id, actor(actorId)) != 1) throw new ServiceException("Quality gate changed or its task became read-only");
        return 1;
    }

    @Override
    @Transactional
    public void passQualityGate(Long gateId, Long approvedEvidenceId, String result, Long actorId) {
        LabTaskQualityGate gate = taskMapper.selectQualityGateById(gateId);
        LabTaskEvidence evidence = evidenceMapper.selectEvidenceById(approvedEvidenceId);
        if (gate != null) { LabTask task = loadTask(gate.getTaskId()); requireGatePassAllowed(task); accessService.requireTaskReview(task, actorId); }
        if (gate == null || evidence == null || !gate.getTaskId().equals(evidence.getTaskId())
                || !LabConstants.EVIDENCE_AUDIT_APPROVED.equals(evidence.getAuditStatus()) || "2".equals(evidence.getDelFlag())) {
            throw new ServiceException("Quality gate requires explicit approved evidence from the same task");
        }
        if (taskMapper.markQualityGatePassed(gateId, approvedEvidenceId, member(actorId), Date.from(clock.instant()), result, actor(actorId)) != 1) {
            throw new ServiceException("Quality gate is already passed or changed");
        }
    }

    @Override
    @Transactional
    public LabTaskBlockEvent blockTask(Long taskId, Integer version, String type, String reason, Long actorId) {
        LabTask task = loadVersioned(taskId, version);
        requireContentMutable(task);
        accessService.requireTaskWrite(task, actorId);
        if (LabConstants.YES.equals(task.getBlockFlag()) || taskMapper.selectOpenBlockEvent(taskId) != null) {
            throw new ServiceException("Task is already blocked");
        }
        if (blank(type) || blank(reason)) throw new ServiceException("Block type and reason are required");
        Date now = Date.from(clock.instant());
        task.setBlockFlag(LabConstants.YES); task.setBlockStartTime(now); saveWorkflowTask(task, actorId);
        LabTaskBlockEvent event = new LabTaskBlockEvent(); event.setTaskId(taskId); event.setBlockType(type);
        event.setEpisodeNo(taskMapper.selectNextBlockEpisodeNo(taskId));
        event.setBlockReason(reason); event.setBlockStartTime(now); event.setBlockStatus("OPEN");
        event.setDelFlag(LabConstants.NO); event.setCreateBy(actor(actorId)); taskMapper.insertBlockEvent(event); return event;
    }

    @Override
    @Transactional
    public void unblockTask(Long taskId, Integer version, String resolution, Long actorId) {
        LabTask task = loadVersioned(taskId, version);
        requireContentMutable(task);
        accessService.requireTaskWrite(task, actorId);
        LabTaskBlockEvent event = taskMapper.selectOpenBlockEvent(taskId);
        if (!LabConstants.YES.equals(task.getBlockFlag()) || event == null) throw new ServiceException("Task is not blocked");
        if (blank(resolution)) throw new ServiceException("Block resolution is required");
        Date now = Date.from(clock.instant());
        task.setBlockFlag(LabConstants.NO); task.setBlockStartTime(null); saveWorkflowTask(task, actorId);
        if (taskMapper.closeBlockEvent(event.getId(), member(actorId), now, resolution, actor(actorId)) != 1) {
            throw new ServiceException("Open block episode changed; refresh and retry");
        }
    }

    @Override public List<LabTaskBlockEvent> listBlockEvents(Long taskId, Long actorId) { getTask(taskId, actorId); return taskMapper.selectBlockEvents(taskId); }

    private LabTask loadVersioned(Long id, Integer version) {
        LabTask task = loadTask(id);
        if (version == null || !version.equals(task.getVersion())) throw optimisticConflict();
        return task;
    }

    private LabTask loadTask(Long id) {
        LabTask task = taskMapper.selectTaskById(id);
        if (task == null) throw new ServiceException("Task does not exist");
        task.setEvidenceList(evidenceMapper.selectEvidenceByTaskId(id));
        return task;
    }

    private void saveWorkflowTask(LabTask task, Long actorId) {
        task.setUpdateBy(actor(actorId));
        if (taskMapper.updateTask(task) != 1) throw optimisticConflict();
    }

    private void appendWorkflowEvent(LabTask task, String fromStatus, String toStatus,
            Long actorMemberId, String eventType, String reason) {
        if (workflowEventService != null && LabConstants.TASK_LEVEL_MONTH.equals(task.getTaskLevel())) {
            workflowEventService.append(task, fromStatus, toStatus, actorMemberId, eventType, reason);
        }
    }

    private void requireDefinitionWrite(LabTask task, Long actorId) {
        if (task != null && LabConstants.TASK_LEVEL_MONTH.equals(task.getTaskLevel())) {
            accessService.requireMonthlyDefinitionWrite(task, actorId);
        } else {
            accessService.requireWeeklyWrite(task, actorId);
        }
    }

    private void appendDefinitionAudit(LabTask task, String status, Long actorMemberId,
            String eventType, String reason) {
        if (workflowEventService != null) {
            workflowEventService.append(task, status, status, actorMemberId, eventType, reason);
        }
    }

    private boolean requiresDefinitionAudit(LabTask stored, LabTask proposed) {
        if (stored == null || proposed == null || LabConstants.WORKFLOW_DRAFT.equals(stored.getWorkflowStatus())) return false;
        return !same(stored.getTitle(),proposed.getTitle()) || !same(stored.getDeliverable(),proposed.getDeliverable())
                || !same(stored.getPlanDate(),proposed.getPlanDate()) || !same(stored.getOwnerId(),proposed.getOwnerId())
                || !same(stored.getBizLine(),proposed.getBizLine()) || !same(stored.getParentId(),proposed.getParentId())
                || !same(stored.getPerfWeight(),proposed.getPerfWeight()) || !same(stored.getGoalWeight(),proposed.getGoalWeight())
                || !same(stored.getAssetId(),proposed.getAssetId())
                || !same(stored.getCoordinationRequired(),proposed.getCoordinationRequired())
                || !same(stored.getCoordinationOwnerId(),proposed.getCoordinationOwnerId())
                || !same(stored.getCoordinationDeptId(),proposed.getCoordinationDeptId())
                || !same(stored.getCoordinationContent(),proposed.getCoordinationContent())
                || !same(stored.getCoordinationSupport(),proposed.getCoordinationSupport());
    }

    private void validateTaskConnections(LabTask task, Long actorId, Map<Long, LabGoal> lockedGoals,
            Map<Long, LabTask> lockedTasks, Map<Long, String> lockedOwners) {
        if (task == null || blank(task.getTaskLevel()) || blank(task.getPeriod())) throw new ServiceException("Task level and period are required");
        requireWeight("Performance weight", task.getPerfWeight());
        requireWeight("Goal weight", task.getGoalWeight());
        String ownerBizLine = task.getOwnerId() == null ? null : lockedOwners.get(task.getOwnerId());
        if (ownerBizLine == null || !ownerBizLine.equals(task.getBizLine())) {
            throw new ServiceException("Task business line must match the active owner's responsible scope");
        }
        if (LabConstants.TASK_LEVEL_WEEK.equals(task.getTaskLevel())) {
            if (task.getId() != null && task.getId().equals(task.getParentId())) {
                throw new ServiceException("Task cannot be its own parent");
            }
            LabTask parent = lockedTasks.get(task.getParentId());
            if (parent == null || !LabConstants.TASK_LEVEL_MONTH.equals(parent.getTaskLevel())) throw new ServiceException("Weekly task must belong to a month task");
            accessService.requireTaskRead(parent, actorId);
            if (!same(parent.getGoalId(), task.getGoalId()) || !same(parent.getMilestoneId(), task.getMilestoneId())) {
                throw new ServiceException("Weekly task must inherit annual goal and milestone links from its month task");
            }
            if (!same(parent.getBizLine(), task.getBizLine())) throw new ServiceException("Weekly task must use its month task business line");
            if (LabConstants.YES.equals(parent.getPeriodLockFlag())) throw new ServiceException("Weekly task cannot change a period-locked month plan");
            LabPeriodUtils.PeriodRange month = LabPeriodUtils.parseMonth(parent.getPeriod());
            LabPeriodUtils.parseWeek(task.getPeriod());
            LocalDate planned = businessDate(task.getPlanDate());
            if (planned == null || planned.isBefore(month.getStartDate()) || planned.isAfter(month.getEndDate())) {
                throw new ServiceException("Weekly task plan date must belong to its parent month");
            }
            return;
        }
        if (!LabConstants.TASK_LEVEL_MONTH.equals(task.getTaskLevel())) throw new ServiceException("Task level must be month or week");
        if (task.getParentId() != null && task.getParentId() != 0L) {
            throw new ServiceException("Month task cannot have a task parent");
        }
        LabPeriodUtils.parseMonth(task.getPeriod());
        LabGoal annual = lockedGoals.get(task.getGoalId());
        LabGoal milestone = lockedGoals.get(task.getMilestoneId());
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

    private void prevalidateMonthlyGoalTypes(LabTask... tasks) {
        if (tasks == null) return;
        for (LabTask task : tasks) {
            if (!isMonth(task)) continue;
            LabGoal annual = goalMapper.selectGoalById(task.getGoalId());
            LabGoal milestone = goalMapper.selectGoalById(task.getMilestoneId());
            if (annual == null || milestone == null || !"YEAR".equals(annual.getGoalLevel())
                    || !"QUARTER".equals(milestone.getGoalLevel())
                    || !annual.getId().equals(milestone.getParentId())) {
                throw new ServiceException("Month task must link a quarterly milestone under its annual goal");
            }
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

    private void requireContentMutable(LabTask task) {
        requireUnlocked(task);
        if (LabConstants.WORKFLOW_PENDING_REVIEW.equals(task.getWorkflowStatus())
                || LabConstants.WORKFLOW_CONFIRMED.equals(task.getWorkflowStatus())) {
            throw new ServiceException("Pending-review and confirmed task content is immutable; withdraw or reopen it first");
        }
    }

    private void requireGatePassAllowed(LabTask task) {
        requireUnlocked(task);
        if (!LabConstants.WORKFLOW_CONFIRMED.equals(task.getWorkflowStatus())) {
            throw new ServiceException("Quality gate can pass only after task review confirms its approved evidence");
        }
    }

    private void requireUnlocked(LabTask task) {
        if (LabConstants.YES.equals(task.getPeriodLockFlag())) {
            throw new ServiceException("Period-locked task is read-only");
        }
    }

    private void requireStableActivatedFields(LabTask stored, LabTask proposed,
            Map<Long, LabGoal> lockedGoals) {
        if (LabConstants.WORKFLOW_DRAFT.equals(stored.getWorkflowStatus())) {
            LabGoal milestone = isMonth(stored) ? lockedGoals.get(stored.getMilestoneId())
                    : goalMapper.selectGoalById(stored.getMilestoneId());
            if (milestone != null && "ACTIVE".equals(milestone.getStatus())
                    && !same(stored.getGoalWeight(), proposed.getGoalWeight())) {
                throw new ServiceException("Goal weight is immutable after milestone activation");
            }
            return;
        }
        if (!same(stored.getPlanDate(), proposed.getPlanDate()) || !same(stored.getGoalId(), proposed.getGoalId())
                || !same(stored.getMilestoneId(), proposed.getMilestoneId()) || !same(stored.getPerfWeight(), proposed.getPerfWeight())
                || !same(stored.getGoalWeight(), proposed.getGoalWeight()) || !same(stored.getOwnerId(), proposed.getOwnerId())
                || !same(stored.getPeriod(), proposed.getPeriod()) || !same(stored.getParentId(), proposed.getParentId())
                || !same(stored.getTaskLevel(), proposed.getTaskLevel()) || !same(stored.getBizLine(), proposed.getBizLine())
                || !same(stored.getTaskType(), proposed.getTaskType()) || !same(stored.getDeptId(), proposed.getDeptId())) {
            throw new ServiceException("Activated task planning links, owner, period and weights are immutable");
        }
    }

    private void requireMonthChildrenStable(LabTask stored, LabTask proposed) {
        if (!LabConstants.TASK_LEVEL_MONTH.equals(stored.getTaskLevel()) || taskMapper.selectTasksByParentIdForUpdate(stored.getId()).isEmpty()) return;
        if (!same(stored.getGoalId(), proposed.getGoalId()) || !same(stored.getMilestoneId(), proposed.getMilestoneId())
                || !same(stored.getOwnerId(), proposed.getOwnerId()) || !same(stored.getPeriod(), proposed.getPeriod())
                || !same(stored.getParentId(), proposed.getParentId()) || !same(stored.getTaskLevel(), proposed.getTaskLevel())
                || !same(stored.getBizLine(), proposed.getBizLine()) || !same(stored.getDeptId(), proposed.getDeptId())
                || !same(stored.getTaskType(), proposed.getTaskType())) {
            throw new ServiceException("Month task hierarchy and inherited links are immutable while weekly children exist");
        }
    }

    private void lockKeyTaskCollectionsForCreate(LabTask task, Map<Long, LabGoal> lockedGoals) {
        if (!isKeyMonth(task)) return;
        LabGoal milestone = lockedGoals.get(task.getMilestoneId());
        if (milestone == null || !"DRAFT".equals(milestone.getStatus())) {
            throw new ServiceException("Key month membership is frozen after milestone activation");
        }
        requirePlanCollectionDraft(task.getOwnerId(), task.getPeriod());
    }

    private Map<Long, LabTask> lockTaskHierarchyForCreate(LabTask task) {
        Map<Long, LabTask> locked = new LinkedHashMap<Long, LabTask>();
        if (task != null && LabConstants.TASK_LEVEL_WEEK.equals(task.getTaskLevel()) && task.getParentId() != null) {
            locked.put(task.getParentId(), taskMapper.selectTaskForUpdate(task.getParentId()));
        }
        return locked;
    }

    private Map<Long, LabGoal> lockMonthlyGoalMembership(LabTask... tasks) {
        List<Long> annualIds = new ArrayList<Long>();
        List<Long> milestoneIds = new ArrayList<Long>();
        if (tasks != null) {
            for (LabTask task : tasks) {
                if (!isMonth(task)) continue;
                addDistinctId(annualIds, task.getGoalId());
                addDistinctId(milestoneIds, task.getMilestoneId());
            }
        }
        Collections.sort(annualIds);
        Collections.sort(milestoneIds);
        Map<Long, LabGoal> locked = new LinkedHashMap<Long, LabGoal>();
        for (Long annualId : annualIds) locked.put(annualId, goalMapper.selectGoalForUpdate(annualId));
        for (Long milestoneId : milestoneIds) {
            if (!locked.containsKey(milestoneId)) locked.put(milestoneId, goalMapper.selectGoalForUpdate(milestoneId));
        }
        return locked;
    }

    private Map<Long, String> lockTaskOwners(LabTask... tasks) {
        List<Long> ownerIds = new ArrayList<Long>();
        if (tasks != null) {
            for (LabTask task : tasks) {
                if (task != null) addDistinctId(ownerIds, task.getOwnerId());
            }
        }
        Collections.sort(ownerIds);
        Map<Long, String> locked = new LinkedHashMap<Long, String>();
        for (Long ownerId : ownerIds) {
            String bizLine = taskMapper.lockMemberForUpdate(ownerId);
            if (bizLine == null) throw new ServiceException("Task owner is not an active lab member");
            locked.put(ownerId, bizLine);
        }
        return locked;
    }

    private void addDistinctId(List<Long> ids, Long id) {
        if (id != null && !ids.contains(id)) ids.add(id);
    }

    private Map<Long, LabTask> lockTaskHierarchyForUpdate(LabTask stored, LabTask proposed) {
        List<Long> ids = new ArrayList<Long>();
        addHierarchyLockId(ids, stored);
        addHierarchyLockId(ids, proposed);
        Collections.sort(ids);
        Map<Long, LabTask> locked = new LinkedHashMap<Long, LabTask>();
        for (Long id : ids) locked.put(id, taskMapper.selectTaskForUpdate(id));
        return locked;
    }

    private void lockTaskHierarchyForDelete(LabTask task) {
        List<Long> ids = new ArrayList<Long>();
        addHierarchyLockId(ids, task);
        Collections.sort(ids);
        for (Long id : ids) taskMapper.selectTaskForUpdate(id);
    }

    private void addHierarchyLockId(List<Long> ids, LabTask task) {
        if (task == null) return;
        Long id = LabConstants.TASK_LEVEL_WEEK.equals(task.getTaskLevel()) ? task.getParentId() : task.getId();
        if (id != null && !ids.contains(id)) ids.add(id);
    }

    private void lockKeyTaskCollectionsForDelete(LabTask task, Map<Long, LabGoal> lockedGoals) {
        if (!isKeyMonth(task)) return;
        LabGoal milestone = lockedGoals.get(task.getMilestoneId());
        if (milestone == null || !"DRAFT".equals(milestone.getStatus())) {
            throw new ServiceException("Key month membership is frozen after milestone activation");
        }
        lockMember(task.getOwnerId());
        requirePlanCollectionDraft(task.getOwnerId(), task.getPeriod());
    }

    private void lockKeyTaskCollectionsForUpdate(LabTask stored, LabTask proposed,
            Map<Long, LabGoal> lockedGoals) {
        boolean storedKeyMonth = isKeyMonth(stored);
        boolean proposedKeyMonth = isKeyMonth(proposed);
        if (!storedKeyMonth && !proposedKeyMonth) return;
        List<Long> milestoneIds = distinctSorted(stored.getMilestoneId(), proposed.getMilestoneId());
        List<LabGoal> milestones = new ArrayList<LabGoal>();
        for (Long milestoneId : milestoneIds) milestones.add(lockedGoals.get(milestoneId));
        boolean milestoneCollectionChanges = storedKeyMonth != proposedKeyMonth
                || !same(stored.getMilestoneId(), proposed.getMilestoneId())
                || !same(stored.getGoalWeight(), proposed.getGoalWeight());
        if (milestoneCollectionChanges) {
            for (LabGoal milestone : milestones) {
                if (milestone == null || !"DRAFT".equals(milestone.getStatus())) {
                    throw new ServiceException("Key month weights and membership are frozen after milestone activation");
                }
            }
        }

        boolean planCollectionChanges = storedKeyMonth != proposedKeyMonth
                || !same(stored.getOwnerId(), proposed.getOwnerId()) || !same(stored.getPeriod(), proposed.getPeriod())
                || !same(stored.getPerfWeight(), proposed.getPerfWeight());
        if (planCollectionChanges) {
            if (storedKeyMonth) requirePlanCollectionDraft(stored.getOwnerId(), stored.getPeriod());
            if (proposedKeyMonth && (!storedKeyMonth || !same(stored.getOwnerId(), proposed.getOwnerId())
                    || !same(stored.getPeriod(), proposed.getPeriod()))) {
                requirePlanCollectionDraft(proposed.getOwnerId(), proposed.getPeriod());
            }
        }
    }

    private void requirePlanCollectionDraft(Long ownerId, String period) {
        for (LabTask sibling : taskMapper.selectKeyMonthTasksByOwnerPeriodForUpdate(ownerId, period)) {
            if (LabConstants.WORKFLOW_ACTIVE.equals(sibling.getWorkflowStatus())
                    || LabConstants.WORKFLOW_PENDING_REVIEW.equals(sibling.getWorkflowStatus())
                    || LabConstants.WORKFLOW_CONFIRMED.equals(sibling.getWorkflowStatus())) {
                throw new ServiceException("Monthly key-task membership and weights are frozen after plan activation");
            }
        }
    }

    private void lockMember(Long ownerId) {
        if (ownerId == null || taskMapper.lockMemberForUpdate(ownerId) == null) {
            throw new ServiceException("Task owner is not an active lab member");
        }
    }

    private List<Long> distinctSorted(Long first, Long second) {
        List<Long> ids = new ArrayList<Long>();
        if (first != null) ids.add(first);
        if (second != null && !ids.contains(second)) ids.add(second);
        Collections.sort(ids);
        return ids;
    }

    private boolean isKeyMonth(LabTask task) {
        return task != null && LabConstants.TASK_LEVEL_MONTH.equals(task.getTaskLevel()) && "key".equals(task.getTaskType());
    }

    private boolean isMonth(LabTask task) {
        return task != null && LabConstants.TASK_LEVEL_MONTH.equals(task.getTaskLevel());
    }

    private void requireWeight(String label, BigDecimal value) {
        if (value != null && (value.signum() < 0 || value.compareTo(ONE_HUNDRED) > 0)) {
            throw new ServiceException(label + " must be between 0 and 100");
        }
    }

    private void requireOwner(LabTask task, Long actorId) {
        if (actorId == null || !actorId.equals(task.getOwnerId())) throw new ServiceException("Only the task owner can submit or withdraw this result");
    }

    private Long member(Long userId) {
        return accessService.context(userId).getMemberId();
    }

    private void requireValid(List<FieldValidationError> errors) {
        if (errors == null || errors.isEmpty()) return;
        throw new LabValidationException(errors);
    }

    private boolean isCompleted(String status) {
        return LabConstants.RESULT_EXCEEDED.equals(status) || LabConstants.RESULT_ONTIME.equals(status) || LabConstants.RESULT_DELAYED.equals(status);
    }

    private LocalDate businessDate(Date value) {
        return value == null ? null : value.toInstant().atZone(clock.getZone()).toLocalDate();
    }
    private boolean same(Object left, Object right) { return left == null ? right == null : left.equals(right); }
    private String actor(Long id) { if (id == null) throw new ServiceException("Authenticated actor is required"); return String.valueOf(id); }
    private ServiceException optimisticConflict() { return new ServiceException("Record was changed by another user; refresh and retry"); }
    private BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
