package com.ailab.system.service.impl;

import com.ailab.system.constant.LabConstants;
import com.ailab.system.domain.LabTask;
import com.ailab.system.domain.LabTaskWorkflowEvent;
import com.ailab.system.mapper.LabTaskWorkflowEventMapper;
import com.ruoyi.common.exception.ServiceException;
import java.time.Clock;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 只追加月度任务工作流事件。 */
@Service
public class LabTaskWorkflowEventService {
    private final LabTaskWorkflowEventMapper mapper;
    private final Clock clock;

    @Autowired
    public LabTaskWorkflowEventService(LabTaskWorkflowEventMapper mapper) {
        this(mapper, Clock.systemDefaultZone());
    }

    public LabTaskWorkflowEventService(LabTaskWorkflowEventMapper mapper, Clock clock) {
        if (mapper == null || clock == null) {
            throw new IllegalArgumentException("工作流事件依赖不能为空");
        }
        this.mapper = mapper;
        this.clock = clock;
    }

    public LabTaskWorkflowEvent append(LabTask task, String fromStatus, String toStatus,
            Long actorId, String eventType, String reason) {
        if (task == null || task.getId() == null || task.getVersion() == null
                || actorId == null || isBlank(eventType) || isBlank(toStatus)) {
            throw new ServiceException("工作流事件缺少必要字段");
        }
        LabTaskWorkflowEvent event = new LabTaskWorkflowEvent();
        event.setTaskId(task.getId());
        event.setFromStatus(fromStatus);
        event.setToStatus(toStatus);
        event.setResultStatus(task.getResultStatus());
        event.setActorId(actorId);
        event.setEventType(eventType);
        event.setReason(reason);
        event.setTaskVersion(task.getVersion());
        event.setEventTime(Date.from(clock.instant()));
        event.setIdempotencyKey(task.getId() + ":" + eventType + ":" + task.getVersion());
        event.setDelFlag(LabConstants.NO);
        event.setCreateBy(String.valueOf(actorId));
        if (mapper.insertEvent(event) != 1) {
            throw new ServiceException("工作流审计事件写入失败");
        }
        return event;
    }

    private boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }
}
