package com.ailab.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ailab.system.domain.LabFormalAcceptanceFact;
import com.ailab.system.domain.LabFormalAcceptanceRevision;
import com.ailab.system.domain.LabTask;
import com.ailab.system.domain.LabTaskEvidence;
import com.ailab.system.mapper.LabFormalAcceptanceMapper;
import com.ailab.system.service.impl.LabFormalAcceptanceService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.verify;

class LabFormalAcceptanceServiceTest {
    @Test
    void confirmationAppendsRevisionAndImmutableAcceptedFact() {
        LabFormalAcceptanceMapper mapper=mock(LabFormalAcceptanceMapper.class);when(mapper.selectMaxRevision("2026-08","algorithm")).thenReturn(2);
        when(mapper.lockPeriod("2026-08")).thenReturn(90L);
        when(mapper.insertRevision(any())).thenAnswer(invocation->{LabFormalAcceptanceRevision row=invocation.getArgument(0);row.setId(30L);return 1;});
        when(mapper.insertFact(any())).thenReturn(1);
        LabFormalAcceptanceService service=new LabFormalAcceptanceService(mapper,
                Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"),ZoneOffset.UTC));
        LabTask task=acceptedTask();

        LabFormalAcceptanceRevision revision=service.accept(task,8L,"验收通过",3);

        assertEquals(3,revision.getRevisionNo());
        ArgumentCaptor<LabFormalAcceptanceFact> capture=ArgumentCaptor.forClass(LabFormalAcceptanceFact.class);verify(mapper).insertFact(capture.capture());
        LabFormalAcceptanceFact fact=capture.getValue();
        assertEquals(30L,fact.getFormalRevisionId());assertEquals(9L,fact.getTaskId());assertEquals(8L,fact.getReviewerId());
        assertEquals(3,fact.getEvidenceVersion());
        org.junit.jupiter.api.Assertions.assertTrue(fact.getFactJson().contains("\"id\":71"));
        org.junit.jupiter.api.Assertions.assertTrue(fact.getFactJson().contains("\"auditStatus\":\"APPROVED\""));
        org.junit.jupiter.api.Assertions.assertTrue(fact.getFactJson().contains("\"reviewerId\":8"));
        org.junit.jupiter.api.Assertions.assertTrue(fact.getFactJson().contains("\"goalId\":21"));
        org.junit.jupiter.api.Assertions.assertTrue(fact.getFactJson().contains("\"milestoneId\":22"));
        org.junit.jupiter.api.Assertions.assertTrue(fact.getFactJson().contains("\"ownerId\":23"));
        verify(mapper).ensurePeriodLock("2026-08","8");
        verify(mapper).lockPeriod("2026-08");
    }

    @Test
    void exactRevisionReadNeverFallsBackToLiveRows() {
        LabFormalAcceptanceMapper mapper=mock(LabFormalAcceptanceMapper.class);LabFormalAcceptanceFact old=new LabFormalAcceptanceFact();old.setTaskId(9L);old.setFactJson("{\"result\":\"old\"}");
        when(mapper.selectFactsByRevision(30L)).thenReturn(Collections.singletonList(old));
        LabFormalAcceptanceService service=new LabFormalAcceptanceService(mapper,Clock.systemUTC());
        assertEquals("{\"result\":\"old\"}",service.readFacts(30L).get(0).getFactJson());
    }

    private static LabTask acceptedTask(){LabTask task=new LabTask();task.setId(9L);task.setGoalId(21L);task.setMilestoneId(22L);task.setOwnerId(23L);task.setTaskLevel("month");task.setPeriod("2026-08");task.setBizLine("algorithm");
        task.setTitle("完成算法验证");task.setDeliverable("验收记录");task.setResultStatus("ONTIME");task.setResultDesc("完成");
        task.setWorkflowStatus("CONFIRMED");task.setPerfWeight(new BigDecimal("60"));task.setGoalWeight(new BigDecimal("40"));task.setVersion(4);
        LabTaskEvidence evidence=new LabTaskEvidence();evidence.setId(71L);evidence.setTaskId(9L);evidence.setAuditStatus("APPROVED");
        evidence.setEvidenceTitle("验收记录");task.setEvidenceList(Collections.singletonList(evidence));return task;}
}
