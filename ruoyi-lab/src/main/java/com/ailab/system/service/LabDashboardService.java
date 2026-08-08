package com.ailab.system.service;

import com.ailab.system.dto.DashboardOverview;
import com.ailab.system.dto.GoalHealth;
import com.ailab.system.dto.GoalHealthFact;

public interface LabDashboardService {
    DashboardOverview getOverview(String period, Long actorUserId);
    GoalHealth calculateHealth(GoalHealthFact fact);
}
