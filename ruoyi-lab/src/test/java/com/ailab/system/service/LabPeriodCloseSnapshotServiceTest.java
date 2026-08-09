package com.ailab.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ailab.system.domain.LabPeriodCloseSnapshot;
import com.ailab.system.domain.LabCollaborationRecord;
import com.ailab.system.domain.LabMember;
import com.ailab.system.domain.LabTask;
import com.ailab.system.domain.LabTaskExecutionEvent;
import com.ailab.system.domain.LabTaskWorkflowEvent;
import com.ailab.system.mapper.LabPeriodCloseSnapshotMapper;
import com.ailab.system.service.impl.LabPeriodCloseSnapshotService;
import com.ruoyi.common.exception.ServiceException;
import java.time.Clock;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class LabPeriodCloseSnapshotServiceTest {
    @Test
    void closeAppendsNextRevisionAndTypedFactsWithoutUpdatingOldRevision() {
        LabPeriodCloseSnapshotMapper mapper=mock(LabPeriodCloseSnapshotMapper.class);when(mapper.selectMaxRevision("2026-08")).thenReturn(1);
        when(mapper.taskMatchesLastEvent(9L)).thenReturn(true);when(mapper.taskMatchesLastEvent(10L)).thenReturn(true);
        when(mapper.selectWorkflowEvents(9L)).thenReturn(Collections.<LabTaskWorkflowEvent>emptyList());
        when(mapper.selectExecutionEvents(10L)).thenReturn(Collections.<LabTaskExecutionEvent>emptyList());
        when(mapper.insertSnapshot(any())).thenAnswer(invocation->{LabPeriodCloseSnapshot row=invocation.getArgument(0);row.setId(51L);return 1;});
        when(mapper.insertFact(any())).thenReturn(1);
        LabPeriodCloseSnapshotService service=new LabPeriodCloseSnapshotService(mapper,Clock.systemUTC());
        LabTask month=new LabTask();month.setId(9L);month.setTaskLevel("month");month.setWorkflowStatus("CONFIRMED");
        month.setGoalId(1L);month.setMilestoneId(2L);month.setOwnerId(8L);month.setBizLine("algorithm");
        month.setPerfWeight(new java.math.BigDecimal("60"));month.setGoalWeight(new java.math.BigDecimal("40"));
        LabTask week=new LabTask();week.setId(10L);week.setTaskLevel("week");week.setExecutionStatus("SELF_DONE");

        LabPeriodCloseSnapshot snapshot=service.close("2026-08",5,30L,7,8L,Arrays.asList(month,week));

        assertEquals(2,snapshot.getRevisionNo());assertEquals(5,snapshot.getPeriodVersion());
        ArgumentCaptor<com.ailab.system.domain.LabPeriodCloseFact> facts=ArgumentCaptor.forClass(com.ailab.system.domain.LabPeriodCloseFact.class);
        verify(mapper,times(2)).insertFact(facts.capture());assertEquals(2,facts.getAllValues().size());
        org.junit.jupiter.api.Assertions.assertTrue(facts.getAllValues().get(0).getFactJson().contains("\"ownerId\":8"));
        org.junit.jupiter.api.Assertions.assertTrue(facts.getAllValues().get(0).getFactJson().contains("\"perfWeight\":60"));
    }

    @Test
    void closeRefusesTaskThatDiffersFromItsLastEvent() {
        LabPeriodCloseSnapshotMapper mapper=mock(LabPeriodCloseSnapshotMapper.class);when(mapper.taskMatchesLastEvent(9L)).thenReturn(false);
        LabPeriodCloseSnapshotService service=new LabPeriodCloseSnapshotService(mapper,Clock.systemUTC());
        LabTask task=new LabTask();task.setId(9L);task.setTaskLevel("month");
        assertThrows(ServiceException.class,()->service.close("2026-08",1,null,1,8L,Collections.singletonList(task)));
    }

    @Test
    void closePersistsMonthlyAndWeeklyEventChainsAsSeparateImmutableFacts() {
        LabPeriodCloseSnapshotMapper mapper=mock(LabPeriodCloseSnapshotMapper.class);
        when(mapper.taskMatchesLastEvent(9L)).thenReturn(true);when(mapper.taskMatchesLastEvent(10L)).thenReturn(true);
        when(mapper.insertSnapshot(any())).thenAnswer(invocation->{LabPeriodCloseSnapshot row=invocation.getArgument(0);row.setId(51L);return 1;});
        when(mapper.insertFact(any())).thenReturn(1);
        LabTaskWorkflowEvent workflow=new LabTaskWorkflowEvent();workflow.setId(91L);workflow.setTaskId(9L);workflow.setEventType("CONFIRM");
        LabTaskExecutionEvent execution=new LabTaskExecutionEvent();execution.setId(101L);execution.setTaskId(10L);execution.setEventType("SELF_DONE");
        when(mapper.selectWorkflowEvents(9L)).thenReturn(Collections.singletonList(workflow));
        when(mapper.selectExecutionEvents(10L)).thenReturn(Collections.singletonList(execution));
        LabTask month=new LabTask();month.setId(9L);month.setTaskLevel("month");month.setWorkflowStatus("CONFIRMED");
        LabTask week=new LabTask();week.setId(10L);week.setTaskLevel("week");week.setExecutionStatus("SELF_DONE");

        new LabPeriodCloseSnapshotService(mapper,Clock.systemUTC())
                .close("2026-08",1,30L,2,8L,Arrays.asList(month,week));

        ArgumentCaptor<com.ailab.system.domain.LabPeriodCloseFact> facts=ArgumentCaptor.forClass(com.ailab.system.domain.LabPeriodCloseFact.class);
        verify(mapper,times(4)).insertFact(facts.capture());
        assertEquals("MONTH_WORKFLOW_EVENTS",facts.getAllValues().get(2).getFactType());
        assertEquals("WEEK_EXECUTION_EVENTS",facts.getAllValues().get(3).getFactType());
    }

    @Test
    void terminalTaskWithOpenBlockCannotBeClosed() {
        LabPeriodCloseSnapshotMapper mapper=mock(LabPeriodCloseSnapshotMapper.class);
        when(mapper.taskMatchesLastEvent(9L)).thenReturn(true);when(mapper.hasOpenBlock(9L)).thenReturn(true);
        LabTask task=new LabTask();task.setId(9L);task.setTaskLevel("month");task.setWorkflowStatus("CONFIRMED");

        assertThrows(ServiceException.class,()->new LabPeriodCloseSnapshotService(mapper,Clock.systemUTC())
                .close("2026-08",1,30L,2,8L,Collections.singletonList(task)));
    }

    @Test
    void closePinsUnacceptedResultsAndSupportingIdentityFactsWithoutForgingAcceptance() {
        LabPeriodCloseSnapshotMapper mapper=mock(LabPeriodCloseSnapshotMapper.class);
        when(mapper.taskMatchesLastEvent(9L)).thenReturn(true);
        when(mapper.insertSnapshot(any())).thenAnswer(invocation->{LabPeriodCloseSnapshot row=invocation.getArgument(0);row.setId(51L);return 1;});
        when(mapper.insertFact(any())).thenReturn(1);
        LabTask month=new LabTask();month.setId(9L);month.setTaskLevel("month");month.setWorkflowStatus("ACTIVE");
        month.setResultStatus("DOING");month.setOwnerId(8L);month.setBizLine("algorithm");
        LabCollaborationRecord collaboration=new LabCollaborationRecord();collaboration.setId(81L);collaboration.setPeriod("2026-08");
        collaboration.setFromMemberId(8L);collaboration.setToMemberId(9L);collaboration.setReviewStatus("APPROVED");
        LabMember member=new LabMember();member.setId(8L);member.setUserId(88L);member.setBizLine("algorithm");member.setMemberStatus("ACTIVE");

        new LabPeriodCloseSnapshotService(mapper,Clock.systemUTC()).close("2026-08",1,null,2,7L,
                Collections.singletonList(month),Collections.singletonList(collaboration),Collections.singletonList(member));

        ArgumentCaptor<com.ailab.system.domain.LabPeriodCloseFact> facts=ArgumentCaptor.forClass(com.ailab.system.domain.LabPeriodCloseFact.class);
        verify(mapper,times(3)).insertFact(facts.capture());
        assertEquals("MONTH_RESULT",facts.getAllValues().get(0).getFactType());
        org.junit.jupiter.api.Assertions.assertTrue(facts.getAllValues().get(0).getFactJson().contains("\"accepted\":false"));
        org.junit.jupiter.api.Assertions.assertTrue(facts.getAllValues().get(0).getFactJson().contains("\"closeResultStatus\":\"UNDONE\""));
        assertEquals("COLLABORATION",facts.getAllValues().get(1).getFactType());
        assertEquals("MEMBER_IDENTITY",facts.getAllValues().get(2).getFactType());
    }
}
