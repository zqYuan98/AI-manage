package com.ailab.system.mapper;

import com.ailab.system.dto.LabAccessContext;
import org.apache.ibatis.annotations.Param;

public interface LabAccessMapper {
    LabAccessContext selectAccessContext(Long userId);
    int countEligibleReviewers(@Param("ownerId") Long ownerId, @Param("bizLine") String bizLine);
}
