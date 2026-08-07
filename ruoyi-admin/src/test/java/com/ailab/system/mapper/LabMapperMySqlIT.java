package com.ailab.system.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailab.system.domain.LabGoal;
import com.ailab.system.domain.LabTask;
import com.ailab.system.domain.LabTaskEvidence;
import com.ailab.system.service.LabGoalService;
import com.ailab.system.service.LabTaskService;
import com.ruoyi.RuoYiApplication;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
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

    @Test
    void filtersByOwnerBusinessLinePeriodAndFrameworkDataScopeFragment() {
        LabTask query = new LabTask();
        query.setOwnerId(30002L); query.setBizLine("algorithm"); query.setPeriod("2026-01");
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

        assertEquals(new BigDecimal("70.00"), task.getPerfWeight());
        assertEquals(new BigDecimal("25.00"), task.getGoalWeight());
        assertTrue(evidence.getEvidenceJson().contains("integration"));
        assertEquals("PASSED", taskMapper.selectQualityGateById(39001L).getGateStatus());
        assertEquals("OPEN", taskMapper.selectOpenBlockEvent(39004L).getBlockStatus());
        assertEquals(new BigDecimal("25.00"), goalService.calculateMilestoneProgress(39002L));
        assertEquals(new BigDecimal("70.00"), goalService.calculateAnnualProgress(39001L));
        assertEquals(new BigDecimal("50.00"), taskService.calculateMonthProgress(39004L));
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
