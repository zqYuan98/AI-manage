package com.ailab.system.service;

import com.ailab.system.domain.LabManagementDecision;
import java.util.List;

public interface LabManagementDecisionService {
    List<LabManagementDecision> list(String period, String status, Long actorId);
    LabManagementDecision create(LabManagementDecision decision, Long actorId);
    void complete(Long id, Integer version, Long actorId);
}
