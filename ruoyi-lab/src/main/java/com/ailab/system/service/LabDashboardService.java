package com.ailab.system.service;

import com.ailab.system.dto.DashboardOverview;
import com.ailab.system.dto.GoalHealth;
import com.ailab.system.dto.GoalHealthFact;
import com.ailab.system.dto.CommitmentProgress;
import com.ailab.system.dto.ProgressComparison;

public interface LabDashboardService {
    DashboardOverview getOverview(String period, Long actorUserId);
    GoalHealth calculateHealth(GoalHealthFact fact);
    ProgressComparison compareProgress(GoalHealthFact legacyFact, CommitmentProgress namedProgress);
}
