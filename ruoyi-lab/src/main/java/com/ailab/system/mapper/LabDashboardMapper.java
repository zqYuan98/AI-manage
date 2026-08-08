package com.ailab.system.mapper;

import com.ailab.system.domain.LabReminder;
import com.ailab.system.dto.DashboardActionItem;
import com.ailab.system.dto.DashboardCountItem;
import com.ailab.system.dto.DashboardKpiFact;
import com.ailab.system.dto.GoalHealthFact;
import com.ailab.system.dto.GoalTrendPoint;
import com.ailab.system.dto.LabAccessContext;
import com.ailab.system.dto.MemberLoad;
import com.ailab.system.dto.ReminderCandidate;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface LabDashboardMapper {
    List<ReminderCandidate> selectOpenBlockReminderCandidates();
    List<ReminderCandidate> selectPendingTaskReminderCandidates(@Param("period") String period,
            @Param("managerEscalation") boolean managerEscalation);
    int insertReminderIfAbsent(LabReminder reminder);
    List<LabReminder> selectReminderList(@Param("scope") LabAccessContext scope,
            @Param("unreadOnly") Boolean unreadOnly);
    int markReminderRead(@Param("id") Long id, @Param("recipientId") Long recipientId,
            @Param("version") Integer version, @Param("readTime") Date readTime, @Param("actor") String actor);
    int markAllRemindersRead(@Param("recipientId") Long recipientId, @Param("readTime") Date readTime,
            @Param("actor") String actor);

    List<GoalHealthFact> selectGoalHealthFacts(@Param("year") Integer year, @Param("asOf") Date asOf,
            @Param("scope") LabAccessContext scope);
    List<GoalTrendPoint> selectGoalProgressTrend(@Param("year") Integer year, @Param("asOf") Date asOf,
            @Param("scope") LabAccessContext scope);
    DashboardKpiFact selectKpiFact(@Param("period") String period, @Param("asOf") Date asOf,
            @Param("scope") LabAccessContext scope);
    List<DashboardCountItem> selectTaskStatusDistribution(@Param("period") String period,
            @Param("scope") LabAccessContext scope);
    List<MemberLoad> selectMemberLoads(@Param("period") String period, @Param("twoWeekStart") Date twoWeekStart,
            @Param("asOf") Date asOf, @Param("scope") LabAccessContext scope);
    List<DashboardActionItem> selectCoordinationItems(@Param("period") String period,
            @Param("scope") LabAccessContext scope);
    List<DashboardActionItem> selectRecentIpr(@Param("asOf") Date asOf, @Param("scope") LabAccessContext scope);
    List<DashboardActionItem> selectRecentReports(@Param("period") String period,
            @Param("scope") LabAccessContext scope);
    DashboardActionItem selectLatestReport(@Param("period") String period, @Param("scope") LabAccessContext scope);
    List<DashboardCountItem> selectPerformanceSummary(@Param("period") String period,
            @Param("scope") LabAccessContext scope);
    List<Long> selectActiveManagerUserIds();
}
