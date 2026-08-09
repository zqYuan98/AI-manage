package com.ailab.system.service.impl;

import com.ailab.system.constant.LabConstants;
import com.ailab.system.domain.LabFormalAcceptanceFact;
import com.ailab.system.domain.LabFormalAcceptanceRevision;
import com.ailab.system.domain.LabTask;
import com.ailab.system.mapper.LabFormalAcceptanceMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import java.time.Clock;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 固化月度任务的正式验收修订。 */
@Service
public class LabFormalAcceptanceService {
    private static final String CALCULATION_VERSION = "FORMAL_ACCEPTANCE_V1";
    private static final ObjectMapper JSON = new ObjectMapper();
    private final LabFormalAcceptanceMapper mapper;
    private final Clock clock;

    public LabFormalAcceptanceService(LabFormalAcceptanceMapper mapper) {
        this(mapper, Clock.systemDefaultZone());
    }

    public LabFormalAcceptanceService(LabFormalAcceptanceMapper mapper, Clock clock) {
        if (mapper == null || clock == null) {
            throw new IllegalArgumentException("正式验收依赖不能为空");
        }
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public LabFormalAcceptanceRevision accept(LabTask task, Long reviewerId, String comment, int evidenceVersion) {
        if (task == null || task.getId() == null || isBlank(task.getPeriod()) || isBlank(task.getBizLine())
                || reviewerId == null || isBlank(comment) || evidenceVersion < 0) {
            throw new ServiceException("正式验收缺少必要字段");
        }
        if (!LabConstants.TASK_LEVEL_MONTH.equals(task.getTaskLevel())
                || !LabConstants.WORKFLOW_CONFIRMED.equals(task.getWorkflowStatus())) {
            throw new ServiceException("只有已确认的月度结果可以固化正式验收");
        }
        mapper.ensurePeriodLock(task.getPeriod(), String.valueOf(reviewerId));
        if (mapper.lockPeriod(task.getPeriod()) == null) {
            throw new ServiceException("正式验收周期锁创建失败");
        }
        LabFormalAcceptanceRevision revision = new LabFormalAcceptanceRevision();
        revision.setPeriod(task.getPeriod());
        revision.setBizLine(task.getBizLine());
        Integer maximum = mapper.selectMaxRevision(task.getPeriod(), task.getBizLine());
        revision.setRevisionNo(maximum == null ? 1 : maximum + 1);
        revision.setAcceptedBy(reviewerId);
        Date acceptedAt = Date.from(clock.instant());
        revision.setAcceptedTime(acceptedAt);
        revision.setCalculationVersion(CALCULATION_VERSION);
        revision.setDelFlag(LabConstants.NO);
        revision.setCreateBy(String.valueOf(reviewerId));
        if (mapper.insertRevision(revision) != 1 || revision.getId() == null) {
            throw new ServiceException("正式验收修订写入失败");
        }
        LabFormalAcceptanceFact fact = new LabFormalAcceptanceFact();
        fact.setFormalRevisionId(revision.getId());
        fact.setTaskId(task.getId());
        fact.setFactJson(snapshot(task, reviewerId, acceptedAt, comment));
        fact.setEvidenceVersion(evidenceVersion);
        fact.setReviewerId(reviewerId);
        fact.setReviewTime(acceptedAt);
        fact.setDelFlag(LabConstants.NO);
        fact.setCreateBy(String.valueOf(reviewerId));
        if (mapper.insertFact(fact) != 1) {
            throw new ServiceException("正式验收事实写入失败");
        }
        return revision;
    }

    public List<LabFormalAcceptanceFact> readFacts(Long revisionId) {
        if (revisionId == null) { throw new ServiceException("正式验收修订不能为空"); }
        return mapper.selectFactsByRevision(revisionId);
    }

    private String snapshot(LabTask task, Long reviewerId, Date acceptedAt, String comment) {
        java.util.LinkedHashMap<String, Object> value = new java.util.LinkedHashMap<String, Object>();
        value.put("taskId", task.getId());
        value.put("period", task.getPeriod());
        value.put("bizLine", task.getBizLine());
        value.put("title", task.getTitle());
        value.put("deliverable", task.getDeliverable());
        value.put("resultStatus", task.getResultStatus());
        value.put("resultDesc", task.getResultDesc());
        value.put("actualFinishTime", task.getActualFinishTime());
        value.put("perfWeight", task.getPerfWeight());
        value.put("goalWeight", task.getGoalWeight());
        value.put("taskVersion", task.getVersion());
        value.put("evidence", task.getEvidenceList());
        value.put("reviewerId", reviewerId);
        value.put("reviewTime", acceptedAt);
        value.put("reviewerComment", comment);
        try { return JSON.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new ServiceException("正式验收事实序列化失败"); }
    }

    private boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }
}
