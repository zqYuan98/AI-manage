package com.ailab.system.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailab.system.domain.LabGoal;
import com.ailab.system.domain.LabAsset;
import com.ailab.system.domain.LabIpr;
import com.ailab.system.domain.LabMember;
import com.ailab.system.domain.LabMemberSkill;
import com.ailab.system.domain.LabOne2One;
import com.ailab.system.domain.LabTask;
import com.ailab.system.domain.LabTaskEvidence;
import com.ailab.system.domain.LabPerfScore;
import com.ailab.system.dto.LabAccessContext;
import com.ailab.system.dto.RedLineRevokeCommand;
import com.ailab.system.dto.TaskSubmitCommand;
import com.ailab.system.service.LabAccessService;
import com.ailab.system.service.LabGoalService;
import com.ailab.system.service.LabLedgerService;
import com.ailab.system.service.LabMemberService;
import com.ailab.system.service.LabTaskService;
import com.ailab.system.service.LabPerformanceService;
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
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.springframework.transaction.annotation.Propagation;

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
    @Autowired private LabMemberMapper memberMapper;
    @Autowired private LabLedgerMapper ledgerMapper;
    @Autowired private LabGoalService goalService;
    @Autowired private LabTaskService taskService;
    @Autowired private LabMemberService memberService;
    @Autowired private LabLedgerService ledgerService;
    @Autowired private LabAccessService accessService;
    @Autowired private LabPerformanceMapper performanceMapper;
    @Autowired private LabPerformanceService performanceService;
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
    void upgradesLegacyTaskAuditTablesAndCurrentBootstrapCanRunTwice() throws Exception {
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(1) from information_schema.columns where table_schema=database() and table_name='lab_task_quality_gate' and column_name='evidence_id' and is_nullable='YES'",
                Integer.class));
        assertEquals(Arrays.asList(1, 2), jdbcTemplate.queryForList(
                "select episode_no from lab_task_block_event where task_id=39990 order by block_start_time,id",
                Integer.class));
        assertEquals(Arrays.asList("task_id", "episode_no"), jdbcTemplate.queryForList(
                "select column_name from information_schema.statistics where table_schema=database() and table_name='lab_task_block_event' and index_name='uk_lab_block_task_episode' and non_unique=0 order by seq_in_index",
                String.class));
        assertEquals(2, jdbcTemplate.queryForObject("select count(1) from lab_asset where id in (39993,39994)", Integer.class));
        List<String> migratedAssetVersions = jdbcTemplate.queryForList(
                "select asset_version from lab_asset where id in (39993,39994) order by id", String.class);
        assertEquals(Arrays.asList("LEGACY-ASSET-A", "LEGACY-ASSET-B"), migratedAssetVersions);
        assertEquals(Arrays.asList("asset_name", "asset_version", "asset_type", "active_unique_flag"),
                jdbcTemplate.queryForList(
                        "select column_name from information_schema.statistics where table_schema=database() and table_name='lab_asset' and index_name='uk_lab_asset_business' and non_unique=0 order by seq_in_index",
                        String.class));
        assertEquals(9, jdbcTemplate.queryForObject("select count(1) from lab_skill where id between 39993 and 40001", Integer.class));
        List<String> migratedSkillNames = jdbcTemplate.queryForList(
                "select skill_name from lab_skill where id between 39993 and 40001 order by id", String.class);
        assertEquals("Legacy Duplicate Skill", migratedSkillNames.get(0));
        assertEquals("Legacy Duplicate Skill [LEGACY-SKILL-B:39994]", migratedSkillNames.get(1));
        assertTrue(migratedSkillNames.get(2).contains("LEGACY-SKILL-C") && migratedSkillNames.get(2).contains("39995"),
                "the pre-existing generated-name collision must receive its own stable code/id suffix");
        assertEquals("Legacy Mixed Skill", migratedSkillNames.get(3));
        assertTrue(migratedSkillNames.get(4).contains("LEGACY-SKILL-D") && migratedSkillNames.get(4).contains("39997"),
                "an inactive skill must be renamed when an active undeleted row already owns its name");
        assertEquals("Legacy Inactive Skill", migratedSkillNames.get(5));
        assertTrue(migratedSkillNames.get(6).contains("LEGACY-SKILL-F") && migratedSkillNames.get(6).contains("39999"),
                "two inactive undeleted skills must also receive distinct names");
        assertEquals("Legacy Mixed Skill", migratedSkillNames.get(7),
                "a logically deleted skill must stay outside the uniqueness migration");
        assertEquals("Legacy Code Collision", migratedSkillNames.get(8));
        assertEquals(8, jdbcTemplate.queryForObject(
                "select count(distinct skill_name) from lab_skill where id between 39993 and 40001 and del_flag='0'",
                Integer.class));
        assertTrue(migratedSkillNames.stream().allMatch(name -> name.length() <= 100));
        List<String> migratedSkillCodes = jdbcTemplate.queryForList(
                "select skill_code from lab_skill where id between 39993 and 40001 order by id", String.class);
        assertEquals("LEGACY-SKILL-D", migratedSkillCodes.get(3));
        assertTrue(migratedSkillCodes.get(4).contains("39997"),
                "the inactive half of an active/inactive code collision must receive a deterministic code");
        assertEquals("LEGACY-SKILL-F", migratedSkillCodes.get(5));
        assertTrue(migratedSkillCodes.get(6).contains("39999"),
                "two inactive rows sharing a code must also be repaired");
        assertEquals("LEGACY-SKILL-D", migratedSkillCodes.get(7),
                "a deleted row may retain a code used by an undeleted row");
        assertTrue(migratedSkillCodes.get(8).contains("40001"),
                "a pre-existing first-pass generated code collision needs a second deterministic repair");
        assertEquals(8, jdbcTemplate.queryForObject(
                "select count(distinct skill_code) from lab_skill where id between 39993 and 40001 and del_flag='0'",
                Integer.class));
        assertTrue(migratedSkillCodes.stream().allMatch(code -> code.length() <= 64));
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(1) from information_schema.columns where table_schema=database() and table_name='lab_skill' and column_name='active_unique_flag' and lower(generation_expression) not like '%status%'",
                Integer.class));
        assertEquals(Arrays.asList("skill_name", "active_unique_flag"), jdbcTemplate.queryForList(
                "select column_name from information_schema.statistics where table_schema=database() and table_name='lab_skill' and index_name='uk_lab_skill_name' and non_unique=0 order by seq_in_index",
                String.class));
        assertEquals(Arrays.asList("skill_code", "active_unique_flag"), jdbcTemplate.queryForList(
                "select column_name from information_schema.statistics where table_schema=database() and table_name='lab_skill' and index_name='uk_lab_skill_code' and non_unique=0 order by seq_in_index",
                String.class));
        LabOne2One legacyConversation = ledgerMapper.selectOne2OneById(39991L);
        LabIpr legacyIpr = ledgerMapper.selectIprById(39991L);
        assertEquals("Legacy one-to-one feedback", legacyConversation.getFactsEvidence());
        assertEquals("Legacy action item", legacyConversation.getNextAction());
        assertEquals("LEGACY-APPLICATION-39991", legacyIpr.getAcceptanceNo());
        assertEquals("2026-06-15", new java.sql.Date(legacyIpr.getActualSubmitDate().getTime()).toString());
        assertEquals("DRAFT", ledgerMapper.selectIprById(39992L).getIprStage());
        assertEquals("DRAFT", jdbcTemplate.queryForObject(
                "select column_default from information_schema.columns where table_schema=database() and table_name='lab_ipr' and column_name='ipr_stage'",
                String.class));
        assertEquals(1, jdbcTemplate.update("update lab_one2one set facts_evidence='Curated current facts',next_action='Curated current action' where id=39991"));
        assertEquals(1, jdbcTemplate.update("update lab_ipr set acceptance_no='CURATED-CURRENT-APPLICATION',actual_submit_date='2026-06-14' where id=39991"));

        Connection connection = org.springframework.jdbc.datasource.DataSourceUtils
                .getConnection(jdbcTemplate.getDataSource());
        try {
            ScriptUtils.executeSqlScript(connection,
                    new FileSystemResource(DatabaseInitializer.repositoryRoot().resolve("sql/ailab.sql")));
        } finally {
            org.springframework.jdbc.datasource.DataSourceUtils.releaseConnection(connection, jdbcTemplate.getDataSource());
        }

        assertEquals(Arrays.asList(1, 2), jdbcTemplate.queryForList(
                "select episode_no from lab_task_block_event where task_id=39990 order by block_start_time,id",
                Integer.class));
        assertEquals("Curated current facts", ledgerMapper.selectOne2OneById(39991L).getFactsEvidence());
        assertEquals("Curated current action", ledgerMapper.selectOne2OneById(39991L).getNextAction());
        assertEquals("CURATED-CURRENT-APPLICATION", ledgerMapper.selectIprById(39991L).getAcceptanceNo());
        assertEquals("2026-06-14", new java.sql.Date(ledgerMapper.selectIprById(39991L).getActualSubmitDate().getTime()).toString());
        assertEquals("DRAFT", ledgerMapper.selectIprById(39992L).getIprStage());
        assertEquals("DRAFT", jdbcTemplate.queryForObject(
                "select column_default from information_schema.columns where table_schema=database() and table_name='lab_ipr' and column_name='ipr_stage'",
                String.class));
        assertEquals(migratedAssetVersions, jdbcTemplate.queryForList(
                "select asset_version from lab_asset where id in (39993,39994) order by id", String.class));
        assertEquals(migratedSkillNames, jdbcTemplate.queryForList(
                "select skill_name from lab_skill where id between 39993 and 40001 order by id", String.class));
        assertEquals(migratedSkillCodes, jdbcTemplate.queryForList(
                "select skill_code from lab_skill where id between 39993 and 40001 order by id", String.class));
        assertEquals(2, jdbcTemplate.queryForObject("select count(1) from lab_asset where id in (39993,39994)", Integer.class));
        assertEquals(9, jdbcTemplate.queryForObject("select count(1) from lab_skill where id between 39993 and 40001", Integer.class));
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
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(1) from sys_role_menu rm join sys_menu m on m.menu_id=rm.menu_id where rm.role_id=30002 and m.perms='lab:task:remove'",
                Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(1) from sys_role_menu rm join sys_menu m on m.menu_id=rm.menu_id where rm.role_id=30003 and m.perms='lab:task:remove'",
                Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(1) from sys_role_menu rm join sys_menu m on m.menu_id=rm.menu_id where rm.role_id in (30002,30003) and m.perms='lab:skill:config'",
                Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(1) from sys_role_menu rm join sys_menu m on m.menu_id=rm.menu_id where rm.role_id=30001 and m.perms='lab:one2one:edit'",
                Integer.class));

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

        LabTask removable = newTask(39203L, "member removes own draft task");
        assertEquals(1, taskService.createTask(removable, 39103L));
        assertEquals(1, taskService.deleteTask(removable.getId(), removable.getVersion(), 39103L));
        assertNull(taskMapper.selectTaskById(removable.getId()));

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
    void teamLedgerMappingsKeepSystemIdentitySeparateAndEnforceMatrixHistoryAndRisk() {
        LabMember joined = memberMapper.selectMemberById(39203L);
        assertEquals(Long.valueOf(39103L), joined.getUserId());
        assertEquals("it_algorithm_member", joined.getUserName());
        assertEquals("IT Algorithm Member", joined.getNickName());
        assertFalse(joined.getId().equals(joined.getUserId()));

        LabMemberSkill updated = memberMapper.selectMemberSkills(39203L, false).get(0);
        updated.setLevel(4);
        LabMemberSkill added = new LabMemberSkill();
        added.setMemberId(39203L); added.setSkillId(39302L); added.setLevel(5);
        added.setEvidenceUrl("https://example.invalid/it/skill-2");
        assertEquals(2, memberService.saveSkillMatrix(39203L, Arrays.asList(added, updated), 39103L));
        assertEquals(Arrays.asList(4, 5), jdbcTemplate.queryForList(
                "select skill_level from lab_member_skill where member_id=39203 and del_flag='0' order by skill_id", Integer.class));
        assertThrows(org.springframework.dao.DuplicateKeyException.class, () -> jdbcTemplate.update(
                "insert into lab_member_skill(member_id,skill_id,skill_level,version,del_flag,create_by,create_time) values(39203,39302,3,0,'0','it',now())"));

        List<LabAsset> risks = ledgerService.listAssetRisks(new LabAsset(), 39103L);
        assertTrue(risks.stream().anyMatch(asset -> Long.valueOf(39301L).equals(asset.getId())));
        assertFalse(risks.stream().anyMatch(asset -> Long.valueOf(39302L).equals(asset.getId())));
        assertEquals("IT Algorithm Member", ledgerMapper.selectAssetById(39301L).getPrimaryOwnerName());

        assertNotNull(ledgerService.getOne2One(39301L, 39103L));
        assertNotNull(ledgerService.getOne2One(39301L, 39101L));
        assertThrows(ServiceException.class, () -> ledgerService.getOne2One(39301L, 39102L));

        assertEquals(1, memberService.deactivateMember(39203L, 0, 39101L));
        assertEquals("INACTIVE", memberMapper.selectMemberById(39203L).getMemberStatus());
        assertEquals(2, jdbcTemplate.queryForObject("select count(1) from lab_member_skill where member_id=39203", Integer.class));
        assertEquals(2, jdbcTemplate.queryForObject("select count(1) from lab_asset where primary_owner_id=39203", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("select count(1) from lab_one2one where member_id=39203", Integer.class));
        assertTrue(memberMapper.selectAvailableSystemUsers().stream()
                .noneMatch(candidate -> Long.valueOf(39103L).equals(candidate.getUserId())),
                "inactive member history must be reactivated instead of offered as a new profile");
    }

    @Test
    void iprResultMapAndOptimisticLockAreComplete() {
        LabIpr ipr = ledgerMapper.selectIprById(39301L);
        assertEquals("IT Algorithm Member", ipr.getOwnerName());
        assertEquals("algorithm", ipr.getOwnerBizLine());
        assertEquals("IT-ACCEPT-1", ipr.getAcceptanceNo());
        ipr.setIprName("IT patent updated"); ipr.setUpdateBy("it");
        assertEquals(1, ledgerMapper.updateIpr(ipr));
        assertEquals(0, ledgerMapper.updateIpr(ipr));
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

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentCloseReopenAndRecloseKeepImmutableRevisionsAndIdempotentDeductions() throws Exception {
        String period = "2098-11";
        long taskId = 39880L;
        cleanupPerformanceFixture(period, taskId);
        jdbcTemplate.update("insert into lab_task(id,parent_id,goal_id,milestone_id,task_level,period,biz_line,task_type,title,owner_id,dept_id,plan_date,deliverable,perf_weight,goal_weight,workflow_status,result_status,asset_id,coordination_required,current_block_flag,period_lock_flag,version,del_flag,create_by,create_time) values(?,0,39001,39002,'month',?,'algorithm','key','IT concurrent close',39203,101,'2098-11-20','artifact',100,100,'ACTIVE','DOING',39301,'0','0','0',0,'0','it',now())", taskId, period);
        try {
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<List<LabPerfScore>> first = pool.submit(() -> { start.await(); return performanceService.closePeriod(period, "concurrent close", 39101L); });
            Future<List<LabPerfScore>> second = pool.submit(() -> { start.await(); return performanceService.closePeriod(period, "concurrent close", 39101L); });
            start.countDown();
            assertEquals(3, first.get().size());
            assertEquals(3, second.get().size());
            pool.shutdownNow();

            assertEquals(1, jdbcTemplate.queryForObject("select count(1) from lab_period_close where period=? and close_status='CLOSED' and del_flag='0'", Integer.class, period));
            assertEquals(3, jdbcTemplate.queryForObject("select count(1) from lab_perf_score where period=? and current_flag='1' and del_flag='0'", Integer.class, period));
            assertEquals(3, jdbcTemplate.queryForObject("select count(1) from lab_perf_score where period=? and revision_no=1 and del_flag='0'", Integer.class, period));
            assertEquals(1, jdbcTemplate.queryForObject("select count(1) from lab_collaboration_record where idempotency_key=? and del_flag='0'", Integer.class, "PERIOD_OVERDUE:" + period + ":" + taskId));
            assertEquals("ACTIVE", jdbcTemplate.queryForObject("select workflow_status from lab_task where id=?", String.class, taskId));
            assertEquals("DOING", jdbcTemplate.queryForObject("select result_status from lab_task where id=?", String.class, taskId));
            assertEquals("1", jdbcTemplate.queryForObject("select period_lock_flag from lab_task where id=?", String.class, taskId));

            Long oldId = jdbcTemplate.queryForObject("select id from lab_perf_score where member_id=39203 and period=? and current_flag='1'", Long.class, period);
            LabPerfScore old = performanceMapper.selectScoreForUpdate(oldId);
            assertNotNull(old);
            assertEquals("RED_LINE", old.getResultStatus());
            assertTrue(old.getDetailJson().contains("MONTH_CLOSE_UNCONFIRMED_AS_UNDONE"));
            String originalDetail = old.getDetailJson();
            performanceService.revokeRedLine(old.getId(), new RedLineRevokeCommand("https://example.invalid/it/corrective", "corrected evidence"), 39101L);
            assertEquals(originalDetail, jdbcTemplate.queryForObject("select cast(detail_json as char) from lab_perf_score where id=?", String.class, old.getId()));
            assertTrue(jdbcTemplate.queryForObject("select cast(red_line_correction_json as char) from lab_perf_score where id=?", String.class, old.getId()).contains("originalTriggers"));

            performanceService.reopenPeriod(period, "late correction", 39101L);
            assertEquals("OPEN", jdbcTemplate.queryForObject("select close_status from lab_period_close where period=?", String.class, period));
            assertEquals(0, jdbcTemplate.queryForObject("select count(1) from lab_perf_score where period=? and current_flag='1'", Integer.class, period));
            assertEquals("0", jdbcTemplate.queryForObject("select period_lock_flag from lab_task where id=?", String.class, taskId));
            assertEquals(1, jdbcTemplate.queryForObject("select json_length(reopen_history_json) from lab_period_close where period=?", Integer.class, period));

            jdbcTemplate.update("update lab_task set workflow_status='CONFIRMED',result_status='ONTIME',actual_finish_time='2098-11-19 10:00:00',result_desc='fixed',version=version+1 where id=?", taskId);
            jdbcTemplate.update("insert into lab_task_evidence(id,task_id,evidence_type,evidence_title,evidence_url,submitter_id,submit_time,audit_status,auditor_id,audit_time,audit_comment,del_flag,create_by,create_time) values(39880,?,'URL','IT corrected evidence','https://example.invalid/it/fixed',39203,now(),'APPROVED',39201,now(),'verified','0','it',now())", taskId);
            jdbcTemplate.update("insert into lab_collaboration_record(task_id,period,from_member_id,to_member_id,category,signed_score,evidence_url,reviewer_id,review_status,review_time,review_comment,version,del_flag,create_by,create_time) values(?, ?,39202,39203,'BACKUP',4,'https://example.invalid/it/backup',39201,'APPROVED',now(),'trained',0,'0','it',now())", taskId, period);

            List<LabPerfScore> revised = performanceService.closePeriod(period, "corrected close", 39101L);
            assertEquals(3, revised.size());
            assertEquals(Arrays.asList(1, 2), jdbcTemplate.queryForList("select revision_no from lab_perf_score where member_id=39203 and period=? order by revision_no", Integer.class, period));
            assertEquals(1, jdbcTemplate.queryForObject("select count(1) from lab_perf_score where member_id=39203 and period=? and current_flag='1' and revision_no=2", Integer.class, period));
            assertEquals(originalDetail, jdbcTemplate.queryForObject("select cast(detail_json as char) from lab_perf_score where id=?", String.class, old.getId()));
            assertEquals(1, jdbcTemplate.queryForObject("select count(1) from lab_collaboration_record where idempotency_key=? and del_flag='0'", Integer.class, "PERIOD_OVERDUE:" + period + ":" + taskId));
            assertEquals("CONFIRMED", jdbcTemplate.queryForObject("select workflow_status from lab_task where id=?", String.class, taskId));
            assertThrows(org.springframework.dao.DuplicateKeyException.class, () -> jdbcTemplate.update("insert into lab_perf_score(member_id,period,revision_no,current_flag,version,del_flag,create_by,create_time) values(39203,?,99,'1',0,'0','it',now())", period));
        } finally {
            cleanupPerformanceFixture(period, taskId);
        }
    }

    private void cleanupPerformanceFixture(String period, long taskId) {
        jdbcTemplate.update("delete from lab_perf_score where period=?", period);
        jdbcTemplate.update("delete from lab_collaboration_record where period=? or idempotency_key=?", period, "PERIOD_OVERDUE:" + period + ":" + taskId);
        jdbcTemplate.update("delete from lab_task_evidence where task_id=?", taskId);
        jdbcTemplate.update("delete from lab_task where id=?", taskId);
        jdbcTemplate.update("delete from lab_period_close where period=?", period);
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
                ScriptUtils.executeSqlScript(connection, new FileSystemResource(root.resolve("sql/test/ailab-legacy-fixture.sql")));
                requireLegacySkillUniquenessState(connection);
                ScriptUtils.executeSqlScript(connection, new FileSystemResource(root.resolve("sql/ailab.sql")));
                ScriptUtils.executeSqlScript(connection, new FileSystemResource(root.resolve("sql/test/ailab-mapper-fixture.sql")));
            } catch (Exception exception) {
                throw new IllegalStateException("Real MySQL 8 integration database is unavailable or could not be initialized. Configure LAB_IT_DB_URL/LAB_IT_DB_USERNAME/LAB_IT_DB_PASSWORD.", exception);
            }
        }

        private static void requireLegacySkillUniquenessState(Connection connection) throws Exception {
            try (java.sql.Statement statement = connection.createStatement();
                    java.sql.ResultSet result = statement.executeQuery(
                            "select generation_expression from information_schema.columns where table_schema=database() and table_name='lab_skill' and column_name='active_unique_flag'")) {
                if (!result.next() || !result.getString(1).toLowerCase().contains("status")) {
                    throw new IllegalStateException("Legacy skill fixture must begin with status-scoped uniqueness");
                }
            }
            try (java.sql.Statement statement = connection.createStatement();
                    java.sql.ResultSet result = statement.executeQuery(
                            "select count(distinct index_name) from information_schema.statistics where table_schema=database() and table_name='lab_skill' and index_name in ('uk_lab_skill_name','uk_lab_skill_code') and non_unique=0")) {
                if (!result.next() || result.getInt(1) != 2) {
                    throw new IllegalStateException("Legacy skill fixture must retain both status-scoped unique indexes");
                }
            }
            try (java.sql.Statement statement = connection.createStatement();
                    java.sql.ResultSet result = statement.executeQuery(
                            "select (select count(1) from (select skill_name from lab_skill where del_flag='0' group by skill_name having count(1)>1) names),"
                                    + "(select count(1) from (select skill_code from lab_skill where del_flag='0' group by skill_code having count(1)>1) codes)")) {
                if (!result.next() || result.getInt(1) < 3 || result.getInt(2) < 2) {
                    throw new IllegalStateException("Legacy skill fixture must contain undeleted name and code collisions across statuses");
                }
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
