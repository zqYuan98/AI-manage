package com.ailab.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailab.system.domain.LabTask;
import com.ailab.system.domain.LabTaskWorkflowEvent;
import com.ailab.system.mapper.LabTaskWorkflowEventMapper;
import com.ailab.system.service.impl.LabTaskWorkflowEventService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LabTaskWorkflowEventTest {
    @Test
    void appendUsesTaskVersionAsIdempotentAuditFence() {
        LabTaskWorkflowEventMapper mapper=mock(LabTaskWorkflowEventMapper.class);when(mapper.insertEvent(org.mockito.ArgumentMatchers.any())).thenReturn(1);
        LabTaskWorkflowEventService service=new LabTaskWorkflowEventService(mapper,
                Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"),ZoneOffset.UTC));
        LabTask task=new LabTask();task.setId(9L);task.setVersion(4);task.setWorkflowStatus("PENDING_REVIEW");task.setResultStatus("ONTIME");

        service.append(task,"ACTIVE","PENDING_REVIEW",7L,"SUBMIT","提交月度结果");

        ArgumentCaptor<LabTaskWorkflowEvent> event=ArgumentCaptor.forClass(LabTaskWorkflowEvent.class);verify(mapper).insertEvent(event.capture());
        assertEquals("9:SUBMIT:4",event.getValue().getIdempotencyKey());assertEquals(4,event.getValue().getTaskVersion());
    }
}
