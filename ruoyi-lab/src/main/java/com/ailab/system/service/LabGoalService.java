package com.ailab.system.service;

import com.ailab.system.domain.LabGoal;
import com.ailab.system.dto.ProgressComparison;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

public interface LabGoalService {
    List<LabGoal> listGoals(LabGoal query, Long actorId);
    List<LabGoal> goalTree(LabGoal query, Long actorId);
    LabGoal getGoal(Long id, Long actorId);
    int createGoal(LabGoal goal, Long actorId);
    int updateGoal(LabGoal goal, Long actorId);
    int deleteGoal(Long id, Integer version, Long actorId);
    void activateGoal(Long id, Integer version, Long actorId);
    void terminateGoal(Long id, Integer version, String reason, Long actorId);
    BigDecimal calculateMilestoneProgress(Long milestoneId, Long actorId);
    BigDecimal calculateAnnualProgress(Long annualGoalId, Long actorId);
    ProgressComparison compareMilestoneProgress(Long milestoneId, Date asOf, Long actorId);
    ProgressComparison compareAnnualProgress(Long annualGoalId, Date asOf, Long actorId);
}
