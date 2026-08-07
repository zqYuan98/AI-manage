package com.ailab.system.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Contract test for the re-runnable AI laboratory database bootstrap script. */
class LabSqlContractTest {
    private static final Set<String> TABLES = new LinkedHashSet<>(Arrays.asList(
        "lab_goal", "lab_task", "lab_task_evidence", "lab_task_quality_gate", "lab_task_block_event",
        "lab_reminder", "lab_asset", "lab_member", "lab_skill", "lab_member_skill", "lab_one2one",
        "lab_ipr", "lab_collaboration_record", "lab_perf_score", "lab_period_close", "lab_report_template",
        "lab_report_section", "lab_report_summary", "lab_report_instance", "lab_report_job"));
    private static final Set<String> AUDIT_COLUMNS = new LinkedHashSet<>(Arrays.asList(
        "id", "del_flag", "create_by", "create_time", "update_by", "update_time", "remark"));
    private static final Set<String> DICTS = new LinkedHashSet<>(Arrays.asList(
        "lab_biz_line|hardware", "lab_biz_line|platform", "lab_biz_line|algorithm", "lab_biz_line|manage",
        "lab_task_workflow_status|DRAFT", "lab_task_workflow_status|ACTIVE", "lab_task_workflow_status|PENDING_REVIEW", "lab_task_workflow_status|CONFIRMED",
        "lab_task_result_status|DOING", "lab_task_result_status|EXCEEDED", "lab_task_result_status|ONTIME", "lab_task_result_status|DELAYED", "lab_task_result_status|UNDONE",
        "lab_task_type|key", "lab_task_type|daily", "lab_task_level|month", "lab_task_level|week",
        "lab_asset_type|hardware", "lab_asset_type|algorithm", "lab_asset_type|platform",
        "lab_asset_stage|VERIFYING", "lab_asset_stage|DEPLOYED", "lab_asset_stage|ACCEPTED",
        "lab_ipr_type|SOFTWARE_COPYRIGHT", "lab_ipr_type|PATENT", "lab_ipr_type|CERTIFICATION",
        "lab_ipr_stage|DRAFTING", "lab_ipr_stage|SUBMITTED", "lab_ipr_stage|ACCEPTED", "lab_ipr_stage|AUTHORIZED",
        "lab_section_type|TABLE", "lab_section_type|STAT", "lab_section_type|TEXT", "lab_section_type|MANUAL", "lab_section_type|GROUP_TEXT", "lab_section_type|CHART",
        "lab_goal_status|ACTIVE", "lab_goal_status|COMPLETED", "lab_goal_status|TERMINATED"));
    private static final Set<String> PERMISSIONS = new LinkedHashSet<>(Arrays.asList(
        "lab:dashboard:view", "lab:goal:list", "lab:goal:add", "lab:goal:edit", "lab:goal:remove",
        "lab:task:list", "lab:task:add", "lab:task:edit", "lab:task:remove", "lab:task:evidence", "lab:task:review",
        "lab:member:list", "lab:member:add", "lab:member:edit", "lab:member:remove", "lab:skill:list", "lab:skill:config", "lab:one2one:list", "lab:one2one:add",
        "lab:asset:list", "lab:asset:add", "lab:asset:edit", "lab:asset:remove", "lab:ipr:list", "lab:ipr:add", "lab:ipr:edit",
        "lab:perf:list", "lab:perf:close", "lab:perf:reopen", "lab:perf:redline", "lab:perf:revoke", "lab:perf:calibrate",
        "lab:template:list", "lab:template:config", "lab:template:import", "lab:template:export",
        "lab:report:list", "lab:report:generate", "lab:report:retry", "lab:report:download", "lab:report:finalize", "lab:report:sensitive"));
    private static final Set<String> INDEXES = new LinkedHashSet<>(Arrays.asList(
        "uk_lab_goal_year_no", "idx_lab_goal_parent", "idx_lab_task_goal", "idx_lab_task_owner_status",
        "uk_lab_gate_task_no", "idx_lab_block_task_status", "uk_lab_reminder_idempotency", "idx_lab_reminder_recipient_read",
        "uk_lab_asset_no", "uk_lab_member_user", "uk_lab_skill_code", "uk_lab_member_skill", "idx_lab_one2one_member_date",
        "uk_lab_ipr_no", "idx_lab_collab_to_status", "uk_lab_perf_member_period_rev", "uk_lab_period_close_period",
        "uk_lab_report_tpl_code_rev", "uk_lab_report_section", "uk_lab_report_summary", "uk_lab_report_instance_no", "uk_lab_report_job_idempotency"));

    @Test
    void ailabSqlContainsTheApprovedDatabaseAndInitializationContract() throws IOException {
        String sql = Files.readString(findRepositoryRoot().resolve("sql/ailab.sql"), StandardCharsets.UTF_8);
        String normalized = sql.toLowerCase(Locale.ROOT);
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("(?is)create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?`?(lab_[a-z0-9_]+)`?\\s*\\((.*?)\\)\\s*(?:engine|comment)").matcher(sql);
        while (matcher.find()) {
            String table = matcher.group(1).toLowerCase(Locale.ROOT);
            found.add(table);
            String block = matcher.group(2).toLowerCase(Locale.ROOT);
            int statementEnd = sql.indexOf(';', matcher.end());
            assertTrue(sql.substring(matcher.start(), statementEnd).toLowerCase(Locale.ROOT).contains("comment='"), "missing table comment: " + table);
            for (String column : AUDIT_COLUMNS) {
                assertTrue(Pattern.compile("(?:^|,)\\s*`?" + column + "`?\\s+").matcher(block).find(), "missing " + column + " in " + table);
            }
            assertTrue(Pattern.compile("(?:^|,)\\s*`?id`?\\s+bigint\\s+not\\s+null\\s+auto_increment.*comment", Pattern.CASE_INSENSITIVE).matcher(block).find(), "id comment/type missing: " + table);
            assertTrue(Pattern.compile("(?:^|,)\\s*primary\\s+key", Pattern.CASE_INSENSITIVE).matcher(block).find(), "primary key missing: " + table);
            assertTrue(Pattern.compile("(?is)(?:unique\\s+)?key\\s+`").matcher(block).find(), "focused index missing: " + table);
        }
        assertEquals(TABLES, found, "lab table set must be exactly the approved twenty tables");
        for (String required : DICTS) {
            String[] pair = required.toLowerCase(Locale.ROOT).split(Pattern.quote("|"), 2);
            assertTrue(normalized.contains("'" + pair[0] + "'"), "missing dict type " + pair[0]);
            assertTrue(normalized.contains("'" + pair[1] + "'"), "missing dict value " + required);
        }
        for (String permission : PERMISSIONS) assertTrue(normalized.contains("'" + permission + "'"), "missing permission " + permission);
        for (String target : Arrays.asList("labScheduleTask.scanBlocks()", "labScheduleTask.scanPendingTasks()", "labScheduleTask.closeDuePeriods()", "labScheduleTask.cleanReportTempFiles()", "labScheduleTask.recoverReportJobs()"))
            assertTrue(sql.contains(target), "missing Quartz target " + target);
        for (String type : Arrays.asList("TABLE", "STAT", "TEXT", "MANUAL", "GROUP_TEXT", "CHART")) assertTrue(normalized.contains("'" + type.toLowerCase(Locale.ROOT) + "'"), "missing renderer " + type);
        assertTrue(normalized.contains("'standard_month'"), "default month template missing");
        assertEquals(6, count(normalized, "insert into `lab_member`"), "six deterministic demo members are required");
        assertTrue(normalized.contains("unique key `uk_lab_goal_year_no`"), "goal unique key missing");
        assertTrue(normalized.contains("unique key `uk_lab_member_user`"), "member user unique key missing");
        assertTrue(normalized.contains("unique key `uk_lab_perf_member_period_rev`"), "performance unique key missing");
        assertTrue(normalized.contains("unique key `uk_lab_report_tpl_code_rev`"), "template unique key missing");
        assertTrue(normalized.contains("unique key `uk_lab_reminder_idempotency`"), "reminder idempotency unique key missing");
        for (String index : INDEXES) assertTrue(normalized.contains("key `" + index + "`"), "required index/unique key missing " + index);
    }

    private static long count(String text, String needle) { return Pattern.compile(Pattern.quote(needle)).matcher(text).results().count(); }

    private static Path findRepositoryRoot() {
        for (Path path = Path.of(System.getProperty("user.dir")).toAbsolutePath(); path != null; path = path.getParent()) {
            if (Files.isRegularFile(path.resolve("pom.xml")) && Files.isDirectory(path.resolve("ruoyi-lab"))) return path;
        }
        throw new IllegalStateException("Cannot locate repository root from " + System.getProperty("user.dir"));
    }
}
