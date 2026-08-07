package com.ailab.system.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailab.system.domain.LabGoal;
import com.ailab.system.domain.LabTask;
import com.ailab.system.domain.LabTaskEvidence;
import com.ailab.system.dto.LabAccessContext;
import com.ailab.system.dto.TaskSubmitCommand;
import com.ailab.system.service.LabAccessService;
import com.ailab.system.service.LabGoalService;
import com.ailab.system.service.LabTaskService;
import com.ruoyi.RuoYiApplication;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.service.ISysUserService;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real MySQL 8 mapper integration. This class deliberately ends in IT so normal
 * Surefire unit-test discovery does not run it without an explicitly prepared database.
 */
@SpringBootTest(classes = RuoYiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("lab-it")
@ContextConfiguration(initializers = LabMapperMySqlIT.DatabaseInitializer.class)
@Transactional
class LabMapperMySqlIT {
    @Autowired private LabGoalMapper goalMapper;
    @Autowired private LabTaskMapper taskMapper;
    @Autowired private LabTaskEvidenceMapper evidenceMapper;
    @Autowired private LabGoalService goalService;
    @Autowired private LabTaskService taskService;
    @Autowired private LabAccessService accessService;
    @Autowired private ISysUserService userService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    @Test
    void filtersByOwnerBusinessLinePeriodAndFrameworkDataScopeFragment() {
        LabTask query = new LabTask();
        query.setOwnerId(39202L); query.setBizLine("algorithm"); query.setPeriod("2026-01");
        query.getParams().put("dataScope", " AND t.dept_id = 101 ");

        List<LabTask> rows = taskMapper.selectTaskList(query);

        assertEquals(1, rows.size());
        assertEquals(Long.valueOf(39001L), rows.get(0).getId());
        query.getParams().put("dataScope", " AND t.dept_id = 999 ");
        assertEquals(0, taskMapper.selectTaskList(query).size());
    }

    @Test
    void mapsAllResultTypesAndAggregatesConfirmedResults() {
        LabTask task = taskMapper.selectTaskById(39001L);
        LabTaskEvidence evidence = evidenceMapper.selectEvidenceById(39001L);

        assertEquals("algorithm", taskMapper.lockMemberForUpdate(39203L),
                "member locking read must return the current active business line");
        assertEquals(new BigDecimal("70.00"), task.getPerfWeight());
        assertEquals(new BigDecimal("25.00"), task.getGoalWeight());
        assertTrue(evidence.getEvidenceJson().contains("integration"));
        assertEquals("PASSED", taskMapper.selectQualityGateById(39001L).getGateStatus());
        assertEquals(Long.valueOf(39001L), taskMapper.selectQualityGateById(39001L).getEvidenceId());
        assertEquals("OPEN", taskMapper.selectOpenBlockEvent(39004L).getBlockStatus());
        assertEquals(Integer.valueOf(1), taskMapper.selectOpenBlockEvent(39004L).getEpisodeNo());
        assertEquals(new BigDecimal("25.00"), goalService.calculateMilestoneProgress(39002L, 39101L));
        assertEquals(new BigDecimal("70.00"), goalService.calculateAnnualProgress(39001L, 39101L));
        assertEquals(new BigDecimal("100.00"), taskService.calculateMonthProgress(39004L, 39101L),
                "pending weeks must not enter either side of the live progress denominator");
    }

    @Test
    void realSecurityContextRolesDriveDataScopeAndServiceObjectAuthorization() {
        assertThrows(ServiceException.class, () -> accessService.context(30001L), "disabled demo account must not resolve");
        LabAccessContext manager = accessService.context(39101L);
        LabAccessContext lead = accessService.context(39102L);
        LabAccessContext member = accessService.context(39103L);
        assertEquals("lab_manager", manager.getRoleKey());
        assertEquals("lab_lead", lead.getRoleKey());
        assertEquals("lab_member", member.getRoleKey());
        assertEquals(Long.valueOf(39203L), member.getMemberId());
        assertFalse(member.getUserId().equals(member.getMemberId()), "IT must keep system-user and member identity spaces distinct");

        List<LabTask> managerRows = asUser(39101L, () -> taskService.listTasks(new LabTask(), 39101L));
        List<LabTask> leadRows = asUser(39102L, () -> taskService.listTasks(new LabTask(), 39102L));
        List<LabTask> memberRows = asUser(39103L, () -> taskService.listTasks(new LabTask(), 39103L));
        assertTrue(managerRows.size() > leadRows.size());
        assertTrue(leadRows.stream().allMatch(task -> "algorithm".equals(task.getBizLine())));
        assertTrue(memberRows.stream().allMatch(task -> Long.valueOf(39203L).equals(task.getOwnerId())));
        assertTrue(memberRows.stream().anyMatch(task -> Long.valueOf(39007L).equals(task.getId())));

        LabTask own = taskService.getTask(39007L, 39103L); own.setTitle("member updated through real service");
        assertEquals(1, taskService.updateTask(own, 39103L));
        assertEquals("member updated through real service", taskMapper.selectTaskById(39007L).getTitle());
        assertThrows(ServiceException.class, () -> taskService.getTask(39003L, 39102L));

        LabTask attemptedOtherOwner = newTask(39202L, "member cannot assign the lead");
        assertThrows(ServiceException.class, () -> taskService.createTask(attemptedOtherOwner, 39103L));
        LabTask ownNew = newTask(39203L, "member creates own task");
        assertEquals(1, taskService.createTask(ownNew, 39103L));
        LabTaskEvidence attributed = new LabTaskEvidence(); attributed.setEvidenceType("URL"); attributed.setEvidenceTitle("IT proof");
        attributed.setEvidenceUrl("https://example.invalid/it/member-proof");
        taskService.addEvidence(ownNew.getId(), attributed, 39103L);
        assertEquals(Long.valueOf(39203L), attributed.getSubmitterId());

        TaskSubmitCommand returned = new TaskSubmitCommand(); returned.setReviewerComment("return through real service");
        taskService.reviewReturn(39008L, 0, returned, 39102L);
        assertEquals("ACTIVE", taskMapper.selectTaskById(39008L).getWorkflowStatus());
        assertThrows(ServiceException.class, () -> taskService.reviewReturn(39009L, 0, returned, 39102L));
        taskService.reopenTask(39001L, 0, "manager correction", 39101L);
        assertEquals("ACTIVE", taskMapper.selectTaskById(39001L).getWorkflowStatus());

        LabGoal ownQuarter = goalMapper.selectGoalById(39002L);
        accessService.requireGoalWrite(ownQuarter, 39102L);
        assertThrows(ServiceException.class,
                () -> accessService.requireGoalWrite(goalMapper.selectGoalById(39001L), 39102L));
        assertThrows(ServiceException.class,
                () -> accessService.requireGoalWrite(goalMapper.selectGoalById(39003L), 39102L));
        assertThrows(ServiceException.class, () -> accessService.requireGoalWrite(ownQuarter, 39103L));
        int managerGoals = goalService.listGoals(new LabGoal(), 39101L).size();
        for (Long actorId : new Long[] {39102L, 39103L}) {
            LabGoal unrestricted = new LabGoal();
            unrestricted.getParams().put("dataScope", " AND 1=0 /* goal scope must be ignored */");
            assertEquals(managerGoals, goalService.listGoals(unrestricted, actorId).size());
            assertFalse(unrestricted.getParams().containsKey("dataScope"));
        }
    }

    @Test
    void gateMapperRejectsWritesAfterTaskBecomesPeriodLocked() {
        com.ailab.system.domain.LabTaskQualityGate gate = new com.ailab.system.domain.LabTaskQualityGate();
        gate.setTaskId(39007L); gate.setGateNo("IT-LOCK"); gate.setGateName("period lock race guard");
        gate.setGateStatus("PENDING"); gate.setDelFlag("0"); gate.setCreateBy("it");
        assertEquals(1, taskMapper.insertQualityGate(gate));
        assertEquals(1, jdbcTemplate.update("update lab_task set period_lock_flag='1' where id=39007"));
        gate.setGateName("must not update"); gate.setUpdateBy("it");
        assertEquals(0, taskMapper.updateQualityGate(gate));
        assertEquals(0, taskMapper.deleteQualityGate(gate.getId(), "it"));
    }

    @Test
    void gatePassSqlRequiresApprovedSameTaskEvidenceAndAnUnlockedConfirmedTask() {
        assertEquals(1, jdbcTemplate.update("insert into lab_task_quality_gate(task_id,gate_no,gate_name,gate_status,del_flag,create_by,create_time) values(39001,'IT-PASS-1','pass guard','PENDING','0','it',now())"));
        Long firstGate = jdbcTemplate.queryForObject("select id from lab_task_quality_gate where task_id=39001 and gate_no='IT-PASS-1'", Long.class);
        assertEquals(0, taskMapper.markQualityGatePassed(firstGate, 999999L, 39201L, new Date(), "invalid", "it"));
        assertEquals(1, taskMapper.markQualityGatePassed(firstGate, 39001L, 39201L, new Date(), "accepted", "it"));

        assertEquals(1, jdbcTemplate.update("insert into lab_task_quality_gate(task_id,gate_no,gate_name,gate_status,del_flag,create_by,create_time) values(39001,'IT-PASS-2','lock guard','PENDING','0','it',now())"));
        Long lockedGate = jdbcTemplate.queryForObject("select id from lab_task_quality_gate where task_id=39001 and gate_no='IT-PASS-2'", Long.class);
        assertEquals(1, jdbcTemplate.update("update lab_task set period_lock_flag='1' where id=39001"));
        assertEquals(0, taskMapper.markQualityGatePassed(lockedGate, 39001L, 39201L, new Date(), "must not pass", "it"));
    }

    @Test
    void disabledAndDeletedSystemUsersCannotResolveAsTrustedActors() {
        assertThrows(ServiceException.class, () -> accessService.context(30001L));
        assertEquals(1, jdbcTemplate.update("update sys_user set del_flag='2' where user_id=39103"));
        assertThrows(ServiceException.class, () -> accessService.context(39103L));
    }

    @Test
    void logicalDeleteAndOptimisticUpdateAreEnforced() {
        LabTask task = taskMapper.selectTaskById(39004L);
        task.setTitle("changed");
        assertEquals(1, taskMapper.updateTask(task));
        assertEquals(0, taskMapper.updateTask(task), "stale version must not overwrite the first update");
        assertEquals(1, taskMapper.deleteTask(39004L, 1, "it"));
        assertNull(taskMapper.selectTaskById(39004L));

        LabGoal goal = goalMapper.selectGoalById(39003L);
        assertNotNull(goal);
        assertEquals(1, goalMapper.deleteGoal(39003L, 0, "it"));
        assertNull(goalMapper.selectGoalById(39003L));
        assertFalse(goalMapper.selectChildrenByParentId(39001L).isEmpty());
    }

    private LabTask newTask(Long ownerId, String title) {
        LabTask task = new LabTask(); task.setParentId(0L); task.setGoalId(39001L); task.setMilestoneId(39002L);
        task.setTaskLevel("month"); task.setPeriod("2026-03"); task.setBizLine("algorithm"); task.setTaskType("daily");
        task.setTitle(title); task.setOwnerId(ownerId); task.setDeptId(101L); task.setPlanDate(new Date(1773964800000L));
        task.setDeliverable("IT notes"); task.setPerfWeight(BigDecimal.ZERO); task.setGoalWeight(BigDecimal.ZERO);
        task.setCoordinationRequired("0");
        return task;
    }

    private <T> T asUser(Long userId, Supplier<T> action) {
        SysUser user = userService.selectUserById(userId);
        for (SysRole role : user.getRoles()) role.setPermissions(Collections.singleton("lab:task:list"));
        LoginUser loginUser = new LoginUser(userId, user.getDeptId(), user, Collections.singleton("lab:task:list"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(loginUser, null));
        try { return action.get(); } finally { SecurityContextHolder.clearContext(); }
    }

    static final class DatabaseInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            String url = value("LAB_IT_DB_URL", "jdbc:mysql://localhost:3306/ailab_it?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=false&allowMultiQueries=true&serverTimezone=Asia/Shanghai");
            String username = value("LAB_IT_DB_USERNAME", "root");
            String password = value("LAB_IT_DB_PASSWORD", "password");
            Path root = repositoryRoot();
            try (Connection connection = DriverManager.getConnection(url, username, password)) {
                ScriptUtils.executeSqlScript(connection, new FileSystemResource(root.resolve("sql/ry_20240629.sql")));
                ScriptUtils.executeSqlScript(connection, new FileSystemResource(root.resolve("sql/ailab.sql")));
                ScriptUtils.executeSqlScript(connection, new FileSystemResource(root.resolve("sql/test/ailab-mapper-fixture.sql")));
            } catch (Exception exception) {
                throw new IllegalStateException("Real MySQL 8 integration database is unavailable or could not be initialized. Configure LAB_IT_DB_URL/LAB_IT_DB_USERNAME/LAB_IT_DB_PASSWORD.", exception);
            }
        }

        private static String value(String name, String fallback) {
            String system = System.getProperty(name);
            if (system != null && !system.isEmpty()) return system;
            String environment = System.getenv(name);
            return environment == null || environment.isEmpty() ? fallback : environment;
        }

        private static Path repositoryRoot() {
            Path cursor = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
            while (cursor != null && !Files.exists(cursor.resolve("sql/ailab.sql"))) cursor = cursor.getParent();
            if (cursor == null) throw new IllegalStateException("Could not locate repository SQL directory");
            return cursor;
        }
    }
}
