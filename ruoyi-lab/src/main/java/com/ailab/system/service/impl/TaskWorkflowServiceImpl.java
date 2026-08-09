package com.ailab.system.service.impl;

import com.ailab.system.constant.LabConstants;
import com.ailab.system.domain.LabTask;
import com.ailab.system.domain.LabTaskEvidence;
import com.ailab.system.dto.FieldValidationError;
import com.ailab.system.dto.TaskSubmitCommand;
import com.ailab.system.service.TaskWorkflowService;
import com.ailab.system.util.LabPeriodUtils;
import com.ruoyi.common.exception.ServiceException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** In-memory, deterministic transition rules; persistence belongs to a higher layer. */
@Service
public class TaskWorkflowServiceImpl implements TaskWorkflowService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private final Clock clock;

    public TaskWorkflowServiceImpl() {
        this(Clock.systemDefaultZone());
    }

    public TaskWorkflowServiceImpl(Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("clock不能为空");
        }
        this.clock = clock;
    }

    @Override
    public List<FieldValidationError> activatePlan(LabTask task) {
        requireTask(task);
        requireWorkflow(task, LabConstants.WORKFLOW_DRAFT);
        if (LabConstants.YES.equals(task.getPeriodLockFlag())) {
            throw new ServiceException("已锁定周期不能激活任务计划");
        }
        List<FieldValidationError> errors = validatePlan(task);
        if (!errors.isEmpty()) {
            return errors;
        }
        task.setWorkflowStatus(LabConstants.WORKFLOW_ACTIVE);
        task.setResultStatus(LabConstants.RESULT_DOING);
        return errors;
    }

    @Override
    public List<FieldValidationError> submitResult(LabTask task, TaskSubmitCommand command, Long actorId) {
        requireTask(task);
        requireMonthlyTask(task);
        requireWorkflow(task, LabConstants.WORKFLOW_ACTIVE);
        if (command == null) {
            return single("command", "提交内容不能为空");
        }
        String requested = command.getRequestedResultStatus();
        if (LabConstants.RESULT_ONTIME.equals(requested) || LabConstants.RESULT_DELAYED.equals(requested)) {
            return single("requestedResultStatus", "完成结果由系统根据实际完成时间自动计算");
        }
        if (requested != null && !requested.isEmpty()
                && !LabConstants.RESULT_EXCEEDED.equals(requested)
                && !LabConstants.RESULT_UNDONE.equals(requested)) {
            return single("requestedResultStatus", "不支持的任务结果状态");
        }
        List<FieldValidationError> errors = LabConstants.RESULT_UNDONE.equals(requested)
                ? validateUndone(command) : validateCompletion(command);
        if (LabConstants.RESULT_UNDONE.equals(requested)) {
            validateEvidenceBatch(command.getEvidenceList(), false, errors);
        }
        validateSubmissionContext(task, actorId, errors);
        validateCoordination(task, errors);
        if (!errors.isEmpty()) {
            return errors;
        }
        task.setResultDesc(command.getResultDesc());
        task.setFailReason(command.getFailReason());
        task.setNextAction(command.getNextAction());
        task.setActualFinishTime(command.getActualFinishTime());
        appendEvidence(task, command.getEvidenceList(), actorId);
        task.setResultStatus(LabConstants.RESULT_UNDONE.equals(requested)
                ? LabConstants.RESULT_UNDONE : deriveCompletionStatus(task, requested));
        task.setWorkflowStatus(LabConstants.WORKFLOW_PENDING_REVIEW);
        return errors;
    }

    @Override
    public void withdraw(LabTask task) {
        requireTask(task);
        requireMonthlyTask(task);
        requireWorkflow(task, LabConstants.WORKFLOW_PENDING_REVIEW);
        task.setWorkflowStatus(LabConstants.WORKFLOW_ACTIVE);
    }

    @Override
    public List<FieldValidationError> reviewPass(LabTask task, TaskSubmitCommand command, Long actorId) {
        requireTask(task);
        requireMonthlyTask(task);
        requireWorkflow(task, LabConstants.WORKFLOW_PENDING_REVIEW);
        if (command == null) {
            return single("command", "审核内容不能为空");
        }
        if (actorId == null) {
            throw new ServiceException("审核人不能为空");
        }
        if (actorId.equals(task.getOwnerId())) {
            throw new ServiceException("审核人不能是任务负责人");
        }
        List<FieldValidationError> errors = validateReview(task, command);
        if (!errors.isEmpty()) {
            return errors;
        }
        verifyEvidence(task, selectedPendingEvidence(task, command), command, actorId);
        task.setWorkflowStatus(LabConstants.WORKFLOW_CONFIRMED);
        return errors;
    }

    @Override
    public void reviewReturn(LabTask task, TaskSubmitCommand command, Long actorId) {
        requireTask(task);
        requireMonthlyTask(task);
        requireWorkflow(task, LabConstants.WORKFLOW_PENDING_REVIEW);
        if (command == null || actorId == null || isBlank(command.getReviewerComment())) {
            throw new ServiceException("退回审核需要指定审核人并填写原因");
        }
        if (actorId.equals(task.getOwnerId())) {
            throw new ServiceException("审核人不能是任务负责人");
        }
        task.setWorkflowStatus(LabConstants.WORKFLOW_ACTIVE);
        task.setResultStatus(LabConstants.RESULT_DOING);
        task.setActualFinishTime(null);
        task.setResultDesc(null);
        task.setFailReason(null);
        task.setNextAction(null);
    }

    @Override
    public void managerReopen(LabTask task, Long managerId, String reason) {
        requireTask(task);
        requireMonthlyTask(task);
        requireWorkflow(task, LabConstants.WORKFLOW_CONFIRMED);
        if (managerId == null || isBlank(reason)) {
            throw new ServiceException("管理者重新打开任务需要填写原因");
        }
        task.setWorkflowStatus(LabConstants.WORKFLOW_ACTIVE);
        task.setResultStatus(LabConstants.RESULT_DOING);
        task.setActualFinishTime(null);
        task.setResultDesc(null);
        task.setFailReason(null);
        task.setNextAction(null);
    }

    private List<FieldValidationError> validatePlan(LabTask task) {
        List<FieldValidationError> errors = new ArrayList<FieldValidationError>();
        required(errors, "title", task.getTitle(), "任务标题不能为空");
        if (task.getOwnerId() == null) { error(errors, "ownerId", "负责人不能为空"); }
        required(errors, "period", task.getPeriod(), "周期不能为空");
        if (task.getPlanDate() == null) { error(errors, "planDate", "计划完成日期不能为空"); }
        required(errors, "deliverable", task.getDeliverable(), "交付物不能为空");
        required(errors, "taskLevel", task.getTaskLevel(), "任务层级不能为空");
        required(errors, "bizLine", task.getBizLine(), "业务线不能为空");
        required(errors, "taskType", task.getTaskType(), "任务类型不能为空");
        if (!isBlank(task.getTaskLevel()) && !isValidTaskLevel(task.getTaskLevel())) {
            error(errors, "taskLevel", "任务层级必须为 month 或 week");
        }
        if (!isBlank(task.getTaskType()) && !isValidTaskType(task.getTaskType())) {
            error(errors, "taskType", "任务类型必须为 key 或 daily");
        }
        validatePeriod(task, errors);
        validateWeight(errors, "perfWeight", task.getPerfWeight());
        validateWeight(errors, "goalWeight", task.getGoalWeight());
        if (isNonKeyMonth(task) && isNonZero(task.getPerfWeight())) {
            error(errors, "perfWeight", "仅月度重点任务可以设置权重");
        }
        if (isNonKeyMonth(task) && isNonZero(task.getGoalWeight())) {
            error(errors, "goalWeight", "仅月度重点任务可以设置权重");
        }
        validateCoordination(task, errors);
        return errors;
    }

    private void requireMonthlyTask(LabTask task) {
        if (!LabConstants.TASK_LEVEL_MONTH.equals(task.getTaskLevel())) {
            throw new ServiceException("周承诺请使用成员自主闭环操作，不能进入月度审核流程");
        }
    }

    private void validatePeriod(LabTask task, List<FieldValidationError> errors) {
        if (isBlank(task.getPeriod()) || isBlank(task.getTaskLevel())) {
            return;
        }
        try {
            LabPeriodUtils.PeriodRange range = LabPeriodUtils.parse(task.getPeriod());
            boolean month = LabConstants.TASK_LEVEL_MONTH.equals(task.getTaskLevel());
            boolean week = LabConstants.TASK_LEVEL_WEEK.equals(task.getTaskLevel());
            if ((!month && !week) || (month && task.getPeriod().contains("W")) || (week && !task.getPeriod().contains("W"))
                    || !task.getPeriod().equals(range.getPeriod())) {
                error(errors, "period", "周期与任务层级不匹配");
            }
        } catch (IllegalArgumentException exception) {
            error(errors, "period", "周期格式无效");
        }
    }

    private List<FieldValidationError> validateCompletion(TaskSubmitCommand command) {
        List<FieldValidationError> errors = new ArrayList<FieldValidationError>();
        required(errors, "resultDesc", command.getResultDesc(), "完成说明不能为空");
        if (command.getActualFinishTime() == null) { error(errors, "actualFinishTime", "实际完成时间不能为空"); }
        validateEvidenceBatch(command.getEvidenceList(), true, errors);
        return errors;
    }

    private void validateEvidenceBatch(List<LabTaskEvidence> evidenceList, boolean required, List<FieldValidationError> errors) {
        if (evidenceList == null || evidenceList.isEmpty()) {
            if (required) {
                error(errors, "evidenceList", "至少需要一条佐证材料");
            }
            return;
        }
        for (int index = 0; index < evidenceList.size(); index++) {
            LabTaskEvidence evidence = evidenceList.get(index);
            String fieldPrefix = "evidenceList[" + index + "]";
            if (evidence == null) {
                error(errors, fieldPrefix, "佐证材料不能为空");
                continue;
            }
            required(errors, fieldPrefix + ".evidenceType", evidence.getEvidenceType(), "佐证类型不能为空");
            required(errors, fieldPrefix + ".evidenceTitle", evidence.getEvidenceTitle(), "佐证名称不能为空");
            required(errors, fieldPrefix + ".evidenceUrl", evidence.getEvidenceUrl(), "佐证链接不能为空");
        }
    }

    private List<FieldValidationError> validateUndone(TaskSubmitCommand command) {
        List<FieldValidationError> errors = new ArrayList<FieldValidationError>();
        required(errors, "failReason", command.getFailReason(), "未完成原因不能为空");
        required(errors, "nextAction", command.getNextAction(), "下一步行动不能为空");
        return errors;
    }

    private List<FieldValidationError> validateReview(LabTask task, TaskSubmitCommand command) {
        List<FieldValidationError> errors = new ArrayList<FieldValidationError>();
        required(errors, "reviewerComment", command.getReviewerComment(), "审核意见不能为空");
        if (LabConstants.RESULT_UNDONE.equals(task.getResultStatus())) {
            required(errors, "failReason", task.getFailReason(), "未完成原因不能为空");
            required(errors, "nextAction", task.getNextAction(), "下一步行动不能为空");
        }
        if (isCompletion(task.getResultStatus())) {
            int errorsBeforeSelection = errors.size();
            validateEvidenceSelection(task, command, errors);
            List<LabTaskEvidence> selected = selectedPendingEvidence(task, command);
            if (errors.size() == errorsBeforeSelection && (hasPendingEvidence(task.getEvidenceList())
                    || !hasApprovedEvidence(task.getEvidenceList())) && selected.isEmpty()) {
                error(errors, "approvedEvidenceIds", "至少选择一条待审核证据");
            }
        }
        if (LabConstants.RESULT_EXCEEDED.equals(task.getResultStatus()) && !command.isExceededConfirmed()) {
            error(errors, "exceededConfirmed", "超额完成需要审核人明确确认");
        }
        return errors;
    }

    private String deriveCompletionStatus(LabTask task, String requested) {
        if (LabConstants.RESULT_EXCEEDED.equals(requested)) {
            return LabConstants.RESULT_EXCEEDED;
        }
        LocalDate finishDate = dateAtBusinessZone(task.getActualFinishTime());
        LocalDate planDate = dateAtBusinessZone(task.getPlanDate());
        return finishDate.isAfter(planDate) ? LabConstants.RESULT_DELAYED : LabConstants.RESULT_ONTIME;
    }

    private LocalDate dateAtBusinessZone(Date value) {
        return value.toInstant().atZone(clock.getZone()).toLocalDate();
    }

    private void appendEvidence(LabTask task, List<LabTaskEvidence> evidenceList, Long actorId) {
        List<LabTaskEvidence> evidence = new ArrayList<LabTaskEvidence>(task.getEvidenceList());
        if (evidenceList != null) {
            for (LabTaskEvidence item : evidenceList) {
                evidence.add(copyPendingEvidence(task, item, actorId));
            }
        }
        task.setEvidenceList(evidence);
    }

    private LabTaskEvidence copyPendingEvidence(LabTask task, LabTaskEvidence source, Long actorId) {
        LabTaskEvidence copy = new LabTaskEvidence();
        copy.setEvidenceType(source.getEvidenceType());
        copy.setEvidenceTitle(source.getEvidenceTitle());
        copy.setEvidenceUrl(source.getEvidenceUrl());
        copy.setEvidenceJson(source.getEvidenceJson());
        copy.setTaskId(task.getId());
        copy.setSubmitterId(actorId);
        copy.setSubmitTime(Date.from(clock.instant()));
        copy.setDelFlag(LabConstants.NO);
        copy.setAuditStatus(LabConstants.EVIDENCE_AUDIT_PENDING);
        return copy;
    }

    private void verifyEvidence(LabTask task, List<LabTaskEvidence> selected, TaskSubmitCommand command, Long actorId) {
        List<LabTaskEvidence> evidenceList = new ArrayList<LabTaskEvidence>(task.getEvidenceList());
        Set<Long> selectedIds = new HashSet<Long>();
        for (LabTaskEvidence evidence : selected) {
            selectedIds.add(evidence.getId());
        }
        for (LabTaskEvidence evidence : evidenceList) {
            if (selectedIds.contains(evidence.getId())) {
                evidence.setAuditStatus(LabConstants.EVIDENCE_AUDIT_APPROVED);
                evidence.setAuditorId(actorId);
                evidence.setAuditTime(Date.from(clock.instant()));
                evidence.setAuditComment(command.getEvidenceAuditComment());
            }
        }
        task.setEvidenceList(evidenceList);
    }

    private boolean hasApprovedEvidence(List<LabTaskEvidence> evidenceList) {
        if (evidenceList == null || evidenceList.isEmpty()) {
            return false;
        }
        for (LabTaskEvidence evidence : evidenceList) {
            if (isValidEvidence(evidence) && LabConstants.EVIDENCE_AUDIT_APPROVED.equals(evidence.getAuditStatus())) {
                return true;
            }
        }
        return false;
    }

    private boolean isValidEvidence(LabTaskEvidence evidence) {
        return evidence != null && !isBlank(evidence.getEvidenceType())
                && !isBlank(evidence.getEvidenceTitle()) && !isBlank(evidence.getEvidenceUrl());
    }

    private boolean hasPendingEvidence(List<LabTaskEvidence> evidenceList) {
        if (evidenceList == null) {
            return false;
        }
        for (LabTaskEvidence evidence : evidenceList) {
            if (isValidEvidence(evidence) && LabConstants.EVIDENCE_AUDIT_PENDING.equals(evidence.getAuditStatus())) {
                return true;
            }
        }
        return false;
    }

    private void validateSubmissionContext(LabTask task, Long actorId, List<FieldValidationError> errors) {
        if (actorId == null) {
            error(errors, "actorId", "提交人不能为空");
        }
        if (task.getId() == null) {
            error(errors, "taskId", "任务标识不能为空");
        }
    }

    private void validateEvidenceSelection(LabTask task, TaskSubmitCommand command, List<FieldValidationError> errors) {
        List<Long> ids = command.getApprovedEvidenceIds();
        Set<Long> seen = new HashSet<Long>();
        for (Long id : ids) {
            LabTaskEvidence evidence = findEvidence(task.getEvidenceList(), id);
            if (id == null || !seen.add(id) || evidence == null || !isValidEvidence(evidence)
                    || !LabConstants.EVIDENCE_AUDIT_PENDING.equals(evidence.getAuditStatus())) {
                error(errors, "approvedEvidenceIds", "所选证据必须存在、有效且待审核");
                return;
            }
        }
    }

    private List<LabTaskEvidence> selectedPendingEvidence(LabTask task, TaskSubmitCommand command) {
        List<LabTaskEvidence> selected = new ArrayList<LabTaskEvidence>();
        for (Long id : command.getApprovedEvidenceIds()) {
            LabTaskEvidence evidence = findEvidence(task.getEvidenceList(), id);
            if (evidence != null && isValidEvidence(evidence)
                    && LabConstants.EVIDENCE_AUDIT_PENDING.equals(evidence.getAuditStatus())) {
                selected.add(evidence);
            }
        }
        return selected;
    }

    private LabTaskEvidence findEvidence(List<LabTaskEvidence> evidenceList, Long id) {
        if (id == null) {
            return null;
        }
        for (LabTaskEvidence evidence : evidenceList) {
            if (evidence != null && id.equals(evidence.getId())) {
                return evidence;
            }
        }
        return null;
    }

    private void validateCoordination(LabTask task, List<FieldValidationError> errors) {
        if (!LabConstants.YES.equals(task.getCoordinationRequired())) {
            return;
        }
        if (task.getCoordinationOwnerId() == null) { error(errors, "coordinationOwnerId", "协同负责人不能为空"); }
        if (task.getCoordinationDeptId() == null) { error(errors, "coordinationDeptId", "协同部门不能为空"); }
        required(errors, "coordinationContent", task.getCoordinationContent(), "协同内容不能为空");
        required(errors, "coordinationSupport", task.getCoordinationSupport(), "所需支持不能为空");
    }

    private boolean isCompletion(String resultStatus) {
        return LabConstants.RESULT_EXCEEDED.equals(resultStatus)
                || LabConstants.RESULT_ONTIME.equals(resultStatus)
                || LabConstants.RESULT_DELAYED.equals(resultStatus);
    }

    private boolean isNonKeyMonth(LabTask task) {
        return !LabConstants.TASK_LEVEL_MONTH.equals(task.getTaskLevel())
                || !LabConstants.TASK_TYPE_KEY.equals(task.getTaskType());
    }

    private boolean isValidTaskLevel(String value) {
        return LabConstants.TASK_LEVEL_MONTH.equals(value) || LabConstants.TASK_LEVEL_WEEK.equals(value);
    }

    private boolean isValidTaskType(String value) {
        return LabConstants.TASK_TYPE_KEY.equals(value) || LabConstants.TASK_TYPE_DAILY.equals(value);
    }

    private boolean isNonZero(BigDecimal value) {
        return value != null && value.compareTo(ZERO) != 0;
    }

    private void validateWeight(List<FieldValidationError> errors, String field, BigDecimal value) {
        if (value != null && (value.compareTo(ZERO) < 0 || value.compareTo(ONE_HUNDRED) > 0)) {
            error(errors, field, "权重必须在 0 到 100 之间");
        }
    }

    private void requireTask(LabTask task) {
        if (task == null) {
            throw new ServiceException("任务不能为空");
        }
    }

    private void requireWorkflow(LabTask task, String expected) {
        if (!expected.equals(task.getWorkflowStatus())) {
            throw new ServiceException("当前任务状态不允许该操作");
        }
    }

    private List<FieldValidationError> single(String field, String message) {
        List<FieldValidationError> errors = new ArrayList<FieldValidationError>();
        error(errors, field, message);
        return errors;
    }

    private void required(List<FieldValidationError> errors, String field, String value, String message) {
        if (isBlank(value)) { error(errors, field, message); }
    }

    private void error(List<FieldValidationError> errors, String field, String message) {
        errors.add(new FieldValidationError(field, message));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
