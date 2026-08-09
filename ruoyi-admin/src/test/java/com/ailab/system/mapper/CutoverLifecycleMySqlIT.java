package com.ailab.system.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ailab.system.config.LabProperties;
import com.ailab.system.service.impl.LabTaskExecutionMigrationService;
import com.ruoyi.RuoYiApplication;
import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * Guarded read/write cutover exercised against the same disposable MySQL schema
 * as {@link LabMapperMySqlIT}. The assertions intentionally use persisted
 * configuration, because an in-memory flag cannot prove rollback safety.
 */
@SpringBootTest(classes = RuoYiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("lab-it")
@ContextConfiguration(initializers = LabMapperMySqlIT.DatabaseInitializer.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CutoverLifecycleMySqlIT {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;
    @Autowired private LabCommitmentMapper commitmentMapper;

    @Test
    @Order(1)
    void bootstrapIsRepeatableAndUsesTheCompleteInfrastructureOrder() throws Exception {
        assertEquals(0, jdbc.queryForObject(
                "select count(1) from lab_task where task_level='week' and del_flag='0' and execution_status is null "
                        + "and id in (39880,39881,39882,39885,39886)", Integer.class));
        assertEquals(5, jdbc.queryForObject(
                "select count(1) from lab_task_execution_event where event_type='MIGRATED_BASELINE' "
                        + "and task_id in (39880,39881,39882,39885,39886)", Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "select count(1) from information_schema.tables where table_schema=database() "
                        + "and upper(table_name)='QRTZ_JOB_DETAILS'", Integer.class),
                "the shared initializer must install Quartz before lab migrations");
        assertEquals(1, jdbc.queryForObject(
                "select count(1) from sys_user where user_id=39101 and status='0' and del_flag='0'", Integer.class));

        try (Connection connection = dataSource.getConnection()) {
            LabMapperMySqlIT.DatabaseInitializer.bootstrap(connection,
                    LabMapperMySqlIT.DatabaseInitializer.repositoryRoot());
        }
        assertEquals(5, jdbc.queryForObject(
                "select count(1) from lab_task_execution_event where event_type='MIGRATED_BASELINE' "
                        + "and task_id in (39880,39881,39882,39885,39886)", Integer.class));
    }

    @Test
    @Order(3)
    void writeCutoverCreatesAPermanentPointOfNoReturn() {
        LabProperties properties = new LabProperties();
        properties.setReadNewModel(true);
        properties.setWriteSelfClose(true);
        LabTaskExecutionMigrationService migration =
                new LabTaskExecutionMigrationService(commitmentMapper, properties);

        jdbc.update("update sys_config set config_value='false' where config_key='lab.commitment.pointOfNoReturn'");
        try {
            assertEquals(1, commitmentMapper.updateCutoverValue(
                    "lab.commitment.pointOfNoReturn", "false", "true"));
            assertThrows(IllegalStateException.class,
                    () -> migration.validateCutover(false, false, 0, true));
            migration.validateCutover(true, true, 0, true);
        } finally {
            jdbc.update("update sys_config set config_value='false' where config_key='lab.commitment.pointOfNoReturn'");
        }
    }

    @Test
    @Order(2)
    void unresolvedTerminalBlockPreventsReadCutoverUntilItIsExplicitlyResolved() throws Exception {
        LabProperties properties = new LabProperties();
        properties.setReadNewModel(true);
        LabTaskExecutionMigrationService migration =
                new LabTaskExecutionMigrationService(commitmentMapper, properties);

        deleteDedicatedMigrationFixtures();
        jdbc.update("insert into lab_task(id,parent_id,goal_id,milestone_id,task_level,period,biz_line,task_type,title,"
                + "owner_id,dept_id,plan_date,actual_finish_time,deliverable,workflow_status,result_status,result_desc,"
                + "current_block_flag,current_block_start,period_lock_flag,version,del_flag,create_by,create_time) values "
                + "(49883,39879,30001,30002,'week','2026-W32','algorithm','daily','Isolated terminal block',39203,101,"
                + "'2026-08-09','2026-08-08 12:00:00','Migration fixture','PENDING_REVIEW','EXCEEDED','Completed early',"
                + "'1','2026-08-08 09:00:00','0',0,'0','it',now()),"
                + "(49884,39879,30001,30002,'week','2026-W32','algorithm','daily','Isolated active anomaly',39203,101,"
                + "'2026-08-09',null,'Migration fixture','ACTIVE','DELAYED','Stale result','0',null,'0',0,'0','it',now())");
        jdbc.update("insert into lab_task_block_event(id,task_id,episode_no,block_type,block_reason,block_start_time,block_status,"
                + "del_flag,create_by,create_time) values(49883,49883,1,'DEPENDENCY','Isolated terminal block',"
                + "'2026-08-08 09:00:00','OPEN','0','it','2026-08-08 09:00:00')");
        runLabMigration();

        assertEquals(2, jdbc.queryForObject(
                "select count(1) from lab_task_migration_issue where task_id in (49883,49884) "
                        + "and resolution_status='OPEN' and del_flag='0'", Integer.class));
        assertThrows(IllegalStateException.class,
                () -> migration.validateCutover(true, false, 2, false));

        assertEquals(1, jdbc.update(
                "update lab_task_block_event set block_status='CLOSED',block_end_time=now(),"
                        + "resolution='MIGRATION_TERMINAL_UNRESOLVED',resolver_id=39201 "
                        + "where task_id=49883 and block_status='OPEN' and del_flag='0'"));
        assertEquals(1, jdbc.update(
                "update lab_task_migration_issue set resolution_status='RESOLVED',"
                        + "resolution_code='MIGRATION_TERMINAL_UNRESOLVED',resolved_by=39201,resolved_time=now(),"
                        + "version=version+1 where task_id=49883 and resolution_status='OPEN' and del_flag='0'"));
        assertEquals(1, jdbc.update(
                "update lab_task set workflow_status='ACTIVE',result_status='DOING',actual_finish_time=null,"
                        + "result_desc=null,update_by='migration-review',update_time=now() where id=49884"));
        assertEquals(1, jdbc.update(
                "update lab_task_migration_issue set resolution_status='RESOLVED',"
                        + "resolution_code='MIGRATION_NORMALIZED_ACTIVE',resolved_by=39201,resolved_time=now(),"
                        + "version=version+1 where task_id=49884 and resolution_status='OPEN' and del_flag='0'"));

        runLabMigration();
        assertEquals(1, jdbc.queryForObject(
                "select count(1) from lab_task_migration_issue where task_id=49883 "
                        + "and resolution_code='MIGRATION_TERMINAL_UNRESOLVED'", Integer.class));
        assertEquals("SELF_DONE", jdbc.queryForObject(
                "select execution_status from lab_task where id=49883", String.class));
        assertEquals("ACTIVE", jdbc.queryForObject(
                "select execution_status from lab_task where id=49884", String.class));
        assertEquals(0, jdbc.queryForObject(
                "select count(1) from lab_task_migration_issue where task_id in (49883,49884) "
                        + "and resolution_status='OPEN' and del_flag='0'", Integer.class));
        assertEquals(2, jdbc.queryForObject(
                "select count(1) from lab_task_execution_event where task_id in (49883,49884) "
                        + "and event_type='MIGRATED_BASELINE'", Integer.class));
        migration.validateCutover(true, false, 0, false);

        runLabMigration();
        assertEquals(2, jdbc.queryForObject(
                "select count(1) from lab_task_execution_event where task_id in (49883,49884) "
                        + "and event_type='MIGRATED_BASELINE'", Integer.class),
                "a repeated migration must not duplicate baseline events");
        deleteDedicatedMigrationFixtures();
    }

    private void deleteDedicatedMigrationFixtures() {
        jdbc.update("delete from lab_task_execution_event where task_id in (49883,49884)");
        jdbc.update("delete from lab_task_migration_issue where task_id in (49883,49884)");
        jdbc.update("delete from lab_task_block_event where task_id in (49883,49884)");
        jdbc.update("delete from lab_task where id in (49883,49884)");
    }

    private void runLabMigration() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(
                    LabMapperMySqlIT.DatabaseInitializer.repositoryRoot().resolve("sql/ailab.sql")));
        }
    }
}
