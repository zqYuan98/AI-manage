package com.ailab.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailab.system.constant.LabConstants;
import com.ailab.system.controller.LabTaskController;
import com.ailab.system.domain.LabGoal;
import com.ailab.system.domain.LabTask;
import com.ailab.system.domain.LabTaskBlockEvent;
import com.ailab.system.domain.LabTaskEvidence;
import com.ailab.system.domain.LabTaskQualityGate;
import com.ailab.system.dto.TaskSubmitCommand;
import com.ailab.system.dto.LabAccessContext;
import com.ailab.system.exception.LabValidationException;
import com.ailab.system.mapper.LabAccessMapper;
import com.ailab.system.mapper.LabGoalMapper;
import com.ailab.system.mapper.LabTaskEvidenceMapper;
import com.ailab.system.mapper.LabTaskMapper;
import com.ailab.system.service.impl.LabTaskServiceImpl;
import com.ailab.system.service.impl.LabAccessServiceImpl;
import com.ailab.system.service.impl.LabTaskWorkflowEventService;
import com.ailab.system.service.impl.TaskWorkflowServiceImpl;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.core.page.TableDataInfo;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.security.access.prepost.PreAuthorize;

class LabTaskServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-08T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private MemoryTaskMapper tasks;
    private MemoryEvidenceMapper evidence;
    private MemoryGoalMapper goals;
    private MemoryAccessMapper access;
    private LabTaskWorkflowEventService workflowEvents;
    private LabTaskService service;

    @BeforeEach
    void setUp() {
        tasks = new MemoryTaskMapper(); evidence = new MemoryEvidenceMapper(); goals = new MemoryGoalMapper();
        access = new MemoryAccessMapper();
        access.put(1L, 1L, "lab_manager", "manage");
        access.put(2L, 12L, "lab_lead", "algorithm");
        access.put(3L, 13L, "lab_member", "algorithm");
        access.put(8L, 8L, "lab_member", "algorithm");
        access.put(9L, 9L, "lab_manager", "manage");
        access.put(900L, 8L, "lab_member", "algorithm");
        workflowEvents = org.mockito.Mockito.mock(LabTaskWorkflowEventService.class);
        service = new LabTaskServiceImpl(tasks, evidence, goals, new TaskWorkflowServiceImpl(CLOCK),
                new LabAccessServiceImpl(access), workflowEvents, null, CLOCK);
    }

    @Test
    void weeklyTaskRequiresMonthParentWithContainedPeriodAndSameGoalLinks() {
        goals.put(goal(1L, 0L, "YEAR", 2026, null));
        goals.put(goal(2L, 1L, "QUARTER", 2026, "2026Q3"));
        LabTask parent = task(10L, 0L, "month", "2026-08", 8L, "40", "60");
        parent.setGoalId(1L); parent.setMilestoneId(2L); tasks.put(parent);
        LabTask weekly = task(null, 10L, "week", "2026-W36", 8L, "0", "0");
        weekly.setGoalId(1L); weekly.setMilestoneId(2L);
        weekly.setPlanDate(Date.from(Instant.parse("2026-09-02T00:00:00Z")));

        assertThrows(ServiceException.class, () -> service.createTask(weekly, 8L));
        weekly.setPlanDate(Date.from(Instant.parse("2026-08-20T00:00:00Z"))); weekly.setPeriod("2026-W32");

        service.createTask(weekly, 8L);
        assertNotNull(weekly.getId());
    }

    @Test
    void crossMonthIsoWeekBelongsToParentMonthByItsPlanDate() {
        goals.put(goal(1L, 0L, "YEAR", 2026, null));
        goals.put(goal(2L, 1L, "QUARTER", 2026, "2026Q3"));
        LabTask parent = activeTask(10L, 8L); parent.setGoalId(1L); parent.setMilestoneId(2L); tasks.put(parent);
        LabTask weekly = task(null, 10L, "week", "2026-W36", 8L, "0", "0");
        weekly.setGoalId(1L); weekly.setMilestoneId(2L);
        weekly.setPlanDate(Date.from(Instant.parse("2026-08-31T00:00:00Z")));

        service.createTask(weekly, 8L);

        assertNotNull(weekly.getId()); assertEquals("2026-W36", weekly.getPeriod());
    }

    @Test
    void taskBusinessLineMustMatchOwnerResponsibleScope() {
        goals.put(goal(1L, 0L, "YEAR", 2026, null));
        goals.put(goal(2L, 1L, "QUARTER", 2026, "2026Q3"));
        tasks.memberLines.put(8L, "algorithm");
        LabTask month = task(null, 0L, "month", "2026-08", 8L, "100", "100");
        month.setGoalId(1L); month.setMilestoneId(2L); month.setBizLine("platform");

        assertThrows(ServiceException.class, () -> service.createTask(month, 8L));
    }

    @Test
    void monthlyPlanActivationIsAtomicPerOwnerAndPeriodAndUsesPerfWeights() {
        LabTask first = task(1L, 0L, "month", "2026-08", 8L, "40", "10");
        LabTask second = task(2L, 0L, "month", "2026-08", 8L, "50", "90");
        tasks.put(first); tasks.put(second);

        assertThrows(ServiceException.class, () -> service.activateMonthlyPlan(8L, "2026-08", 9L));
        second.setPerfWeight(new BigDecimal("60"));

        assertEquals(2, service.activateMonthlyPlan(8L, "2026-08", 9L));
        assertEquals(LabConstants.WORKFLOW_ACTIVE, tasks.find(1L).getWorkflowStatus());
        assertEquals(LabConstants.WORKFLOW_ACTIVE, tasks.find(2L).getWorkflowStatus());
        assertEquals(new BigDecimal("90"), second.getGoalWeight(), "goal weight must not drive performance plan activation");
    }

    @Test
    void monthlyPlanRequiresAnotherEnabledReviewerBeforeActivation() {
        LabTask task = task(1L, 0L, "month", "2026-08", 8L, "100", "100");
        tasks.put(task); access.eligibleReviewerCount = 0;

        assertThrows(ServiceException.class, () -> service.activateMonthlyPlan(8L, "2026-08", 9L));

        access.eligibleReviewerCount = 1;
        assertEquals(1, service.activateMonthlyPlan(8L, "2026-08", 9L));
    }

    @Test
    void weeklyTaskActivatesIndividuallyOnlyUnderAnActiveMonth() {
        LabTask month = activeTask(1L, 8L); tasks.put(month);
        LabTask week = task(2L, 1L, "week", "2026-W32", 8L, "0", "0"); tasks.put(week);

        service.activateTask(2L, 0, 8L);

        assertEquals(LabConstants.WORKFLOW_ACTIVE, tasks.find(2L).getWorkflowStatus());
        assertThrows(ServiceException.class, () -> service.activateTask(1L, 0, 8L));
    }

    @Test
    void weeklyActivationUsesTheLockedCurrentParentStateAndRelationship() {
        LabTask month = activeTask(1L, 8L); tasks.put(month);
        LabTask week = task(2L, 1L, "week", "2026-W32", 8L, "0", "0"); tasks.put(week);
        LabTask currentParent = activeTask(1L, 8L);
        currentParent.setWorkflowStatus(LabConstants.WORKFLOW_PENDING_REVIEW);
        tasks.lockedOverrides.put(1L, currentParent);

        ServiceException statusError = assertThrows(ServiceException.class,
                () -> service.activateTask(2L, 0, 8L));
        assertEquals("Weekly task requires an active, unlocked month task", statusError.getMessage());
        assertEquals(LabConstants.WORKFLOW_DRAFT, tasks.find(2L).getWorkflowStatus());

        currentParent.setWorkflowStatus(LabConstants.WORKFLOW_ACTIVE);
        currentParent.setBizLine("platform");
        ServiceException relationError = assertThrows(ServiceException.class,
                () -> service.activateTask(2L, 0, 8L));
        assertEquals("Weekly task links must match its current month task", relationError.getMessage());
        assertTrue(tasks.lockedTaskIds.contains(1L), "parent must be read with FOR UPDATE semantics");
    }

    @Test
    void taskDeleteControllerUsesTheRoleSeededRemovePermission() throws Exception {
        PreAuthorize permission = LabTaskController.class
                .getMethod("delete", Long.class, Integer.class)
                .getAnnotation(PreAuthorize.class);
        assertEquals("@ss.hasPermi('lab:task:remove')", permission.value());
    }

    @Test
    void monthlyProgressUsesOnlyConfirmedWeeklyChildren() {
        LabTask month = task(1L, 0L, "month", "2026-08", 8L, "100", "100"); tasks.put(month);
        LabTask confirmed = task(2L, 1L, "week", "2026-W32", 8L, "0", "0");
        confirmed.setWorkflowStatus(LabConstants.WORKFLOW_CONFIRMED); confirmed.setResultStatus(LabConstants.RESULT_ONTIME);
        LabTask undone = task(3L, 1L, "week", "2026-W32", 8L, "0", "0");
        undone.setWorkflowStatus(LabConstants.WORKFLOW_CONFIRMED); undone.setResultStatus(LabConstants.RESULT_UNDONE);
        LabTask pending = task(4L, 1L, "week", "2026-W32", 8L, "0", "0");
        pending.setWorkflowStatus(LabConstants.WORKFLOW_PENDING_REVIEW); pending.setResultStatus(LabConstants.RESULT_ONTIME);
        tasks.put(confirmed); tasks.put(undone); tasks.put(pending);

        assertEquals(new BigDecimal("50.00"), service.calculateMonthProgress(1L, 9L));
    }

    @Test
    void taskServiceEnforcesTrustedActorScopeForListDetailAndCreate() {
        LabTask own = activeTask(1L, 13L); tasks.put(own);
        LabTask sameLineOther = activeTask(2L, 14L); tasks.put(sameLineOther);
        LabTask crossLine = activeTask(3L, 15L); crossLine.setBizLine("platform"); tasks.put(crossLine);

        List<LabTask> memberRows = service.listTasks(new LabTask(), 3L);
        assertEquals(1, memberRows.size());
        assertEquals(Long.valueOf(13L), memberRows.get(0).getOwnerId());
        assertThrows(ServiceException.class, () -> service.getTask(2L, 3L));

        goals.put(goal(10L, 0L, "YEAR", 2026, null));
        goals.put(goal(11L, 10L, "QUARTER", 2026, "2026Q3"));
        tasks.memberLines.put(15L, "platform");
        LabTask attempted = task(null, 0L, "month", "2026-08", 15L, "100", "100");
        attempted.setGoalId(10L); attempted.setMilestoneId(11L); attempted.setBizLine("platform");
        assertThrows(ServiceException.class, () -> service.createTask(attempted, 2L));
    }

    @Test
    void controllerPageRequestSurvivesTrustedTaskScopeLookupAndDoesNotPolluteCallerQuery() {
        for (long id = 1L; id <= 4L; id++) tasks.put(activeTask(id, 13L));
        access.consumePage = true;
        tasks.pagedTotal = 7L;
        LabTask query = new LabTask();
        try {
            PageHelper.startPage(2, 2, "plan_date desc");

            List<LabTask> rows = service.listTasks(query, 3L);
            TableDataInfo table = new ExposedTaskController(service).table(rows);

            assertTrue(rows instanceof Page, "the business mapper, not the access lookup, must consume PageHelper");
            assertEquals(7L, table.getTotal());
            assertEquals(2, rows.size());
            assertNull(query.getOwnerId(), "trusted scope belongs on a server copy, not the caller's binding object");
            assertNotSame(query, tasks.lastQuery);
            assertEquals(Long.valueOf(13L), tasks.lastQuery.getOwnerId());
        } finally {
            PageHelper.clearPage();
        }
    }

    @Test
    void dashboardDrillFiltersFeedTheExistingTaskListThroughTypedParameters() {
        LabTask query = new LabTask();
        query.setPeriod("2026-08");
        query.setTaskLevel("month");
        query.setWorkflowStatuses(Arrays.asList("DRAFT", "ACTIVE"));
        query.setOverdueOrPending(Boolean.TRUE);
        query.setAsOf(Date.from(Instant.parse("2026-08-15T00:00:00Z")));
        query.setCurrentBlockFlag("1");
        query.setBlockStartBefore(Date.from(Instant.parse("2026-08-08T00:00:00Z")));

        service.listTasks(query, 9L);

        assertEquals(Arrays.asList("DRAFT", "ACTIVE"), query.getWorkflowStatuses());
        assertEquals("month", query.getTaskLevel());
        assertEquals(Boolean.TRUE, query.getOverdueOrPending());
        assertEquals("1", query.getCurrentBlockFlag());
        assertNotNull(query.getBlockStartBefore());
    }

    @Test
    void indexedGetBindingRetainsDashboardWorkflowStatusArray() {
        LabTask query = new LabTask();
        BeanWrapper requestBinder = new BeanWrapperImpl(query);
        requestBinder.setAutoGrowNestedPaths(true);
        requestBinder.setPropertyValue("taskLevel", "month");
        requestBinder.setPropertyValue("workflowStatuses[0]", "DRAFT");
        requestBinder.setPropertyValue("workflowStatuses[1]", "ACTIVE");

        assertEquals(Arrays.asList("DRAFT", "ACTIVE"), query.getWorkflowStatuses());
        assertEquals("month", query.getTaskLevel());
    }

    @Test
    void cumulativeTrendDrillBindsAValidatedMonthUpperBound() {
        LabTask query = new LabTask();
        BeanWrapper requestBinder = new BeanWrapperImpl(query);
        assertTrue(requestBinder.isWritableProperty("periodTo"), "Spring GET binding needs a typed periodTo property");
        requestBinder.setPropertyValue("periodTo", "2026-08");
        service.listTasks(query, 9L);

        requestBinder.setPropertyValue("periodTo", "2026-W32");
        assertThrows(ServiceException.class, () -> service.listTasks(query, 9L));
    }

    @Test
    void taskListRejectsUnknownTaskLevelBeforeMapperUse() {
        LabTask query = new LabTask(); query.setTaskLevel("quarter");
        assertThrows(ServiceException.class, () -> service.listTasks(query, 9L));
    }

    @Test
    void taskListRejectsUnknownWorkflowStatusCollectionsBeforeMapperUse() {
        LabTask query = new LabTask();
        query.setWorkflowStatuses(Arrays.asList("ACTIVE", "ACTIVE') OR 1=1 --"));
        assertThrows(ServiceException.class, () -> service.listTasks(query, 9L));
    }

    @Test
    void leadCannotReviewOwnTaskButCanReviewTeammateInSameLine() {
        LabTask self = activeTask(1L, 12L); self.setWorkflowStatus(LabConstants.WORKFLOW_PENDING_REVIEW); tasks.put(self);
        TaskSubmitCommand review = new TaskSubmitCommand(); review.setReviewerComment("ok");
        review.setEvidenceAuditComment("verified"); review.setApprovedEvidenceIds(new ArrayList<Long>());

        assertThrows(ServiceException.class, () -> service.reviewPass(1L, 0, review, 2L));

        LabTask teammate = activeTask(2L, 13L); teammate.setWorkflowStatus(LabConstants.WORKFLOW_PENDING_REVIEW); tasks.put(teammate);
        service.reviewPass(2L, 0, review, 2L);
        assertEquals(LabConstants.WORKFLOW_CONFIRMED, tasks.find(2L).getWorkflowStatus());
    }

    @Test
    void submitAndReviewPersistOnlyServerAttributedEvidenceAndSelectedApproval() {
        LabTask task = activeTask(1L, 8L); tasks.put(task);
        LabTaskEvidence client = validEvidence(null); client.setSubmitterId(999L); client.setAuditStatus("APPROVED");
        TaskSubmitCommand submit = new TaskSubmitCommand(); submit.setResultDesc("done");
        submit.setActualFinishTime(Date.from(Instant.parse("2026-08-07T08:00:00Z")));
        submit.setEvidenceList(Arrays.asList(client));

        service.submitResult(1L, 0, submit, 8L);
        LabTaskEvidence stored = evidence.forTask(1L).get(0);
        assertEquals(Long.valueOf(8L), stored.getSubmitterId());
        assertEquals(LabConstants.EVIDENCE_AUDIT_PENDING, stored.getAuditStatus());
        assertEquals(LabConstants.WORKFLOW_PENDING_REVIEW, tasks.find(1L).getWorkflowStatus());

        TaskSubmitCommand review = new TaskSubmitCommand(); review.setReviewerComment("ok");
        review.setEvidenceAuditComment("verified"); review.setApprovedEvidenceIds(Arrays.asList(stored.getId()));
        service.reviewPass(1L, 1, review, 9L);

        assertEquals(LabConstants.EVIDENCE_AUDIT_APPROVED, evidence.find(stored.getId()).getAuditStatus());
        assertEquals(Long.valueOf(9L), evidence.find(stored.getId()).getAuditorId());
        assertEquals(LabConstants.WORKFLOW_CONFIRMED, tasks.find(1L).getWorkflowStatus());
    }

    @Test
    void workflowValidationKeepsStructuredFieldErrorsForRestClients() {
        tasks.put(activeTask(1L, 8L));
        TaskSubmitCommand invalid = new TaskSubmitCommand();

        LabValidationException error = assertThrows(LabValidationException.class,
                () -> service.submitResult(1L, 0, invalid, 8L));

        assertFalse(error.getFieldErrors().isEmpty());
        assertEquals("resultDesc", error.getFieldErrors().get(0).getField());
    }

    @Test
    void authenticatedSysUserIsResolvedToMemberForOwnershipAndEvidenceAudit() {
        tasks.memberIds.put(900L, 8L);
        LabTask task = activeTask(1L, 8L); tasks.put(task);
        TaskSubmitCommand submit = new TaskSubmitCommand(); submit.setResultDesc("done");
        submit.setActualFinishTime(Date.from(Instant.parse("2026-08-07T08:00:00Z")));
        submit.setEvidenceList(Arrays.asList(validEvidence(null)));

        service.submitResult(1L, 0, submit, 900L);

        assertEquals(Long.valueOf(8L), evidence.forTask(1L).get(0).getSubmitterId());
    }

    @Test
    void qualityGateCanPassOnlyWithExplicitApprovedEvidence() {
        LabTask task = activeTask(1L, 8L); tasks.put(task);
        LabTaskQualityGate gate = new LabTaskQualityGate(); gate.setTaskId(1L); gate.setGateNo("Q1"); gate.setGateName("reproducible");
        service.addQualityGate(gate, 8L);
        TaskSubmitCommand submit = new TaskSubmitCommand(); submit.setResultDesc("done");
        submit.setActualFinishTime(Date.from(Instant.parse("2026-08-07T08:00:00Z")));
        submit.setEvidenceList(Arrays.asList(validEvidence(null)));
        service.submitResult(1L, 0, submit, 8L);
        LabTaskEvidence pending = evidence.forTask(1L).get(0);
        assertThrows(ServiceException.class, () -> service.passQualityGate(gate.getId(), pending.getId(), "ok", 9L));
        TaskSubmitCommand review = new TaskSubmitCommand(); review.setReviewerComment("ok");
        review.setEvidenceAuditComment("verified"); review.setApprovedEvidenceIds(Arrays.asList(pending.getId()));
        service.reviewPass(1L, 1, review, 9L);

        service.passQualityGate(gate.getId(), pending.getId(), "ok", 9L);
        assertEquals("PASSED", tasks.gate(gate.getId()).getGateStatus());
        assertEquals(pending.getId(), tasks.gate(gate.getId()).getEvidenceId());
    }

    @Test
    void qualityGateCannotBeMovedToAnotherTaskDuringEdit() {
        tasks.put(activeTask(1L, 8L)); tasks.put(activeTask(2L, 8L));
        LabTaskQualityGate gate = new LabTaskQualityGate(); gate.setTaskId(1L); gate.setGateNo("Q1"); gate.setGateName("original");
        service.addQualityGate(gate, 8L);
        LabTaskQualityGate edit = new LabTaskQualityGate(); edit.setId(gate.getId()); edit.setTaskId(2L);
        edit.setGateNo("Q1"); edit.setGateName("renamed");

        service.updateQualityGate(edit, 8L);

        assertEquals(Long.valueOf(1L), tasks.gate(gate.getId()).getTaskId());
    }

    @Test
    void zeroAffectedQualityGateWritesAreRejectedAsConcurrentStateChanges() {
        tasks.put(activeTask(1L, 8L));
        LabTaskQualityGate gate = new LabTaskQualityGate(); gate.setTaskId(1L); gate.setGateNo("Q1"); gate.setGateName("guarded");
        tasks.rejectGateWrite = true;
        assertThrows(ServiceException.class, () -> service.addQualityGate(gate, 8L));
        tasks.rejectGateWrite = false; service.addQualityGate(gate, 8L);
        tasks.rejectGateWrite = true;
        assertThrows(ServiceException.class, () -> service.updateQualityGate(gate, 8L));
        assertThrows(ServiceException.class, () -> service.deleteQualityGate(gate.getId(), 8L));
    }

    @Test
    void qualityGateSqlGuardsTaskStateLockAndApprovedEvidence() throws Exception {
        Path cursor = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (cursor != null && !Files.exists(cursor.resolve("ruoyi-lab/src/main/resources/mapper/lab/LabTaskMapper.xml"))) cursor = cursor.getParent();
        String xml = new String(Files.readAllBytes(cursor.resolve("ruoyi-lab/src/main/resources/mapper/lab/LabTaskMapper.xml")), StandardCharsets.UTF_8)
                .toLowerCase().replaceAll("\\s+", "");
        assertTrue(xml.contains("insertintolab_task_quality_gate") && xml.contains("fromlab_tasktwhere")
                && xml.contains("t.workflow_statusin('draft','active')") && xml.contains("t.period_lock_flag='0'"));
        assertTrue(xml.contains("updatelab_task_quality_gategjoinlab_tasktont.id=g.task_id"));
        assertTrue(xml.contains("joinlab_task_evidenceeone.id=#{evidenceid}")
                && xml.contains("e.audit_status='approved'") && xml.contains("t.workflow_status='confirmed'"));
    }

    @Test
    void standaloneEvidenceInsertHonorsPersistenceStateGuard() {
        tasks.put(activeTask(1L, 8L));
        evidence.rejectInsert = true;

        assertThrows(ServiceException.class, () -> service.addEvidence(1L, validEvidence(null), 8L));
    }

    @Test
    void pendingEvidenceStillCannotBeDeletedAfterTaskConfirmation() {
        LabTask confirmed = activeTask(1L, 8L); confirmed.setWorkflowStatus(LabConstants.WORKFLOW_CONFIRMED); tasks.put(confirmed);
        LabTaskEvidence pending = validEvidence(1L); pending.setId(22L); pending.setAuditStatus("PENDING"); evidence.put(pending);

        assertThrows(ServiceException.class, () -> service.deleteEvidence(1L, 22L, 8L));
    }

    @Test
    void blockAndUnblockKeepEpisodeHistoryAndCurrentTaskStateTogether() {
        LabTask task = activeTask(1L, 8L); tasks.put(task);

        LabTaskBlockEvent opened = service.blockTask(1L, 0, "DEPENDENCY", "API missing", 8L);
        assertEquals("1", tasks.find(1L).getBlockFlag());
        assertNotNull(tasks.find(1L).getBlockStartTime());
        assertEquals("OPEN", opened.getBlockStatus());
        assertEquals(Integer.valueOf(1), opened.getEpisodeNo());
        assertThrows(ServiceException.class, () -> service.blockTask(1L, 1, "DEPENDENCY", "again", 8L));

        service.unblockTask(1L, 1, "API ready", 8L);
        assertEquals("0", tasks.find(1L).getBlockFlag());
        assertNull(tasks.find(1L).getBlockStartTime());
        assertEquals("CLOSED", tasks.events.get(opened.getId()).getBlockStatus());
        assertFalse(tasks.selectBlockEvents(1L).isEmpty());

        LabTaskBlockEvent second = service.blockTask(1L, 2, "DEPENDENCY", "second issue", 8L);
        assertEquals(Integer.valueOf(2), second.getEpisodeNo());
        service.unblockTask(1L, 3, "second issue resolved", 8L);
        assertEquals(2, tasks.selectBlockEvents(1L).size());
    }

    @Test
    void optimisticConflictRejectsStaleTaskUpdate() {
        LabTask stored = activeTask(1L, 8L); stored.setVersion(3); tasks.put(stored);
        LabTask edit = activeTask(1L, 8L); edit.setVersion(2);
        assertThrows(ServiceException.class, () -> service.updateTask(edit, 8L));
        assertEquals(Integer.valueOf(3), tasks.find(1L).getVersion());
    }

    @Test
    void confirmedOrPeriodLockedTaskCannotBeEditedOrDeleted() {
        LabTask confirmed = activeTask(1L, 8L);
        confirmed.setWorkflowStatus(LabConstants.WORKFLOW_CONFIRMED); tasks.put(confirmed);
        LabTask edit = activeTask(1L, 8L); edit.setWorkflowStatus(LabConstants.WORKFLOW_CONFIRMED);

        assertThrows(ServiceException.class, () -> service.updateTask(edit, 8L));
        assertThrows(ServiceException.class, () -> service.deleteTask(1L, 0, 8L));

        confirmed.setWorkflowStatus(LabConstants.WORKFLOW_ACTIVE); confirmed.setPeriodLockFlag("1");
        assertThrows(ServiceException.class, () -> service.updateTask(edit, 8L));
    }

    @Test
    void pendingReviewContentRejectsGenericUpdateEvidenceGateAndBlockWrites() {
        goals.put(goal(1L, 0L, "YEAR", 2026, null));
        goals.put(goal(2L, 1L, "QUARTER", 2026, "2026Q3"));
        for (long id = 1L; id <= 4L; id++) {
            LabTask pending = activeTask(id, 8L); pending.setGoalId(1L); pending.setMilestoneId(2L);
            pending.setWorkflowStatus(LabConstants.WORKFLOW_PENDING_REVIEW); tasks.put(pending);
        }

        LabTask edit = activeTask(1L, 8L); edit.setGoalId(1L); edit.setMilestoneId(2L);
        assertThrows(ServiceException.class, () -> service.updateTask(edit, 8L));
        assertThrows(ServiceException.class, () -> service.addEvidence(2L, validEvidence(null), 8L));
        LabTaskQualityGate gate = new LabTaskQualityGate(); gate.setTaskId(3L); gate.setGateNo("Q1"); gate.setGateName("locked");
        assertThrows(ServiceException.class, () -> service.addQualityGate(gate, 8L));
        assertThrows(ServiceException.class, () -> service.blockTask(4L, 0, "DEPENDENCY", "locked", 8L));
    }

    @Test
    void periodLockRejectsWorkflowTransitionsAndUnblock() {
        LabTask active = activeTask(1L, 8L); active.setPeriodLockFlag("1"); tasks.put(active);
        TaskSubmitCommand submit = new TaskSubmitCommand(); submit.setResultDesc("done");
        submit.setActualFinishTime(Date.from(Instant.parse("2026-08-07T08:00:00Z")));
        submit.setEvidenceList(Arrays.asList(validEvidence(null)));
        assertThrows(ServiceException.class, () -> service.submitResult(1L, 0, submit, 8L));

        LabTask pendingWithdraw = activeTask(2L, 8L); pendingWithdraw.setWorkflowStatus(LabConstants.WORKFLOW_PENDING_REVIEW);
        pendingWithdraw.setPeriodLockFlag("1"); tasks.put(pendingWithdraw);
        assertThrows(ServiceException.class, () -> service.withdrawResult(2L, 0, 8L));

        LabTask pendingReview = activeTask(3L, 8L); pendingReview.setWorkflowStatus(LabConstants.WORKFLOW_PENDING_REVIEW);
        pendingReview.setPeriodLockFlag("1"); tasks.put(pendingReview);
        TaskSubmitCommand review = new TaskSubmitCommand(); review.setReviewerComment("return");
        assertThrows(ServiceException.class, () -> service.reviewReturn(3L, 0, review, 9L));

        LabTask confirmed = activeTask(4L, 8L); confirmed.setWorkflowStatus(LabConstants.WORKFLOW_CONFIRMED);
        confirmed.setPeriodLockFlag("1"); tasks.put(confirmed);
        assertThrows(ServiceException.class, () -> service.reopenTask(4L, 0, "correction", 9L));

        LabTask blocked = activeTask(5L, 8L); blocked.setPeriodLockFlag("1"); blocked.setBlockFlag("1"); tasks.put(blocked);
        LabTaskBlockEvent event = new LabTaskBlockEvent(); event.setId(91L); event.setTaskId(5L); event.setBlockStatus("OPEN"); tasks.events.put(91L, event);
        assertThrows(ServiceException.class, () -> service.unblockTask(5L, 0, "done", 8L));

        LabTask lockedGateTask = activeTask(6L, 8L); lockedGateTask.setWorkflowStatus(LabConstants.WORKFLOW_CONFIRMED);
        lockedGateTask.setPeriodLockFlag("1"); tasks.put(lockedGateTask);
        LabTaskQualityGate gate = new LabTaskQualityGate(); gate.setId(96L); gate.setTaskId(6L); gate.setGateStatus("PENDING"); tasks.gates.put(96L, gate);
        LabTaskEvidence approved = validEvidence(6L); approved.setId(97L); approved.setAuditStatus("APPROVED"); evidence.put(approved);
        assertThrows(ServiceException.class, () -> service.passQualityGate(96L, 97L, "locked", 9L));
    }

    @Test
    void activatedTaskWeightsAndStructuralLinksAreImmutable() {
        goals.put(goal(1L, 0L, "YEAR", 2026, null));
        goals.put(goal(2L, 1L, "QUARTER", 2026, "2026Q3"));
        LabTask stored = activeTask(1L, 8L); stored.setGoalId(1L); stored.setMilestoneId(2L); tasks.put(stored);
        LabTask edit = activeTask(1L, 8L); edit.setGoalId(1L); edit.setMilestoneId(2L);
        edit.setPerfWeight(new BigDecimal("90"));

        assertThrows(ServiceException.class, () -> service.updateTask(edit, 8L));
        assertEquals(new BigDecimal("100"), tasks.find(1L).getPerfWeight());
    }

    @Test
    void activatedTaskCannotChangeParentLevelBusinessScopeOrType() {
        goals.put(goal(1L, 0L, "YEAR", 2026, null));
        goals.put(goal(2L, 1L, "QUARTER", 2026, "2026Q3"));
        LabTask parent = activeTask(10L, 8L); parent.setGoalId(1L); parent.setMilestoneId(2L); tasks.put(parent);
        LabTask alternateParent = activeTask(11L, 8L); alternateParent.setGoalId(1L); alternateParent.setMilestoneId(2L); tasks.put(alternateParent);
        LabTask stored = task(1L, 10L, "week", "2026-W32", 8L, "0", "0");
        stored.setGoalId(1L); stored.setMilestoneId(2L); stored.setWorkflowStatus(LabConstants.WORKFLOW_ACTIVE); tasks.put(stored);
        LabTask edit = task(1L, 11L, "week", "2026-W32", 8L, "0", "0");
        edit.setGoalId(1L); edit.setMilestoneId(2L); edit.setWorkflowStatus(LabConstants.WORKFLOW_ACTIVE);

        assertThrows(ServiceException.class, () -> service.updateTask(edit, 8L));
    }

    @Test
    void onlyTheWeeklyOwnerOrManagerCanAttachACommitmentToAnotherOwnersMonth() {
        goals.put(goal(1L, 0L, "YEAR", 2026, null));
        goals.put(goal(2L, 1L, "QUARTER", 2026, "2026Q3"));
        LabTask parent = activeTask(10L, 12L); parent.setGoalId(1L); parent.setMilestoneId(2L); tasks.put(parent);
        LabTask week = task(null, 10L, "week", "2026-W32", 13L, "0", "0");
        week.setGoalId(1L); week.setMilestoneId(2L);

        assertThrows(ServiceException.class, () -> service.createTask(week, 3L));
        assertThrows(ServiceException.class, () -> service.createTask(week, 2L));
        service.createTask(week, 9L);
        assertNotNull(week.getId());

        LabTask crossLineParent = activeTask(11L, 15L); crossLineParent.setBizLine("platform");
        crossLineParent.setGoalId(1L); crossLineParent.setMilestoneId(2L); tasks.put(crossLineParent);
        LabTask crossLineWeek = task(null, 11L, "week", "2026-W32", 13L, "0", "0");
        crossLineWeek.setGoalId(1L); crossLineWeek.setMilestoneId(2L);
        assertThrows(ServiceException.class, () -> service.createTask(crossLineWeek, 2L));
    }

    @Test
    void taskWeightsMustStayWithinZeroAndOneHundred() {
        goals.put(goal(1L, 0L, "YEAR", 2026, null));
        goals.put(goal(2L, 1L, "QUARTER", 2026, "2026Q3"));
        LabTask task = task(null, 0L, "month", "2026-08", 8L, "101", "100");
        task.setGoalId(1L); task.setMilestoneId(2L);

        assertThrows(ServiceException.class, () -> service.createTask(task, 8L));
    }

    @Test
    void monthWithWeeklyChildrenCannotChangeInheritedLinks() {
        goals.put(goal(1L, 0L, "YEAR", 2026, null));
        goals.put(goal(2L, 1L, "QUARTER", 2026, "2026Q3"));
        goals.put(goal(3L, 1L, "QUARTER", 2026, "2026Q3"));
        LabTask month = task(1L, 0L, "month", "2026-08", 8L, "100", "100"); month.setGoalId(1L); month.setMilestoneId(2L); tasks.put(month);
        LabTask week = task(2L, 1L, "week", "2026-W32", 8L, "0", "0"); week.setGoalId(1L); week.setMilestoneId(2L); tasks.put(week);
        LabTask edit = task(1L, 0L, "month", "2026-08", 8L, "100", "100"); edit.setGoalId(1L); edit.setMilestoneId(3L);

        assertThrows(ServiceException.class, () -> service.updateTask(edit, 8L));
    }

    @Test
    void activeMilestoneFreezesKeyMonthMembershipAndOnlyDraftTasksCanBeDeleted() {
        goals.put(goal(1L, 0L, "YEAR", 2026, null));
        LabGoal milestone = goal(2L, 1L, "QUARTER", 2026, "2026Q3");
        milestone.setStatus("ACTIVE");
        goals.put(milestone);

        LabTask newKeyMonth = task(null, 0L, "month", "2026-08", 8L, "0", "0");
        newKeyMonth.setGoalId(1L); newKeyMonth.setMilestoneId(2L);
        assertThrows(ServiceException.class, () -> service.createTask(newKeyMonth, 8L));

        LabTask active = activeTask(10L, 8L); active.setGoalId(1L); active.setMilestoneId(2L); tasks.put(active);
        assertThrows(ServiceException.class, () -> service.deleteTask(10L, 0, 8L));

        LabTask draft = task(11L, 0L, "month", "2026-08", 8L, "0", "0");
        draft.setGoalId(1L); draft.setMilestoneId(2L); tasks.put(draft);
        assertThrows(ServiceException.class, () -> service.deleteTask(11L, 0, 8L));
    }

    @Test
    void monthTasksCannotHaveTaskParentsAndWeeklyChildrenFreezeAllHierarchyFields() {
        goals.put(goal(1L, 0L, "YEAR", 2026, null));
        goals.put(goal(2L, 1L, "QUARTER", 2026, "2026Q3"));
        LabTask invalidMonth = task(null, 99L, "month", "2026-08", 8L, "0", "0");
        invalidMonth.setGoalId(1L); invalidMonth.setMilestoneId(2L);
        assertThrows(ServiceException.class, () -> service.createTask(invalidMonth, 8L));

        LabTask month = task(10L, 0L, "month", "2026-08", 8L, "0", "0");
        month.setGoalId(1L); month.setMilestoneId(2L); month.setTaskType("daily"); tasks.put(month);
        LabTask child = task(11L, 10L, "week", "2026-W32", 8L, "0", "0");
        child.setGoalId(1L); child.setMilestoneId(2L); tasks.put(child);
        LabTask alternateParent = task(12L, 0L, "month", "2026-08", 8L, "0", "0");
        alternateParent.setGoalId(1L); alternateParent.setMilestoneId(2L); alternateParent.setTaskType("daily"); tasks.put(alternateParent);
        LabTask converted = task(10L, 12L, "week", "2026-W32", 8L, "0", "0");
        converted.setGoalId(1L); converted.setMilestoneId(2L);

        assertThrows(ServiceException.class, () -> service.updateTask(converted, 8L));
    }

    @Test
    void managerCannotPassQualityGateForOwnTask() {
        LabTask own = activeTask(10L, 9L); own.setWorkflowStatus(LabConstants.WORKFLOW_CONFIRMED); tasks.put(own);
        LabTaskQualityGate gate = new LabTaskQualityGate(); gate.setId(80L); gate.setTaskId(10L); gate.setGateStatus("PENDING"); tasks.gates.put(80L, gate);
        LabTaskEvidence approved = validEvidence(10L); approved.setId(81L); approved.setAuditStatus("APPROVED"); evidence.put(approved);

        assertThrows(ServiceException.class, () -> service.passQualityGate(80L, 81L, "self review", 9L));
    }

    @Test
    void activationMapperSqlUsesParentAndWeightCollectionLocks() throws Exception {
        Path root = Paths.get("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("ruoyi-lab/src/main/resources/mapper/lab/LabTaskMapper.xml"))) root = root.getParent();
        String goalXml = new String(Files.readAllBytes(root.resolve("ruoyi-lab/src/main/resources/mapper/lab/LabGoalMapper.xml")), StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ").toLowerCase();
        String taskXml = new String(Files.readAllBytes(root.resolve("ruoyi-lab/src/main/resources/mapper/lab/LabTaskMapper.xml")), StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ").toLowerCase();

        assertTrue(goalXml.contains("id=\"selectgoalforupdate\"") && goalXml.contains("for update"));
        assertTrue(goalXml.contains("id=\"selectchildrenbyparentidforupdate\"") && goalXml.contains("for update"));
        assertTrue(taskXml.contains("id=\"selectkeymonthtasksbymilestoneidforupdate\"") && taskXml.contains("for update"));
        assertTrue(taskXml.contains("id=\"selecttasksbymilestoneidforupdate\"") && taskXml.contains("for update"));
        assertTrue(taskXml.contains("id=\"selecttasksbygoalormilestoneforupdate\"") && taskXml.contains("for update"));
        assertTrue(taskXml.contains("id=\"selectkeymonthtasksbyownerperiodforupdate\"") && taskXml.contains("for update"));
        assertTrue(taskXml.contains("id=\"lockmemberforupdate\"") && taskXml.contains("for update"));
        assertTrue(taskXml.contains("<select id=\"lockmemberforupdate\" parametertype=\"long\" resulttype=\"string\">select biz_line from lab_member"),
                "member lock must return the current business line from the locking read");
        assertTrue(taskXml.contains("id=\"selecttaskforupdate\"") && taskXml.contains("for update"));
        assertTrue(taskXml.contains("id=\"selecttasksbyparentidforupdate\"") && taskXml.contains("for update"));
    }

    @Test
    void everyMonthlyTaskWriteLocksItsMilestoneMembershipRow() {
        goals.put(goal(1L, 0L, "YEAR", 2026, null));
        goals.put(goal(2L, 1L, "QUARTER", 2026, "2026Q3"));
        LabTask daily = task(null, 0L, "month", "2026-08", 8L, "0", "0");
        daily.setGoalId(1L); daily.setMilestoneId(2L); daily.setTaskType("daily");

        service.createTask(daily, 9L);
        assertEquals(Arrays.asList(1L, 2L), goals.lockedGoalIds);

        goals.lockedGoalIds.clear();
        daily.setTitle("updated daily month");
        service.updateTask(daily, 9L);
        assertEquals(Arrays.asList(1L, 2L), goals.lockedGoalIds);

        goals.lockedGoalIds.clear();
        service.deleteTask(daily.getId(), daily.getVersion(), 9L);
        assertEquals(Arrays.asList(1L, 2L), goals.lockedGoalIds);
    }

    @Test
    void monthUpdateValidatesAgainstLockedCurrentGoalRowsInsteadOfOldSnapshots() {
        goals.put(goal(1L, 0L, "YEAR", 2026, null));
        goals.put(goal(2L, 1L, "QUARTER", 2026, "2026Q3"));
        goals.put(goal(3L, 1L, "QUARTER", 2026, "2026Q3"));
        goals.lockedOverrides.put(3L, goal(3L, 1L, "QUARTER", 2026, "2026Q4"));
        LabTask stored = task(10L, 0L, "month", "2026-08", 8L, "0", "0");
        stored.setGoalId(1L); stored.setMilestoneId(2L); stored.setTaskType("daily"); tasks.put(stored);
        LabTask movedUsingStaleQuarter = task(10L, 0L, "month", "2026-08", 8L, "0", "0");
        movedUsingStaleQuarter.setGoalId(1L); movedUsingStaleQuarter.setMilestoneId(3L); movedUsingStaleQuarter.setTaskType("daily");

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.updateTask(movedUsingStaleQuarter, 9L));

        assertEquals("Month task period must belong to its quarterly milestone", error.getMessage());
    }

    @Test
    void weeklyUpdateValidatesAgainstTheLockedCurrentParentTask() {
        goals.put(goal(1L, 0L, "YEAR", 2026, null));
        goals.put(goal(2L, 1L, "QUARTER", 2026, "2026Q3"));
        LabTask oldParent = task(10L, 0L, "month", "2026-08", 8L, "0", "0");
        oldParent.setGoalId(1L); oldParent.setMilestoneId(2L); oldParent.setTaskType("daily"); tasks.put(oldParent);
        LabTask staleNewParent = task(12L, 0L, "month", "2026-08", 8L, "0", "0");
        staleNewParent.setGoalId(1L); staleNewParent.setMilestoneId(2L); staleNewParent.setTaskType("daily"); tasks.put(staleNewParent);
        LabTask currentNewParent = task(12L, 0L, "month", "2026-08", 8L, "0", "0");
        currentNewParent.setGoalId(1L); currentNewParent.setMilestoneId(2L); currentNewParent.setTaskType("daily");
        currentNewParent.setBizLine("platform"); tasks.lockedOverrides.put(12L, currentNewParent);
        LabTask storedWeek = task(11L, 10L, "week", "2026-W32", 8L, "0", "0");
        storedWeek.setGoalId(1L); storedWeek.setMilestoneId(2L); tasks.put(storedWeek);
        LabTask movedWeek = task(11L, 12L, "week", "2026-W32", 8L, "0", "0");
        movedWeek.setGoalId(1L); movedWeek.setMilestoneId(2L);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.updateTask(movedWeek, 8L));

        assertEquals("Weekly task must use its month task business line", error.getMessage());
    }

    @Test
    void invalidCallerDeclaredGoalTypesAreRejectedBeforeGoalLocks() {
        goals.put(goal(1L, 0L, "YEAR", 2026, null));
        goals.put(goal(2L, 1L, "QUARTER", 2026, "2026Q3"));
        LabTask malicious = task(null, 0L, "month", "2026-08", 8L, "0", "0");
        malicious.setGoalId(2L); malicious.setMilestoneId(1L); malicious.setTaskType("daily");

        assertThrows(ServiceException.class, () -> service.createTask(malicious, 8L));

        assertTrue(goals.lockedGoalIds.isEmpty(), "unvalidated row types must not define a lock phase");
    }

    @Test
    void monthTaskCannotBecomeItsOwnWeeklyParent() {
        goals.put(goal(1L, 0L, "YEAR", 2026, null));
        goals.put(goal(2L, 1L, "QUARTER", 2026, "2026Q3"));
        LabTask stored = task(10L, 0L, "month", "2026-08", 8L, "0", "0");
        stored.setGoalId(1L); stored.setMilestoneId(2L); stored.setTaskType("daily"); tasks.put(stored);
        LabTask selfParent = task(10L, 10L, "week", "2026-W32", 8L, "0", "0");
        selfParent.setGoalId(1L); selfParent.setMilestoneId(2L);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.updateTask(selfParent, 9L));

        assertEquals("Task cannot be its own parent", error.getMessage());
        assertEquals(0L, tasks.find(10L).getParentId());
    }

    @Test
    void taskWritesUseLockedCurrentOwnersInSortedOrder() {
        goals.put(goal(1L, 0L, "YEAR", 2026, null));
        goals.put(goal(2L, 1L, "QUARTER", 2026, "2026Q3"));
        tasks.memberLines.put(8L, "algorithm");
        tasks.lockedMemberLines.put(8L, "platform");
        LabTask daily = task(null, 0L, "month", "2026-08", 8L, "0", "0");
        daily.setGoalId(1L); daily.setMilestoneId(2L); daily.setTaskType("daily");

        ServiceException staleCreate = assertThrows(ServiceException.class,
                () -> service.createTask(daily, 8L));
        assertEquals("Task business line must match the active owner's responsible scope", staleCreate.getMessage());
        assertEquals(Arrays.asList(8L), tasks.lockedMemberIds);

        tasks.lockedMemberIds.clear();
        tasks.lockedMemberLines.clear();
        LabTask stored = task(10L, 0L, "month", "2026-08", 9L, "0", "0");
        stored.setGoalId(1L); stored.setMilestoneId(2L); stored.setTaskType("daily"); tasks.put(stored);
        LabTask changedOwner = task(10L, 0L, "month", "2026-08", 8L, "0", "0");
        changedOwner.setGoalId(1L); changedOwner.setMilestoneId(2L); changedOwner.setTaskType("daily");

        service.updateTask(changedOwner, 9L);

        assertEquals(Arrays.asList(8L, 9L), tasks.lockedMemberIds);
    }

    @Test
    void weeklyMembershipAndMonthWritesUseTheCommonMonthParentLock() {
        goals.put(goal(1L, 0L, "YEAR", 2026, null));
        goals.put(goal(2L, 1L, "QUARTER", 2026, "2026Q3"));
        LabTask month = task(10L, 0L, "month", "2026-08", 8L, "0", "0");
        month.setGoalId(1L); month.setMilestoneId(2L); month.setTaskType("daily"); tasks.put(month);
        LabTask week = task(null, 10L, "week", "2026-W32", 8L, "0", "0");
        week.setGoalId(1L); week.setMilestoneId(2L);

        service.createTask(week, 8L);
        assertTrue(tasks.lockedTaskIds.contains(10L));

        tasks.lockedTaskIds.clear();
        tasks.lockedChildrenParentIds.clear();
        LabTask edit = task(10L, 0L, "month", "2026-08", 8L, "0", "0");
        edit.setGoalId(1L); edit.setMilestoneId(2L); edit.setTaskType("daily"); edit.setTitle("content-only edit");
        service.updateTask(edit, 9L);
        assertTrue(tasks.lockedTaskIds.contains(10L));
        assertTrue(tasks.lockedChildrenParentIds.contains(10L));
    }

    @Test
    void activatedDefinitionChangesRequireAReasonAndAppendAnAuditEvent() {
        goals.put(goal(1L, 0L, "YEAR", 2026, null));
        goals.put(goal(2L, 1L, "QUARTER", 2026, "2026Q3"));
        LabTask stored = activeTask(10L, 8L);
        stored.setGoalId(1L); stored.setMilestoneId(2L); stored.setTaskType("daily"); tasks.put(stored);
        LabTask edit = activeTask(10L, 8L);
        edit.setGoalId(1L); edit.setMilestoneId(2L); edit.setTaskType("daily"); edit.setTitle("clarified outcome");

        assertThrows(ServiceException.class, () -> service.updateTask(edit, 9L));

        edit.setRemark("scope clarified after the weekly review");
        assertEquals(1, service.updateTask(edit, 9L));
        org.mockito.Mockito.verify(workflowEvents).append(
                org.mockito.ArgumentMatchers.eq(edit),
                org.mockito.ArgumentMatchers.eq(LabConstants.WORKFLOW_ACTIVE),
                org.mockito.ArgumentMatchers.eq(LabConstants.WORKFLOW_ACTIVE),
                org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.eq("DEFINITION_CHANGE"),
                org.mockito.ArgumentMatchers.eq("scope clarified after the weekly review"));
    }

    @Test
    void activatedWeeklyDefinitionChangesUseTheAppendOnlyAuditLog() {
        goals.put(goal(1L, 0L, "YEAR", 2026, null));
        goals.put(goal(2L, 1L, "QUARTER", 2026, "2026Q3"));
        LabTask parent = activeTask(10L, 8L);
        parent.setGoalId(1L); parent.setMilestoneId(2L); parent.setTaskType("daily"); tasks.put(parent);
        LabTask stored = task(11L, 10L, "week", "2026-W32", 8L, "0", "0");
        stored.setGoalId(1L); stored.setMilestoneId(2L); stored.setWorkflowStatus(LabConstants.WORKFLOW_ACTIVE);
        stored.setResultStatus(LabConstants.RESULT_DOING); tasks.put(stored);
        LabTask edit = task(11L, 10L, "week", "2026-W32", 8L, "0", "0");
        edit.setGoalId(1L); edit.setMilestoneId(2L); edit.setWorkflowStatus(LabConstants.WORKFLOW_ACTIVE);
        edit.setResultStatus(LabConstants.RESULT_DOING); edit.setTitle("clarified weekly outcome");
        edit.setRemark("commitment scope clarified");

        assertEquals(1, service.updateTask(edit, 8L));
        org.mockito.Mockito.verify(workflowEvents).append(
                org.mockito.ArgumentMatchers.eq(edit),
                org.mockito.ArgumentMatchers.eq(LabConstants.WORKFLOW_ACTIVE),
                org.mockito.ArgumentMatchers.eq(LabConstants.WORKFLOW_ACTIVE),
                org.mockito.ArgumentMatchers.eq(8L),
                org.mockito.ArgumentMatchers.eq("DEFINITION_CHANGE"),
                org.mockito.ArgumentMatchers.eq("commitment scope clarified"));
    }

    private static LabGoal goal(Long id, Long parent, String level, int year, String period) {
        LabGoal goal = new LabGoal(); goal.setId(id); goal.setParentId(parent); goal.setGoalLevel(level);
        goal.setYear(year); goal.setPeriod(period); goal.setOwnerId(8L); goal.setDelFlag("0"); return goal;
    }

    private static LabTask activeTask(Long id, Long owner) {
        LabTask task = task(id, 0L, "month", "2026-08", owner, "100", "100");
        task.setWorkflowStatus(LabConstants.WORKFLOW_ACTIVE); task.setResultStatus(LabConstants.RESULT_DOING);
        task.setPlanDate(Date.from(Instant.parse("2026-08-20T00:00:00Z"))); task.setDeliverable("artifact");
        return task;
    }

    private static LabTask task(Long id, Long parent, String level, String period, Long owner,
                                String perfWeight, String goalWeight) {
        LabTask task = new LabTask(); task.setId(id); task.setParentId(parent); task.setTaskLevel(level);
        task.setPeriod(period); task.setOwnerId(owner); task.setPerfWeight(new BigDecimal(perfWeight));
        task.setGoalWeight(new BigDecimal(goalWeight)); task.setTaskType("month".equals(level) ? "key" : "daily");
        task.setTitle("task"); task.setBizLine("algorithm"); task.setDeptId(101L); task.setDeliverable("artifact");
        task.setPlanDate(Date.from(Instant.parse("2026-08-20T00:00:00Z")));
        task.setWorkflowStatus(LabConstants.WORKFLOW_DRAFT); task.setResultStatus(LabConstants.RESULT_DOING);
        task.setVersion(0); task.setDelFlag("0"); task.setPeriodLockFlag("0"); task.setCoordinationRequired("0");
        task.setBlockFlag("0"); return task;
    }

    private static LabTaskEvidence validEvidence(Long taskId) {
        LabTaskEvidence item = new LabTaskEvidence(); item.setTaskId(taskId); item.setEvidenceType("URL");
        item.setEvidenceTitle("result"); item.setEvidenceUrl("https://example.invalid/result"); item.setDelFlag("0"); return item;
    }

    static final class MemoryGoalMapper implements LabGoalMapper {
        final Map<Long, LabGoal> data = new LinkedHashMap<Long, LabGoal>();
        final Map<Long, LabGoal> lockedOverrides = new LinkedHashMap<Long, LabGoal>();
        final List<Long> lockedGoalIds = new ArrayList<Long>();
        void put(LabGoal goal) { data.put(goal.getId(), goal); }
        @Override public List<LabGoal> selectGoalList(LabGoal q) { return new ArrayList<LabGoal>(data.values()); }
        @Override public LabGoal selectGoalById(Long id) { return data.get(id); }
        @Override public LabGoal selectGoalForUpdate(Long id) { lockedGoalIds.add(id); LabGoal current = lockedOverrides.get(id); return current == null ? selectGoalById(id) : current; }
        @Override public List<LabGoal> selectChildrenByParentId(Long id) { return new ArrayList<LabGoal>(); }
        @Override public List<LabGoal> selectChildrenByParentIdForUpdate(Long id) { return selectChildrenByParentId(id); }
        @Override public int insertGoal(LabGoal goal) { return 1; }
        @Override public int updateGoal(LabGoal goal) { return 1; }
        @Override public int deleteGoal(Long id, Integer version, String actor) { return 1; }
    }

    static final class MemoryEvidenceMapper implements LabTaskEvidenceMapper {
        final Map<Long, LabTaskEvidence> data = new LinkedHashMap<Long, LabTaskEvidence>(); long seq = 30L;
        boolean rejectInsert;
        void put(LabTaskEvidence item) { data.put(item.getId(), item); }
        LabTaskEvidence find(Long id) { return data.get(id); }
        List<LabTaskEvidence> forTask(Long id) { List<LabTaskEvidence> result = new ArrayList<LabTaskEvidence>(); for (LabTaskEvidence e : data.values()) if (id.equals(e.getTaskId()) && !"2".equals(e.getDelFlag())) result.add(e); return result; }
        @Override public List<LabTaskEvidence> selectEvidenceByTaskId(Long taskId) { return forTask(taskId); }
        @Override public LabTaskEvidence selectEvidenceById(Long id) { return data.get(id); }
        @Override public int insertEvidence(LabTaskEvidence item) { if (rejectInsert) return 0; if (item.getId() == null) item.setId(++seq); data.put(item.getId(), item); return 1; }
        @Override public int deleteEvidence(Long id, Long taskId, String updateBy) { LabTaskEvidence e = data.get(id); if (e == null || !taskId.equals(e.getTaskId()) || !"PENDING".equals(e.getAuditStatus())) return 0; e.setDelFlag("2"); return 1; }
        @Override public int approveEvidence(Long id, Long taskId, Long auditorId, Date auditTime, String auditComment, String updateBy) { LabTaskEvidence e = data.get(id); if (e == null || !taskId.equals(e.getTaskId()) || !"PENDING".equals(e.getAuditStatus())) return 0; e.setAuditStatus("APPROVED"); e.setAuditorId(auditorId); e.setAuditTime(auditTime); e.setAuditComment(auditComment); return 1; }
    }

    static final class MemoryTaskMapper implements LabTaskMapper {
        final Map<Long, LabTask> data = new LinkedHashMap<Long, LabTask>();
        final Map<Long, LabTaskQualityGate> gates = new LinkedHashMap<Long, LabTaskQualityGate>();
        final Map<Long, LabTaskBlockEvent> events = new LinkedHashMap<Long, LabTaskBlockEvent>();
        final Map<Long, LabTask> lockedOverrides = new LinkedHashMap<Long, LabTask>();
        final List<Long> lockedTaskIds = new ArrayList<Long>();
        final List<Long> lockedChildrenParentIds = new ArrayList<Long>();
        final Map<Long, Long> memberIds = new LinkedHashMap<Long, Long>();
        final Map<Long, String> memberLines = new LinkedHashMap<Long, String>();
        final Map<Long, String> lockedMemberLines = new LinkedHashMap<Long, String>();
        final List<Long> lockedMemberIds = new ArrayList<Long>();
        long seq = 50L, gateSeq = 70L, eventSeq = 90L;
        boolean rejectGateWrite;
        Long pagedTotal;
        LabTask lastQuery;
        void put(LabTask task) { data.put(task.getId(), task); }
        LabTask find(Long id) { return data.get(id); }
        LabTaskQualityGate gate(Long id) { return gates.get(id); }
        @Override public Long selectMemberIdByUserId(Long userId) { Long memberId = memberIds.get(userId); return memberId == null ? userId : memberId; }
        @Override public String selectMemberBizLineById(Long memberId) { String line = memberLines.get(memberId); return line == null ? "algorithm" : line; }
        @Override public List<LabTask> selectTaskList(LabTask query) {
            lastQuery = query;
            List<LabTask> result = new ArrayList<LabTask>();
            for (LabTask task : data.values()) if (!"2".equals(task.getDelFlag())
                    && (query.getOwnerId() == null || query.getOwnerId().equals(task.getOwnerId()))
                    && (query.getBizLine() == null || query.getBizLine().equals(task.getBizLine()))) result.add(task);
            Page<?> request = PageHelper.getLocalPage();
            if (pagedTotal == null || request == null) return result;
            Page<LabTask> page = new Page<LabTask>(request.getPageNum(), request.getPageSize());
            page.setTotal(pagedTotal);
            int from = Math.min((request.getPageNum() - 1) * request.getPageSize(), result.size());
            int to = Math.min(from + request.getPageSize(), result.size());
            page.addAll(result.subList(from, to));
            PageHelper.clearPage();
            return page;
        }
        @Override public LabTask selectTaskById(Long id) { LabTask t = data.get(id); return t == null || "2".equals(t.getDelFlag()) ? null : t; }
        @Override public LabTask selectTaskForUpdate(Long id) { lockedTaskIds.add(id); LabTask current = lockedOverrides.get(id); return current == null ? selectTaskById(id) : current; }
        @Override public LabTask selectCarriedTask(Long carriedFromId, String period) { for (LabTask row : data.values()) if (carriedFromId.equals(row.getCarriedFromId()) && period.equals(row.getPeriod()) && !"2".equals(row.getDelFlag())) return row; return null; }
        @Override public List<LabTask> selectTasksByParentId(Long parentId) { List<LabTask> r = new ArrayList<LabTask>(); for (LabTask t : data.values()) if (parentId.equals(t.getParentId()) && !"2".equals(t.getDelFlag())) r.add(t); return r; }
        @Override public List<LabTask> selectTasksByParentIdForUpdate(Long parentId) { lockedChildrenParentIds.add(parentId); return selectTasksByParentId(parentId); }
        @Override public List<LabTask> selectKeyMonthTasksByMilestoneId(Long id) { return new ArrayList<LabTask>(); }
        @Override public List<LabTask> selectKeyMonthTasksByMilestoneIdForUpdate(Long id) { return selectKeyMonthTasksByMilestoneId(id); }
        @Override public List<LabTask> selectTasksByMilestoneIdForUpdate(Long id) { List<LabTask> result = new ArrayList<LabTask>(); for (LabTask task : data.values()) if (id.equals(task.getMilestoneId()) && !"2".equals(task.getDelFlag())) result.add(task); return result; }
        @Override public List<LabTask> selectTasksByGoalOrMilestoneForUpdate(Long id) { List<LabTask> result = new ArrayList<LabTask>(); for (LabTask task : data.values()) if ((id.equals(task.getGoalId()) || id.equals(task.getMilestoneId())) && !"2".equals(task.getDelFlag())) result.add(task); return result; }
        @Override public int countTasksByMilestoneId(Long id) { int count = 0; for (LabTask task : data.values()) if (id.equals(task.getMilestoneId()) && !"2".equals(task.getDelFlag())) count++; return count; }
        @Override public List<LabTask> selectKeyMonthTasksByOwnerPeriod(Long ownerId, String period) { List<LabTask> r = new ArrayList<LabTask>(); for (LabTask t : data.values()) if (ownerId.equals(t.getOwnerId()) && period.equals(t.getPeriod()) && "month".equals(t.getTaskLevel()) && "key".equals(t.getTaskType()) && !"2".equals(t.getDelFlag())) r.add(t); return r; }
        @Override public List<LabTask> selectKeyMonthTasksByOwnerPeriodForUpdate(Long ownerId, String period) { return selectKeyMonthTasksByOwnerPeriod(ownerId, period); }
        @Override public String lockMemberForUpdate(Long memberId) { lockedMemberIds.add(memberId); String current = lockedMemberLines.containsKey(memberId) ? lockedMemberLines.get(memberId) : selectMemberBizLineById(memberId); return "INACTIVE".equals(current) ? null : current; }
        @Override public int insertTask(LabTask task) { if (task.getId() == null) task.setId(++seq); data.put(task.getId(), task); return 1; }
        @Override public int updateTask(LabTask task) { LabTask stored = data.get(task.getId()); if (stored == null || !stored.getVersion().equals(task.getVersion())) return 0; task.setVersion(task.getVersion() + 1); data.put(task.getId(), task); return 1; }
        @Override public int deleteTask(Long id, Integer version, String actor) { LabTask t = data.get(id); if (t == null || !t.getVersion().equals(version)) return 0; t.setDelFlag("2"); t.setVersion(version + 1); return 1; }
        @Override public List<LabTaskQualityGate> selectQualityGates(Long taskId) { List<LabTaskQualityGate> r = new ArrayList<LabTaskQualityGate>(); for (LabTaskQualityGate g : gates.values()) if (taskId.equals(g.getTaskId()) && !"2".equals(g.getDelFlag())) r.add(g); return r; }
        @Override public LabTaskQualityGate selectQualityGateById(Long id) { return gates.get(id); }
        @Override public int insertQualityGate(LabTaskQualityGate gate) { if (rejectGateWrite) return 0; if (gate.getId() == null) gate.setId(++gateSeq); gates.put(gate.getId(), gate); return 1; }
        @Override public int updateQualityGate(LabTaskQualityGate gate) { if (rejectGateWrite) return 0; gates.put(gate.getId(), gate); return 1; }
        @Override public int deleteQualityGate(Long id, String actor) { if (rejectGateWrite) return 0; LabTaskQualityGate g = gates.get(id); if (g == null) return 0; g.setDelFlag("2"); return 1; }
        @Override public int markQualityGatePassed(Long id, Long evidenceId, Long checker, Date at, String result, String actor) { if (rejectGateWrite) return 0; LabTaskQualityGate g = gates.get(id); if (g == null || !"PENDING".equals(g.getGateStatus())) return 0; g.setGateStatus("PASSED"); g.setEvidenceId(evidenceId); g.setCheckerId(checker); g.setCheckTime(at); g.setCheckResult(result); return 1; }
        @Override public LabTaskBlockEvent selectOpenBlockEvent(Long taskId) { for (LabTaskBlockEvent e : events.values()) if (taskId.equals(e.getTaskId()) && "OPEN".equals(e.getBlockStatus())) return e; return null; }
        @Override public List<LabTaskBlockEvent> selectBlockEvents(Long taskId) { List<LabTaskBlockEvent> r = new ArrayList<LabTaskBlockEvent>(); for (LabTaskBlockEvent e : events.values()) if (taskId.equals(e.getTaskId())) r.add(e); return r; }
        @Override public Integer selectNextBlockEpisodeNo(Long taskId) { int next = 1; for (LabTaskBlockEvent e : events.values()) if (taskId.equals(e.getTaskId()) && e.getEpisodeNo() != null) next = Math.max(next, e.getEpisodeNo() + 1); return next; }
        @Override public int insertBlockEvent(LabTaskBlockEvent event) { if (event.getId() == null) event.setId(++eventSeq); events.put(event.getId(), event); return 1; }
        @Override public int closeBlockEvent(Long id, Long resolver, Date at, String resolution, String actor) { LabTaskBlockEvent e = events.get(id); if (e == null || !"OPEN".equals(e.getBlockStatus())) return 0; e.setBlockStatus("CLOSED"); e.setResolverId(resolver); e.setBlockEndTime(at); e.setResolution(resolution); return 1; }
    }

    static final class MemoryAccessMapper implements LabAccessMapper {
        final Map<Long, LabAccessContext> contexts = new LinkedHashMap<Long, LabAccessContext>();
        boolean consumePage;
        int eligibleReviewerCount = 1;
        void put(Long userId, Long memberId, String roleKey, String bizLine) { LabAccessContext value = new LabAccessContext(); value.setUserId(userId); value.setMemberId(memberId); value.setRoleKey(roleKey); value.setBizLine(bizLine); value.setDeptId(101L); contexts.put(userId, value); }
        @Override public LabAccessContext selectAccessContext(Long userId) {
            if (consumePage && PageHelper.getLocalPage() != null) PageHelper.clearPage();
            return contexts.get(userId);
        }
        @Override public int countEligibleReviewers(Long ownerId, String bizLine) { return eligibleReviewerCount; }
    }

    static final class ExposedTaskController extends LabTaskController {
        ExposedTaskController(LabTaskService service) { super(service); }
        TableDataInfo table(List<?> rows) { return getDataTable(rows); }
    }
}
