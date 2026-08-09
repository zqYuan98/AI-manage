package com.ailab.system.mapper;

import com.ailab.system.domain.LabTaskWorkflowEvent;
import java.util.List;

/** 月度任务审计事件持久化接口。 */
public interface LabTaskWorkflowEventMapper {
    int insertEvent(LabTaskWorkflowEvent event);
    List<LabTaskWorkflowEvent> selectEvents(Long taskId);
}
