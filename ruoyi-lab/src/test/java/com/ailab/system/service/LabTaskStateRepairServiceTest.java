package com.ailab.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailab.system.domain.LabTask;
import com.ailab.system.domain.LabTaskMigrationIssue;
import com.ailab.system.mapper.LabCommitmentMapper;
import com.ailab.system.service.impl.LabTaskStateRepairService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class LabTaskStateRepairServiceTest {
    @Test
    void mismatchRepairIsIdempotentAndCommitsOutsideTheRejectedClose() throws Exception {
        LabCommitmentMapper mapper = mock(LabCommitmentMapper.class);
        when(mapper.insertRepairIssue(any(LabTaskMigrationIssue.class))).thenReturn(0);
        LabTask task = new LabTask();
        task.setId(8L); task.setTaskLevel("week"); task.setExecutionStatus("ACTIVE");
        task.setExecutionVersion(3); task.setVersion(5);

        new LabTaskStateRepairService(mapper).queueCurrentEventMismatch(task, 9L);

        ArgumentCaptor<LabTaskMigrationIssue> issue = ArgumentCaptor.forClass(LabTaskMigrationIssue.class);
        verify(mapper).insertRepairIssue(issue.capture());
        assertEquals("CURRENT_EVENT_MISMATCH", issue.getValue().getIssueCode());
        assertEquals("OPEN", issue.getValue().getResolutionStatus());
        assertTrue(issue.getValue().getSourceStateJson().contains("\"executionVersion\":3"));
        Transactional boundary = LabTaskStateRepairService.class
                .getMethod("queueCurrentEventMismatch", LabTask.class, Long.class)
                .getAnnotation(Transactional.class);
        assertEquals(Propagation.REQUIRES_NEW, boundary.propagation());
    }
}
