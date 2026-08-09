package com.ailab.system.service;

import com.ailab.system.domain.LabGoal;
import com.ailab.system.domain.LabTask;
import com.ailab.system.dto.LabAccessContext;
import com.ailab.system.dto.LabReportAccessScope;

public interface LabAccessService {
    LabAccessContext context(Long userId);
    void scopeTaskQuery(LabTask query, Long userId);
    void requireTaskRead(LabTask task, Long userId);
    void requireTaskWrite(LabTask task, Long userId);
    void requireWeeklyWrite(LabTask task, Long userId);
    void requireMonthlyDefinitionWrite(LabTask task, Long userId);
    void requireTaskReview(LabTask task, Long userId);
    void requireEligibleReviewer(LabTask task);
    void requireGoalRead(Long userId);
    void requireGoalWrite(LabGoal goal, Long userId);
    void requireManager(Long userId);
    LabReportAccessScope reportScope(Long userId);
    void requireReportRead(String reportBizLine, boolean sensitive, boolean immutable, Long userId);
}
