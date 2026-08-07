package com.ailab.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ailab.system.constant.LabConstants;
import com.ailab.system.domain.LabGoal;
import com.ailab.system.domain.LabTask;
import com.ailab.system.mapper.LabGoalMapper;
import com.ailab.system.mapper.LabTaskMapper;
import com.ailab.system.service.impl.LabGoalServiceImpl;
import com.ruoyi.common.exception.ServiceException;
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

    @BeforeEach
    void setUp() {
        goals = new MemoryGoalMapper();
        tasks = new MemoryTaskMapper();
        service = new LabGoalServiceImpl(goals, tasks);
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

        assertEquals(new BigDecimal("25.00"), service.calculateMilestoneProgress(2L));
        assertEquals(new BigDecimal("70.00"), service.calculateAnnualProgress(1L));
        assertEquals(new BigDecimal("20"), tasks.find(13L).getPerfWeight(), "performance weight must not drive goal progress");
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
        @Override public List<LabGoal> selectChildrenByParentId(Long parentId) {
            List<LabGoal> result = new ArrayList<LabGoal>();
            for (LabGoal value : data.values()) if (parentId.equals(value.getParentId()) && !"2".equals(value.getDelFlag())) result.add(value);
            return result;
        }
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
        @Override public List<LabTask> selectTasksByParentId(Long parentId) { return new ArrayList<LabTask>(); }
        @Override public List<LabTask> selectKeyMonthTasksByMilestoneId(Long milestoneId) {
            List<LabTask> result = new ArrayList<LabTask>();
            for (LabTask task : data.values()) if (milestoneId.equals(task.getMilestoneId()) && "month".equals(task.getTaskLevel()) && "key".equals(task.getTaskType()) && !"2".equals(task.getDelFlag())) result.add(task);
            return result;
        }
        @Override public List<LabTask> selectKeyMonthTasksByOwnerPeriod(Long ownerId, String period) { return new ArrayList<LabTask>(); }
        @Override public int insertTask(LabTask task) { return 1; }
        @Override public int updateTask(LabTask task) { return 1; }
        @Override public int deleteTask(Long id, Integer version, String updateBy) { return 1; }
        @Override public List<com.ailab.system.domain.LabTaskQualityGate> selectQualityGates(Long taskId) { return new ArrayList<com.ailab.system.domain.LabTaskQualityGate>(); }
        @Override public com.ailab.system.domain.LabTaskQualityGate selectQualityGateById(Long id) { return null; }
        @Override public int insertQualityGate(com.ailab.system.domain.LabTaskQualityGate gate) { return 1; }
        @Override public int updateQualityGate(com.ailab.system.domain.LabTaskQualityGate gate) { return 1; }
        @Override public int deleteQualityGate(Long id, String updateBy) { return 1; }
        @Override public int markQualityGatePassed(Long id, Long checkerId, java.util.Date checkTime, String checkResult, String updateBy) { return 1; }
        @Override public com.ailab.system.domain.LabTaskBlockEvent selectOpenBlockEvent(Long taskId) { return null; }
        @Override public List<com.ailab.system.domain.LabTaskBlockEvent> selectBlockEvents(Long taskId) { return new ArrayList<com.ailab.system.domain.LabTaskBlockEvent>(); }
        @Override public int insertBlockEvent(com.ailab.system.domain.LabTaskBlockEvent event) { return 1; }
        @Override public int closeBlockEvent(Long id, Long resolverId, java.util.Date endTime, String resolution, String updateBy) { return 1; }
    }
}
