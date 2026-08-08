package com.ailab.system.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Structural contract for the executable, re-runnable AI laboratory SQL bootstrap. */
class LabSqlContractTest {
    private static final Set<String> TABLES = set("lab_goal", "lab_task", "lab_task_evidence", "lab_task_quality_gate", "lab_task_block_event", "lab_reminder", "lab_asset", "lab_member", "lab_skill", "lab_member_skill", "lab_one2one", "lab_ipr", "lab_collaboration_record", "lab_perf_score", "lab_period_close", "lab_report_template", "lab_report_section", "lab_report_summary", "lab_report_instance", "lab_report_job");
    private static final Set<String> AUDIT = set("id", "del_flag", "create_by", "create_time", "update_by", "update_time", "remark");
    private static final Set<String> DICTS = set("lab_biz_line|hardware", "lab_biz_line|platform", "lab_biz_line|algorithm", "lab_biz_line|manage", "lab_task_workflow_status|DRAFT", "lab_task_workflow_status|ACTIVE", "lab_task_workflow_status|PENDING_REVIEW", "lab_task_workflow_status|CONFIRMED", "lab_task_result_status|DOING", "lab_task_result_status|EXCEEDED", "lab_task_result_status|ONTIME", "lab_task_result_status|DELAYED", "lab_task_result_status|UNDONE", "lab_task_type|key", "lab_task_type|daily", "lab_task_level|month", "lab_task_level|week", "lab_asset_type|hardware", "lab_asset_type|algorithm", "lab_asset_type|platform", "lab_asset_stage|VERIFYING", "lab_asset_stage|DEPLOYED", "lab_asset_stage|ACCEPTED", "lab_ipr_type|SOFTWARE_COPYRIGHT", "lab_ipr_type|PATENT", "lab_ipr_type|CERTIFICATION", "lab_ipr_stage|DRAFT", "lab_ipr_stage|PREPARING", "lab_ipr_stage|SUBMITTED", "lab_ipr_stage|ACCEPTED", "lab_ipr_stage|AUTHORIZED", "lab_section_type|TABLE", "lab_section_type|STAT", "lab_section_type|TEXT", "lab_section_type|MANUAL", "lab_section_type|GROUP_TEXT", "lab_section_type|CHART", "lab_goal_status|ACTIVE", "lab_goal_status|COMPLETED", "lab_goal_status|TERMINATED");
    private static final Set<String> PERMISSIONS = set("lab:dashboard:view", "lab:reminder:list", "lab:reminder:read", "lab:goal:list", "lab:goal:add", "lab:goal:edit", "lab:goal:remove", "lab:goal:activate", "lab:task:list", "lab:task:add", "lab:task:edit", "lab:task:remove", "lab:task:evidence", "lab:task:review", "lab:member:list", "lab:member:add", "lab:member:edit", "lab:member:remove", "lab:skill:list", "lab:skill:config", "lab:one2one:list", "lab:one2one:add", "lab:one2one:edit", "lab:asset:list", "lab:asset:add", "lab:asset:edit", "lab:asset:remove", "lab:ipr:list", "lab:ipr:add", "lab:ipr:edit", "lab:ipr:remove", "lab:perf:list", "lab:perf:close", "lab:perf:reopen", "lab:perf:redline", "lab:perf:revoke", "lab:perf:calibrate", "lab:perf:history", "lab:template:list", "lab:template:config", "lab:template:import", "lab:template:export", "lab:report:list", "lab:report:generate", "lab:report:retry", "lab:report:download", "lab:report:finalize", "lab:report:sensitive");
    private static final Map<String, Set<String>> FIELDS = fields();
    private static final Set<String> LOOKUP_INDEXES = lookupIndexes();
    static { LOOKUP_INDEXES.remove("lab_member_skill|keyidx_lab_member_skill_member(member_id)"); LOOKUP_INDEXES.remove("lab_report_summary|keyidx_lab_report_summary_period(period,biz_line)"); }
    static { FIELDS.get("lab_task_quality_gate").add("evidence_id"); FIELDS.get("lab_task_block_event").add("episode_no"); FIELDS.get("lab_reminder").addAll(set("business_type","business_id","episode_no","reminder_level","reminder_date","title","version")); }

    @Test
    void ailabSqlMeetsTheApprovedSchemaAndSeedContract() throws IOException {
        String sql = readSql();
        Map<String, String> blocks = tableBlocks(sql);
        assertEquals(TABLES, blocks.keySet(), "lab table set must be exactly the approved twenty tables");
        for (Map.Entry<String, String> entry : blocks.entrySet()) {
            String table = entry.getKey();
            String statement = entry.getValue();
            assertTrue(statement.toLowerCase(Locale.ROOT).contains("engine=innodb"), "engine missing: " + table);
            assertTrue(statement.toLowerCase(Locale.ROOT).contains("charset=utf8mb4"), "charset missing: " + table);
            assertTrue(statement.toLowerCase(Locale.ROOT).contains("collate=utf8mb4_general_ci"), "collation missing: " + table);
            assertTrue(Pattern.compile("(?is)\\)\\s*engine=.*comment\\s*=\\s*'").matcher(statement).find(), "table comment missing: " + table);
            Map<String, String> columns = columns(statement);
            for (String column : columns.keySet()) assertTrue(columns.get(column).toLowerCase(Locale.ROOT).contains("comment '"), "column comment missing: " + table + "." + column);
            assertTrue(columns.keySet().containsAll(AUDIT), "audit columns missing: " + table);
            assertTrue(columns.get("id").toLowerCase(Locale.ROOT).matches("(?s).*bigint.*not null.*auto_increment.*"), "id contract missing: " + table);
            assertTrue(columns.keySet().containsAll(FIELDS.get(table)), "model fields missing: " + table);
            assertTrue(Pattern.compile("(?is)primary\\s+key").matcher(statement).find(), "primary key missing: " + table);
        }
        String compactSql = sql.toLowerCase(Locale.ROOT).replace("`", "").replaceAll("\\s+", "");
        assertTrue(columns(blocks.get("lab_task_quality_gate")).containsKey("evidence_id"), "quality gate evidence_id missing");
        assertTrue(columns(blocks.get("lab_task_block_event")).containsKey("episode_no"), "block episode_no missing");
        assertTrue(!columns(blocks.get("lab_skill")).get("active_unique_flag").toLowerCase(Locale.ROOT).contains("status"),
                "every undeleted skill must occupy its code and display name even while inactive");
        assertLogicalUniqueContracts(sql, blocks);
        assertNoPostCreateActiveUniquenessMigration(sql);
        assertLookupIndexes(blocks);
        assertSafeSeedCleanup(sql);
        assertDemoCredentials(sql);
        List<String> dictionaryPairs = dictPairList(sql);
        for (String pair : DICTS) assertTrue(dictionaryPairs.contains(pair.toLowerCase(Locale.ROOT)), "missing dict " + pair);
        Set<String> iprStages = new LinkedHashSet<String>();
        for (String pair : dictionaryPairs) if (pair.startsWith("lab_ipr_stage|")) iprStages.add(pair);
        assertEquals(set("lab_ipr_stage|draft", "lab_ipr_stage|preparing", "lab_ipr_stage|submitted",
                "lab_ipr_stage|accepted", "lab_ipr_stage|authorized"), iprStages,
                "IPR service stages and SQL dictionary must have one exact vocabulary");
        assertEquals(dictionaryPairs.size(), new HashSet<>(dictionaryPairs).size(), "duplicate dictionary type/value tuple");
        for (String permission : PERMISSIONS) assertTrue(sql.contains("'" + permission + "'"), "missing permission " + permission);
        assertEquals(set("TABLE","STAT","TEXT","MANUAL","GROUP_TEXT","CHART"), sectionTypes(sql), "section renderer types");
        assertJobSeeds(sql);
        assertTemplateSeed(sql);
        assertMemberSeeds(sql);
        assertLabRoleScopes(sql);
        assertGoalActivationPermissionIsManagerOnly(sql);
        assertLegacyUpgradeContract(sql);
        assertTaskDeletePermissionForTaskWriters(sql);
        assertTaskFivePermissions(sql);
        assertDemoTaskGoalLinks(sql);
        assertMenuArity(sql);
        assertAllInsertArities(sql);
        assertSqlLexicallyBalanced(sql);
    }

    @Test
    void coordinationFieldContractRejectsARequiredTaskFieldMutation() throws IOException {
        String sql = readSql();
        assertTaskCoordinationContract(sql);
        String mutation = sql.replaceFirst("`coordination_required`", "`coordination_required_removed`");
        assertThrows(AssertionError.class, () -> assertTaskCoordinationContract(mutation), "required coordination field mutation must fail");
    }

    @Test
    void seedCleanupContractRejectsUnsafeRangeMutationAndTestUsesJava8Apis() throws IOException {
        String sql = readSql();
        assertSafeSeedCleanup(sql);
        assertThrows(AssertionError.class, () -> assertSafeSeedCleanup(sql.replaceFirst("IN \\(30001\\)", "BETWEEN 30001 AND 30099")));
        String source = new String(Files.readAllBytes(findRoot().resolve("ruoyi-lab/src/test/java/com/ailab/system/sql/LabSqlContractTest.java")), StandardCharsets.UTF_8);
        for (String forbidden : Arrays.asList("Path" + ".of(", "Files" + ".readString(", "Stream" + ".toList(")) assertTrue(!source.contains(forbidden), "post-Java-8 API used: " + forbidden);
    }

    @Test
    void availableSystemUserQueryExcludesEveryExistingProfileIncludingInactiveHistory() throws IOException {
        String xml = new String(Files.readAllBytes(findRoot().resolve("ruoyi-lab/src/main/resources/mapper/lab/LabMemberMapper.xml")), StandardCharsets.UTF_8)
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        Matcher matcher = Pattern.compile("(?is)<select id=\"selectavailablesystemusers\".*?</select>").matcher(xml);
        assertTrue(matcher.find(), "available system user query missing");
        String query = matcher.group();
        assertTrue(query.contains("left join lab_member m on m.user_id=u.user_id and m.del_flag='0'"),
                "any existing member profile must be excluded so inactive history is explicitly reactivated");
        assertTrue(!query.contains("m.member_status='active'"), "inactive profiles must not reappear as add candidates");
    }

    @Test
    void skillNameConflictLockCoversEveryUndeletedStatus() throws IOException {
        String xml = new String(Files.readAllBytes(findRoot().resolve("ruoyi-lab/src/main/resources/mapper/lab/LabMemberMapper.xml")), StandardCharsets.UTF_8)
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        Matcher matcher = Pattern.compile("(?is)<select id=\"selectskillbynameforupdate\".*?</select>").matcher(xml);
        assertTrue(matcher.find(), "locking skill-name conflict query missing");
        String query = matcher.group();
        assertTrue(query.contains("s.skill_name=#{skillname}") && query.contains("s.del_flag='0'"),
                "skill-name conflicts must include all undeleted rows");
        assertTrue(!query.contains("s.status='active'") && query.contains("for update"),
                "conflict lookup must not exclude inactive skills and must lock the current name owner");
    }

    @Test
    void matrixLockingReadDoesNotJoinOrImplicitlyLockSkillRows() throws IOException {
        String xml = new String(Files.readAllBytes(findRoot().resolve("ruoyi-lab/src/main/resources/mapper/lab/LabMemberMapper.xml")), StandardCharsets.UTF_8)
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        Matcher matcher = Pattern.compile("(?is)<select id=\"selectmemberskillsforupdate\".*?</select>").matcher(xml);
        assertTrue(matcher.find(), "member-skill current locking read missing");
        String query = matcher.group();
        assertTrue(query.contains("from lab_member_skill ms"), "locking read must target member-skill rows");
        assertTrue(query.contains("order by ms.skill_id for update"), "member-skill rows must lock in skill id order");
        assertTrue(!query.contains(" join "), "locking current rows must not implicitly lock the skill table");
    }

    private static void assertJobSeeds(String sql) {
        List<List<String>> rows = insertRows(sql, "sys_job");
        List<List<String>> labRows = new ArrayList<>();
        for (List<String> row : rows) if (row.stream().anyMatch(v -> v.contains("labScheduleTask."))) labRows.add(row);
        assertEquals(5, labRows.size(), "exactly five AI-lab job rows required");
        Set<String> targets = new HashSet<>();
        for (List<String> row : labRows) { assertEquals("'0'", row.get(7), "job must be enabled"); targets.add(unquote(row.get(3))); }
        assertEquals(set("labScheduleTask.scanBlocks()","labScheduleTask.scanPendingTasks()","labScheduleTask.closeDuePeriods()","labScheduleTask.cleanReportTempFiles()","labScheduleTask.recoverReportJobs()"), targets, "exactly one row per job target");
    }

    private static void assertTemplateSeed(String sql) {
        List<List<String>> rows = insertRows(sql, "lab_report_template");
        List<String> standard = rows.stream().filter(r -> r.size() > 2 && "standard_month".equals(unquote(r.get(1)))).findFirst().orElseThrow(() -> new AssertionError("default template row missing"));
        assertEquals("MONTH", unquote(standard.get(3))); assertEquals("1", unquote(standard.get(5))); assertEquals("1", unquote(standard.get(6))); assertEquals("ENABLED", unquote(standard.get(7)));
    }

    private static void assertMemberSeeds(String sql) {
        List<List<String>> rows = insertRows(sql, "lab_member");
        assertEquals(6, rows.size(), "six demo members required");
        Set<String> userIds = new HashSet<>(); for (List<String> row : rows) userIds.add(row.get(1));
        assertEquals(6, userIds.size(), "demo members must use distinct sys_user ids");
    }

    private static void assertLabRoleScopes(String sql) {
        Map<String, String> scopes = new LinkedHashMap<>();
        for (List<String> row : insertRows(sql, "sys_role")) scopes.put(unquote(row.get(2)), row.get(4));
        assertEquals("1", scopes.get("lab_manager"), "manager must have all-data scope");
        assertEquals("2", scopes.get("lab_lead"), "lead must have custom department scope");
        assertEquals("5", scopes.get("lab_member"), "member must have self scope");
        List<List<String>> departments = insertRows(sql, "sys_role_dept");
        assertEquals(1, departments.size(), "lead custom scope must have one deterministic demo department");
        assertEquals("30002", departments.get(0).get(0));
        assertEquals("101", departments.get(0).get(1));
        assertTrue(sql.contains("DELETE FROM `sys_role_dept` WHERE `role_id` IN (30001,30002,30003);"),
                "role department cleanup must use exact lab role ids");
    }

    private static void assertGoalActivationPermissionIsManagerOnly(String sql) {
        assertTrue(sql.contains("'lab:goal:activate'"), "goal activation permission missing");
        assertTrue(roleMenuIds(sql, 30001L).contains("31023"), "manager must receive goal activation permission");
        assertTrue(!roleMenuIds(sql, 30002L).contains("31023"), "lead must not receive goal activation permission");
        assertTrue(!roleMenuIds(sql, 30003L).contains("31023"), "member must not receive goal activation permission");
    }

    private static void assertTaskDeletePermissionForTaskWriters(String sql) {
        assertTrue(roleMenuIds(sql, 30002L).contains("31032"), "lead must receive task delete permission");
        assertTrue(roleMenuIds(sql, 30003L).contains("31032"), "member must receive task delete permission");
    }

    private static void assertTaskFivePermissions(String sql) {
        Set<String> manager = roleMenuIds(sql, 30001L);
        Set<String> lead = roleMenuIds(sql, 30002L);
        Set<String> member = roleMenuIds(sql, 30003L);
        assertTrue(manager.containsAll(set("31050", "31060", "31061")), "manager must configure skills and write one-to-one records");
        assertTrue(!lead.contains("31050") && !member.contains("31050"), "only manager may configure skills");
        assertTrue(!lead.contains("31060") && !lead.contains("31061") && !member.contains("31060") && !member.contains("31061"),
                "one-to-one records are read-only for their talk subject");
    }

    private static void assertLegacyUpgradeContract(String sql) throws IOException {
        String compact = sql.toLowerCase(Locale.ROOT).replace("`", "").replaceAll("\\s+", " ");
        assertTrue(compact.contains("information_schema.columns"), "legacy column checks must use INFORMATION_SCHEMA");
        assertTrue(compact.contains("information_schema.statistics"), "legacy index checks must use INFORMATION_SCHEMA");
        assertTrue(compact.contains("prepare ailab_ddl") && compact.contains("execute ailab_ddl")
                && compact.contains("deallocate prepare ailab_ddl"), "conditional MySQL 8 DDL must use prepared dynamic SQL");
        assertTrue(Pattern.compile("(?is)alter\\s+table\\s+`?lab_task_quality_gate`?\\s+add\\s+column\\s+`?evidence_id`?\\s+bigint\\s+(?:default\\s+)?null").matcher(sql).find(),
                "legacy quality gate must add nullable evidence_id");
        assertTrue(Pattern.compile("(?is)alter\\s+table\\s+`?lab_task_block_event`?\\s+add\\s+column\\s+`?episode_no`?\\s+int\\s+(?:default\\s+)?null").matcher(sql).find(),
                "legacy block event must add nullable episode_no before backfill");
        assertTrue(compact.contains("row_number() over(partition by task_id order by block_start_time,id)"),
                "legacy block episodes require deterministic MySQL 8 row-number backfill");
        assertTrue(Pattern.compile("(?is)alter\\s+table\\s+`?lab_task_block_event`?\\s+modify\\s+column\\s+`?episode_no`?\\s+int\\s+not\\s+null").matcher(sql).find(),
                "episode_no must become NOT NULL after backfill");
        assertTrue(compact.contains("table_name='lab_one2one' and column_name='feedback'"),
                "legacy one-to-one feedback column must be detected before backfill");
        assertTrue(compact.contains("table_name='lab_one2one' and column_name='action_items'"),
                "legacy one-to-one action_items column must be detected before backfill");
        assertTrue(compact.contains("column_name='feedback') = 1, 'update lab_one2one set facts_evidence=coalesce(facts_evidence,feedback)', 'select 1'"),
                "legacy one-to-one feedback must be independently detected and backfilled");
        assertTrue(compact.contains("column_name='action_items') = 1, 'update lab_one2one set next_action=coalesce(next_action,action_items)', 'select 1'"),
                "legacy one-to-one action items must be independently detected and backfilled");
        assertTrue(compact.contains("table_name='lab_ipr' and column_name='application_no'"),
                "legacy IPR application_no column must be detected before backfill");
        assertTrue(compact.contains("table_name='lab_ipr' and column_name='submit_date'"),
                "legacy IPR submit_date column must be detected before backfill");
        assertTrue(compact.contains("column_name='application_no') = 1, 'update lab_ipr set acceptance_no=coalesce(acceptance_no,application_no)', 'select 1'"),
                "legacy IPR application number must be independently detected and backfilled");
        assertTrue(compact.contains("column_name='submit_date') = 1, 'update lab_ipr set actual_submit_date=coalesce(actual_submit_date,submit_date)', 'select 1'"),
                "legacy IPR submit date must be independently detected and backfilled");
        assertTrue(compact.contains("update lab_ipr set ipr_stage='draft' where ipr_stage='drafting'"),
                "legacy draft stage must migrate into the configured IPR vocabulary");
        assertTrue(compact.contains("alter table lab_ipr alter column ipr_stage set default ''draft''"),
                "upgraded IPR columns must not keep producing the removed DRAFTING alias");
        String assetVersionBackfill = "update lab_asset set asset_version=asset_no where (asset_version is null or trim(asset_version)='')";
        String assetBusinessIndex = "alter table lab_asset add unique index uk_lab_asset_business (asset_name,asset_version,asset_type,active_unique_flag)";
        assertTrue(compact.contains(assetVersionBackfill),
                "legacy empty asset versions must derive from the already unique asset number");
        assertTrue(compact.indexOf(assetVersionBackfill) < compact.indexOf(assetBusinessIndex),
                "legacy asset versions must be repaired before the business unique index is added");
        assertTrue(compact.contains("row_number() over(partition by skill_name order by id)"),
                "active duplicate legacy skill names require deterministic first-row ranking");
        assertTrue(compact.contains("concat(left(skill_name,35),' [',left(skill_code,40),':',id,']')"),
                "later duplicate skill names require a stable bounded display-name suffix");
        assertTrue(compact.contains("concat('[ailab-legacy:',id,':',left(skill_code,32),'] ',left(skill_name,30))"),
                "generated legacy names that collide globally require a second deterministic code/id suffix");
        assertTrue(compact.contains("row_number() over(partition by skill_code order by id)"),
                "status-scoped legacy skill codes must be ranked before widening uniqueness");
        assertTrue(compact.contains("concat(left(skill_code,35),'-legacy-',id)"),
                "later duplicate skill codes require a stable bounded id suffix");
        assertTrue(compact.contains("concat('ailab-legacy-',id,'-',left(skill_code,29))"),
                "generated legacy skill-code collisions require a second bounded repair");
        assertTrue(compact.indexOf("row_number() over(partition by skill_code order by id)")
                        != compact.lastIndexOf("row_number() over(partition by skill_code order by id)"),
                "skill-code migration needs a second global collision pass");
        assertTrue(!compact.contains("from lab_skill where del_flag='0' and status='active'"),
                "both legacy skill-name repair passes must include inactive undeleted rows");
        String skillFlagMigration = "alter table lab_skill modify column active_unique_flag tinyint generated always as (case when del_flag = ''0'' then 1 else null end)";
        assertTrue(compact.contains(skillFlagMigration),
                "upgrades from status-scoped uniqueness must converge on undeleted-row uniqueness");
        assertTrue(compact.indexOf("row_number() over(partition by skill_name order by id)")
                        != compact.lastIndexOf("row_number() over(partition by skill_name order by id)"),
                "legacy skill repair must rerank names after the initial rename to catch generated-name collisions");
        String skillNameIndex = "alter table lab_skill add unique index uk_lab_skill_name (skill_name,active_unique_flag)";
        String skillCodeIndex = "alter table lab_skill add unique index uk_lab_skill_code (skill_code,active_unique_flag)";
        String dropSkillNameIndex = "alter table lab_skill drop index uk_lab_skill_name";
        String dropSkillCodeIndex = "alter table lab_skill drop index uk_lab_skill_code";
        int flagMigrationPosition = compact.indexOf(skillFlagMigration);
        int firstSkillRepairPosition = Math.min(compact.indexOf("row_number() over(partition by skill_name order by id)"),
                compact.indexOf("row_number() over(partition by skill_code order by id)"));
        assertTrue(compact.contains(dropSkillNameIndex) && compact.contains(dropSkillCodeIndex),
                "status-scoped skill unique indexes must be conditionally removed before collision repair");
        assertTrue(compact.indexOf(dropSkillNameIndex) < firstSkillRepairPosition
                        && compact.indexOf(dropSkillCodeIndex) < firstSkillRepairPosition,
                "legacy unique indexes must be removed before temporary repair values can collide");
        assertTrue(compact.lastIndexOf("row_number() over(partition by skill_name order by id)") < flagMigrationPosition,
                "both skill-name repair passes must finish before widening the generated flag");
        assertTrue(compact.lastIndexOf("row_number() over(partition by skill_code order by id)") < flagMigrationPosition,
                "both skill-code repair passes must finish before widening the generated flag");
        assertTrue(flagMigrationPosition < compact.indexOf(skillNameIndex),
                "the generated flag must be widened before the final name index is added");
        assertTrue(flagMigrationPosition < compact.indexOf(skillCodeIndex),
                "the generated flag must be widened before the final code index is added");
        assertTrue(Files.isRegularFile(findRoot().resolve("sql/test/ailab-legacy-fixture.sql")),
                "legacy MySQL fixture is required");
        String legacy = new String(Files.readAllBytes(findRoot().resolve("sql/test/ailab-legacy-fixture.sql")), StandardCharsets.UTF_8)
                .toLowerCase(Locale.ROOT);
        assertTrue(legacy.contains("create table `lab_task_quality_gate`") && !legacy.contains("`evidence_id`"),
                "legacy quality gate fixture must predate evidence_id");
        assertTrue(legacy.contains("create table `lab_task_block_event`") && !legacy.contains("`episode_no`"),
                "legacy block fixture must predate episode_no");
        assertTrue(legacy.contains("create table `lab_one2one`") && legacy.contains("`feedback`") && legacy.contains("`action_items`")
                        && !legacy.contains("`facts_evidence`"),
                "legacy one-to-one fixture must contain the preceding content columns");
        assertTrue(legacy.contains("create table `lab_ipr`") && legacy.contains("`application_no`") && legacy.contains("`submit_date`")
                        && !legacy.contains("`acceptance_no`"),
                "legacy IPR fixture must contain the preceding filing columns");
        assertTrue(legacy.contains("create table `lab_asset`") && !legacy.contains("`asset_version`"),
                "legacy asset fixture must predate business versions");
        assertTrue(legacy.indexOf("legacy shared asset") != legacy.lastIndexOf("legacy shared asset")
                        && legacy.contains("legacy-asset-a") && legacy.contains("legacy-asset-b"),
                "legacy asset fixture must contain a real business-key collision");
        Matcher legacySkillBlockMatcher = Pattern.compile("(?is)create table `lab_skill`.*?engine=.*?;").matcher(legacy);
        assertTrue(legacySkillBlockMatcher.find(), "legacy skill table fixture missing");
        String legacySkillBlock = legacySkillBlockMatcher.group();
        assertTrue(legacySkillBlock.contains("case when `del_flag` = '0' and `status` = 'active' then 1 else null end"),
                "legacy skill fixture must begin with status-scoped uniqueness");
        assertTrue(legacySkillBlock.contains("unique key `uk_lab_skill_code`")
                        && legacySkillBlock.contains("unique key `uk_lab_skill_name`"),
                "legacy fixture must retain both status-scoped unique indexes during upgrade");
        assertTrue(legacy.contains("create table `lab_skill`") && legacy.indexOf("legacy duplicate skill") != legacy.lastIndexOf("legacy duplicate skill")
                        && legacy.contains("legacy-skill-a") && legacy.contains("legacy-skill-b") && legacy.contains("legacy-skill-c")
                        && legacy.contains("legacy duplicate skill [legacy-skill-b:39994]")
                        && legacy.contains("legacy-skill-d") && legacy.contains("legacy-skill-f")
                        && legacy.contains("legacy-skill-d-legacy-39997")
                        && legacy.contains("'2','it'"),
                "legacy skill fixture must cover active/inactive, inactive/inactive and deleted-name collisions");
        assertTrue(legacy.contains("legacy one-to-one feedback") && legacy.contains("legacy action item")
                        && legacy.contains("legacy-application-39991") && legacy.contains("2026-06-15"),
                "legacy Task 5 fixture must seed history for real MySQL upgrade assertions");
    }

    private static Set<String> roleMenuIds(String sql, long roleId) {
        Matcher matcher = Pattern.compile("(?is)INSERT\\s+INTO\\s+`sys_role_menu`.*?SELECT\\s+" + roleId
                + ",`menu_id`.*?IN\\s*\\(([^)]*)\\);").matcher(sql);
        assertTrue(matcher.find(), "role menu seed missing for role " + roleId);
        Set<String> result = new LinkedHashSet<>();
        for (String id : matcher.group(1).split(",")) result.add(id.trim());
        return result;
    }

    private static void assertDemoTaskGoalLinks(String sql) {
        Map<String, List<String>> expected = new LinkedHashMap<>();
        expected.put("30001", Arrays.asList("30001", "30002"));
        expected.put("30002", Arrays.asList("30001", "30002"));
        expected.put("30003", Arrays.asList("30001", "30003"));
        expected.put("30004", Arrays.asList("30001", "30003"));
        expected.put("30005", Arrays.asList("30001", "30004"));
        Map<String, List<String>> actual = new LinkedHashMap<>();
        for (List<String> row : insertRows(sql, "lab_task")) {
            if (expected.containsKey(row.get(0))) actual.put(row.get(0), Arrays.asList(row.get(2), row.get(3)));
        }
        assertEquals(expected, actual, "demo tasks must reference the annual goal and their quarterly milestone");
    }

    private static void assertMenuArity(String sql) {
        Matcher matcher = Pattern.compile("(?is)INSERT\\s+INTO\\s+`sys_menu`\\s*\\((.*?)\\)\\s*VALUES\\s*(.*?);").matcher(sql);
        assertTrue(matcher.find(), "sys_menu seed missing");
        int columns = splitValues(matcher.group(1)).size();
        for (List<String> row : tuples(matcher.group(2))) assertEquals(columns, row.size(), "sys_menu row arity");
    }

    private static void assertAllInsertArities(String sql) {
        Matcher matcher = Pattern.compile("(?is)INSERT\\s+INTO\\s+`?[a-z0-9_]+`?\\s*\\(([^;]*?)\\)\\s*VALUES\\s*([^;]*?);").matcher(sql);
        int statements = 0;
        while (matcher.find()) {
            statements++;
            int columns = splitValues(matcher.group(1)).size();
            for (List<String> row : tuples(matcher.group(2))) assertEquals(columns, row.size(), "INSERT values arity");
        }
        assertTrue(statements > 20, "expected a substantive set of VALUES inserts");
    }

    private static void assertSqlLexicallyBalanced(String sql) {
        boolean quote = false;
        int parentheses = 0;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'' && (i == 0 || sql.charAt(i - 1) != '\\')) quote = !quote;
            if (!quote && c == '(') parentheses++;
            if (!quote && c == ')') {
                parentheses--;
                assertTrue(parentheses >= 0, "unbalanced closing parenthesis");
            }
        }
        assertTrue(!quote, "unclosed SQL string literal");
        assertEquals(0, parentheses, "unbalanced SQL parentheses");
    }

    private static void assertTaskCoordinationContract(String sql) {
        Set<String> taskColumns = columns(tableBlocks(sql).get("lab_task")).keySet();
        assertTrue(taskColumns.containsAll(set("coordination_required", "coordination_owner_id", "coordination_dept_id", "coordination_content", "coordination_support")), "task coordination fields missing");
    }
    private static void assertNoPostCreateActiveUniquenessMigration(String sql) { assertTrue(!Pattern.compile("(?is)ALTER\\s+TABLE\\s+`?lab_[a-z0-9_]+`?\\s+ADD\\s+COLUMN\\s+`?active_unique_flag").matcher(sql).find(), "active uniqueness must be declared in CREATE TABLE"); }

    private static String readSql() throws IOException { return new String(Files.readAllBytes(findRoot().resolve("sql/ailab.sql")), StandardCharsets.UTF_8); }
    private static void assertLookupIndexes(Map<String, String> blocks) { for (String contract : LOOKUP_INDEXES) { String[] parts = contract.split("\\|", 2); String compact = blocks.get(parts[0]).toLowerCase(Locale.ROOT).replace("`", "").replaceAll("\\s+", ""); assertTrue(compact.contains(parts[1]), "lookup index missing in table " + contract); } }
    private static void assertLogicalUniqueContracts(String sql, Map<String, String> blocks) { String compact = sql.toLowerCase(Locale.ROOT).replace("`", "").replaceAll("\\s+", ""); for (String contract : uniqueContracts()) assertTrue(compact.contains(contract), "unique contract missing: " + contract); Matcher unique = Pattern.compile("(?is)unique\\s+key\\s+`?[^` (]+`?\\s*\\(([^)]*)\\)").matcher(sql); while (unique.find()) assertTrue(!unique.group(1).toLowerCase(Locale.ROOT).contains("del_flag"), "unique key cannot include del_flag"); for (String table : logicalUniqueTables()) assertTrue(blocks.get(table).contains("active_unique_flag") && blocks.get(table).contains("GENERATED ALWAYS AS"), "generated active uniqueness flag missing: " + table); }
    private static void assertSafeSeedCleanup(String sql) { assertTrue(!Pattern.compile("(?is)delete\\s+from[^;]+between").matcher(sql).find(), "range cleanup is unsafe"); for (Map.Entry<String, String> entry : seedIds().entrySet()) assertTrue(sql.contains("DELETE FROM `" + entry.getKey() + "` WHERE `id` IN (" + entry.getValue() + ");"), "exact cleanup IDs missing: " + entry.getKey()); }
    private static void assertDemoCredentials(String sql) { List<List<String>> rows = insertRows(sql, "sys_user"); assertEquals(6, rows.size(), "six demo users"); Set<String> hashes = new HashSet<>(); String baseline = "$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2"; for (List<String> row : rows) { assertEquals("'1'", row.get(10), "demo account disabled"); String hash = unquote(row.get(9)); assertTrue(hash.matches("\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}"), "bcrypt format"); assertTrue(!baseline.equals(hash), "baseline admin hash forbidden"); hashes.add(hash); } assertEquals(6, hashes.size(), "demo hashes distinct"); }
    private static Map<String, String> seedIds() { Map<String, String> ids = new LinkedHashMap<>(); ids.put("lab_report_job","30001"); ids.put("lab_report_instance","30001"); ids.put("lab_report_summary","30001,30002"); ids.put("lab_report_section","30001,30002,30003,30004,30005,30006"); ids.put("lab_report_template","30001"); ids.put("lab_period_close","30001"); ids.put("lab_perf_score","30001,30002,30003"); ids.put("lab_collaboration_record","30001"); ids.put("lab_ipr","30001,30002"); ids.put("lab_one2one","30001"); ids.put("lab_member_skill","30001,30002,30003,30004,30005"); ids.put("lab_skill","30001,30002,30003"); ids.put("lab_reminder","30001,30002"); ids.put("lab_task_block_event","30001,30002"); ids.put("lab_task_quality_gate","30001,30002"); ids.put("lab_task_evidence","30001,30002"); ids.put("lab_task","30001,30002,30003,30004,30005"); ids.put("lab_goal","30001,30002,30003,30004"); ids.put("lab_asset","30001,30002,30003"); ids.put("lab_member","30001,30002,30003,30004,30005,30006"); return ids; }
    private static Set<String> logicalUniqueTables() { return set("lab_goal","lab_task_quality_gate","lab_reminder","lab_asset","lab_member","lab_skill","lab_member_skill","lab_ipr","lab_perf_score","lab_period_close","lab_report_template","lab_report_section","lab_report_summary","lab_report_instance","lab_report_job"); }

    private static Map<String, String> tableBlocks(String sql) {
        Map<String, String> result = new LinkedHashMap<>(); Matcher m = Pattern.compile("(?is)(CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?(lab_[a-z0-9_]+)`?\\s*\\(.*?\\)\\s*ENGINE=.*?;)").matcher(sql);
        while (m.find()) result.put(m.group(2).toLowerCase(Locale.ROOT), m.group(1)); return result;
    }
    private static Map<String, String> columns(String statement) {
        int open = statement.indexOf('('); int close = statement.lastIndexOf(") ENGINE"); String body = statement.substring(open + 1, close); Map<String, String> result = new LinkedHashMap<>();
        Matcher m = Pattern.compile("(?is)(?:^|,)\\s*`([a-z0-9_]+)`\\s+(.*?)(?=,\\s*`|,\\s*(?:PRIMARY|UNIQUE|KEY)\\b|$)").matcher(body); while (m.find()) result.put(m.group(1), m.group(2)); return result;
    }
    private static List<String> dictPairList(String sql) { List<String> pairs = new ArrayList<>(); Matcher m = Pattern.compile("(?im)\\(\\s*\\d+\\s*,\\s*\\d+\\s*,\\s*'[^']*'\\s*,\\s*'([^']*)'\\s*,\\s*'(lab_[^']*)'").matcher(sql); while (m.find()) pairs.add((m.group(2) + "|" + m.group(1)).toLowerCase(Locale.ROOT)); return pairs; }
    private static Set<String> sectionTypes(String sql) { Set<String> result = new LinkedHashSet<>(); for (List<String> row : insertRows(sql, "lab_report_section")) result.add(unquote(row.get(4))); return result; }
    private static List<List<String>> insertRows(String sql, String table) { Matcher m = Pattern.compile("(?is)INSERT\\s+INTO\\s+`" + Pattern.quote(table) + "`\\s*\\((.*?)\\)\\s*VALUES\\s*(.*?);").matcher(sql); List<List<String>> result = new ArrayList<>(); while (m.find()) result.addAll(tuples(m.group(2))); return result; }
    private static List<List<String>> tuples(String values) { List<List<String>> result = new ArrayList<>(); boolean quote = false; int depth = 0, start = -1; for (int i = 0; i < values.length(); i++) { char c = values.charAt(i); if (c == '\'' && (i == 0 || values.charAt(i - 1) != '\\')) quote = !quote; if (!quote && c == '(') { if (depth++ == 0) start = i + 1; } if (!quote && c == ')' && --depth == 0) result.add(splitValues(values.substring(start, i))); } return result; }
    private static List<String> splitValues(String value) { List<String> result = new ArrayList<>(); boolean quote = false; int depth = 0, start = 0; for (int i = 0; i < value.length(); i++) { char c = value.charAt(i); if (c == '\'' && (i == 0 || value.charAt(i - 1) != '\\')) quote = !quote; if (!quote && c == '(') depth++; else if (!quote && c == ')') depth--; else if (!quote && depth == 0 && c == ',') { result.add(value.substring(start, i).trim()); start = i + 1; } } result.add(value.substring(start).trim()); return result; }
    private static String unquote(String value) { return value.replaceAll("^'|'$", ""); }
    private static Set<String> set(String... items) { return new LinkedHashSet<>(Arrays.asList(items)); }
    private static Set<String> uniqueContracts() { return set("uniquekeyuk_lab_goal_year_no(year,goal_no,active_unique_flag)","uniquekeyuk_lab_gate_task_no(task_id,gate_no,active_unique_flag)","uniquekeyuk_lab_block_task_episode(task_id,episode_no)","uniquekeyuk_lab_reminder_idempotency(idempotency_key,active_unique_flag)","uniquekeyuk_lab_asset_no(asset_no,active_unique_flag)","uniquekeyuk_lab_asset_business(asset_name,asset_version,asset_type,active_unique_flag)","uniquekeyuk_lab_member_user(user_id,active_unique_flag)","uniquekeyuk_lab_member_no(member_no,active_unique_flag)","uniquekeyuk_lab_skill_code(skill_code,active_unique_flag)","uniquekeyuk_lab_skill_name(skill_name,active_unique_flag)","uniquekeyuk_lab_member_skill(member_id,skill_id,active_unique_flag)","uniquekeyuk_lab_ipr_no(ipr_no,active_unique_flag)","uniquekeyuk_lab_perf_member_period_rev(member_id,period,revision_no,active_unique_flag)","uniquekeyuk_lab_period_close_period(period,active_unique_flag)","uniquekeyuk_lab_report_tpl_code_rev(template_code,revision_no,active_unique_flag)","uniquekeyuk_lab_report_section(template_id,section_code,active_unique_flag)","uniquekeyuk_lab_report_summary(period,biz_line,section_code,active_unique_flag)","uniquekeyuk_lab_report_instance_no(report_no,active_unique_flag)","uniquekeyuk_lab_report_instance_period_rev(template_id,period,biz_line,revision_no,active_unique_flag)","uniquekeyuk_lab_report_job_no(job_no,active_unique_flag)","uniquekeyuk_lab_report_job_idempotency(idempotency_key,active_unique_flag)"); }
    private static Set<String> lookupIndexes() { return set("lab_goal|keyidx_lab_goal_parent(parent_id)","lab_goal|keyidx_lab_goal_owner_status(owner_id,status)","lab_goal|keyidx_lab_goal_year_status(year,status)","lab_task|keyidx_lab_task_parent(parent_id)","lab_task|keyidx_lab_task_goal(goal_id)","lab_task|keyidx_lab_task_milestone(milestone_id)","lab_task|keyidx_lab_task_owner_period_workflow(owner_id,period,workflow_status)","lab_task|keyidx_lab_task_period_workflow(period,workflow_status,period_lock_flag)","lab_task|keyidx_lab_task_dept(dept_id)","lab_task_evidence|keyidx_lab_evidence_task(task_id)","lab_task_quality_gate|keyidx_lab_gate_task_status(task_id,gate_status)","lab_task_quality_gate|keyidx_lab_gate_evidence(evidence_id)","lab_task_block_event|keyidx_lab_block_task_open(task_id,block_status,block_start_time)","lab_task_block_event|keyidx_lab_block_status_start(block_status,block_start_time,task_id,episode_no)","lab_reminder|keyidx_lab_reminder_recipient_read_date(recipient_id,read_flag,reminder_date)","lab_reminder|keyidx_lab_reminder_task_episode(task_id,episode_no,reminder_level)","lab_asset|keyidx_lab_asset_primary_status(primary_owner_id,status)","lab_member|keyidx_lab_member_line_status(biz_line,member_status)","lab_member_skill|keyidx_lab_member_skill_member(member_id)","lab_one2one|keyidx_lab_one2one_member_date(member_id,meeting_date)","lab_ipr|keyidx_lab_ipr_owner_stage(owner_id,ipr_stage)","lab_collaboration_record|keyidx_lab_collab_period_id(period,id)","lab_collaboration_record|keyidx_lab_collab_member_period_status(to_member_id,period,review_status)","lab_perf_score|keyidx_lab_perf_member_period_current(member_id,period,current_flag)","lab_report_section|keyidx_lab_report_section_tpl_sort(template_id,sort_no)","lab_report_summary|keyidx_lab_report_summary_period(period,biz_line)","lab_report_instance|keyidx_lab_report_instance_template_pin(template_code,template_revision)","lab_report_instance|keyidx_lab_report_instance_tpl_period_lifecycle(template_id,period,lifecycle_status)","lab_report_job|keyidx_lab_report_job_instance_status(report_id,job_status)"); }
    private static Map<String, Set<String>> fields() { Map<String, Set<String>> map = new LinkedHashMap<>(); map.put("lab_goal",set("parent_id","goal_level","year","period","goal_no","owner_id","weight","progress_rate","status","version")); map.put("lab_task",set("parent_id","goal_id","milestone_id","task_level","period","biz_line","task_type","owner_id","perf_weight","goal_weight","workflow_status","result_status","asset_id","coordination_required","coordination_owner_id","coordination_dept_id","coordination_content","coordination_support","current_block_flag","period_lock_flag","version")); map.put("lab_task_evidence",set("task_id","evidence_json","submitter_id","audit_status")); map.put("lab_task_quality_gate",set("task_id","gate_no","gate_status")); map.put("lab_task_block_event",set("task_id","block_start_time","block_end_time","block_status")); map.put("lab_reminder",set("task_id","recipient_id","read_flag","idempotency_key")); map.put("lab_asset",set("asset_name","asset_version","primary_owner_id","backup_owner_id","test_set_url","deploy_package_url","document_url","critical_flag","status","version")); map.put("lab_member",set("user_id","position","biz_line","leader_id","primary_responsibilities","backup_responsibilities","member_status","version")); map.put("lab_skill",set("skill_code","skill_name","skill_category","status","version")); map.put("lab_member_skill",set("member_id","skill_id","skill_level","last_verified_date","evidence_url","version")); map.put("lab_one2one",set("member_id","leader_id","meeting_date","facts_evidence","difficulties","next_action","manager_comment","version")); map.put("lab_ipr",set("ipr_type","ipr_stage","owner_id","planned_submit_date","actual_submit_date","acceptance_no","certificate_no","status","stage_change_reason","version")); map.put("lab_collaboration_record",set("task_id","to_member_id","period","category","signed_score","evidence_url","reviewer_id","review_status")); map.put("lab_perf_score",set("member_id","period","revision_no","current_flag","detail_json","red_line_flag","revoked_flag","calibration_status")); map.put("lab_period_close",set("period","close_by","close_time","reopen_by","reopen_time","version")); map.put("lab_report_template",set("template_code","period_type","revision_no","latest_flag","default_flag","status","header_json","style_json","version")); map.put("lab_report_section",set("template_id","section_type","query_config_json","render_config_json","style_config_json","manual_flag","visible_flag","sensitive_flag","sensitive_permission","version")); map.put("lab_report_summary",set("period","biz_line","section_code","summary_json")); map.put("lab_report_instance",set("template_id","template_code","template_revision","period","revision_no","lifecycle_status","current_flag","final_flag","sensitive_flag","source_data_json","source_perf_revision","content_json","content_markdown","json_status","json_path","json_error","markdown_status","markdown_path","markdown_error","word_status","word_path","word_error","pdf_status","pdf_path","pdf_error","version")); map.put("lab_report_job",set("report_id","job_type","job_status","progress_rate","attempt_count","error_message","idempotency_key","version")); return map; }
    private static Path findRoot() { for (Path p = Paths.get(System.getProperty("user.dir")).toAbsolutePath(); p != null; p = p.getParent()) if (Files.isRegularFile(p.resolve("pom.xml")) && Files.isDirectory(p.resolve("ruoyi-lab"))) return p; throw new IllegalStateException("Cannot locate repository root"); }
}
