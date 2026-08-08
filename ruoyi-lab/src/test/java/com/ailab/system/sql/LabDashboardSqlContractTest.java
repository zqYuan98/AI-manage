package com.ailab.system.sql;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
                && compact.contains("ri.lifecycle_status='final'")
                && compact.contains("ri.json_status='ready'")
                && compact.contains("ri.markdown_status='ready'")
                && compact.contains("ri.word_status='ready'")
                && compact.contains("ri.pdf_status='ready'")
                && compact.contains("coalesce(ri.update_time,ri.create_time)due_date"),
                "non-managers may see current FINAL/READY and future immutable report revisions with a usable timestamp");
        assertTrue(compact.contains("r.recipient_id=#{scope.memberid}") && compact.contains("recipient.biz_line=#{scope.bizline}"));
        assertFalse(compact.contains("'coordinator'audience"),
                "seven-day block reminders go only to the owner; coordinators are not escalation recipients");
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
    void goalProgressSqlMatchesTaskFourActiveMonthAndConfirmedWeekContractWithoutNPlusOne() throws Exception {
        String compact = compact(read("ruoyi-lab/src/main/resources/mapper/lab/LabDashboardMapper.xml"));
        assertTrue(compact.contains("withweekly_progressas(") || compact.contains("withrecursiveweekly_progressas("));
        assertTrue(compact.contains("w.workflow_status='confirmed'")
                && compact.contains("w.result_statusin('exceeded','ontime','delayed')"));
        assertTrue(compact.contains("count(casewhenw.workflow_status='confirmed'then1end)")
                || compact.contains("sum(casewhenw.workflow_status='confirmed'then1else0end)"),
                "pending weekly tasks must not enter the active-month denominator");
        assertTrue(compact.contains("m.workflow_status='active'") && compact.contains("weekly_progress"));
        assertTrue(compact.contains("round(100*sum(casewhenw.workflow_status='confirmed'andw.result_statusin('exceeded','ontime','delayed')then1else0end)"),
                "active month weekly percentage must use the same two-decimal rounding as LabGoalService");
        assertTrue(compact.contains("least(100,greatest(0,round(sum(mp.goal_weight*mp.completion_ratio),2)))"),
                "milestone progress must round and clamp before annual weighting");
        assertTrue(compact.contains("least(100,greatest(0,round(sum(mt.quarter_weight*mt.milestone_progress/100),2)))"),
                "annual progress must round and clamp after weighting rounded milestones");
        assertTrue(compact.contains("goal_riskas(") && compact.contains("fromlab_taskrt")
                && compact.contains("blocks.task_id=rt.id"),
                "goal risk must consider every current task under the goal without multiplying progress aggregates");
        assertEquals(2, occurrences(compact,
                "fromlab_taskwjoinlab_taskparent_monthonparent_month.id=w.parent_id"),
                "both health and trend weekly aggregates must start from valid requested parent months");
        assertEquals(2, occurrences(compact, "weekly_annual.year=#{year}"));
        assertEquals(2, occurrences(compact, "parent_month.period&lt;=date_format(#{asof},'%y-%m')"));
        assertFalse(compact.contains("fromlab_taskwwherew.task_level='week'"),
                "weekly progress must not aggregate the complete cross-year task history");
        assertFalse(compact.contains("selectkeymonthtasksbymilestoneid"), "dashboard aggregation must remain set based");
    }

    @Test
    void goalTrendReturnsIndependentGoalSeriesInsteadOfSummingPercentagesAcrossGoals() throws Exception {
        String compact = compact(read("ruoyi-lab/src/main/resources/mapper/lab/LabDashboardMapper.xml"));
        assertTrue(compact.contains("property=\"goalid\"column=\"goal_id\"")
                && compact.contains("property=\"goalname\"column=\"goal_name\""),
                "trend DTO mapping must identify the annual-goal series");
        assertTrue(compact.contains("selectet.goal_id,g.titlegoal_name,et.period")
                && compact.contains("partitionbygoal_idorderbyperiod"),
                "each annual goal needs its own cumulative monthly series");
        assertFalse(compact.contains("selectperiod,sum(annual_progress)actual_progress"),
                "two goals at 60 percent must remain two 60-percent series, never a 120-percent point");
    }

    @Test
    void taskDrillFiltersAreParameterizedAndMatchDashboardKpiCutoffs() throws Exception {
        String xml = read("ruoyi-lab/src/main/resources/mapper/lab/LabTaskMapper.xml");
        String compact = compact(xml);
        assertTrue(compact.contains("workflowstatuses!=null") && compact.contains("<foreach")
                && compact.contains("#{workflowstatus}"));
        assertTrue(compact.contains("t.current_block_flag=#{currentblockflag}")
                && compact.contains("exists(select1fromlab_task_block_eventbe")
                && compact.contains("date(be.block_start_time)&lt;=date(#{blockstartbefore})"));
        assertTrue(compact(read("ruoyi-lab/src/main/resources/mapper/lab/LabDashboardMapper.xml"))
                .contains("count(distinctt.id)") , "block KPI must count the same distinct OPEN-event task set as drill-down");
        assertTrue(compact.contains("overdueorpending") && compact.contains("t.period_lock_flag='0'"));
        assertFalse(xml.contains("${workflow") || xml.contains("${currentBlock") || xml.contains("${blockStart"));
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
    private int occurrences(String value, String needle) {
        int count = 0;
        for (int offset = 0; (offset = value.indexOf(needle, offset)) >= 0; offset += needle.length()) count++;
        return count;
    }
    private Path root() {
        for (Path cursor = Paths.get(System.getProperty("user.dir")).toAbsolutePath(); cursor != null; cursor = cursor.getParent()) {
            if (Files.isRegularFile(cursor.resolve("pom.xml")) && Files.isDirectory(cursor.resolve("ruoyi-lab"))) return cursor;
        }
        throw new IllegalStateException("repository root not found");
    }
}
