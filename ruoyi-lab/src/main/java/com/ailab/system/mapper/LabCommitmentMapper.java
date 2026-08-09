package com.ailab.system.mapper;

import com.ailab.system.domain.LabTask;
import com.ailab.system.domain.LabTaskExecutionEvent;
import com.ailab.system.domain.LabTaskMigrationIssue;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** Persistence boundary for weekly execution facts and migration quarantine. */
public interface LabCommitmentMapper {
    List<LabTask> selectUnmigratedWeeklyTasks();
    List<LabTaskExecutionEvent> selectExecutionEvents(Long taskId);
    List<LabTaskMigrationIssue> selectOpenMigrationIssues();
    int countOpenMigrationIssues();
    int insertExecutionEvent(LabTaskExecutionEvent event);
    int insertMigrationIssue(LabTaskMigrationIssue issue);
    int updateExecutionStatus(@Param("taskId") Long taskId, @Param("expectedExecutionVersion") Integer expectedExecutionVersion,
            @Param("executionStatus") String executionStatus, @Param("updateBy") String updateBy);
    String selectCutoverValue(@Param("configKey") String configKey);
    int updateCutoverValue(@Param("configKey") String configKey, @Param("expectedValue") String expectedValue,
            @Param("newValue") String newValue);
}
