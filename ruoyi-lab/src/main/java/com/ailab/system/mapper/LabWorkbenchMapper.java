package com.ailab.system.mapper;

import com.ailab.system.domain.LabManagementDecision;
import com.ailab.system.dto.DashboardActionItem;
import com.ailab.system.dto.LabAccessContext;
import com.ailab.system.dto.MemberLoad;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** Narrow, trusted-scope workbench projections. */
public interface LabWorkbenchMapper {
    List<LabManagementDecision> selectPendingDecisions(@Param("scope") LabAccessContext scope, @Param("period") String period);
    List<DashboardActionItem> selectNewBlocks(@Param("scope") LabAccessContext scope, @Param("since") Date since);
    List<DashboardActionItem> selectForecastDelays(@Param("scope") LabAccessContext scope, @Param("asOf") Date asOf);
    List<DashboardActionItem> selectPendingAcceptance(@Param("scope") LabAccessContext scope);
    List<DashboardActionItem> selectStaleKeyResults(@Param("scope") LabAccessContext scope, @Param("staleBefore") Date staleBefore);
    List<MemberLoad> selectTeamCommitmentCounts(@Param("scope") LabAccessContext scope, @Param("period") String period, @Param("asOf") Date asOf);
    List<DashboardActionItem> selectOwnMonthlyResults(@Param("scope") LabAccessContext scope, @Param("period") String period);
    List<DashboardActionItem> selectOwnWeeklyCommitments(@Param("scope") LabAccessContext scope, @Param("period") String period);
    List<DashboardActionItem> selectOwnDueItems(@Param("scope") LabAccessContext scope, @Param("asOf") Date asOf);
    List<DashboardActionItem> selectOwnBlocks(@Param("scope") LabAccessContext scope);
    List<DashboardActionItem> selectOwnMissingEvidence(@Param("scope") LabAccessContext scope, @Param("period") String period);
}
