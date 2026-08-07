package com.ailab.system.service;

import com.ailab.system.domain.LabGoal;
import java.math.BigDecimal;
import java.util.List;

public interface LabGoalService {
    List<LabGoal> listGoals(LabGoal query, Long actorId);
    List<LabGoal> goalTree(LabGoal query, Long actorId);
    LabGoal getGoal(Long id, Long actorId);
    int createGoal(LabGoal goal, Long actorId);
    int updateGoal(LabGoal goal, Long actorId);
    int deleteGoal(Long id, Integer version, Long actorId);
    void activateGoal(Long id, Integer version, Long actorId);
    BigDecimal calculateMilestoneProgress(Long milestoneId, Long actorId);
    BigDecimal calculateAnnualProgress(Long annualGoalId, Long actorId);
}
