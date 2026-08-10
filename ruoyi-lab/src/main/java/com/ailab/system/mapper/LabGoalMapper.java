package com.ailab.system.mapper;

import com.ailab.system.domain.LabGoal;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface LabGoalMapper {
    List<LabGoal> selectGoalList(LabGoal query);
    LabGoal selectGoalById(Long id);
    LabGoal selectGoalForUpdate(Long id);
    List<LabGoal> selectChildrenByParentId(Long parentId);
    List<LabGoal> selectChildrenByParentIdForUpdate(Long parentId);
    int insertGoal(LabGoal goal);
    int updateGoal(LabGoal goal);
    int deleteGoal(@Param("id") Long id, @Param("version") Integer version, @Param("updateBy") String updateBy);
    int terminateGoal(@Param("id") Long id, @Param("version") Integer version,
            @Param("expectedStatus") String expectedStatus, @Param("reason") String reason,
            @Param("terminatedBy") Long terminatedBy, @Param("updateBy") String updateBy);
}
