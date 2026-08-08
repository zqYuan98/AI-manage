package com.ailab.system.service;

import com.ailab.system.domain.LabCollaborationRecord;
import com.ailab.system.domain.LabPerfScore;
import com.ailab.system.dto.CalibrationCommand;
import com.ailab.system.dto.CollaborationReviewCommand;
import com.ailab.system.dto.PerformanceCalculationResult;
import com.ailab.system.dto.RedLineRevokeCommand;
import java.util.List;

public interface LabPerformanceService {
    List<LabPerfScore> listMyScores(String period,Long actorUserId);
    List<LabPerfScore> listScores(String period,Long actorUserId);
    List<LabPerfScore> listScoreRevisions(Long memberId,String period,Long actorUserId);
    List<LabCollaborationRecord> listCollaboration(String period,Long actorUserId);
    PerformanceCalculationResult preview(Long memberId,String period,Long actorUserId);
    LabCollaborationRecord createCollaboration(LabCollaborationRecord record,Long actorUserId);
    void reviewCollaboration(Long id,CollaborationReviewCommand command,Long actorUserId);
    List<LabPerfScore> closePeriod(String period,String reason,Long actorUserId);
    void reopenPeriod(String period,String reason,Long actorUserId);
    void confirmMonthlyScore(Long id,Integer version,Long actorUserId);
    void revokeRedLine(Long id,RedLineRevokeCommand command,Long actorUserId);
    LabPerfScore calibrateQuarter(String quarter,Long memberId,CalibrationCommand command,Long actorUserId);
}
