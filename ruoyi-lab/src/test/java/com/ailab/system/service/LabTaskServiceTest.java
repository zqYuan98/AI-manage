package com.ailab.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailab.system.constant.LabConstants;
import com.ailab.system.domain.LabGoal;
import com.ailab.system.domain.LabTask;
import com.ailab.system.domain.LabTaskBlockEvent;
import com.ailab.system.domain.LabTaskEvidence;
import com.ailab.system.domain.LabTaskQualityGate;
import com.ailab.system.dto.TaskSubmitCommand;
import com.ailab.system.exception.LabValidationException;
import com.ailab.system.mapper.LabGoalMapper;
import com.ailab.system.mapper.LabTaskEvidenceMapper;
import com.ailab.system.mapper.LabTaskMapper;
import com.ailab.system.service.impl.LabTaskServiceImpl;
import com.ailab.system.service.impl.TaskWorkflowServiceImpl;
import com.ruoyi.common.exception.ServiceException;
import java.math.BigDecimal;
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

class LabTaskServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-08T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private MemoryTaskMapper tasks;
    private MemoryEvidenceMapper evidence;
    private MemoryGoalMapper goals;
    private LabTaskService service;

    @BeforeEach
    void setUp() {
        tasks = new MemoryTaskMapper(); evidence = new MemoryEvidenceMapper(); goals = new MemoryGoalMapper();
        service = new LabTaskServiceImpl(tasks, evidence, goals, new TaskWorkflowServiceImpl(CLOCK), CLOCK);
    }

    @Test
    void weeklyTaskRequiresMonthParentWithContainedPeriodAndSameGoalLinks() {
        goals.put(goal(1L, 0L, "YEAR", 2026, null));
        goals.put(goal(2L, 1L, "QUARTER", 2026, "2026Q3"));
        LabTask parent = task(10L, 0L, "month", "2026-08", 8L, "40", "60");
        parent.setGoalId(1L); parent.setMilestoneId(2L); tasks.put(parent);
        LabTask weekly = task(null, 10L, "week", "2026-W36", 8L, "0", "0");
        weekly.setGoalId(1L); weekly.setMilestoneId(2L);

        assertThrows(ServiceException.class, () -> service.createTask(weekly, 8L));
        weekly.setPeriod("2026-W32");

        service.createTask(weekly, 8L);
        assertNotNull(weekly.getId());
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

        assertThrows(ServiceException.class, () -> service.activateMonthlyPlan(8L, "2026-08", 8L));
        second.setPerfWeight(new BigDecimal("60"));

        assertEquals(2, service.activateMonthlyPlan(8L, "2026-08", 8L));
        assertEquals(LabConstants.WORKFLOW_ACTIVE, tasks.find(1L).getWorkflowStatus());
        assertEquals(LabConstants.WORKFLOW_ACTIVE, tasks.find(2L).getWorkflowStatus());
        assertEquals(new BigDecimal("90"), second.getGoalWeight(), "goal weight must not drive performance plan activation");
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
    void monthlyProgressUsesOnlyConfirmedWeeklyChildren() {
        LabTask month = task(1L, 0L, "month", "2026-08", 8L, "100", "100"); tasks.put(month);
        LabTask confirmed = task(2L, 1L, "week", "2026-W32", 8L, "0", "0");
        confirmed.setWorkflowStatus(LabConstants.WORKFLOW_CONFIRMED); confirmed.setResultStatus(LabConstants.RESULT_ONTIME);
        LabTask undone = task(3L, 1L, "week", "2026-W32", 8L, "0", "0");
        undone.setWorkflowStatus(LabConstants.WORKFLOW_CONFIRMED); undone.setResultStatus(LabConstants.RESULT_UNDONE);
        LabTask pending = task(4L, 1L, "week", "2026-W32", 8L, "0", "0");
        pending.setWorkflowStatus(LabConstants.WORKFLOW_PENDING_REVIEW); pending.setResultStatus(LabConstants.RESULT_ONTIME);
        tasks.put(confirmed); tasks.put(undone); tasks.put(pending);

        assertEquals(new BigDecimal("33.33"), service.calculateMonthProgress(1L));
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
        LabTaskEvidence pending = validEvidence(1L); pending.setId(21L); pending.setAuditStatus("PENDING"); evidence.put(pending);

        assertThrows(ServiceException.class, () -> service.passQualityGate(gate.getId(), pending.getId(), "ok", 9L));
        pending.setAuditStatus("APPROVED");

        service.passQualityGate(gate.getId(), pending.getId(), "ok", 9L);
        assertEquals("PASSED", tasks.gate(gate.getId()).getGateStatus());
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
        assertThrows(ServiceException.class, () -> service.blockTask(1L, 1, "DEPENDENCY", "again", 8L));

        service.unblockTask(1L, 1, "API ready", 8L);
        assertEquals("0", tasks.find(1L).getBlockFlag());
        assertNull(tasks.find(1L).getBlockStartTime());
        assertEquals("CLOSED", tasks.events.get(opened.getId()).getBlockStatus());
        assertFalse(tasks.selectBlockEvents(1L).isEmpty());
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
        void put(LabGoal goal) { data.put(goal.getId(), goal); }
        @Override public List<LabGoal> selectGoalList(LabGoal q) { return new ArrayList<LabGoal>(data.values()); }
        @Override public LabGoal selectGoalById(Long id) { return data.get(id); }
        @Override public List<LabGoal> selectChildrenByParentId(Long id) { return new ArrayList<LabGoal>(); }
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
        final Map<Long, Long> memberIds = new LinkedHashMap<Long, Long>();
        final Map<Long, String> memberLines = new LinkedHashMap<Long, String>();
        long seq = 50L, gateSeq = 70L, eventSeq = 90L;
        void put(LabTask task) { data.put(task.getId(), task); }
        LabTask find(Long id) { return data.get(id); }
        LabTaskQualityGate gate(Long id) { return gates.get(id); }
        @Override public Long selectMemberIdByUserId(Long userId) { Long memberId = memberIds.get(userId); return memberId == null ? userId : memberId; }
        @Override public String selectMemberBizLineById(Long memberId) { String line = memberLines.get(memberId); return line == null ? "algorithm" : line; }
        @Override public List<LabTask> selectTaskList(LabTask query) { return new ArrayList<LabTask>(data.values()); }
        @Override public LabTask selectTaskById(Long id) { LabTask t = data.get(id); return t == null || "2".equals(t.getDelFlag()) ? null : t; }
        @Override public List<LabTask> selectTasksByParentId(Long parentId) { List<LabTask> r = new ArrayList<LabTask>(); for (LabTask t : data.values()) if (parentId.equals(t.getParentId()) && !"2".equals(t.getDelFlag())) r.add(t); return r; }
        @Override public List<LabTask> selectKeyMonthTasksByMilestoneId(Long id) { return new ArrayList<LabTask>(); }
        @Override public List<LabTask> selectKeyMonthTasksByOwnerPeriod(Long ownerId, String period) { List<LabTask> r = new ArrayList<LabTask>(); for (LabTask t : data.values()) if (ownerId.equals(t.getOwnerId()) && period.equals(t.getPeriod()) && "month".equals(t.getTaskLevel()) && "key".equals(t.getTaskType()) && !"2".equals(t.getDelFlag())) r.add(t); return r; }
        @Override public int insertTask(LabTask task) { if (task.getId() == null) task.setId(++seq); data.put(task.getId(), task); return 1; }
        @Override public int updateTask(LabTask task) { LabTask stored = data.get(task.getId()); if (stored == null || !stored.getVersion().equals(task.getVersion())) return 0; task.setVersion(task.getVersion() + 1); data.put(task.getId(), task); return 1; }
        @Override public int deleteTask(Long id, Integer version, String actor) { LabTask t = data.get(id); if (t == null || !t.getVersion().equals(version)) return 0; t.setDelFlag("2"); t.setVersion(version + 1); return 1; }
        @Override public List<LabTaskQualityGate> selectQualityGates(Long taskId) { List<LabTaskQualityGate> r = new ArrayList<LabTaskQualityGate>(); for (LabTaskQualityGate g : gates.values()) if (taskId.equals(g.getTaskId()) && !"2".equals(g.getDelFlag())) r.add(g); return r; }
        @Override public LabTaskQualityGate selectQualityGateById(Long id) { return gates.get(id); }
        @Override public int insertQualityGate(LabTaskQualityGate gate) { if (gate.getId() == null) gate.setId(++gateSeq); gates.put(gate.getId(), gate); return 1; }
        @Override public int updateQualityGate(LabTaskQualityGate gate) { gates.put(gate.getId(), gate); return 1; }
        @Override public int deleteQualityGate(Long id, String actor) { LabTaskQualityGate g = gates.get(id); if (g == null) return 0; g.setDelFlag("2"); return 1; }
        @Override public int markQualityGatePassed(Long id, Long checker, Date at, String result, String actor) { LabTaskQualityGate g = gates.get(id); if (g == null || !"PENDING".equals(g.getGateStatus())) return 0; g.setGateStatus("PASSED"); g.setCheckerId(checker); g.setCheckTime(at); g.setCheckResult(result); return 1; }
        @Override public LabTaskBlockEvent selectOpenBlockEvent(Long taskId) { for (LabTaskBlockEvent e : events.values()) if (taskId.equals(e.getTaskId()) && "OPEN".equals(e.getBlockStatus())) return e; return null; }
        @Override public List<LabTaskBlockEvent> selectBlockEvents(Long taskId) { List<LabTaskBlockEvent> r = new ArrayList<LabTaskBlockEvent>(); for (LabTaskBlockEvent e : events.values()) if (taskId.equals(e.getTaskId())) r.add(e); return r; }
        @Override public int insertBlockEvent(LabTaskBlockEvent event) { if (event.getId() == null) event.setId(++eventSeq); events.put(event.getId(), event); return 1; }
        @Override public int closeBlockEvent(Long id, Long resolver, Date at, String resolution, String actor) { LabTaskBlockEvent e = events.get(id); if (e == null || !"OPEN".equals(e.getBlockStatus())) return 0; e.setBlockStatus("CLOSED"); e.setResolverId(resolver); e.setBlockEndTime(at); e.setResolution(resolution); return 1; }
    }
}
