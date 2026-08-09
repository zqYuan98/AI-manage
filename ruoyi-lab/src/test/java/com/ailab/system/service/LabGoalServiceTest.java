package com.ailab.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailab.system.constant.LabConstants;
import com.ailab.system.controller.LabGoalController;
import com.ailab.system.domain.LabGoal;
import com.ailab.system.domain.LabTask;
import com.ailab.system.dto.LabAccessContext;
import com.ailab.system.mapper.LabAccessMapper;
import com.ailab.system.mapper.LabGoalMapper;
import com.ailab.system.mapper.LabTaskMapper;
import com.ailab.system.service.impl.LabGoalServiceImpl;
import com.ailab.system.service.impl.LabAccessServiceImpl;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.annotation.DataScope;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;

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
    void goalActivationRequiresDraftLifecycleAndManagerRole() {
        String[] closedStatuses = {"ACTIVE", "COMPLETED", "TERMINATED"};
        for (int i = 0; i < closedStatuses.length; i++) {
            long goalId = 20L + i;
            LabGoal milestone = goal(goalId, 1L, "QUARTER", 2026, "2026Q1", 10L, "100");
            milestone.setStatus(closedStatuses[i]);
            goals.put(milestone);
            tasks.put(month(30L + i, goalId, "2026-01", "100", "100",
                    LabConstants.WORKFLOW_DRAFT, LabConstants.RESULT_DOING));

            ServiceException error = assertThrows(ServiceException.class,
                    () -> service.activateGoal(goalId, 0, 99L));
            assertEquals("Only draft goals can be activated", error.getMessage());
        }

        LabGoal leadOwnedMilestone = goal(40L, 1L, "QUARTER", 2026, "2026Q2", 10L, "100");
        goals.put(leadOwnedMilestone);
        tasks.put(month(41L, 40L, "2026-04", "100", "100",
                LabConstants.WORKFLOW_DRAFT, LabConstants.RESULT_DOING));

        ServiceException roleError = assertThrows(ServiceException.class,
                () -> service.activateGoal(40L, 0, 2L));
        assertEquals("Manager role is required", roleError.getMessage());
    }

    @Test
    void goalActivationControllerUsesItsOwnPermission() throws Exception {
        PreAuthorize permission = LabGoalController.class
                .getMethod("activate", Long.class, Integer.class)
                .getAnnotation(PreAuthorize.class);

        assertEquals("@ss.hasPermi('lab:goal:activate')", permission.value());
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

        goals.put(goal(1L, 0L, "YEAR", 2026, null, 99L, "100"));
        for (Long actorId : new Long[] {99L, 2L, 3L}) {
            LabGoal query = new LabGoal();
            query.getParams().put("dataScope", " AND 1=0 /* untrusted goal scope */");
            assertEquals(1, service.listGoals(query, actorId).size());
            assertFalse(goals.lastQuery.getParams().containsKey("dataScope"));
        }
    }

    @Test
    void dashboardRiskGoalIdsBindThroughGetAndRemainAnExactTypedGoalListFilter() {
        LabGoal query = new LabGoal();
        BeanWrapper requestBinder = new BeanWrapperImpl(query);
        requestBinder.setAutoGrowNestedPaths(true);
        requestBinder.setPropertyValue("goalIdsFilter", Boolean.TRUE);
        requestBinder.setPropertyValue("goalIds[0]", 11L);
        requestBinder.setPropertyValue("goalIds[1]", 12L);

        service.listGoals(query, 99L);

        assertEquals(Boolean.TRUE, requestBinder.getPropertyValue("goalIdsFilter"));
        assertEquals(java.util.Arrays.asList(11L, 12L), requestBinder.getPropertyValue("goalIds"));
        assertEquals(query, goals.lastQuery);
    }

    @Test
    void goalMapperNeverExpandsDynamicSqlFragments() throws Exception {
        Path root = Paths.get("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("ruoyi-lab/src/main/resources/mapper/lab/LabGoalMapper.xml"))) {
            root = root.getParent();
        }
        String xml = new String(Files.readAllBytes(root.resolve("ruoyi-lab/src/main/resources/mapper/lab/LabGoalMapper.xml")),
                StandardCharsets.UTF_8);

        assertFalse(xml.contains("${"), "goal queries must never expand caller-provided SQL fragments");
        assertFalse(xml.toLowerCase().contains("datascope"), "goal reads are intentionally global for all lab roles");
        String compact = xml.toLowerCase().replaceAll("\\s+", "");
        assertTrue(compact.contains("goalidsfilter") && compact.contains("collection=\"goalids\"")
                && compact.contains("#{goalid}") && compact.contains("<otherwise>and1=0</otherwise>"),
                "empty and non-empty risk-goal id filters must both reproduce the dashboard set exactly");
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
    void draftAnnualStructureIsFrozenOnceItHasQuarterMilestonesButContentRemainsEditable() {
        LabGoal annual = goal(1L, 0L, "YEAR", 2026, null, 99L, "100");
        goals.put(annual);
        goals.put(goal(2L, 1L, "QUARTER", 2026, "2026Q3", 10L, "100"));
        LabGoal changedYear = goal(1L, 0L, "YEAR", 2027, null, 99L, "100");

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.updateGoal(changedYear, 99L));
        assertEquals("Goal structure is immutable after dependent records exist", error.getMessage());

        LabGoal contentEdit = goal(1L, 0L, "YEAR", 2026, null, 99L, "100");
        contentEdit.setTitle("updated annual content");
        service.updateGoal(contentEdit, 99L);
        assertEquals("updated annual content", goals.find(1L).getTitle());
    }

    @Test
    void draftQuarterStructureIsFrozenOnceItHasMonthlyTasksButContentRemainsEditable() {
        goals.put(goal(1L, 0L, "YEAR", 2026, null, 99L, "100"));
        LabGoal quarter = goal(2L, 1L, "QUARTER", 2026, "2026Q3", 10L, "100");
        goals.put(quarter);
        LabTask linked = month(11L, 2L, "2026-08", "0", "0",
                LabConstants.WORKFLOW_DRAFT, LabConstants.RESULT_DOING);
        linked.setTaskType("daily");
        tasks.put(linked);
        LabGoal changedOwner = goal(2L, 1L, "QUARTER", 2026, "2026Q3", 99L, "100");

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.updateGoal(changedOwner, 99L));
        assertEquals("Goal structure is immutable after dependent records exist", error.getMessage());

        LabGoal contentEdit = goal(2L, 1L, "QUARTER", 2026, "2026Q3", 10L, "100");
        contentEdit.setTitle("updated milestone content");
        service.updateGoal(contentEdit, 99L);
        assertEquals("updated milestone content", goals.find(2L).getTitle());
    }

    @Test
    void goalUpdateValidatesAgainstTheLockedCurrentParentInsteadOfAnOldSnapshot() {
        goals.put(goal(1L, 0L, "YEAR", 2026, null, 99L, "100"));
        goals.put(goal(2L, 1L, "QUARTER", 2026, "2026Q3", 10L, "100"));
        goals.put(goal(3L, 0L, "YEAR", 2026, null, 99L, "100"));
        goals.lockedOverrides.put(3L, goal(3L, 0L, "YEAR", 2027, null, 99L, "100"));
        LabGoal reparentedUsingStaleYear = goal(2L, 3L, "QUARTER", 2026, "2026Q3", 10L, "100");

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.updateGoal(reparentedUsingStaleYear, 99L));

        assertEquals("Quarter milestone must use the parent annual goal year", error.getMessage());
    }

    @Test
    void invalidRequestedParentIsRejectedBeforeTakingAnyGoalLock() {
        goals.put(goal(1L, 0L, "YEAR", 2026, null, 99L, "100"));
        goals.put(goal(2L, 1L, "QUARTER", 2026, "2026Q1", 10L, "50"));
        goals.put(goal(3L, 1L, "QUARTER", 2026, "2026Q2", 10L, "50"));
        LabGoal cyclicRequest = goal(2L, 3L, "QUARTER", 2026, "2026Q1", 10L, "50");

        assertThrows(ServiceException.class, () -> service.updateGoal(cyclicRequest, 99L));

        assertTrue(goals.lockedGoalIds.isEmpty(), "invalid caller-declared parent types must not influence lock order");
    }

    @Test
    void goalLevelIsImmutableSoPreLockTypeValidationCannotGoStale() {
        goals.put(goal(1L, 0L, "YEAR", 2026, null, 99L, "100"));
        goals.put(goal(2L, 0L, "YEAR", 2026, null, 99L, "100"));
        LabGoal converted = goal(1L, 2L, "QUARTER", 2026, "2026Q3", 99L, "100");

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.updateGoal(converted, 99L));

        assertEquals("Goal level is immutable after creation", error.getMessage());
        assertTrue(goals.lockedGoalIds.isEmpty());
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

    @Test
    void goalDeleteUsesLockingCurrentReadsForChildrenAndConnectedTasks() {
        LabGoal annual = goal(1L, 0L, "YEAR", 2026, null, 99L, "100");
        goals.put(annual);
        goals.lockedChildrenOverrides.put(1L,
                java.util.Arrays.asList(goal(2L, 1L, "QUARTER", 2026, "2026Q1", 10L, "100")));

        ServiceException childError = assertThrows(ServiceException.class,
                () -> service.deleteGoal(1L, 0, 99L));
        assertEquals("Delete child milestones before deleting the goal", childError.getMessage());

        goals.lockedChildrenOverrides.clear();
        tasks.lockedGoalTaskOverrides.put(1L,
                java.util.Arrays.asList(month(11L, 2L, "2026-01", "0", "0",
                        LabConstants.WORKFLOW_DRAFT, LabConstants.RESULT_DOING)));
        ServiceException taskError = assertThrows(ServiceException.class,
                () -> service.deleteGoal(1L, 0, 99L));
        assertEquals("Delete connected tasks before deleting the goal", taskError.getMessage());
        assertEquals("0", goals.find(1L).getDelFlag());
    }

    @Test
    void goalCreateAndUpdateRequireTheLockedCurrentOwnerToBeActive() {
        tasks.inactiveOwnerIds.add(42L);
        LabGoal invalidCreate = goal(null, 0L, "YEAR", 2026, null, 42L, "100");

        ServiceException createError = assertThrows(ServiceException.class,
                () -> service.createGoal(invalidCreate, 99L));
        assertEquals("Goal owner is not an active lab member", createError.getMessage());

        LabGoal stored = goal(1L, 0L, "YEAR", 2026, null, 10L, "100");
        goals.put(stored);
        tasks.inactiveOwnerIds.add(10L);
        LabGoal edit = goal(1L, 0L, "YEAR", 2026, null, 10L, "100");
        edit.setTitle("must not persist");

        ServiceException updateError = assertThrows(ServiceException.class,
                () -> service.updateGoal(edit, 99L));
        assertEquals("Goal owner is not an active lab member", updateError.getMessage());
        assertEquals("goal", goals.find(1L).getTitle());
    }

    @Test
    void progressRejectsWrongGoalLevelsAndUnknownControllerLevel() {
        goals.put(goal(1L, 0L, "YEAR", 2026, null, 99L, "100"));
        goals.put(goal(2L, 1L, "QUARTER", 2026, "2026Q1", 10L, "100"));

        ServiceException milestoneError = assertThrows(ServiceException.class,
                () -> service.calculateMilestoneProgress(1L, 99L));
        assertEquals("Milestone progress requires a QUARTER goal", milestoneError.getMessage());
        assertThrows(ServiceException.class, () -> service.calculateAnnualProgress(2L, 99L));

        SysUser user = new SysUser(); user.setUserId(99L); user.setDeptId(101L);
        LoginUser login = new LoginUser(99L, 101L, user, java.util.Collections.singleton("lab:goal:list"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(login, null));
        try {
            ServiceException levelError = assertThrows(ServiceException.class,
                    () -> new LabGoalController(service).progress(1L, "MONTH"));
            assertEquals("Goal progress level must be YEAR or QUARTER", levelError.getMessage());
        } finally {
            SecurityContextHolder.clearContext();
        }
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
        private final Map<Long, LabGoal> lockedOverrides = new LinkedHashMap<Long, LabGoal>();
        private final Map<Long, List<LabGoal>> lockedChildrenOverrides = new LinkedHashMap<Long, List<LabGoal>>();
        private final List<Long> lockedGoalIds = new ArrayList<Long>();
        private long sequence = 100L;
        private LabGoal lastQuery;
        void put(LabGoal goal) { data.put(goal.getId(), goal); }
        LabGoal find(Long id) { return data.get(id); }
        @Override public List<LabGoal> selectGoalList(LabGoal query) { lastQuery = query; return new ArrayList<LabGoal>(data.values()); }
        @Override public LabGoal selectGoalById(Long id) { LabGoal value = data.get(id); return value == null || "2".equals(value.getDelFlag()) ? null : value; }
        @Override public LabGoal selectGoalForUpdate(Long id) { lockedGoalIds.add(id); LabGoal current = lockedOverrides.get(id); return current == null ? selectGoalById(id) : current; }
        @Override public List<LabGoal> selectChildrenByParentId(Long parentId) {
            List<LabGoal> result = new ArrayList<LabGoal>();
            for (LabGoal value : data.values()) if (parentId.equals(value.getParentId()) && !"2".equals(value.getDelFlag())) result.add(value);
            return result;
        }
        @Override public List<LabGoal> selectChildrenByParentIdForUpdate(Long parentId) { List<LabGoal> current = lockedChildrenOverrides.get(parentId); return current == null ? selectChildrenByParentId(parentId) : current; }
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
        final Map<Long, List<LabTask>> lockedGoalTaskOverrides = new LinkedHashMap<Long, List<LabTask>>();
        final List<Long> inactiveOwnerIds = new ArrayList<Long>();
        void put(LabTask task) { data.put(task.getId(), task); }
        LabTask find(Long id) { return data.get(id); }
        @Override public Long selectMemberIdByUserId(Long userId) { return userId; }
        @Override public String selectMemberBizLineById(Long memberId) { return "algorithm"; }
        @Override public List<LabTask> selectTaskList(LabTask query) { return new ArrayList<LabTask>(data.values()); }
        @Override public LabTask selectTaskById(Long id) { return data.get(id); }
        @Override public LabTask selectTaskForUpdate(Long id) { return selectTaskById(id); }
        @Override public LabTask selectCarriedTask(Long carriedFromId, String period) { return null; }
        @Override public List<LabTask> selectTasksByParentId(Long parentId) { List<LabTask> result = new ArrayList<LabTask>(); for (LabTask task : data.values()) if (parentId.equals(task.getParentId()) && !"2".equals(task.getDelFlag())) result.add(task); return result; }
        @Override public List<LabTask> selectTasksByParentIdForUpdate(Long parentId) { return selectTasksByParentId(parentId); }
        @Override public List<LabTask> selectKeyMonthTasksByMilestoneId(Long milestoneId) {
            List<LabTask> result = new ArrayList<LabTask>();
            for (LabTask task : data.values()) if (milestoneId.equals(task.getMilestoneId()) && "month".equals(task.getTaskLevel()) && "key".equals(task.getTaskType()) && !"2".equals(task.getDelFlag())) result.add(task);
            return result;
        }
        @Override public List<LabTask> selectKeyMonthTasksByMilestoneIdForUpdate(Long milestoneId) { return selectKeyMonthTasksByMilestoneId(milestoneId); }
        @Override public List<LabTask> selectTasksByMilestoneIdForUpdate(Long milestoneId) { List<LabTask> result = new ArrayList<LabTask>(); for (LabTask task : data.values()) if (milestoneId.equals(task.getMilestoneId()) && !"2".equals(task.getDelFlag())) result.add(task); return result; }
        @Override public int countTasksByMilestoneId(Long milestoneId) { int count = 0; for (LabTask task : data.values()) if (milestoneId.equals(task.getMilestoneId()) && !"2".equals(task.getDelFlag())) count++; return count; }
        @Override public List<LabTask> selectKeyMonthTasksByOwnerPeriod(Long ownerId, String period) { return new ArrayList<LabTask>(); }
        @Override public List<LabTask> selectKeyMonthTasksByOwnerPeriodForUpdate(Long ownerId, String period) { return selectKeyMonthTasksByOwnerPeriod(ownerId, period); }
        @Override public List<LabTask> selectTasksByGoalOrMilestoneForUpdate(Long goalId) { List<LabTask> current = lockedGoalTaskOverrides.get(goalId); return current == null ? new ArrayList<LabTask>() : current; }
        @Override public String lockMemberForUpdate(Long memberId) { return inactiveOwnerIds.contains(memberId) ? null : "algorithm"; }
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
        @Override public int countEligibleReviewers(Long ownerId, String bizLine) { return 1; }
    }
}
