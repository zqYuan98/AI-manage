package com.ailab.system.mapper;

import com.ailab.system.dto.LabAccessContext;

public interface LabAccessMapper {
    LabAccessContext selectAccessContext(Long userId);
}
