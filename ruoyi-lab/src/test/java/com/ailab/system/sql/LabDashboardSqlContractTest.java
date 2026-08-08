package com.ailab.system.sql;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailab.system.quartz.LabScheduleTask;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class LabDashboardSqlContractTest {
    @Test
    void reminderSchemaSupportsAuditHistoryOptimisticReadAndExplainableIdempotency() throws Exception {
        String sql = compact(read("sql/ailab.sql"));
        for (String column : Arrays.asList("business_type", "business_id", "episode_no", "reminder_level",
                "reminder_date", "title", "version")) {
            assertTrue(sql.contains("`" + column + "`"), "reminder column missing: " + column);
        }
        assertTrue(sql.contains("key`idx_lab_reminder_recipient_read_date`(`recipient_id`,`read_flag`,`reminder_date`)"));
        assertTrue(sql.contains("key`idx_lab_task_period_workflow`(`period`,`workflow_status`,`period_lock_flag`)"));
        assertTrue(sql.contains("key`idx_lab_block_status_start`(`block_status`,`block_start_time`,`task_id`,`episode_no`)"));
        assertTrue(sql.contains("table_name='lab_reminder'andcolumn_name='version'"), "legacy reminder schema requires rerunnable upgrade DDL");
        assertTrue(sql.contains("index_name='idx_lab_task_period_workflow'")
                && sql.contains("index_name='idx_lab_block_status_start'"), "legacy databases require rerunnable scan-index upgrades");
        assertTrue(sql.contains("'lab:reminder:list'") && sql.contains("'lab:reminder:read'"));
    }

    @Test
    void mapperUsesCurrentFactsParameterizedScopeAndDatabaseAggregates() throws Exception {
        String xml = read("ruoyi-lab/src/main/resources/mapper/lab/LabDashboardMapper.xml");
        String compact = compact(xml);
        assertFalse(xml.contains("${"), "dashboard mapper must never interpolate client SQL fragments");
        assertTrue(compact.contains("e.block_status='open'ande.del_flag='0'") && compact.contains("t.current_block_flag='1'"));
        assertTrue(compact.contains("t.workflow_statusin('draft','active')") && compact.contains("pc.close_status='closed'"));
        assertTrue(compact.contains("groupbyg.id,g.title,g.year") && compact.contains("groupbym.id,u.nick_name,m.biz_line"));
        assertTrue(compact.contains("p.current_flag='1'") && compact.contains("ri.sensitive_flag='0'"));
        assertTrue(compact.contains("ri.lifecycle_statusin('finalized','superseded')")
                && compact.contains("coalesce(ri.update_time,ri.create_time)due_date"),
                "line leads may see all non-sensitive immutable report revisions with a usable timestamp");
        assertTrue(compact.contains("r.recipient_id=#{scope.memberid}") && compact.contains("recipient.biz_line=#{scope.bizline}"));
        assertTrue(compact.contains("coordinator_user.user_id=coordinator.user_id")
                && compact.contains("coordinator_role.role_key='lab_manager'")
                && compact.contains("coordinator_role.role_key='lab_lead'andcoordinator.biz_line=t.biz_line"),
                "coordinator reminders must be limited to trusted users who can read the task");
        assertTrue(compact.contains("owner_user.user_id=owner.user_id")
                && compact.contains("owner_user.status='0'andowner_user.del_flag='0'"),
                "owner reminders must also use an enabled trusted sys_user mapping");
        assertTrue(compact.contains("insertignoreintolab_reminder"),
                "duplicate reminder keys must report zero inserts regardless of JDBC found-row settings");
        assertTrue(compact.contains("datediff(date(#{asof}),date(blocks.block_start_time))")
                && compact.contains("datediff(date(#{asof}),date(be.block_start_time))"),
                "block-day dashboard boundaries must use business calendar dates");
    }

    @Test
    void pendingTaskScanTargetsOnlyActuallyMissingRequiredFieldsForTheCurrentWorkflowPath() throws Exception {
        String compact = compact(read("ruoyi-lab/src/main/resources/mapper/lab/LabDashboardMapper.xml"));
        assertFalse(compact.contains("and(t.workflow_status='draft'or"),
                "a complete draft must not be reminded merely because it is still a draft");
        for (String required : Arrays.asList("t.plan_dateisnull", "trim(t.deliverable)=''",
                "t.actual_finish_timeisnull", "trim(t.result_desc)=''", "trim(t.fail_reason)=''",
                "trim(t.next_action)=''", "t.coordination_owner_idisnull", "t.coordination_dept_idisnull",
                "trim(t.coordination_content)=''", "trim(t.coordination_support)=''")) {
            assertTrue(compact.contains(required), "pending required-field contract missing: " + required);
        }
        assertTrue(compact.contains("coalesce(t.result_status,'doing')='undone'")
                && compact.contains("coalesce(t.result_status,'doing')&lt;&gt;'undone'"),
                "active UNDONE and completion submissions require different fields");
        assertTrue(compact.contains("t.workflow_statusin('draft','active')")
                && compact.contains("pc.close_status='closed'"));
    }

    @Test
    void allSeededQuartzTargetsResolveToPublicParameterlessMethods() throws Exception {
        for (String name : Arrays.asList("scanBlocks", "scanPendingTasks", "closeDuePeriods", "cleanReportTempFiles", "recoverReportJobs")) {
            Method method = LabScheduleTask.class.getMethod(name);
            assertTrue(java.lang.reflect.Modifier.isPublic(method.getModifiers()));
            assertTrue(method.getParameterTypes().length == 0);
        }
        assertTrue(compact(read("sql/ailab.sql")).contains("'labscheduletask.closedueperiods()','0021*?'"),
                "period close job must run only at 02:00 on the first day of each month");
    }

    private String read(String relative) throws Exception { return new String(Files.readAllBytes(root().resolve(relative)), StandardCharsets.UTF_8); }
    private String compact(String value) { return value.toLowerCase().replaceAll("\\s+", ""); }
    private Path root() {
        for (Path cursor = Paths.get(System.getProperty("user.dir")).toAbsolutePath(); cursor != null; cursor = cursor.getParent()) {
            if (Files.isRegularFile(cursor.resolve("pom.xml")) && Files.isDirectory(cursor.resolve("ruoyi-lab"))) return cursor;
        }
        throw new IllegalStateException("repository root not found");
    }
}
