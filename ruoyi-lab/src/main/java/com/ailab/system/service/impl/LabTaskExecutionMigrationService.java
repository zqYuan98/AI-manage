package com.ailab.system.service.impl;

import com.ailab.system.constant.LabConstants;
import com.ailab.system.config.LabProperties;
import com.ailab.system.domain.LabTask;
import com.ailab.system.mapper.LabCommitmentMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Pure legacy-state classifier used by the idempotent database migration. */
@Service
public class LabTaskExecutionMigrationService {
    private static final String POINT_OF_NO_RETURN_KEY = "lab.commitment.pointOfNoReturn";

    private final LabCommitmentMapper mapper;
    private final LabProperties properties;

    public LabTaskExecutionMigrationService() {
        this(null, null);
    }

    @Autowired
    public LabTaskExecutionMigrationService(LabCommitmentMapper mapper, LabProperties properties) {
        this.mapper = mapper;
        this.properties = properties;
    }

    public MigrationDecision classify(LabTask task, boolean hasOpenBlock) {
        if (task == null || !LabConstants.TASK_LEVEL_WEEK.equals(task.getTaskLevel())) {
            return MigrationDecision.quarantine(LabConstants.MIGRATION_AMBIGUOUS_LEGACY_COMBINATION, null);
        }
        boolean terminalCandidate = isCompleted(task.getResultStatus()) || LabConstants.RESULT_UNDONE.equals(task.getResultStatus());
        if (hasOpenBlock && terminalCandidate) {
            return MigrationDecision.quarantine(LabConstants.MIGRATION_TERMINAL_WITH_OPEN_BLOCK, task.getPeriodLockFlag());
        }
        if (isConfirmedOrPending(task.getWorkflowStatus()) && isCompleted(task.getResultStatus())
                && task.getActualFinishTime() != null) {
            return MigrationDecision.mapped(LabConstants.EXECUTION_SELF_DONE, task.getPeriodLockFlag());
        }
        if (isConfirmedOrPending(task.getWorkflowStatus()) && LabConstants.RESULT_UNDONE.equals(task.getResultStatus())) {
            return MigrationDecision.mapped(LabConstants.EXECUTION_SELF_UNDONE, task.getPeriodLockFlag());
        }
        if (LabConstants.WORKFLOW_DRAFT.equals(task.getWorkflowStatus())
                && LabConstants.RESULT_DOING.equals(task.getResultStatus()) && task.getActualFinishTime() == null) {
            return MigrationDecision.mapped(LabConstants.EXECUTION_PLANNED, task.getPeriodLockFlag());
        }
        if (LabConstants.WORKFLOW_ACTIVE.equals(task.getWorkflowStatus())
                && LabConstants.RESULT_DOING.equals(task.getResultStatus()) && task.getActualFinishTime() == null) {
            return MigrationDecision.mapped(LabConstants.EXECUTION_ACTIVE, task.getPeriodLockFlag());
        }
        return MigrationDecision.quarantine(LabConstants.MIGRATION_AMBIGUOUS_LEGACY_COMBINATION, task.getPeriodLockFlag());
    }

    private boolean isConfirmedOrPending(String workflowStatus) {
        return LabConstants.WORKFLOW_CONFIRMED.equals(workflowStatus)
                || LabConstants.WORKFLOW_PENDING_REVIEW.equals(workflowStatus);
    }

    private boolean isCompleted(String resultStatus) {
        return LabConstants.RESULT_EXCEEDED.equals(resultStatus)
                || LabConstants.RESULT_ONTIME.equals(resultStatus)
                || LabConstants.RESULT_DELAYED.equals(resultStatus);
    }

    public void validateCutover(boolean readNewModel, boolean writeSelfClose, int openIssues,
            boolean pointOfNoReturn) {
        if ((readNewModel || writeSelfClose) && openIssues > 0) {
            throw new IllegalStateException("存在未处理的历史周承诺隔离项，不能切换事实模型");
        }
        if (writeSelfClose && !readNewModel) {
            throw new IllegalStateException("启用成员自主闭环前必须先启用新事实读取");
        }
        if (pointOfNoReturn && !readNewModel) {
            throw new IllegalStateException("已写入新执行事实，禁止回退旧读取口径");
        }
    }

    public void validateConfiguredCutover() {
        requireRuntimeDependencies();
        boolean pointOfNoReturn = Boolean.parseBoolean(mapper.selectCutoverValue(POINT_OF_NO_RETURN_KEY));
        validateCutover(properties.isReadNewModel(), properties.isWriteSelfClose(),
                mapper.countOpenMigrationIssues(), pointOfNoReturn);
    }

    public boolean advancesPointOfNoReturn(boolean writeSelfClose, String eventType) {
        return writeSelfClose && eventType != null
                && !LabConstants.EXECUTION_EVENT_MIGRATED_BASELINE.equals(eventType);
    }

    public void recordPointOfNoReturn(String eventType) {
        requireRuntimeDependencies();
        if (advancesPointOfNoReturn(properties.isWriteSelfClose(), eventType)) {
            mapper.updateCutoverValue(POINT_OF_NO_RETURN_KEY, "false", "true");
        }
    }

    private void requireRuntimeDependencies() {
        if (mapper == null || properties == null) {
            throw new IllegalStateException("迁移运行依赖未配置");
        }
    }

    public static final class MigrationDecision {
        private final String executionStatus;
        private final String issueCode;
        private final String eventType;
        private final String periodLockFlag;

        private MigrationDecision(String executionStatus, String issueCode, String eventType, String periodLockFlag) {
            this.executionStatus = executionStatus;
            this.issueCode = issueCode;
            this.eventType = eventType;
            this.periodLockFlag = periodLockFlag;
        }

        static MigrationDecision mapped(String executionStatus, String periodLockFlag) {
            return new MigrationDecision(executionStatus, null, LabConstants.EXECUTION_EVENT_MIGRATED_BASELINE, periodLockFlag);
        }

        static MigrationDecision quarantine(String issueCode, String periodLockFlag) {
            return new MigrationDecision(null, issueCode, null, periodLockFlag);
        }

        public boolean isQuarantined() { return issueCode != null; }
        public String getExecutionStatus() { return executionStatus; }
        public String getIssueCode() { return issueCode; }
        public String getEventType() { return eventType; }
        public String getPeriodLockFlag() { return periodLockFlag; }
    }
}
