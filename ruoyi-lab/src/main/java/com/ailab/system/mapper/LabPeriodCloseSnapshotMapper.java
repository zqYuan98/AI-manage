package com.ailab.system.mapper;

import com.ailab.system.domain.LabPeriodCloseFact;
import com.ailab.system.domain.LabPeriodCloseSnapshot;
import com.ailab.system.domain.LabTaskExecutionEvent;
import com.ailab.system.domain.LabTaskWorkflowEvent;
import com.ailab.system.domain.LabTaskBlockEvent;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 月结快照持久化接口。 */
public interface LabPeriodCloseSnapshotMapper {
    Integer selectMaxRevision(String period);
    Long selectLatestFormalRevisionId(String period);
    boolean taskMatchesLastEvent(Long taskId);
    List<LabTaskBlockEvent> selectOpenBlocksForTaskIdsForUpdate(@Param("taskIds") List<Long> taskIds);
    int closeOpenBlockForPeriod(@Param("id") Long id, @Param("resolverId") Long resolverId,
            @Param("endTime") Date endTime, @Param("updateBy") String updateBy);
    int clearTaskBlockForPeriod(@Param("taskId") Long taskId, @Param("updateBy") String updateBy);
    List<LabTaskWorkflowEvent> selectWorkflowEvents(Long taskId);
    List<LabTaskExecutionEvent> selectExecutionEvents(Long taskId);
    int insertSnapshot(LabPeriodCloseSnapshot snapshot);
    int insertFact(LabPeriodCloseFact fact);
    LabPeriodCloseSnapshot selectSnapshot(Long id);
    List<LabPeriodCloseFact> selectFactsBySnapshot(Long closeSnapshotId);
    LabPeriodCloseSnapshot selectLatestSnapshotForPeriod(String period);
    LabPeriodCloseFact selectFactByTypeAndBusinessId(@Param("closeSnapshotId") Long closeSnapshotId,
            @Param("factType") String factType, @Param("businessId") Long businessId);
}
