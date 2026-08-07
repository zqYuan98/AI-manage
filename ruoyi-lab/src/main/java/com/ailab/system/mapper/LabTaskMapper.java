package com.ailab.system.mapper;

import com.ailab.system.domain.LabTask;
import com.ailab.system.domain.LabTaskBlockEvent;
import com.ailab.system.domain.LabTaskQualityGate;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface LabTaskMapper {
    Long selectMemberIdByUserId(Long userId);
    String selectMemberBizLineById(Long memberId);
    List<LabTask> selectTaskList(LabTask query);
    LabTask selectTaskById(Long id);
    LabTask selectTaskForUpdate(Long id);
    List<LabTask> selectTasksByParentId(Long parentId);
    List<LabTask> selectTasksByParentIdForUpdate(Long parentId);
    List<LabTask> selectKeyMonthTasksByMilestoneId(Long milestoneId);
    List<LabTask> selectKeyMonthTasksByMilestoneIdForUpdate(Long milestoneId);
    List<LabTask> selectTasksByMilestoneIdForUpdate(Long milestoneId);
    int countTasksByMilestoneId(Long milestoneId);
    List<LabTask> selectKeyMonthTasksByOwnerPeriod(@Param("ownerId") Long ownerId, @Param("period") String period);
    List<LabTask> selectKeyMonthTasksByOwnerPeriodForUpdate(@Param("ownerId") Long ownerId, @Param("period") String period);
    String lockMemberForUpdate(Long memberId);
    int insertTask(LabTask task);
    int updateTask(LabTask task);
    int deleteTask(@Param("id") Long id, @Param("version") Integer version, @Param("updateBy") String updateBy);

    List<LabTaskQualityGate> selectQualityGates(Long taskId);
    LabTaskQualityGate selectQualityGateById(Long id);
    int insertQualityGate(LabTaskQualityGate gate);
    int updateQualityGate(LabTaskQualityGate gate);
    int deleteQualityGate(@Param("id") Long id, @Param("updateBy") String updateBy);
    int markQualityGatePassed(@Param("id") Long id, @Param("evidenceId") Long evidenceId,
            @Param("checkerId") Long checkerId,
            @Param("checkTime") Date checkTime, @Param("checkResult") String checkResult,
            @Param("updateBy") String updateBy);

    LabTaskBlockEvent selectOpenBlockEvent(Long taskId);
    List<LabTaskBlockEvent> selectBlockEvents(Long taskId);
    Integer selectNextBlockEpisodeNo(Long taskId);
    int insertBlockEvent(LabTaskBlockEvent event);
    int closeBlockEvent(@Param("id") Long id, @Param("resolverId") Long resolverId,
            @Param("endTime") Date endTime, @Param("resolution") String resolution,
            @Param("updateBy") String updateBy);
}
