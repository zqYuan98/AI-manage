package com.ailab.system.mapper;

import com.ailab.system.domain.LabPeriodCloseFact;
import com.ailab.system.domain.LabPeriodCloseSnapshot;
import com.ailab.system.domain.LabTaskExecutionEvent;
import com.ailab.system.domain.LabTaskWorkflowEvent;
import java.util.List;

/** 月结快照持久化接口。 */
public interface LabPeriodCloseSnapshotMapper {
    Integer selectMaxRevision(String period);
    Long selectLatestFormalRevisionId(String period);
    boolean taskMatchesLastEvent(Long taskId);
    boolean hasOpenBlock(Long taskId);
    List<LabTaskWorkflowEvent> selectWorkflowEvents(Long taskId);
    List<LabTaskExecutionEvent> selectExecutionEvents(Long taskId);
    int insertSnapshot(LabPeriodCloseSnapshot snapshot);
    int insertFact(LabPeriodCloseFact fact);
    LabPeriodCloseSnapshot selectSnapshot(Long id);
    List<LabPeriodCloseFact> selectFactsBySnapshot(Long closeSnapshotId);
}
