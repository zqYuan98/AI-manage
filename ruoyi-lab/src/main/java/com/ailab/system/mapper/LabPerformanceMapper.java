package com.ailab.system.mapper;

import com.ailab.system.domain.LabCollaborationRecord;
import com.ailab.system.domain.LabMember;
import com.ailab.system.domain.LabPerfScore;
import com.ailab.system.domain.LabPeriodClose;
import com.ailab.system.domain.LabTask;
import com.ailab.system.domain.LabTaskEvidence;
import com.ailab.system.domain.LabTaskQualityGate;
import com.ailab.system.dto.PerformanceAssetFact;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface LabPerformanceMapper {
    LabPeriodClose selectPeriodForUpdate(String period);
    int ensureOpenPeriod(@Param("period") String period,@Param("actor") String actor);
    List<LabTask> selectPeriodTasks(String period);
    List<LabTask> selectPeriodTasksForUpdate(String period);
    List<LabMember> selectActiveMembersForUpdate();
    List<LabTaskEvidence> selectEvidenceForTaskIds(@Param("taskIds") List<Long> taskIds);
    List<LabTaskQualityGate> selectQualityGatesForTaskIds(@Param("taskIds") List<Long> taskIds);
    List<LabCollaborationRecord> selectCollaborationForPeriod(String period);
    List<LabCollaborationRecord> selectCollaborationsForPeriodForUpdate(String period);
    List<PerformanceAssetFact> selectCriticalAssetFacts(@Param("quarterStart") String quarterStart,@Param("quarterEnd") String quarterEnd);
    List<PerformanceAssetFact> selectCriticalAssetFactsForUpdate(@Param("quarterStart") String quarterStart,@Param("quarterEnd") String quarterEnd);
    int insertOverdueRecord(LabCollaborationRecord record);
    Integer selectMaxRevision(@Param("memberId") Long memberId,@Param("period") String period);
    int markCurrentScoresHistorical(@Param("period") String period,@Param("memberId") Long memberId,@Param("actor") String actor);
    int markPeriodScoresHistorical(@Param("period") String period,@Param("actor") String actor);
    int insertPerfScore(LabPerfScore score);
    int lockTasksForPeriod(@Param("period") String period,@Param("lockFlag") String lockFlag);
    int closePeriod(@Param("id") Long id,@Param("version") Integer version,@Param("actor") String actor,@Param("time") Date time,@Param("reason") String reason);
    int reopenPeriod(@Param("id") Long id,@Param("version") Integer version,@Param("actor") String actor,@Param("time") Date time,@Param("reason") String reason);
    List<LabPerfScore> selectCurrentScores(String period);
    List<LabPerfScore> selectCurrentScoresForUpdate(String period);
    List<LabPerfScore> selectScoresForMember(@Param("memberId") Long memberId,@Param("period") String period);
    List<LabPerfScore> selectScoreRevisions(@Param("memberId") Long memberId,@Param("period") String period);
    List<LabCollaborationRecord> selectCollaborationList(@Param("period") String period,@Param("memberId") Long memberId,@Param("bizLine") String bizLine,@Param("roleKey") String roleKey);
    LabCollaborationRecord selectCollaborationById(Long id);
    LabCollaborationRecord selectCollaborationForUpdate(Long id);
    int insertCollaboration(LabCollaborationRecord record);
    int reviewCollaboration(@Param("id") Long id,@Param("score") BigDecimal score,@Param("reviewerId") Long reviewerId,@Param("reviewTime") Date reviewTime,@Param("comment") String comment,@Param("actor") String actor);
    LabPerfScore selectScoreForUpdate(Long id);
    int revokeRedLine(@Param("id") Long id,@Param("version") Integer version,@Param("evidenceUrl") String evidenceUrl,@Param("reason") String reason,@Param("managerId") Long managerId,@Param("time") Date time,@Param("actor") String actor);
    List<LabPerfScore> selectCurrentMonthlyScoresForUpdate(@Param("memberId") Long memberId,@Param("startPeriod") String startPeriod,@Param("endPeriod") String endPeriod);
    int confirmScore(@Param("id") Long id,@Param("version") Integer version,@Param("memberId") Long memberId,@Param("time") Date time,@Param("actor") String actor);
}
