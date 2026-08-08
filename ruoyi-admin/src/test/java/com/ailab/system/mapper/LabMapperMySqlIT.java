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
import com.ailab.system.domain.LabCollaborationRecord;
import com.ailab.system.domain.LabReminder;
import com.ailab.system.domain.LabReportInstance;
import com.ailab.system.domain.LabReportJob;
import com.ailab.system.report.ReportJobQueuePersistence;
import com.ailab.system.dto.CollaborationReviewCommand;
import com.ailab.system.dto.DashboardOverview;
import com.ailab.system.dto.GoalHealthFact;
import com.ailab.system.dto.GoalTrendPoint;
import com.ailab.system.dto.ReminderCandidate;
import com.ailab.system.dto.LabAccessContext;
import com.ailab.system.dto.PerformanceAssetFact;
import com.ailab.system.dto.PerformanceCalculationInput;
import com.ailab.system.dto.PerformanceCalculationResult;
import com.ailab.system.dto.RedLineRevokeCommand;
import com.ailab.system.dto.TaskSubmitCommand;
import com.ailab.system.service.LabAccessService;
import com.ailab.system.service.LabGoalService;
import com.ailab.system.service.LabLedgerService;
import com.ailab.system.service.LabMemberService;
import com.ailab.system.service.LabPerformanceCalculator;
import com.ailab.system.service.LabTaskService;
import com.ailab.system.service.LabPerformanceService;
import com.ailab.system.service.LabDashboardService;
import com.ailab.system.service.LabReminderService;
import com.ailab.system.report.model.ReportContext;
import com.ailab.system.report.model.ReportQueryCriteria;
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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.Supplier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
    @Autowired private LabDashboardMapper dashboardMapper;
    @Autowired private LabDashboardService dashboardService;
    @Autowired private LabReminderService reminderService;
    @Autowired private LabReportDataMapper reportDataMapper;
    @Autowired private LabReportMapper reportMapper;
    @Autowired private ReportJobQueuePersistence reportJobQueuePersistence;
    @Autowired private ISysUserService userService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void remindersDashboardScopeAndAggregatesUseRealMySqlContracts() {
        long taskId = 39890L;
        jdbcTemplate.update("insert into lab_task(id,parent_id,goal_id,milestone_id,task_level,period,biz_line,task_type,title,owner_id,dept_id,plan_date,deliverable,perf_weight,goal_weight,workflow_status,result_status,result_desc,coordination_required,coordination_owner_id,current_block_flag,current_block_start,period_lock_flag,version,del_flag,create_by,create_time) values(?,0,39001,39002,'month','2026-08','algorithm','key','IT dashboard blocked task',39203,101,current_date,'IT artifact',20,10,'ACTIVE','DOING','working','1',39202,'1',date_sub(now(),interval 14 day),'0',0,'0','it',now())", taskId);
        jdbcTemplate.update("insert into lab_task_block_event(task_id,episode_no,block_type,block_reason,block_start_time,block_status,del_flag,create_by,create_time) values(?,1,'DEPENDENCY','IT blocker',date_sub(now(),interval 14 day),'OPEN','0','it',now())", taskId);

        int first = reminderService.scanBlocks();
        int second = reminderService.scanBlocks();
        assertEquals(2, first, "14-day episode must create only owner warning and manager critical reminders");
        assertEquals(0, second, "same date/episode/recipient/level must be idempotent");
        assertEquals(1, jdbcTemplate.queryForObject("select count(1) from lab_reminder where task_id=? and episode_no=1 and recipient_id=39203 and reminder_level='WARNING' and del_flag='0'", Integer.class, taskId));
        assertEquals(1, jdbcTemplate.queryForObject("select count(1) from lab_reminder where task_id=? and episode_no=1 and recipient_id=39201 and reminder_level='CRITICAL' and del_flag='0'", Integer.class, taskId));
        assertEquals(0, jdbcTemplate.queryForObject("select count(1) from lab_reminder where task_id=? and episode_no=1 and recipient_id=39202 and del_flag='0'", Integer.class, taskId));

        LabReminder ownerReminder = dashboardMapper.selectReminderList(accessService.context(39103L), true).stream()
                .filter(reminder -> Long.valueOf(taskId).equals(reminder.getTaskId())).findFirst().orElseThrow(() -> new AssertionError("owner reminder missing"));
        assertThrows(ServiceException.class, () -> reminderService.markRead(ownerReminder.getId(), ownerReminder.getVersion(), 39101L),
                "manager cannot mark another recipient's notification read");
        reminderService.markRead(ownerReminder.getId(), ownerReminder.getVersion(), 39103L);
        assertEquals("1", jdbcTemplate.queryForObject("select read_flag from lab_reminder where id=?", String.class, ownerReminder.getId()));

        jdbcTemplate.update("update lab_task_block_event set block_status='CLOSED',block_end_time=now() where task_id=? and episode_no=1", taskId);
        jdbcTemplate.update("update lab_task set current_block_flag='0',current_block_start=null where id=?", taskId);
        int beforeResolvedScan = jdbcTemplate.queryForObject("select count(1) from lab_reminder where task_id=?", Integer.class, taskId);
        reminderService.scanBlocks();
        assertEquals(beforeResolvedScan, jdbcTemplate.queryForObject("select count(1) from lab_reminder where task_id=?", Integer.class, taskId));

        jdbcTemplate.update("update lab_task set current_block_flag='1',current_block_start=date_sub(now(),interval 7 day) where id=?", taskId);
        jdbcTemplate.update("insert into lab_task_block_event(task_id,episode_no,block_type,block_reason,block_start_time,block_status,del_flag,create_by,create_time) values(?,2,'DEPENDENCY','new IT blocker',date_sub(now(),interval 7 day),'OPEN','0','it',now())", taskId);
        reminderService.scanBlocks();
        assertEquals(1, jdbcTemplate.queryForObject("select count(1) from lab_reminder where task_id=? and episode_no=2 and recipient_id=39203 and reminder_level='WARNING' and del_flag='0'", Integer.class, taskId));

        jdbcTemplate.update("insert into lab_report_instance(id,report_no,template_id,template_code,template_revision,period,biz_line,revision_no,lifecycle_status,current_flag,final_flag,sensitive_flag,json_status,markdown_status,word_status,pdf_status,version,del_flag,create_by,create_time) values(39870,'IT-RPT-2026-08',30001,'standard_month',1,'2026-08','ALL',1,'FINAL','1','1','0','READY','READY','READY','READY',0,'0','it',now())");
        DashboardOverview manager = dashboardService.getOverview("2026-08", 39101L);
        DashboardOverview lead = dashboardService.getOverview("2026-08", 39102L);
        DashboardOverview member = dashboardService.getOverview("2026-08", 39103L);
        assertEquals(5, manager.getKpis().size());
        assertTrue(manager.getMemberLoads().size() >= lead.getMemberLoads().size());
        assertTrue(lead.getMemberLoads().stream().allMatch(load -> "algorithm".equals(load.getBizLine())));
        assertTrue(member.getMemberLoads().isEmpty() && member.getCoordinationItems().isEmpty() && member.getPerformanceSummary().isEmpty());
        assertTrue(member.getRecentReports().stream().anyMatch(item -> Long.valueOf(39870L).equals(item.getId())));
        assertTrue(member.getRecentIpr().stream().anyMatch(item -> Long.valueOf(39301L).equals(item.getId())));
        assertTrue(dashboardService.getOverview("2026-07", 39101L).getRecentReports().stream()
                .anyMatch(item -> Long.valueOf(30001L).equals(item.getId())), "current FINAL/READY demo report must remain visible to manager");
        assertTrue(manager.getTaskStatusDistribution().stream().anyMatch(item -> "ACTIVE".equals(item.getCode())));
        assertEquals(taskId, manager.getCoordinationItems().stream().filter(item -> Long.valueOf(taskId).equals(item.getId())).findFirst().orElseThrow(() -> new AssertionError("coordination item missing")).getId().longValue());
    }

    @AfterEach
    void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    @Test
    void filtersByTrustedTypedOwnerBusinessLineAndPeriodAndIgnoresSqlFragments() {
        LabTask query = new LabTask();
        query.setOwnerId(39202L); query.setBizLine("algorithm"); query.setPeriod("2026-01");
        query.getParams().put("dataScope", " AND t.dept_id = 101 ");

        List<LabTask> rows = taskMapper.selectTaskList(query);

        assertEquals(1, rows.size());
        assertEquals(Long.valueOf(39001L), rows.get(0).getId());
        query.getParams().put("dataScope", " AND t.dept_id = 999 ");
        assertEquals(1, taskMapper.selectTaskList(query).size(), "client SQL fragments must never change mapper scope");
    }

    @Test
    void reportProjectionMappersBindNormalizedAndNextPeriodsWithRealMaps() {
        long currentTask = 39895L, nextTask = 39896L;
        jdbcTemplate.update("insert into lab_task(id,parent_id,goal_id,milestone_id,task_level,period,biz_line,task_type,title,owner_id,dept_id,plan_date,deliverable,perf_weight,goal_weight,workflow_status,result_status,coordination_required,current_block_flag,period_lock_flag,version,del_flag,create_by,create_time) values(?,0,39001,39002,'month','2026-08','algorithm','key','IT report current',39203,101,'2026-08-15','report',1,1,'ACTIVE','DOING','0','0','0',0,'0','it',now())", currentTask);
        jdbcTemplate.update("insert into lab_task(id,parent_id,goal_id,milestone_id,task_level,period,biz_line,task_type,title,owner_id,dept_id,plan_date,deliverable,perf_weight,goal_weight,workflow_status,result_status,coordination_required,current_block_flag,period_lock_flag,version,del_flag,create_by,create_time) values(?,0,39001,39002,'month','2026-09','algorithm','key','IT report next',39203,101,'2026-09-15','report',1,1,'ACTIVE','DOING','0','0','0',0,'0','it',now())", nextTask);
        ReportContext context = new ReportContext("2026-08", "algorithm", 39203L, new Date().toInstant(), Collections.<String,Object>emptyMap());
        ReportQueryCriteria criteria = new ReportQueryCriteria("2026-08", context.getAccessScope());
        Map<String,Object> current = reportDataMapper.selectTasks(criteria).stream().filter(item -> Long.valueOf(currentTask).equals(((Number)item.get("id")).longValue())).findFirst().orElseThrow(() -> new AssertionError("current report task missing"));
        Map<String,Object> next = reportDataMapper.selectNextTasks(criteria).stream().filter(item -> Long.valueOf(nextTask).equals(((Number)item.get("id")).longValue())).findFirst().orElseThrow(() -> new AssertionError("next report task missing"));
        assertEquals("2026-08", current.get("period")); assertEquals("2026-08", current.get("taskPeriod"));
        assertEquals("2026-09", next.get("period")); assertEquals("2026-09", next.get("taskPeriod"));
        assertNotNull(reportDataMapper.selectUndoneTasks(criteria));
        assertNotNull(reportDataMapper.selectCoordinationTasks(criteria));
        assertNotNull(reportDataMapper.selectBlockedTasks(criteria));
        assertTrue(reportDataMapper.selectTaskStats(criteria).stream().allMatch(item -> "2026-08".equals(item.get("period"))));
        assertNotNull(reportDataMapper.selectAssets(criteria));
        assertNotNull(reportDataMapper.selectIprs(criteria));
        assertNotNull(reportDataMapper.selectCurrentPerfScores(criteria));
    }

    @Test
    void reportLifecycleFencesArtifactsEnforcesActiveUniquenessAndRecoversOnlyTheStaleRun() {
        long reportId=39830L,dataJobId=39831L,wordJobId=39832L;String token="it-run-token-1234567890";
        jdbcTemplate.update("insert into lab_report_instance(id,report_no,template_id,template_code,template_revision,period,biz_line,revision_no,lifecycle_status,current_flag,final_flag,sensitive_flag,source_type,json_status,markdown_status,word_status,pdf_status,version,del_flag,create_by,create_time) values(?,?,?,?,?,?,?,1,'DRAFT','0','0','0','AUTO','PENDING','PENDING','NOT_REQUESTED','NOT_REQUESTED',0,'0','it',now())",reportId,"IT-RPT-39830",30001L,"it-report-lifecycle",1,"2099-02","ALL");
        jdbcTemplate.update("insert into lab_report_job(id,job_no,report_id,job_type,job_status,progress_rate,attempt_count,idempotency_key,version,del_flag,create_by,create_time) values(?,?,?,'DATA','QUEUED',0,1,?,0,'0','it',now())",dataJobId,"IT-RPJ-39831",reportId,"it-report-39830-data");
        LabReportJob queued=reportMapper.selectReportJobById(dataJobId);assertEquals(1,reportMapper.claimReportJob(dataJobId,queued.getVersion(),token,"it",new Date()));
        assertEquals(0,reportMapper.markDataPending(reportId,dataJobId,"wrong-run-token-1234","it"));assertEquals(1,reportMapper.markDataPending(reportId,dataJobId,token,"it"));
        assertEquals(1,reportMapper.completeJson(reportId,dataJobId,token,"{}","archive/report-39830/runs/it/report.json","it"));assertEquals(1,reportMapper.completeMarkdown(reportId,dataJobId,token,"# report","archive/report-39830/runs/it/report.md","it"));assertEquals(1,reportMapper.activateWordAfterData(reportId,dataJobId,token,"it"));assertEquals(1,reportMapper.completeReportJob(dataJobId,token,"it",new Date()));
        jdbcTemplate.update("insert into lab_report_job(id,job_no,report_id,job_type,job_status,progress_rate,attempt_count,idempotency_key,run_token,version,del_flag,create_by,create_time,started_time,update_time) values(?,?,?,'WORD','RUNNING',1,1,?,?,0,'0','it',now(),date_sub(now(),interval 20 minute),date_sub(now(),interval 20 minute))",wordJobId,"IT-RPJ-39832",reportId,"it-report-39830-word","stale-run-token-1234");
        assertThrows(org.springframework.dao.DuplicateKeyException.class,()->jdbcTemplate.update("insert into lab_report_job(job_no,report_id,job_type,job_status,idempotency_key,version,del_flag,create_by,create_time) values(?,?,'WORD','QUEUED',?,0,'0','it',now())","IT-RPJ-DUP",reportId,"it-report-39830-word-dup"));
        Date cutoff=new Date(System.currentTimeMillis()-10L*60L*1000L);List<LabReportJob> recoverable=reportMapper.selectRecoverableReportJobs(cutoff,0L,100);assertTrue(recoverable.stream().anyMatch(value->Long.valueOf(wordJobId).equals(value.getId())));assertEquals(1,reportMapper.resetStaleReportJob(wordJobId,0,"stale-run-token-1234",cutoff,"it"));
        assertEquals(1,reportMapper.claimReportJob(wordJobId,1,"fresh-run-token-1234","it",new Date()));assertEquals(1,reportMapper.heartbeatReportJob(wordJobId,"fresh-run-token-1234","it"));assertEquals(0,reportMapper.resetStaleReportJob(wordJobId,2,"fresh-run-token-1234",cutoff,"it"),"an old recovery read cannot reset a freshly heartbeating run");
        LabReportInstance report=reportMapper.selectReportById(reportId);assertEquals("SUCCESS",report.getJsonStatus());assertEquals("SUCCESS",report.getMarkdownStatus());assertEquals("PENDING",report.getWordStatus());assertEquals("NOT_REQUESTED",report.getPdfStatus());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentDurableQueueCreationUsesTheReportRowLockAndReturnsOneWinner() throws Exception {
        long reportId=39840L;jdbcTemplate.update("delete from lab_report_job where report_id=?",reportId);jdbcTemplate.update("delete from lab_report_instance where id=?",reportId);
        jdbcTemplate.update("insert into lab_report_instance(id,report_no,template_id,template_code,template_revision,period,biz_line,revision_no,lifecycle_status,current_flag,final_flag,sensitive_flag,source_type,json_status,markdown_status,word_status,pdf_status,version,del_flag,create_by,create_time) values(?,?,?,?,?,?,?,1,'DRAFT','0','0','0','AUTO','PENDING','PENDING','NOT_REQUESTED','NOT_REQUESTED',0,'0','it',now())",reportId,"IT-RPT-39840",30001L,"it-report-concurrent",1,"2099-03","ALL");
        ExecutorService pool=Executors.newFixedThreadPool(2);try{CountDownLatch start=new CountDownLatch(1);Future<Long> first=pool.submit(()->{start.await();return reportJobQueuePersistence.createOrGet(reportId,"DATA","it").getJob().getId();});Future<Long> second=pool.submit(()->{start.await();return reportJobQueuePersistence.createOrGet(reportId,"DATA","it").getJob().getId();});start.countDown();Long firstId=first.get(10,TimeUnit.SECONDS),secondId=second.get(10,TimeUnit.SECONDS);assertEquals(firstId,secondId);assertEquals(1,jdbcTemplate.queryForObject("select count(1) from lab_report_job where report_id=? and job_type='DATA' and job_status in ('QUEUED','RUNNING')",Integer.class,reportId));}finally{shutdownExecutor(pool);jdbcTemplate.update("delete from lab_report_job where report_id=?",reportId);jdbcTemplate.update("delete from lab_report_instance where id=?",reportId);}
    }

    @Test
    void dashboardHistoricalYearAndActiveMonthProgressMatchGoalService() {
        jdbcTemplate.update("update lab_task set goal_weight=50 where id in (39003,39004)");
        BigDecimal taskFourAnnual = goalService.calculateAnnualProgress(39001L, 39101L);
        GoalHealthFact current = dashboardMapper.selectGoalHealthFacts(2026,
                java.sql.Date.valueOf("2026-08-31"), accessService.context(39101L),
                LabDashboardMapper.MAX_GOAL_HEALTH_ROWS + 1).stream()
                .filter(fact -> Long.valueOf(39001L).equals(fact.getGoalId())).findFirst()
                .orElseThrow(() -> new AssertionError("2026 goal health missing"));
        assertEquals(taskFourAnnual, current.getActualProgress().setScale(2),
                "ACTIVE month progress from confirmed weeks must match LabGoalService exactly");
        assertEquals(new BigDecimal("70.00"), current.getActualProgress().setScale(2));

        jdbcTemplate.update("insert into lab_goal(id,parent_id,goal_level,year,period,goal_no,title,owner_id,weight,progress_mode,progress_rate,status,version,del_flag,create_by,create_time) values(39860,0,'YEAR',2025,null,'IT-YEAR-2025','IT historical goal',39201,100,'AUTO',0,'ACTIVE',0,'0','it',now()),(39861,39860,'QUARTER',2025,'2025Q2','IT-2025-Q2','IT historical quarter',39201,100,'AUTO',0,'ACTIVE',0,'0','it',now())");
        jdbcTemplate.update("insert into lab_task(id,parent_id,goal_id,milestone_id,task_level,period,biz_line,task_type,title,owner_id,dept_id,plan_date,actual_finish_time,deliverable,perf_weight,goal_weight,workflow_status,result_status,result_desc,coordination_required,current_block_flag,period_lock_flag,version,del_flag,create_by,create_time) values(39862,0,39860,39861,'month','2025-04','algorithm','key','IT historical delivery',39203,101,'2025-04-20','2025-04-19 10:00:00','history',100,100,'CONFIRMED','ONTIME','done','0','0','0',0,'0','it',now())");
        DashboardOverview historical = dashboardService.getOverview("2025-04", 39101L);
        assertEquals(Collections.singletonList(Long.valueOf(39860L)), historical.getGoalHealth().stream()
                .map(item -> item.getGoalId()).collect(Collectors.toList()));
        assertTrue(historical.getGoalTrend().stream().allMatch(point -> point.getPeriod().startsWith("2025-")));

        jdbcTemplate.update("insert into lab_goal(id,parent_id,goal_level,year,period,goal_no,title,owner_id,weight,progress_mode,progress_rate,status,version,del_flag,create_by,create_time) values"
                + "(39871,0,'YEAR',2024,null,'IT-YEAR-2024','IT rounding goal',39201,100,'AUTO',0,'ACTIVE',0,'0','it',now()),"
                + "(39872,39871,'QUARTER',2024,'2024Q2','IT-2024-Q2','IT rounding quarter',39201,53,'AUTO',0,'ACTIVE',0,'0','it',now())");
        jdbcTemplate.update("insert into lab_task(id,parent_id,goal_id,milestone_id,task_level,period,biz_line,task_type,title,owner_id,dept_id,plan_date,deliverable,perf_weight,goal_weight,workflow_status,result_status,result_desc,coordination_required,current_block_flag,period_lock_flag,version,del_flag,create_by,create_time) values"
                + "(39873,0,39871,39872,'month','2024-04','algorithm','key','IT one-third active month',39203,101,'2024-04-20','rounding',1,1,'ACTIVE','DOING','working','0','0','0',0,'0','it',now()),"
                + "(39874,39873,39871,39872,'week','2024-04','algorithm','daily','IT completed week',39203,101,'2024-04-05','week one',0,0,'CONFIRMED','ONTIME','done','0','0','0',0,'0','it',now()),"
                + "(39875,39873,39871,39872,'week','2024-04','algorithm','daily','IT undone week two',39203,101,'2024-04-12','week two',0,0,'CONFIRMED','UNDONE','missed','0','0','0',0,'0','it',now()),"
                + "(39876,39873,39871,39872,'week','2024-04','algorithm','daily','IT undone week three',39203,101,'2024-04-19','week three',0,0,'CONFIRMED','UNDONE','missed','0','0','0',0,'0','it',now())");
        jdbcTemplate.update("insert into lab_goal(id,parent_id,goal_level,year,period,goal_no,title,owner_id,weight,progress_mode,progress_rate,status,version,del_flag,create_by,create_time) values"
                + "(39710,0,'YEAR',2022,null,'IT-YEAR-2022-DECOY','IT old decoy goal',39201,100,'AUTO',0,'ACTIVE',0,'0','it',now()),"
                + "(39711,39710,'QUARTER',2022,'2022Q1','IT-2022-Q1-DECOY','IT old decoy quarter',39201,100,'AUTO',0,'ACTIVE',0,'0','it',now())");
        jdbcTemplate.update("insert into lab_task(id,parent_id,goal_id,milestone_id,task_level,period,biz_line,task_type,title,owner_id,dept_id,plan_date,deliverable,perf_weight,goal_weight,workflow_status,result_status,result_desc,coordination_required,current_block_flag,period_lock_flag,version,del_flag,create_by,create_time) values"
                + "(39712,0,39710,39711,'month','2022-01','algorithm','key','IT old decoy month',39203,101,'2022-01-20','old',100,100,'ACTIVE','DOING','working','0','0','0',0,'0','it',now()),"
                + "(39713,39712,39710,39711,'week','2022-01','algorithm','daily','IT old decoy week',39203,101,'2022-01-07','old week',0,0,'CONFIRMED','ONTIME','done','0','0','0',0,'0','it',now())");
        BigDecimal roundedByTaskFour = goalService.calculateAnnualProgress(39871L, 39101L);
        List<GoalHealthFact> roundedYearFacts = dashboardMapper.selectGoalHealthFacts(2024,
                java.sql.Date.valueOf("2024-04-30"), accessService.context(39101L),
                LabDashboardMapper.MAX_GOAL_HEALTH_ROWS + 1);
        GoalHealthFact roundedDashboard = roundedYearFacts.stream()
                .filter(fact -> Long.valueOf(39871L).equals(fact.getGoalId())).findFirst()
                .orElseThrow(() -> new AssertionError("2024 rounding goal health missing"));
        assertTrue(roundedYearFacts.stream().noneMatch(fact -> Long.valueOf(39710L).equals(fact.getGoalId())),
                "requested-year dashboard queries must ignore old parent months and their weekly history");
        assertEquals(new BigDecimal("0.17"), roundedByTaskFour);
        assertEquals(roundedByTaskFour, roundedDashboard.getActualProgress().setScale(2),
                "dashboard must preserve Task4 weekly, milestone, then annual rounding stages");

        jdbcTemplate.update("insert into lab_goal(id,parent_id,goal_level,year,period,goal_no,title,owner_id,weight,progress_mode,progress_rate,status,version,del_flag,create_by,create_time) values"
                + "(39701,0,'YEAR',2023,null,'IT-YEAR-2023-A','IT trend A',39201,100,'AUTO',0,'ACTIVE',0,'0','it',now()),"
                + "(39702,39701,'QUARTER',2023,'2023Q2','IT-2023-A-Q2','IT trend A quarter',39201,100,'AUTO',0,'ACTIVE',0,'0','it',now()),"
                + "(39704,0,'YEAR',2023,null,'IT-YEAR-2023-B','IT trend B',39201,100,'AUTO',0,'ACTIVE',0,'0','it',now()),"
                + "(39705,39704,'QUARTER',2023,'2023Q2','IT-2023-B-Q2','IT trend B quarter',39201,100,'AUTO',0,'ACTIVE',0,'0','it',now())");
        jdbcTemplate.update("insert into lab_task(id,parent_id,goal_id,milestone_id,task_level,period,biz_line,task_type,title,owner_id,dept_id,plan_date,deliverable,perf_weight,goal_weight,workflow_status,result_status,result_desc,coordination_required,current_block_flag,period_lock_flag,version,del_flag,create_by,create_time) values"
                + "(39703,0,39701,39702,'month','2023-04','algorithm','key','IT trend A sixty',39203,101,'2023-04-20','A',60,60,'CONFIRMED','ONTIME','done','0','0','0',0,'0','it',now()),"
                + "(39706,0,39704,39705,'month','2023-04','algorithm','key','IT trend B sixty',39203,101,'2023-04-20','B',60,60,'CONFIRMED','ONTIME','done','0','0','0',0,'0','it',now())");
        List<com.ailab.system.dto.GoalTrendPoint> independentTrend = dashboardMapper.selectGoalProgressTrend(2023,
                java.sql.Timestamp.valueOf("2023-04-30 23:59:59"), accessService.context(39101L));
        assertEquals(new java.util.HashSet<Long>(Arrays.asList(39701L, 39704L)), independentTrend.stream()
                .map(com.ailab.system.dto.GoalTrendPoint::getGoalId).collect(Collectors.toSet()));
        assertTrue(independentTrend.stream().allMatch(point -> new BigDecimal("60.00").compareTo(point.getActualProgress()) == 0),
                "two annual goals at 60 percent must be returned as separate series, not a 120 percent aggregate");
    }

    @Test
    void pendingReminderSqlTargetsOnlyOpenDraftOrActiveMissingRows() {
        String period = "2097-11";
        jdbcTemplate.update("insert into lab_task(id,parent_id,goal_id,milestone_id,task_level,period,biz_line,task_type,title,owner_id,dept_id,plan_date,actual_finish_time,deliverable,perf_weight,goal_weight,workflow_status,result_status,result_desc,fail_reason,next_action,coordination_required,current_block_flag,period_lock_flag,version,del_flag,create_by,create_time) values"
                + "(39863,0,39001,39002,'month',?,'algorithm','daily','IT incomplete draft',39203,101,null,null,null,0,0,'DRAFT','DOING',null,null,null,'0','0','0',0,'0','it',now()),"
                + "(39864,0,39001,39002,'month',?,'algorithm','daily','IT complete draft',39203,101,'2097-11-20',null,'draft artifact',0,0,'DRAFT','DOING',null,null,null,'0','0','0',0,'0','it',now()),"
                + "(39865,0,39001,39002,'month',?,'algorithm','daily','IT pending review ignored',39203,101,null,null,null,0,0,'PENDING_REVIEW','DOING',null,null,null,'0','0','0',0,'0','it',now()),"
                + "(39866,0,39001,39002,'month',?,'algorithm','daily','IT incomplete active undone',39203,101,'2097-11-20',null,'artifact',0,0,'ACTIVE','UNDONE',null,null,null,'0','0','0',0,'0','it',now()),"
                + "(39867,0,39001,39002,'month',?,'algorithm','daily','IT complete weekly parent',39203,101,'2097-11-25',null,'parent artifact',0,0,'DRAFT','DOING',null,null,null,'0','0','0',0,'0','it',now()),"
                + "(39868,39867,39001,39002,'week','2097-W47','algorithm','daily','IT incomplete week child',39203,101,null,null,null,0,0,'DRAFT','DOING',null,null,null,'0','0','0',0,'0','it',now())",
                period, period, period, period, period);
        List<ReminderCandidate> candidates = dashboardMapper.selectPendingTaskReminderCandidates(period, false);
        Set<Long> taskIds = candidates.stream().map(ReminderCandidate::getTaskId).collect(Collectors.toSet());
        assertEquals(new java.util.HashSet<Long>(Arrays.asList(39863L, 39866L, 39868L)), taskIds,
                "a YYYY-Www child belongs to the reminder month through its trusted parent");
        assertTrue(candidates.stream().allMatch(candidate -> "OWNER".equals(candidate.getAudience())));

        jdbcTemplate.update("insert into lab_period_close(period,close_status,version,del_flag,create_by,create_time) values(?,'CLOSED',0,'0','it',now())", period);
        assertTrue(dashboardMapper.selectPendingTaskReminderCandidates(period, false).isEmpty(),
                "closing the parent month suppresses both month and weekly-child reminders");
    }

    @Test
    void weeklyDashboardFactsCteScopeAssetRiskAndCumulativeDrillUseRealMySql() {
        jdbcTemplate.update("insert into lab_goal(id,parent_id,goal_level,year,period,goal_no,title,owner_id,weight,progress_mode,progress_rate,status,version,del_flag,create_by,create_time) values"
                + "(39680,0,'YEAR',2096,null,'IT-YEAR-2096-SCOPE','IT scoped dashboard goal',39201,100,'AUTO',0,'ACTIVE',0,'0','it',now()),"
                + "(39681,39680,'QUARTER',2096,'2096Q3','IT-2096-Q3-SCOPE','IT scoped dashboard quarter',39201,100,'AUTO',0,'ACTIVE',0,'0','it',now())");
        jdbcTemplate.update("insert into lab_task(id,parent_id,goal_id,milestone_id,task_level,period,biz_line,task_type,title,owner_id,dept_id,plan_date,actual_finish_time,deliverable,perf_weight,goal_weight,workflow_status,result_status,result_desc,coordination_required,current_block_flag,period_lock_flag,version,del_flag,create_by,create_time) values"
                + "(39682,0,39680,39681,'month','2096-08','algorithm','key','IT scoped algorithm month',39203,101,'2096-08-25',null,'algorithm month',50,50,'ACTIVE','DOING','working','0','0','0',0,'0','it',now()),"
                + "(39683,39682,39680,39681,'week','2096-W32','algorithm','daily','IT delegated algorithm week',39202,101,'2096-08-10','2096-08-09 10:00:00','algorithm week',0,0,'CONFIRMED','ONTIME','done','0','0','0',0,'0','it',now()),"
                + "(39684,39682,39680,39681,'week','2096-08','algorithm','daily','IT legacy weekly period',39203,101,'2096-08-17',null,'legacy week',0,0,'CONFIRMED','UNDONE','missed','0','0','0',0,'0','it',now()),"
                + "(39685,0,39680,39681,'month','2096-08','platform','key','IT scoped platform month',30003,102,'2096-08-25',null,'platform month',50,50,'ACTIVE','DOING','working','0','0','0',0,'0','it',now()),"
                + "(39686,39685,39680,39681,'week','2096-W32','platform','daily','IT scoped platform week',30003,102,'2096-08-10','2096-08-09 10:00:00','platform week',0,0,'CONFIRMED','ONTIME','done','0','0','0',0,'0','it',now())");
        java.sql.Date asOf = java.sql.Date.valueOf("2096-08-31");
        GoalHealthFact managerFact = dashboardMapper.selectGoalHealthFacts(2096, asOf, accessService.context(39101L), LabDashboardMapper.MAX_GOAL_HEALTH_ROWS + 1).get(0);
        GoalHealthFact leadFact = dashboardMapper.selectGoalHealthFacts(2096, asOf, accessService.context(39102L), LabDashboardMapper.MAX_GOAL_HEALTH_ROWS + 1).get(0);
        GoalHealthFact memberFact = dashboardMapper.selectGoalHealthFacts(2096, asOf, accessService.context(39103L), LabDashboardMapper.MAX_GOAL_HEALTH_ROWS + 1).get(0);
        assertEquals(new BigDecimal("75.00"), managerFact.getActualProgress().setScale(2));
        assertEquals(new BigDecimal("25.00"), leadFact.getActualProgress().setScale(2),
                "lead goal facts must exclude a platform decoy while the goal remains readable");
        assertEquals(new BigDecimal("25.00"), memberFact.getActualProgress().setScale(2),
                "a member-authorized parent month must aggregate a same-line week delegated to another owner");
        GoalTrendPoint memberTrend = dashboardMapper.selectGoalProgressTrend(2096, asOf, accessService.context(39103L)).stream()
                .filter(point -> Long.valueOf(39680L).equals(point.getGoalId())).findFirst().orElseThrow(AssertionError::new);
        assertEquals(new BigDecimal("25.00"), memberTrend.getActualProgress().setScale(2),
                "trend uses the parent authorization too and excludes the platform parent from this goal's aggregate");

        long managerConfirmed = dashboardMapper.selectTaskStatusDistribution("2096-08", accessService.context(39101L)).stream()
                .filter(item -> "CONFIRMED".equals(item.getCode())).mapToLong(item -> item.getCount()).sum();
        long leadConfirmed = dashboardMapper.selectTaskStatusDistribution("2096-08", accessService.context(39102L)).stream()
                .filter(item -> "CONFIRMED".equals(item.getCode())).mapToLong(item -> item.getCount()).sum();
        assertEquals(3L, managerConfirmed, "month status distribution must include YYYY-Www and legacy weekly children");
        assertEquals(2L, leadConfirmed, "lead distribution must exclude the platform child");

        LabTask cumulative = new LabTask(); cumulative.setGoalId(39680L); cumulative.setTaskLevel("month");
        cumulative.setTaskType("key"); cumulative.setPeriodTo("2096-08");
        assertEquals(new java.util.HashSet<Long>(Arrays.asList(39682L, 39685L)), taskService.listTasks(cumulative, 39101L).stream()
                .map(LabTask::getId).collect(Collectors.toSet()), "goalId+periodTo drill must reproduce the cumulative parent-month set");

        Date dashboardAsOf = java.sql.Timestamp.valueOf("2096-08-15 12:00:00");
        int managerBefore = dashboardMapper.selectKpiFact("2096-08", dashboardAsOf, accessService.context(39101L)).getAssetsWithoutBackupCount();
        int leadBefore = dashboardMapper.selectKpiFact("2096-08", dashboardAsOf, accessService.context(39102L)).getAssetsWithoutBackupCount();
        int memberBefore = dashboardMapper.selectKpiFact("2096-08", dashboardAsOf, accessService.context(39103L)).getAssetsWithoutBackupCount();
        jdbcTemplate.update("insert into lab_asset(id,asset_no,asset_name,asset_version,asset_type,asset_stage,primary_owner_id,backup_owner_id,critical_flag,status,version,del_flag,create_by,create_time) values"
                + "(39690,'IT-ASSET-SCOPE-RISK','IT platform scoped risk','v1','platform','DEPLOYED',30003,null,'0','ACTIVE',0,'0','it',now()),"
                + "(39691,'IT-ASSET-NON-RISK','IT verifying non risk','v1','platform','VERIFYING',30003,null,'0','ACTIVE',0,'0','it',now()),"
                + "(39692,'IT-ASSET-INACTIVE-CRITICAL','IT inactive critical risk','v1','platform','VERIFYING',30003,null,'1','INACTIVE',0,'0','it',now())");
        assertEquals(managerBefore + 2, dashboardMapper.selectKpiFact("2096-08", dashboardAsOf, accessService.context(39101L)).getAssetsWithoutBackupCount(),
                "the shared policy includes both active in-use and inactive critical assets");
        assertEquals(leadBefore, dashboardMapper.selectKpiFact("2096-08", dashboardAsOf, accessService.context(39102L)).getAssetsWithoutBackupCount());
        assertEquals(memberBefore, dashboardMapper.selectKpiFact("2096-08", dashboardAsOf, accessService.context(39103L)).getAssetsWithoutBackupCount());
        LabAsset riskDrill = new LabAsset(); riskDrill.setSinglePointRisk(true);
        assertTrue(ledgerService.listAssets(riskDrill, 39101L).stream().anyMatch(asset -> Long.valueOf(39690L).equals(asset.getId())));
        assertTrue(ledgerService.listAssets(riskDrill, 39101L).stream().anyMatch(asset -> Long.valueOf(39692L).equals(asset.getId())));
        assertFalse(ledgerService.listAssets(riskDrill, 39102L).stream().anyMatch(asset -> Long.valueOf(39690L).equals(asset.getId())));
        assertTrue(ledgerService.listAssets(new LabAsset(), 39102L).stream().anyMatch(asset -> Long.valueOf(39691L).equals(asset.getId())),
                "ordinary asset inventory stays globally readable; only dashboard risk drill is scoped");
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
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(1) from sys_role_menu rm join sys_menu m on m.menu_id=rm.menu_id where rm.role_id=30001 and m.perms='lab:perf:history'",
                Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(1) from sys_role_menu rm join sys_menu m on m.menu_id=rm.menu_id where rm.role_id in (30002,30003) and m.perms='lab:perf:history'",
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
        assertThrows(ServiceException.class, () -> performanceService.closePeriod(period, "must roll back", 39101L));
        assertEquals(0, jdbcTemplate.queryForObject("select count(1) from lab_period_close where period=?", Integer.class, period));
        assertEquals(0, jdbcTemplate.queryForObject("select count(1) from lab_perf_score where period=?", Integer.class, period));
        assertEquals("0", jdbcTemplate.queryForObject("select period_lock_flag from lab_task where id=?", String.class, taskId));
        jdbcTemplate.update("insert into lab_task_quality_gate(id,task_id,gate_no,gate_name,gate_status,del_flag,create_by,create_time) values(39880,?,'IT-CLOSE-GATE','IT close gate','PENDING','0','it',now())", taskId);
        try {
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                CountDownLatch start = new CountDownLatch(1);
                Future<List<LabPerfScore>> first = pool.submit(() -> { start.await(); return performanceService.closePeriod(period, "concurrent close", 39101L); });
                Future<List<LabPerfScore>> second = pool.submit(() -> { start.await(); return performanceService.closePeriod(period, "concurrent close", 39101L); });
                start.countDown();
                assertEquals(3, first.get().size());
                assertEquals(3, second.get().size());
            } finally {
                shutdownExecutor(pool);
            }

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
            jdbcTemplate.update("update lab_task_quality_gate set gate_status='PASSED',evidence_id=39880,checker_id=39201,check_time='2098-11-30 10:00:00',check_result='verified' where id=39880");
            LabCollaborationRecord training = new LabCollaborationRecord(); training.setTaskId(taskId); training.setPeriod(period); training.setToMemberId(39203L);
            training.setCategory("BACKUP"); training.setSignedScore(new BigDecimal("4")); training.setEvidenceUrl("https://example.invalid/it/backup");
            performanceService.createCollaboration(training, 39102L);

            List<LabPerfScore> revised;
            boolean reviewedBeforeCutoff;
            ExecutorService reclosePool = Executors.newFixedThreadPool(2);
            try {
                CountDownLatch recloseStart = new CountDownLatch(1);
                Future<List<LabPerfScore>> reclose = reclosePool.submit(() -> { recloseStart.await(); return performanceService.closePeriod(period, "corrected close", 39101L); });
                Future<Boolean> review = reclosePool.submit(() -> { recloseStart.await(); try { performanceService.reviewCollaboration(training.getId(), new CollaborationReviewCommand(new BigDecimal("4"), "trained"), 39101L); return true; } catch (ServiceException closed) { if (!closed.getMessage().contains("open period")) throw closed; return false; } });
                recloseStart.countDown();
                revised = reclose.get();
                reviewedBeforeCutoff = review.get();
            } finally {
                shutdownExecutor(reclosePool);
            }
            assertEquals(3, revised.size());
            assertEquals(Arrays.asList(1, 2), jdbcTemplate.queryForList("select revision_no from lab_perf_score where member_id=39203 and period=? order by revision_no", Integer.class, period));
            assertEquals(1, jdbcTemplate.queryForObject("select count(1) from lab_perf_score where member_id=39203 and period=? and current_flag='1' and revision_no=2", Integer.class, period));
            assertEquals(originalDetail, jdbcTemplate.queryForObject("select cast(detail_json as char) from lab_perf_score where id=?", String.class, old.getId()));
            assertEquals(1, jdbcTemplate.queryForObject("select count(1) from lab_collaboration_record where idempotency_key=? and del_flag='0'", Integer.class, "PERIOD_OVERDUE:" + period + ":" + taskId));
            assertEquals("CONFIRMED", jdbcTemplate.queryForObject("select workflow_status from lab_task where id=?", String.class, taskId));
            String revisedDetail = jdbcTemplate.queryForObject("select cast(detail_json as char) from lab_perf_score where member_id=39203 and period=? and current_flag='1'", String.class, period);
            assertTrue(revisedDetail.contains("IT corrected evidence") && revisedDetail.contains("IT-CLOSE-GATE") && revisedDetail.contains("https://example.invalid/it/backup"));
            com.alibaba.fastjson2.JSONArray facts = com.alibaba.fastjson2.JSON.parseObject(revisedDetail).getJSONObject("collaboration").getJSONArray("items");
            com.alibaba.fastjson2.JSONObject trainingFact = null; for (int i = 0; i < facts.size(); i++) if (training.getId().equals(facts.getJSONObject(i).getLong("recordId"))) trainingFact = facts.getJSONObject(i);
            assertNotNull(trainingFact); assertEquals(reviewedBeforeCutoff ? "APPROVED" : "PENDING", trainingFact.getString("reviewStatus"));
            assertEquals(reviewedBeforeCutoff, trainingFact.getBooleanValue("included"));
            assertEquals(reviewedBeforeCutoff ? "APPROVED" : "PENDING", jdbcTemplate.queryForObject("select review_status from lab_collaboration_record where id=?", String.class, training.getId()));
            assertEquals(2, performanceService.listScoreRevisions(39203L, period, 39101L).size());
            assertThrows(ServiceException.class, () -> performanceService.listScoreRevisions(39203L, period, 39103L));
            assertThrows(org.springframework.dao.DuplicateKeyException.class, () -> jdbcTemplate.update("insert into lab_perf_score(member_id,period,revision_no,current_flag,version,del_flag,create_by,create_time) values(39203,?,99,'1',0,'0','it',now())", period));
        } finally {
            cleanupPerformanceFixture(period, taskId);
        }
    }

    @Test
    void quarterBackupFactsIncludeJulyAndAugustButExcludeSeptemberAndOtherOwners() {
        long taskId = 39881L;
        jdbcTemplate.update("insert into lab_task(id,parent_id,goal_id,milestone_id,task_level,period,biz_line,task_type,title,owner_id,dept_id,plan_date,deliverable,perf_weight,goal_weight,workflow_status,result_status,asset_id,coordination_required,current_block_flag,period_lock_flag,version,del_flag,create_by,create_time) values(?,0,39001,39002,'month','2098-08','algorithm','daily','IT backup cutoff',39203,101,'2098-08-20','artifact',0,0,'CONFIRMED','ONTIME',39301,'0','0','0',0,'0','it',now())", taskId);
        jdbcTemplate.update("insert into lab_collaboration_record(task_id,period,from_member_id,to_member_id,category,signed_score,evidence_url,reviewer_id,review_status,review_time,review_comment,version,del_flag,create_by,create_time) values(?,'2098-07',39202,39203,'BACKUP',4,'https://example.invalid/it/july-backup',39201,'APPROVED',now(),'July training',0,'0','it',now())", taskId);
        jdbcTemplate.update("insert into lab_collaboration_record(task_id,period,from_member_id,to_member_id,category,signed_score,evidence_url,reviewer_id,review_status,review_time,review_comment,version,del_flag,create_by,create_time) values(?,'2098-08',39203,39202,'BACKUP',4,'https://example.invalid/it/unrelated-backup',39201,'APPROVED',now(),'Other owner',0,'0','it',now())", taskId);
        jdbcTemplate.update("insert into lab_collaboration_record(task_id,period,from_member_id,to_member_id,category,signed_score,evidence_url,reviewer_id,review_status,review_time,review_comment,version,del_flag,create_by,create_time) values(?,'2098-09',39202,39203,'BACKUP',4,'https://example.invalid/it/future-backup',39201,'APPROVED',now(),'Future training',0,'0','it',now())", taskId);
        jdbcTemplate.update("insert into lab_collaboration_record(task_id,period,from_member_id,to_member_id,category,signed_score,evidence_url,reviewer_id,review_status,review_time,review_comment,version,del_flag,create_by,create_time) values(999999,'2098-07',39202,39203,'BACKUP',4,'https://example.invalid/it/orphaned-task',39201,'APPROVED',now(),'Orphaned task',0,'0','it',now())");

        List<LabCollaborationRecord> bounded = performanceMapper.selectQuarterCollaborationFacts("2098-07", "2098-08");

        assertEquals(Arrays.asList("2098-07", "2098-07", "2098-08"), Arrays.asList(bounded.get(0).getPeriod(), bounded.get(1).getPeriod(), bounded.get(2).getPeriod()));
        assertEquals(Long.valueOf(39301L), bounded.get(0).getRelatedAssetId());
        assertNull(bounded.get(1).getRelatedAssetId());
        assertEquals(Long.valueOf(39301L), bounded.get(2).getRelatedAssetId());
        List<LabCollaborationRecord> currentMonth = performanceMapper.selectCollaborationForPeriod("2098-08");
        assertEquals(1, currentMonth.size());
        assertEquals(Long.valueOf(39301L), currentMonth.get(0).getRelatedAssetId());
        PerformanceAssetFact criticalAsset = new PerformanceAssetFact();
        criticalAsset.setAssetId(39301L); criticalAsset.setAssetName("IT critical model"); criticalAsset.setPrimaryOwnerId(39203L);
        PerformanceCalculationInput accepted = backupCutoffInput(bounded, criticalAsset);
        String acceptedDetail = new LabPerformanceCalculator().calculate(accepted).getDetailJson();
        assertFalse(acceptedDetail.contains("CRITICAL_ASSET_WITHOUT_BACKUP"));
        com.alibaba.fastjson2.JSONObject acceptedSnapshot = com.alibaba.fastjson2.JSON.parseObject(acceptedDetail);
        com.alibaba.fastjson2.JSONArray snapshotFacts = acceptedSnapshot.getJSONArray("quarterBackupFacts");
        assertEquals(2, snapshotFacts.size());
        assertEquals(bounded.get(0).getId(), snapshotFacts.getJSONObject(0).getLong("recordId"));
        assertTrue(snapshotFacts.getJSONObject(0).getBooleanValue("qualified"));
        assertEquals(bounded.get(1).getId(), snapshotFacts.getJSONObject(1).getLong("recordId"));
        assertEquals("NO_MATCHING_MEMBER_ASSET", snapshotFacts.getJSONObject(1).getString("exclusionReason"));
        assertEquals(Collections.singletonList(bounded.get(0).getId()), acceptedSnapshot.getJSONArray("assetFacts").getJSONObject(0).getList("qualifyingCollaborationIds", Long.class));
        assertFalse(acceptedDetail.contains("https://example.invalid/it/unrelated-backup"));
        assertFalse(acceptedDetail.contains("https://example.invalid/it/future-backup"));

        PerformanceCalculationInput unrelatedOnly = backupCutoffInput(Collections.singletonList(bounded.get(2)), criticalAsset);
        PerformanceCalculationResult unrelated = new LabPerformanceCalculator().calculate(unrelatedOnly);
        assertTrue(unrelated.getDetailJson().contains("CRITICAL_ASSET_WITHOUT_BACKUP"));
        assertTrue(unrelated.getDetailJson().contains("\"quarterBackupTraining\":false"));
    }

    private void cleanupPerformanceFixture(String period, long taskId) {
        jdbcTemplate.update("delete from lab_perf_score where period=?", period);
        jdbcTemplate.update("delete from lab_collaboration_record where period=? or idempotency_key=?", period, "PERIOD_OVERDUE:" + period + ":" + taskId);
        jdbcTemplate.update("delete from lab_task_quality_gate where task_id=?", taskId);
        jdbcTemplate.update("delete from lab_task_evidence where task_id=?", taskId);
        jdbcTemplate.update("delete from lab_task where id=?", taskId);
        jdbcTemplate.update("delete from lab_period_close where period=?", period);
    }

    private void shutdownExecutor(ExecutorService executor) {
        executor.shutdownNow();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private PerformanceCalculationInput backupCutoffInput(List<LabCollaborationRecord> facts, PerformanceAssetFact asset) {
        PerformanceCalculationInput input = new PerformanceCalculationInput();
        input.setMemberId(39203L); input.setPeriod("2098-08"); input.setCloseMode(true); input.setCutoffTime(new Date(0));
        input.setQuarterCollaborationFacts(facts); input.setAssetFacts(Collections.singletonList(asset));
        return input;
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
                requireLegacyReportTemplatePin(connection);
                requireLegacySensitivePermission(connection);
                requireAllSensitiveSectionsPinned(connection);
                requireLegacySensitiveInstance(connection);
                requireLegacyReportArtifactDefaults(connection);
                insertPermissionOnlySensitiveSection(connection);
                insertNullableLifecycleActiveJob(connection);
                ScriptUtils.executeSqlScript(connection, new FileSystemResource(root.resolve("sql/ailab.sql")));
                requireLegacyReportTemplatePin(connection);
                requireLegacySensitivePermission(connection);
                requireAllSensitiveSectionsPinned(connection);
                requireLegacySensitiveInstance(connection);
                requireLegacyReportArtifactDefaults(connection);
                requirePermissionDrivenSensitiveUpgrade(connection);
                requireNullableLifecycleActiveJobTerminalized(connection);
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

        private static void requireLegacyReportTemplatePin(Connection connection) throws Exception {
            try (java.sql.Statement statement = connection.createStatement();
                    java.sql.ResultSet result = statement.executeQuery(
                            "select template_code, template_revision from lab_report_instance where id=39990")) {
                if (!result.next() || !"legacy-report-template-39990".equals(result.getString(1)) || result.getInt(2) != 7) {
                    throw new IllegalStateException("Legacy report instance must be pinned to its template family and revision");
                }
            }
            try (java.sql.Statement statement = connection.createStatement();
                    java.sql.ResultSet result = statement.executeQuery(
                            "select count(1) from information_schema.statistics where table_schema=database() and table_name='lab_report_instance' and index_name='idx_lab_report_instance_template_pin'")) {
                if (!result.next() || result.getInt(1) != 2) throw new IllegalStateException("Template pin index must have both columns");
            }
        }

        private static void requireLegacySensitivePermission(Connection connection) throws Exception {
            try (java.sql.Statement statement = connection.createStatement();
                    java.sql.ResultSet result = statement.executeQuery(
                            "select count(1) from lab_report_section where id in (39990,39991,39993) and sensitive_flag='1' and sensitive_permission='lab:report:sensitive'")) {
                if (!result.next() || result.getInt(1) != 3) {
                    throw new IllegalStateException("Legacy provider- and flag-sensitive sections must receive irreversible permission snapshots");
                }
            }
        }

        private static void insertPermissionOnlySensitiveSection(Connection connection) throws Exception {
            try (java.sql.Statement statement = connection.createStatement()) {
                statement.executeUpdate("insert into lab_report_section(id,template_id,section_code,section_name,section_type,sort_no,data_source,query_config_json,render_config_json,style_config_json,manual_flag,visible_flag,sensitive_flag,sensitive_permission,version,del_flag,create_by,create_time) values "
                        + "(39992,39990,'LEGACY_PERMISSION','Legacy permission-only sensitive section','TEXT',30,'GOAL_PROGRESS',JSON_OBJECT(),JSON_OBJECT(),JSON_OBJECT(),'0','1','0','lab:report:restricted',0,'0','it',NOW())");
            }
        }

        private static void requirePermissionDrivenSensitiveUpgrade(Connection connection) throws Exception {
            try (java.sql.Statement statement = connection.createStatement();
                    java.sql.ResultSet result = statement.executeQuery(
                            "select sensitive_flag, sensitive_permission from lab_report_section where id=39992")) {
                if (!result.next() || !"1".equals(result.getString(1)) || !"lab:report:restricted".equals(result.getString(2))) {
                    throw new IllegalStateException("A persisted sensitive permission must irreversibly promote its section flag");
                }
            }
        }

        private static void insertNullableLifecycleActiveJob(Connection connection) throws Exception {
            try (java.sql.Statement statement = connection.createStatement()) {
                statement.executeUpdate("insert into lab_report_instance(id,report_no,template_id,template_code,template_revision,period,biz_line,revision_no,lifecycle_status,current_flag,final_flag,sensitive_flag,source_type,version,del_flag,create_by,create_time) values "
                        + "(39996,'RPT-NULL-LIFECYCLE-39996',39990,'legacy-report-template-39990',7,'2098-12','ALL',1,NULL,'0','0','0','AUTO',0,'0','it',NOW())");
                statement.executeUpdate("insert into lab_report_job(id,job_no,report_id,job_type,job_status,idempotency_key,version,del_flag,create_by,create_time) values "
                        + "(39996,'JOB-NULL-LIFECYCLE-39996',39996,'DATA','QUEUED','NULL-LIFECYCLE-39996',0,'0','it',NOW())");
            }
        }

        private static void requireNullableLifecycleActiveJobTerminalized(Connection connection) throws Exception {
            try (java.sql.Statement statement = connection.createStatement();
                    java.sql.ResultSet result = statement.executeQuery("select job_status,error_message from lab_report_job where id=39996")) {
                if (!result.next() || !"FAILED".equals(result.getString(1))
                        || result.getString(2) == null || !result.getString(2).startsWith("REPORT_JOB_ORPHANED")) {
                    throw new IllegalStateException("A legacy active job with a nullable report lifecycle must be terminalized");
                }
            }
        }

        private static void requireAllSensitiveSectionsPinned(Connection connection) throws Exception {
            try (java.sql.Statement statement = connection.createStatement();
                    java.sql.ResultSet result = statement.executeQuery(
                            "select count(1) from lab_report_section where sensitive_flag='1' "
                                    + "and (sensitive_permission is null or trim(sensitive_permission)='')")) {
                if (!result.next() || result.getInt(1) != 0) {
                    throw new IllegalStateException("Every sensitive report section must retain a permission snapshot");
                }
            }
        }

        private static void requireLegacySensitiveInstance(Connection connection)throws Exception{
            try(java.sql.Statement statement=connection.createStatement();java.sql.ResultSet result=statement.executeQuery("select count(1) from lab_report_instance where id in (39990,39993) and sensitive_flag='1'")){if(!result.next()||result.getInt(1)!=2)throw new IllegalStateException("Every report pinned to a legacy sensitive section must receive a sensitive snapshot");}
        }

        private static void requireLegacyReportArtifactDefaults(Connection connection)throws Exception{
            try(java.sql.Statement statement=connection.createStatement();java.sql.ResultSet result=statement.executeQuery("select count(1) from information_schema.columns where table_schema=database() and table_name='lab_report_instance' and column_name in ('word_status','pdf_status') and column_default='NOT_REQUESTED'")){if(!result.next()||result.getInt(1)!=2)throw new IllegalStateException("Legacy Word and PDF status defaults must be upgraded idempotently");}
            try(java.sql.Statement statement=connection.createStatement();java.sql.ResultSet result=statement.executeQuery("select default_flag from lab_report_template where id=39994")){if(!result.next()||!"1".equals(result.getString(1)))throw new IllegalStateException("An enabled legacy report type with zero defaults must receive one default");}
            try(java.sql.Statement statement=connection.createStatement();java.sql.ResultSet result=statement.executeQuery("select default_flag from lab_report_template where id=39995")){if(!result.next()||!"0".equals(result.getString(1)))throw new IllegalStateException("A nullable non-latest legacy default must be cleared");}
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
