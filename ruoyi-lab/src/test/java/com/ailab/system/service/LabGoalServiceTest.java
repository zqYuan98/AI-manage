package com.ailab.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.ailab.system.constant.LabConstants;
import com.ailab.system.domain.LabGoal;
import com.ailab.system.domain.LabTask;
import com.ailab.system.dto.LabAccessContext;
import com.ailab.system.mapper.LabAccessMapper;
import com.ailab.system.mapper.LabGoalMapper;
import com.ailab.system.mapper.LabTaskMapper;
import com.ailab.system.service.impl.LabGoalServiceImpl;
import com.ailab.system.service.impl.LabAccessServiceImpl;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.annotation.DataScope;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LabGoalServiceTest {
    private MemoryGoalMapper goals;
    private MemoryTaskMapper tasks;
    private LabGoalService service;
    private MemoryAccessMapper access;

    @BeforeEach
    void setUp() {
        goals = new MemoryGoalMapper();
        tasks = new MemoryTaskMapper();
        access = new MemoryAccessMapper();
        access.put(99L, 99L, "lab_manager");
        access.put(2L, 10L, "lab_lead");
        access.put(3L, 11L, "lab_member");
        service = new LabGoalServiceImpl(goals, tasks, new LabAccessServiceImpl(access));
    }

    @Test
    void quarterMustBelongToAnnualGoalInSameYearAndOwnerScope() {
        LabGoal annual = goal(1L, 0L, "YEAR", 2026, null, 10L, "100");
        goals.put(annual);
        LabGoal quarter = goal(null, 1L, "QUARTER", 2027, "2027Q1", 10L, "50");

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createGoal(quarter, 99L));

        assertEquals("Quarter milestone must use the parent annual goal year", error.getMessage());
    }

    @Test
    void annualActivationRequiresDirectQuarterWeightsExactlyOneHundred() {
        goals.put(goal(1L, 0L, "YEAR", 2026, null, 10L, "100"));
        goals.put(goal(2L, 1L, "QUARTER", 2026, "2026Q1", 10L, "40"));
        goals.put(goal(3L, 1L, "QUARTER", 2026, "2026Q2", 11L, "50"));

        assertThrows(ServiceException.class, () -> service.activateGoal(1L, 0, 99L));
        goals.find(3L).setWeight(new BigDecimal("60"));

        service.activateGoal(1L, 0, 99L);

        assertEquals("ACTIVE", goals.find(1L).getStatus());
        assertEquals(Integer.valueOf(1), goals.find(1L).getVersion());
    }

    @Test
    void milestoneActivationRequiresDirectKeyMonthGoalWeightsExactlyOneHundred() {
        goals.put(goal(2L, 1L, "QUARTER", 2026, "2026Q3", 10L, "40"));
        tasks.put(month(11L, 2L, "2026-08", "40", "70", LabConstants.WORKFLOW_DRAFT, LabConstants.RESULT_DOING));
        tasks.put(month(12L, 2L, "2026-09", "50", "30", LabConstants.WORKFLOW_DRAFT, LabConstants.RESULT_DOING));

        assertThrows(ServiceException.class, () -> service.activateGoal(2L, 0, 99L));
        tasks.find(12L).setGoalWeight(new BigDecimal("60"));

        service.activateGoal(2L, 0, 99L);

        assertEquals("ACTIVE", goals.find(2L).getStatus());
    }

    @Test
    void milestoneAndAnnualProgressUseGoalWeightAndConfirmedResultsOnly() {
        LabGoal annual = goal(1L, 0L, "YEAR", 2026, null, 10L, "100");
        LabGoal q1 = goal(2L, 1L, "QUARTER", 2026, "2026Q1", 10L, "40");
        LabGoal q2 = goal(3L, 1L, "QUARTER", 2026, "2026Q2", 11L, "60");
        goals.put(annual); goals.put(q1); goals.put(q2);
        tasks.put(month(11L, 2L, "2026-01", "25", "70", LabConstants.WORKFLOW_CONFIRMED, LabConstants.RESULT_EXCEEDED));
        tasks.put(month(12L, 2L, "2026-02", "75", "30", LabConstants.WORKFLOW_CONFIRMED, LabConstants.RESULT_UNDONE));
        tasks.put(month(13L, 3L, "2026-04", "100", "20", LabConstants.WORKFLOW_CONFIRMED, LabConstants.RESULT_DELAYED));

        assertEquals(new BigDecimal("25.00"), service.calculateMilestoneProgress(2L, 99L));
        assertEquals(new BigDecimal("70.00"), service.calculateAnnualProgress(1L, 99L));
        assertEquals(new BigDecimal("20"), tasks.find(13L).getPerfWeight(), "performance weight must not drive goal progress");
    }

    @Test
    void unconfirmedWeeklyTasksAreExcludedFromMonthMilestoneAndAnnualDenominators() {
        LabGoal annual = goal(1L, 0L, "YEAR", 2026, null, 10L, "100");
        LabGoal quarter = goal(2L, 1L, "QUARTER", 2026, "2026Q3", 10L, "100");
        goals.put(annual); goals.put(quarter);
        LabTask month = month(11L, 2L, "2026-08", "100", "100", LabConstants.WORKFLOW_ACTIVE, LabConstants.RESULT_DOING);
        tasks.put(month);
        LabTask confirmed = month(12L, 2L, "2026-08", "0", "0", LabConstants.WORKFLOW_CONFIRMED, LabConstants.RESULT_ONTIME);
        confirmed.setTaskLevel("week"); confirmed.setTaskType("daily"); confirmed.setParentId(11L); tasks.put(confirmed);
        LabTask pending = month(13L, 2L, "2026-08", "0", "0", LabConstants.WORKFLOW_PENDING_REVIEW, LabConstants.RESULT_ONTIME);
        pending.setTaskLevel("week"); pending.setTaskType("daily"); pending.setParentId(11L); tasks.put(pending);

        assertEquals(new BigDecimal("100.00"), service.calculateMilestoneProgress(2L, 99L));
        assertEquals(new BigDecimal("100.00"), service.calculateAnnualProgress(1L, 99L));
    }

    @Test
    void goalWritesAreEnforcedByTrustedActorRoleAndOwnership() {
        LabGoal annual = goal(1L, 0L, "YEAR", 2026, null, 99L, "100"); goals.put(annual);
        LabGoal ownQuarter = goal(null, 1L, "QUARTER", 2026, "2026Q3", 10L, "100");
        service.createGoal(ownQuarter, 2L);

        LabGoal annualAttempt = goal(null, 0L, "YEAR", 2027, null, 10L, "100");
        assertThrows(ServiceException.class, () -> service.createGoal(annualAttempt, 2L));
        LabGoal memberQuarter = goal(null, 1L, "QUARTER", 2026, "2026Q4", 11L, "100");
        assertThrows(ServiceException.class, () -> service.createGoal(memberQuarter, 3L));
    }

    @Test
    void goalReadsAreNotNarrowedByTaskDepartmentDataScope() throws Exception {
        assertNull(LabGoalServiceImpl.class.getMethod("listGoals", LabGoal.class, Long.class)
                .getAnnotation(DataScope.class));
        assertNull(LabGoalServiceImpl.class.getMethod("goalTree", LabGoal.class, Long.class)
                .getAnnotation(DataScope.class));
    }

    @Test
    void activeGoalWeightsCannotBeChangedAndMilestoneWithTasksCannotBeDeleted() {
        LabGoal annual = goal(1L, 0L, "YEAR", 2026, null, 99L, "100"); annual.setStatus("ACTIVE"); goals.put(annual);
        LabGoal quarter = goal(2L, 1L, "QUARTER", 2026, "2026Q3", 10L, "100"); quarter.setStatus("ACTIVE"); goals.put(quarter);
        LabGoal edit = goal(2L, 1L, "QUARTER", 2026, "2026Q3", 10L, "90");

        assertThrows(ServiceException.class, () -> service.updateGoal(edit, 99L));

        quarter.setStatus("DRAFT");
        LabTask linked = month(11L, 2L, "2026-08", "100", "100", LabConstants.WORKFLOW_DRAFT, LabConstants.RESULT_DOING);
        linked.setTaskType("daily"); tasks.put(linked);
        assertThrows(ServiceException.class, () -> service.deleteGoal(2L, 0, 99L));
    }

    @Test
    void activeGoalCannotBeDeletedThroughGenericDelete() {
        LabGoal quarter = goal(2L, 1L, "QUARTER", 2026, "2026Q3", 10L, "100");
        quarter.setStatus("ACTIVE"); goals.put(quarter);

        assertThrows(ServiceException.class, () -> service.deleteGoal(2L, 0, 99L));
    }

    @Test
    void activatedGoalCannotChangeHierarchyPeriodOrOwnership() {
        LabGoal annual = goal(1L, 0L, "YEAR", 2026, null, 99L, "100"); annual.setStatus("ACTIVE"); goals.put(annual);
        LabGoal quarter = goal(2L, 1L, "QUARTER", 2026, "2026Q3", 10L, "100"); quarter.setStatus("ACTIVE"); goals.put(quarter);
        LabGoal edit = goal(2L, 1L, "QUARTER", 2026, "2026Q4", 10L, "100");

        assertThrows(ServiceException.class, () -> service.updateGoal(edit, 99L));
    }

    @Test
    void activeAnnualGoalFreezesItsQuarterMilestoneMembership() {
        LabGoal annual = goal(1L, 0L, "YEAR", 2026, null, 99L, "100");
        annual.setStatus("ACTIVE");
        goals.put(annual);

        LabGoal newQuarter = goal(null, 1L, "QUARTER", 2026, "2026Q4", 99L, "0");
        assertThrows(ServiceException.class, () -> service.createGoal(newQuarter, 99L));

        LabGoal existingDraft = goal(2L, 1L, "QUARTER", 2026, "2026Q3", 99L, "0");
        goals.put(existingDraft);
        assertThrows(ServiceException.class, () -> service.deleteGoal(2L, 0, 99L));

        LabGoal otherAnnual = goal(3L, 0L, "YEAR", 2026, null, 99L, "100");
        goals.put(otherAnnual);
        LabGoal reparented = goal(2L, 3L, "QUARTER", 2026, "2026Q3", 99L, "0");
        assertThrows(ServiceException.class, () -> service.updateGoal(reparented, 99L));
    }

    @Test
    void completedAndTerminatedGoalsRemainFrozenLikeActiveGoals() {
        LabGoal completedAnnual = goal(1L, 0L, "YEAR", 2026, null, 99L, "100");
        completedAnnual.setStatus("COMPLETED"); goals.put(completedAnnual);
        LabGoal draftQuarter = goal(2L, 1L, "QUARTER", 2026, "2026Q3", 99L, "40"); goals.put(draftQuarter);
        LabGoal changedChildWeight = goal(2L, 1L, "QUARTER", 2026, "2026Q3", 99L, "50");
        assertThrows(ServiceException.class, () -> service.updateGoal(changedChildWeight, 99L));

        LabGoal draftAnnual = goal(3L, 0L, "YEAR", 2026, null, 99L, "100"); goals.put(draftAnnual);
        LabGoal terminatedQuarter = goal(4L, 3L, "QUARTER", 2026, "2026Q4", 99L, "60");
        terminatedQuarter.setStatus("TERMINATED"); goals.put(terminatedQuarter);
        LabGoal changedClosedWeight = goal(4L, 3L, "QUARTER", 2026, "2026Q4", 99L, "70");
        assertThrows(ServiceException.class, () -> service.updateGoal(changedClosedWeight, 99L));
    }

    @Test
    void optimisticConflictDoesNotOverwriteGoal() {
        LabGoal stored = goal(1L, 0L, "YEAR", 2026, null, 10L, "100");
        stored.setVersion(3);
        goals.put(stored);
        LabGoal edit = goal(1L, 0L, "YEAR", 2026, null, 10L, "100");
        edit.setVersion(2);

        assertThrows(ServiceException.class, () -> service.updateGoal(edit, 99L));
        assertEquals(Integer.valueOf(3), goals.find(1L).getVersion());
    }

    private static LabGoal goal(Long id, Long parentId, String level, int year, String period,
                                Long ownerId, String weight) {
        LabGoal goal = new LabGoal();
        goal.setId(id); goal.setParentId(parentId); goal.setGoalLevel(level); goal.setYear(year);
        goal.setPeriod(period); goal.setGoalNo("G-" + (id == null ? "NEW" : id)); goal.setTitle("goal");
        goal.setOwnerId(ownerId); goal.setWeight(new BigDecimal(weight)); goal.setStatus("DRAFT");
        goal.setVersion(0); goal.setDelFlag("0");
        return goal;
    }

    private static LabTask month(Long id, Long milestoneId, String period, String goalWeight,
                                 String perfWeight, String workflow, String result) {
        LabTask task = new LabTask();
        task.setId(id); task.setParentId(0L); task.setGoalId(1L); task.setMilestoneId(milestoneId);
        task.setTaskLevel("month"); task.setTaskType("key"); task.setPeriod(period);
        task.setGoalWeight(new BigDecimal(goalWeight)); task.setPerfWeight(new BigDecimal(perfWeight));
        task.setWorkflowStatus(workflow); task.setResultStatus(result); task.setDelFlag("0");
        return task;
    }

    static final class MemoryGoalMapper implements LabGoalMapper {
        private final Map<Long, LabGoal> data = new LinkedHashMap<Long, LabGoal>();
        private long sequence = 100L;
        void put(LabGoal goal) { data.put(goal.getId(), goal); }
        LabGoal find(Long id) { return data.get(id); }
        @Override public List<LabGoal> selectGoalList(LabGoal query) { return new ArrayList<LabGoal>(data.values()); }
        @Override public LabGoal selectGoalById(Long id) { LabGoal value = data.get(id); return value == null || "2".equals(value.getDelFlag()) ? null : value; }
        @Override public LabGoal selectGoalForUpdate(Long id) { return selectGoalById(id); }
        @Override public List<LabGoal> selectChildrenByParentId(Long parentId) {
            List<LabGoal> result = new ArrayList<LabGoal>();
            for (LabGoal value : data.values()) if (parentId.equals(value.getParentId()) && !"2".equals(value.getDelFlag())) result.add(value);
            return result;
        }
        @Override public List<LabGoal> selectChildrenByParentIdForUpdate(Long parentId) { return selectChildrenByParentId(parentId); }
        @Override public int insertGoal(LabGoal goal) { if (goal.getId() == null) goal.setId(++sequence); data.put(goal.getId(), goal); return 1; }
        @Override public int updateGoal(LabGoal goal) {
            LabGoal stored = data.get(goal.getId());
            if (stored == null || !stored.getVersion().equals(goal.getVersion())) return 0;
            goal.setVersion(goal.getVersion() + 1); data.put(goal.getId(), goal); return 1;
        }
        @Override public int deleteGoal(Long id, Integer version, String updateBy) {
            LabGoal stored = data.get(id); if (stored == null || !stored.getVersion().equals(version)) return 0;
            stored.setDelFlag("2"); stored.setVersion(version + 1); return 1;
        }
    }

    static class MemoryTaskMapper implements LabTaskMapper {
        final Map<Long, LabTask> data = new LinkedHashMap<Long, LabTask>();
        void put(LabTask task) { data.put(task.getId(), task); }
        LabTask find(Long id) { return data.get(id); }
        @Override public Long selectMemberIdByUserId(Long userId) { return userId; }
        @Override public String selectMemberBizLineById(Long memberId) { return "algorithm"; }
        @Override public List<LabTask> selectTaskList(LabTask query) { return new ArrayList<LabTask>(data.values()); }
        @Override public LabTask selectTaskById(Long id) { return data.get(id); }
        @Override public LabTask selectTaskForUpdate(Long id) { return selectTaskById(id); }
        @Override public List<LabTask> selectTasksByParentId(Long parentId) { List<LabTask> result = new ArrayList<LabTask>(); for (LabTask task : data.values()) if (parentId.equals(task.getParentId()) && !"2".equals(task.getDelFlag())) result.add(task); return result; }
        @Override public List<LabTask> selectTasksByParentIdForUpdate(Long parentId) { return selectTasksByParentId(parentId); }
        @Override public List<LabTask> selectKeyMonthTasksByMilestoneId(Long milestoneId) {
            List<LabTask> result = new ArrayList<LabTask>();
            for (LabTask task : data.values()) if (milestoneId.equals(task.getMilestoneId()) && "month".equals(task.getTaskLevel()) && "key".equals(task.getTaskType()) && !"2".equals(task.getDelFlag())) result.add(task);
            return result;
        }
        @Override public List<LabTask> selectKeyMonthTasksByMilestoneIdForUpdate(Long milestoneId) { return selectKeyMonthTasksByMilestoneId(milestoneId); }
        @Override public int countTasksByMilestoneId(Long milestoneId) { int count = 0; for (LabTask task : data.values()) if (milestoneId.equals(task.getMilestoneId()) && !"2".equals(task.getDelFlag())) count++; return count; }
        @Override public List<LabTask> selectKeyMonthTasksByOwnerPeriod(Long ownerId, String period) { return new ArrayList<LabTask>(); }
        @Override public List<LabTask> selectKeyMonthTasksByOwnerPeriodForUpdate(Long ownerId, String period) { return selectKeyMonthTasksByOwnerPeriod(ownerId, period); }
        @Override public Long lockMemberForUpdate(Long memberId) { return memberId; }
        @Override public int insertTask(LabTask task) { return 1; }
        @Override public int updateTask(LabTask task) { return 1; }
        @Override public int deleteTask(Long id, Integer version, String updateBy) { return 1; }
        @Override public List<com.ailab.system.domain.LabTaskQualityGate> selectQualityGates(Long taskId) { return new ArrayList<com.ailab.system.domain.LabTaskQualityGate>(); }
        @Override public com.ailab.system.domain.LabTaskQualityGate selectQualityGateById(Long id) { return null; }
        @Override public int insertQualityGate(com.ailab.system.domain.LabTaskQualityGate gate) { return 1; }
        @Override public int updateQualityGate(com.ailab.system.domain.LabTaskQualityGate gate) { return 1; }
        @Override public int deleteQualityGate(Long id, String updateBy) { return 1; }
        @Override public int markQualityGatePassed(Long id, Long evidenceId, Long checkerId, java.util.Date checkTime, String checkResult, String updateBy) { return 1; }
        @Override public com.ailab.system.domain.LabTaskBlockEvent selectOpenBlockEvent(Long taskId) { return null; }
        @Override public List<com.ailab.system.domain.LabTaskBlockEvent> selectBlockEvents(Long taskId) { return new ArrayList<com.ailab.system.domain.LabTaskBlockEvent>(); }
        @Override public Integer selectNextBlockEpisodeNo(Long taskId) { return 1; }
        @Override public int insertBlockEvent(com.ailab.system.domain.LabTaskBlockEvent event) { return 1; }
        @Override public int closeBlockEvent(Long id, Long resolverId, java.util.Date endTime, String resolution, String updateBy) { return 1; }
    }

    static final class MemoryAccessMapper implements LabAccessMapper {
        final Map<Long, LabAccessContext> contexts = new LinkedHashMap<Long, LabAccessContext>();
        void put(Long userId, Long memberId, String roleKey) { LabAccessContext value = new LabAccessContext(); value.setUserId(userId); value.setMemberId(memberId); value.setRoleKey(roleKey); value.setBizLine("algorithm"); value.setDeptId(101L); contexts.put(userId, value); }
        @Override public LabAccessContext selectAccessContext(Long userId) { return contexts.get(userId); }
    }
}
