package com.ailab.system.mapper;

import com.ailab.system.domain.LabManagementDecision;
import com.ailab.system.dto.LabAccessContext;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface LabManagementDecisionMapper {
    List<LabManagementDecision> selectDecisionList(@Param("scope") LabAccessContext scope,
            @Param("period") String period, @Param("status") String status);
    LabManagementDecision selectDecisionForUpdate(Long id);
    int insertDecision(LabManagementDecision decision);
    int completeDecision(@Param("id") Long id, @Param("version") Integer version, @Param("actor") String actor);
    String selectActiveMemberBizLine(Long memberId);
    String selectTaskBizLine(Long taskId);
    String selectGoalOwnerBizLine(Long goalId);
}
