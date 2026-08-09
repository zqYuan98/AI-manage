package com.ailab.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailab.system.dto.LabAccessContext;
import com.ailab.system.dto.ManagerWorkbench;
import com.ailab.system.dto.MemberWorkbench;
import com.ailab.system.mapper.LabWorkbenchMapper;
import com.ailab.system.service.impl.LabAccessServiceImpl;
import com.ailab.system.service.impl.LabWorkbenchServiceImpl;
import com.ruoyi.common.exception.ServiceException;
import java.time.Instant;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Date;
import org.junit.jupiter.api.Test;

class LabWorkbenchServiceTest {
    private final LabAccessService access = mock(LabAccessService.class);
    private final LabWorkbenchMapper mapper = mock(LabWorkbenchMapper.class);
    private final LabWorkbenchService service = new LabWorkbenchServiceImpl(access, mapper);
    private final Date asOf = Date.from(Instant.parse("2026-08-10T08:00:00Z"));

    @Test
    void managerWorkbenchReturnsAllActionBucketsFromOneTrustedScope() {
        LabAccessContext scope = scope(7L, 70L, LabAccessServiceImpl.MANAGER, "algorithm");
        when(access.context(7L)).thenReturn(scope);
        when(mapper.selectPendingDecisions(eq(scope), eq("2026-08"))).thenReturn(Collections.singletonList(null));
        when(mapper.selectNewBlocks(eq(scope), any(Date.class))).thenReturn(Collections.singletonList(null));
        when(mapper.selectForecastDelays(eq(scope), eq(asOf))).thenReturn(Collections.singletonList(null));
        when(mapper.selectPendingAcceptance(scope)).thenReturn(Collections.singletonList(null));
        when(mapper.selectStaleKeyResults(eq(scope), any(Date.class))).thenReturn(Collections.singletonList(null));
        when(mapper.selectTeamCommitmentCounts(eq(scope), eq("2026-08"), eq(asOf))).thenReturn(Collections.singletonList(null));

        ManagerWorkbench result = service.manager("2026-08", asOf, 7L);

        assertEquals(1, result.getPendingDecisions().size());
        assertEquals(1, result.getNewBlocks().size());
        assertEquals(1, result.getForecastDelays().size());
        assertEquals(1, result.getPendingAcceptance().size());
        assertEquals(1, result.getStaleKeyResults().size());
        assertEquals(1, result.getTeamCommitments().size());
        assertEquals("MANAGER", result.getScopeType());
        assertEquals(true, result.isManagerActionsAllowed());
        verify(mapper).selectPendingDecisions(scope, "2026-08");
    }

    @Test
    void memberWorkbenchCanOnlyUseTheAuthenticatedMemberScope() {
        LabAccessContext scope = scope(8L, 80L, LabAccessServiceImpl.MEMBER, "platform");
        when(access.context(8L)).thenReturn(scope);
        when(mapper.selectOwnMonthlyResults(scope, "2026-08")).thenReturn(Collections.singletonList(null));
        when(mapper.selectOwnWeeklyCommitments(scope, "2026-08")).thenReturn(Collections.singletonList(null));
        when(mapper.selectOwnDueItems(scope, asOf)).thenReturn(Collections.singletonList(null));
        when(mapper.selectOwnBlocks(scope)).thenReturn(Collections.singletonList(null));
        when(mapper.selectOwnMissingEvidence(scope, "2026-08")).thenReturn(Collections.singletonList(null));

        MemberWorkbench result = service.member("2026-08", asOf, 8L);

        assertEquals(Long.valueOf(80L), result.getMemberId());
        assertEquals(1, result.getMonthlyResults().size());
        assertEquals(1, result.getWeeklyCommitments().size());
        assertEquals(1, result.getDueItems().size());
        assertEquals(1, result.getBlocks().size());
        assertEquals(1, result.getMissingEvidence().size());
        verify(mapper).selectOwnMonthlyResults(scope, "2026-08");
    }

    @Test
    void leadWorkbenchIsSameLineAndNeverReceivesManagerOnlyControls() {
        LabAccessContext scope = scope(9L, 90L, LabAccessServiceImpl.LEAD, "hardware");
        when(access.context(9L)).thenReturn(scope);
        when(mapper.selectPendingDecisions(eq(scope), eq("2026-08"))).thenReturn(Collections.emptyList());
        when(mapper.selectNewBlocks(eq(scope), any(Date.class))).thenReturn(Collections.emptyList());
        when(mapper.selectForecastDelays(scope, asOf)).thenReturn(Collections.emptyList());
        when(mapper.selectPendingAcceptance(scope)).thenReturn(Collections.emptyList());
        when(mapper.selectStaleKeyResults(eq(scope), any(Date.class))).thenReturn(Collections.emptyList());
        when(mapper.selectTeamCommitmentCounts(scope, "2026-08", asOf)).thenReturn(Collections.emptyList());

        ManagerWorkbench result = service.lead("2026-08", asOf, 9L);

        assertEquals("BIZ_LINE", result.getScopeType());
        assertEquals("hardware", result.getBizLine());
        assertFalse(result.isManagerActionsAllowed());
        assertThrows(ServiceException.class, () -> service.manager("2026-08", asOf, 9L));
    }

    @Test
    void workbenchFailsClosedWhenAnyActionBucketExceedsItsBound() {
        LabAccessContext scope = scope(7L, 70L, LabAccessServiceImpl.MANAGER, "algorithm");
        when(access.context(7L)).thenReturn(scope);
        when(mapper.selectPendingDecisions(scope, "2026-08"))
                .thenReturn(new ArrayList<com.ailab.system.domain.LabManagementDecision>(
                        Collections.nCopies(201, new com.ailab.system.domain.LabManagementDecision())));
        assertThrows(ServiceException.class, () -> service.manager("2026-08", asOf, 7L));
    }

    private LabAccessContext scope(Long user, Long member, String role, String line) {
        LabAccessContext value = new LabAccessContext(); value.setUserId(user); value.setMemberId(member);
        value.setRoleKey(role); value.setBizLine(line); return value;
    }
}
