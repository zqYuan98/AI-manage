package com.ailab.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailab.system.constant.LabConstants;
import com.ailab.system.domain.LabManagementDecision;
import com.ailab.system.dto.LabAccessContext;
import com.ailab.system.mapper.LabManagementDecisionMapper;
import com.ailab.system.service.impl.LabAccessServiceImpl;
import com.ailab.system.service.impl.LabManagementDecisionServiceImpl;
import com.ruoyi.common.exception.ServiceException;
import java.time.Instant;
import java.util.Date;
import java.util.ArrayList;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LabManagementDecisionServiceTest {
    private final LabAccessService access = mock(LabAccessService.class);
    private final LabManagementDecisionMapper mapper = mock(LabManagementDecisionMapper.class);
    private final LabManagementDecisionService service = new LabManagementDecisionServiceImpl(access, mapper);

    @Test
    void managerCreatesOnlyTheMinimalOpenDecisionRecord() {
        when(access.context(7L)).thenReturn(scope(70L, LabAccessServiceImpl.MANAGER, "algorithm"));
        when(mapper.selectActiveMemberBizLine(80L)).thenReturn("platform");
        when(mapper.selectTaskBizLine(30003L)).thenReturn("platform");
        when(mapper.insertDecision(any(LabManagementDecision.class))).thenAnswer(invocation -> {
            ((LabManagementDecision) invocation.getArgument(0)).setId(11L); return 1;
        });
        LabManagementDecision input = decision("platform");

        LabManagementDecision result = service.create(input, 7L);

        assertEquals(Long.valueOf(11L), result.getId());
        assertEquals("OPEN", result.getDecisionStatus());
        assertEquals(Integer.valueOf(0), result.getVersion());
        ArgumentCaptor<LabManagementDecision> saved = ArgumentCaptor.forClass(LabManagementDecision.class);
        verify(mapper).insertDecision(saved.capture());
        assertEquals(LabConstants.NO, saved.getValue().getDelFlag());
    }

    @Test
    void leadCannotCreateACrossLineDecision() {
        when(access.context(8L)).thenReturn(scope(80L, LabAccessServiceImpl.LEAD, "hardware"));
        assertThrows(ServiceException.class, () -> service.create(decision("platform"), 8L));
    }

    @Test
    void leadCannotAssignOrLinkARecordOutsideTheTrustedBusinessLine() {
        when(access.context(8L)).thenReturn(scope(80L, LabAccessServiceImpl.LEAD, "hardware"));
        when(mapper.selectActiveMemberBizLine(80L)).thenReturn("platform");
        LabManagementDecision crossOwner = decision("hardware");
        assertThrows(ServiceException.class, () -> service.create(crossOwner, 8L));

        when(mapper.selectActiveMemberBizLine(80L)).thenReturn("hardware");
        when(mapper.selectTaskBizLine(30003L)).thenReturn("platform");
        assertThrows(ServiceException.class, () -> service.create(decision("hardware"), 8L));

        LabManagementDecision crossGoal = decision("hardware"); crossGoal.setRelatedTaskId(null); crossGoal.setRelatedGoalId(30001L);
        when(mapper.selectGoalOwnerBizLine(30001L)).thenReturn("platform");
        assertThrows(ServiceException.class, () -> service.create(crossGoal, 8L));
    }

    @Test
    void ownerCanCompleteButUnrelatedMemberCannotMutateDecision() {
        LabManagementDecision stored = decision("platform"); stored.setId(12L); stored.setOwnerId(80L);
        stored.setDecisionStatus("OPEN"); stored.setVersion(3); stored.setDelFlag(LabConstants.NO);
        when(mapper.selectDecisionForUpdate(12L)).thenReturn(stored);
        when(mapper.completeDecision(12L, 3, "8")).thenReturn(1);
        when(access.context(8L)).thenReturn(scope(80L, LabAccessServiceImpl.MEMBER, "platform"));

        service.complete(12L, 3, 8L);
        verify(mapper).completeDecision(12L, 3, "8");

        when(access.context(9L)).thenReturn(scope(90L, LabAccessServiceImpl.MEMBER, "platform"));
        assertThrows(ServiceException.class, () -> service.complete(12L, 3, 9L));
    }

    @Test
    void decisionListFailsClosedInsteadOfReturningAnUnboundedResult() {
        LabAccessContext manager = scope(70L, LabAccessServiceImpl.MANAGER, "algorithm");
        when(access.context(7L)).thenReturn(manager);
        when(mapper.selectDecisionList(manager, "2026-08", "OPEN"))
                .thenReturn(new ArrayList<LabManagementDecision>(Collections.nCopies(201, new LabManagementDecision())));
        assertThrows(ServiceException.class, () -> service.list("2026-08", "OPEN", 7L));
    }

    private LabAccessContext scope(Long member, String role, String line) {
        LabAccessContext value = new LabAccessContext(); value.setMemberId(member); value.setRoleKey(role); value.setBizLine(line); return value;
    }
    private LabManagementDecision decision(String line) {
        LabManagementDecision value = new LabManagementDecision(); value.setPeriod("2026-08"); value.setBizLine(line);
        value.setProblem("Metric definitions are inconsistent"); value.setDecisionContent("Use the canonical service metric");
        value.setOwnerId(80L); value.setDueDate(Date.from(Instant.parse("2026-08-15T00:00:00Z")));
        value.setRelatedTaskId(30003L); return value;
    }
}
