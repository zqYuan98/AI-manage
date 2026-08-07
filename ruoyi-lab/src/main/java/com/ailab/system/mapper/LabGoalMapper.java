package com.ailab.system.mapper;

import com.ailab.system.domain.LabGoal;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface LabGoalMapper {
    List<LabGoal> selectGoalList(LabGoal query);
    LabGoal selectGoalById(Long id);
    List<LabGoal> selectChildrenByParentId(Long parentId);
    int insertGoal(LabGoal goal);
    int updateGoal(LabGoal goal);
    int deleteGoal(@Param("id") Long id, @Param("version") Integer version, @Param("updateBy") String updateBy);
}
