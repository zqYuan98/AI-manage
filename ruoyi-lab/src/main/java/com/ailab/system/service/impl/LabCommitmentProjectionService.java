package com.ailab.system.service.impl;

import com.ailab.system.constant.LabConstants;
import com.ailab.system.domain.LabFormalAcceptanceFact;
import com.ailab.system.domain.LabPeriodCloseFact;
import com.ailab.system.domain.LabPeriodCloseSnapshot;
import com.ailab.system.domain.LabTask;
import com.ailab.system.dto.CommitmentProgress;
import com.ailab.system.mapper.LabFormalAcceptanceMapper;
import com.ailab.system.mapper.LabPeriodCloseSnapshotMapper;
import com.ailab.system.mapper.LabTaskMapper;
import com.ailab.system.service.LabCommitmentCalculationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import java.util.Date;
import java.util.List;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/** Resolves the immutable formal fact before applying the named progress contract. */
@Service
public class LabCommitmentProjectionService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final LabTaskMapper taskMapper;
    private final LabFormalAcceptanceMapper formalMapper;
    private final LabPeriodCloseSnapshotMapper closeMapper;
    private final LabCommitmentCalculationService calculations;

    public LabCommitmentProjectionService(LabTaskMapper taskMapper, LabFormalAcceptanceMapper formalMapper,
            LabPeriodCloseSnapshotMapper closeMapper, LabCommitmentCalculationService calculations) {
        this.taskMapper = taskMapper;
        this.formalMapper = formalMapper;
        this.closeMapper = closeMapper;
        this.calculations = calculations;
    }

    public CommitmentProgress projectMonth(LabTask month, Date requestedAsOf) {
        validate(month, requestedAsOf);
        Date effectiveAsOf = requestedAsOf;
        String formalResult = null;
        boolean accepted = false;
        Long formalRevision = null;
        Long closeRevision = null;
        BigDecimal formalWeight = null;
        boolean closed = LabConstants.YES.equals(month.getPeriodLockFlag());
        if (closed) {
            LabPeriodCloseSnapshot snapshot = closeMapper.selectLatestSnapshotForPeriod(month.getPeriod());
            if (snapshot == null || snapshot.getId() == null || snapshot.getClosedTime() == null) {
                throw new ServiceException("Closed period is missing its immutable close snapshot");
            }
            LabPeriodCloseFact fact = closeMapper.selectFactByTypeAndBusinessId(
                    snapshot.getId(), "MONTH_RESULT", month.getId());
            if (fact == null) throw new ServiceException("Closed period is missing its pinned month result");
            JsonNode json = read(fact.getFactJson());
            accepted = LabConstants.WORKFLOW_CONFIRMED.equals(text(json, "workflowStatus", true));
            formalResult = text(json, "resultStatus", accepted);
            formalWeight = decimal(json, "goalWeight");
            formalRevision = snapshot.getFormalRevisionId();
            closeRevision = snapshot.getId();
            effectiveAsOf = snapshot.getClosedTime();
        } else {
            LabFormalAcceptanceFact fact = formalMapper.selectLatestFactForTask(month.getId());
            if (fact != null) {
                JsonNode json = read(fact.getFactJson());
                formalResult = text(json, "resultStatus", true);
                formalWeight = decimal(json, "goalWeight");
                accepted = true;
                formalRevision = fact.getFormalRevisionId();
            }
        }
        List<LabTask> commitments = taskMapper.selectCommitmentsForCalculation(month.getId(), effectiveAsOf);
        if (commitments == null) throw new ServiceException("Commitment fact query returned no collection");
        CommitmentProgress result = calculations.calculateMonth(month, commitments, effectiveAsOf, formalResult, accepted,
                formalRevision, closeRevision, closed);
        if (formalWeight != null) result.setFormalWeight(formalWeight);
        return result;
    }

    private void validate(LabTask month, Date asOf) {
        if (month == null || month.getId() == null || !LabConstants.TASK_LEVEL_MONTH.equals(month.getTaskLevel())
                || month.getPeriod() == null || asOf == null) {
            throw new ServiceException("Month projection is missing required facts");
        }
    }

    private JsonNode read(String value) {
        try {
            JsonNode node = JSON.readTree(value);
            if (node == null || !node.isObject()) throw new ServiceException("Immutable progress fact must be an object");
            return node;
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServiceException("Immutable progress fact is invalid JSON");
        }
    }

    private String text(JsonNode node, String field, boolean required) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().trim().isEmpty()) {
            if (required) throw new ServiceException("Immutable progress fact is missing " + field);
            return null;
        }
        return value.asText();
    }

    private BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) throw new ServiceException("Immutable progress fact is missing " + field);
        BigDecimal result = value.decimalValue();
        if (result.signum() < 0 || result.compareTo(new BigDecimal("100")) > 0) {
            throw new ServiceException("Immutable progress fact has invalid " + field);
        }
        return result;
    }
}
