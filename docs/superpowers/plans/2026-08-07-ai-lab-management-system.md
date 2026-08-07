# AI Laboratory Management System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a complete, deployable AI laboratory management system on RuoYi-Vue v3.8.9 with connected goals, tasks, ledgers, performance, dashboards, and configurable multi-format reports.

**Architecture:** Import the pinned RuoYi-Vue modular monolith, add a `ruoyi-lab` Maven module under `com.ailab.system`, and reuse RuoYi authentication, data scopes, dictionaries, Quartz, uploads, and audit logs. MySQL is the source of truth; dashboards and reports are projections over the same task/ledger data. The report engine is separated into providers, renderers, and exporters so template changes do not require code changes.

**Tech Stack:** Java 8, Spring Boot 2.5.15, Spring Security, MyBatis, Druid, Redis, Quartz, MySQL 8, Apache POI 4.1.2, Freemarker 2.3.x, Vue 2.6.12, Element UI 2.15.14, ECharts 5.4.0, SortableJS/VueDraggable from the pinned RuoYi baseline.

**Specification:** `docs/superpowers/specs/2026-08-06-ai-lab-management-system-design.md`

---

## File and responsibility map

- `pom.xml`: register `ruoyi-lab`, dependency versions, and test support.
- `ruoyi-admin/pom.xml`: depend on `ruoyi-lab` so business controllers and jobs are loaded.
- `ruoyi-admin/src/main/java/com/ruoyi/RuoYiApplication.java`: scan `com.ailab` components and mappers.
- `ruoyi-admin/src/main/resources/application.yml`: report storage, LibreOffice, and generation limits.
- `ruoyi-lab/pom.xml`: isolated business module dependencies.
- `ruoyi-lab/src/main/java/com/ailab/system/domain/**`: persistence entities only.
- `ruoyi-lab/src/main/java/com/ailab/system/dto/**`: write commands and view models, preventing over-posting.
- `ruoyi-lab/src/main/java/com/ailab/system/mapper/**` and `src/main/resources/mapper/lab/**`: MyBatis persistence contracts.
- `ruoyi-lab/src/main/java/com/ailab/system/service/**`: business boundaries; implementations own transactions and authorization-aware queries.
- `ruoyi-lab/src/main/java/com/ailab/system/controller/**`: RuoYi REST adapters.
- `ruoyi-lab/src/main/java/com/ailab/system/report/**`: report context/data, providers, renderers, exporters, and orchestration.
- `ruoyi-lab/src/main/java/com/ailab/system/quartz/LabScheduleTask.java`: idempotent reminders, escalation, period close, and cleanup entry points.
- `ruoyi-ui/src/api/lab/**`: one API adapter per user-facing domain.
- `ruoyi-ui/src/views/lab/**`: focused pages and local components.
- `sql/ailab.sql`: schema, indexes, dictionaries, menus, roles, jobs, default template, and demonstration data.
- `scripts/verify-sql.ps1`: deterministic SQL completeness checks that do not require a running database.
- `docs/deployment.md`, `docs/acceptance-checklist.md`: operator and acceptance handoff.
- `samples/2026-07-ai-lab-monthly-report.*`: generated demonstration report artifacts.

---

### Task 1: Import and wire the pinned RuoYi baseline

**Files:**
- Create: all upstream files from official RuoYi-Vue tag `v3.8.9`, excluding upstream `.git`
- Create: `ruoyi-lab/pom.xml`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/config/LabProperties.java`
- Modify: `pom.xml`
- Modify: `ruoyi-admin/pom.xml`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/RuoYiApplication.java`
- Test: `ruoyi-lab/src/test/java/com/ailab/system/ModuleSmokeTest.java`

- [ ] **Step 1: Import the exact upstream tree**

Fetch tag `v3.8.9`, verify commit `5e6c917ab0c29536b6ca792475c6611ebe0cea85`, and copy the working tree into the repository without its `.git`. Preserve `docs/` already in this repository.

- [ ] **Step 2: Write the failing module smoke test**

Create `ModuleSmokeTest` that imports `com.ailab.system.config.LabProperties` and asserts default report settings. It must fail because the module/config class does not yet exist.

- [ ] **Step 3: Run the focused test and verify failure**

Run: `mvn -pl ruoyi-lab -am -Dtest=ModuleSmokeTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because `ruoyi-lab` or `LabProperties` is missing.

- [ ] **Step 4: Add the module and application scanning**

Register `ruoyi-lab` in the root reactor and dependency management; add it to `ruoyi-admin`. Configure component and Mapper scanning for both `com.ruoyi` and `com.ailab`. Add `LabProperties` with defaults for output directory, temporary directory, LibreOffice executable, conversion timeout, and maximum upload size.

- [ ] **Step 5: Run baseline builds**

Run: `mvn -pl ruoyi-admin -am -DskipTests package`

Expected: BUILD SUCCESS.

Run: `npm install` then `npm run build:prod` in `ruoyi-ui`.

Expected: compiled production bundle in `ruoyi-ui/dist`.

- [ ] **Step 6: Commit**

```powershell
git add .
git commit -m "build: import RuoYi v3.8.9 and add lab module"
```

### Task 2: Create complete database, menu, dictionary, job, and demo initialization

**Files:**
- Create: `sql/ailab.sql`
- Create: `scripts/verify-sql.ps1`
- Create: `ruoyi-lab/src/test/java/com/ailab/system/sql/LabSqlContractTest.java`

- [ ] **Step 1: Write the failing SQL contract test**

The test reads `../sql/ailab.sql` and asserts all required table names, audit columns, dictionary codes, report section types, unique keys, menu permissions, and Quartz invoke targets are present. It must check exactly 20 domain tables: goals, tasks, evidence, quality gates, block events, reminders, assets, members, skills, member skills, one-to-ones, IPR, collaboration records, score revisions, period close, templates, sections, summaries, report instances, and report jobs. It also verifies five Quartz jobs: block escalation, pending-task reminders, period close, report temporary-file cleanup, and stale report-job recovery.

- [ ] **Step 2: Verify the test fails**

Run: `mvn -pl ruoyi-lab -am -Dtest=LabSqlContractTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because `sql/ailab.sql` does not exist.

- [ ] **Step 3: Implement the schema and seed data**

Use MySQL 8 `utf8mb4_general_ci`; include comments, logical deletion/audit columns, targeted indexes, composite unique constraints, foreign-key-compatible types without hard database foreign keys (matching RuoYi conventions), dictionaries, menus, role-menu grants, five idempotent Quartz jobs, one standard monthly template with all six renderer types, and six demonstration members mapped to seeded RuoYi users.

- [ ] **Step 4: Add a standalone SQL verifier**

`scripts/verify-sql.ps1` must fail on missing required tables, missing audit columns, duplicate dictionary values, or absent template seed sections, and print a compact success summary otherwise.

- [ ] **Step 5: Run verification**

Run: `mvn -pl ruoyi-lab -am -Dtest=LabSqlContractTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS.

Run: `powershell -ExecutionPolicy Bypass -File scripts/verify-sql.ps1`

Expected: `AI Lab SQL contract verified` and exit code 0.

- [ ] **Step 6: Commit**

```powershell
git add sql/ailab.sql scripts/verify-sql.ps1 ruoyi-lab/src/test/java/com/ailab/system/sql/LabSqlContractTest.java
git commit -m "feat: add AI lab database initialization"
```

### Task 3: Implement shared contracts, periods, validation, and task state machine

**Files:**
- Create: `ruoyi-lab/src/main/java/com/ailab/system/constant/LabConstants.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/domain/LabTask.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/domain/LabTaskEvidence.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/dto/TaskSubmitCommand.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/dto/FieldValidationError.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/service/TaskWorkflowService.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/service/impl/TaskWorkflowServiceImpl.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/util/LabPeriodUtils.java`
- Test: `ruoyi-lab/src/test/java/com/ailab/system/service/TaskWorkflowServiceTest.java`
- Test: `ruoyi-lab/src/test/java/com/ailab/system/util/LabPeriodUtilsTest.java`

- [ ] **Step 1: Write failing period and transition tests**

Cover month/week parsing, invalid periods, `DRAFT→ACTIVE→PENDING_REVIEW→CONFIRMED`, return/withdraw/reopen, self-review rejection, server-derived on-time/delayed result, EXCEEDED reviewer confirmation, and immutable confirmed tasks.

- [ ] **Step 2: Write failing conditional-field tests**

Cover completed result/evidence, undone reason/next action, coordination fields, and structured field-error output.

- [ ] **Step 3: Run focused tests**

Run: `mvn -pl ruoyi-lab -am -Dtest=LabPeriodUtilsTest,TaskWorkflowServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL with missing classes or unimplemented rules.

- [ ] **Step 4: Implement minimal pure business logic**

Keep transition and validation functions deterministic. Use `actualFinishTime` persisted by the command handler, never a client-supplied result label, to derive `ONTIME/DELAYED`.

- [ ] **Step 5: Re-run tests**

Expected: all focused tests PASS.

- [ ] **Step 6: Commit**

```powershell
git add ruoyi-lab/src/main/java/com/ailab/system/constant ruoyi-lab/src/main/java/com/ailab/system/domain/LabTask.java ruoyi-lab/src/main/java/com/ailab/system/domain/LabTaskEvidence.java ruoyi-lab/src/main/java/com/ailab/system/dto ruoyi-lab/src/main/java/com/ailab/system/service/TaskWorkflowService.java ruoyi-lab/src/main/java/com/ailab/system/service/impl/TaskWorkflowServiceImpl.java ruoyi-lab/src/main/java/com/ailab/system/util ruoyi-lab/src/test
git commit -m "feat: add task workflow and validation contracts"
```

### Task 4: Implement goal and task persistence, aggregation, and REST APIs

**Files:**
- Create: `ruoyi-lab/src/main/java/com/ailab/system/domain/LabGoal.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/domain/LabTaskQualityGate.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/domain/LabTaskBlockEvent.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/mapper/LabGoalMapper.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/mapper/LabTaskMapper.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/mapper/LabTaskEvidenceMapper.java`
- Create: `ruoyi-lab/src/main/resources/mapper/lab/LabGoalMapper.xml`
- Create: `ruoyi-lab/src/main/resources/mapper/lab/LabTaskMapper.xml`
- Create: `ruoyi-lab/src/main/resources/mapper/lab/LabTaskEvidenceMapper.xml`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/service/LabGoalService.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/service/LabTaskService.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/service/impl/LabGoalServiceImpl.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/service/impl/LabTaskServiceImpl.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/controller/LabGoalController.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/controller/LabTaskController.java`
- Test: `ruoyi-lab/src/test/java/com/ailab/system/service/LabGoalServiceTest.java`
- Test: `ruoyi-lab/src/test/java/com/ailab/system/service/LabTaskServiceTest.java`
- Test: `ruoyi-admin/src/test/java/com/ailab/system/mapper/LabMapperMySqlIT.java`
- Test config: `ruoyi-admin/src/test/resources/application-lab-it.yml`
- Test fixture: `sql/test/ailab-mapper-fixture.sql`

- [ ] **Step 1: Write failing aggregation tests**

Cover goal/milestone hierarchy validation, sibling weights totaling 100, distinct goal/performance task weights, weekly-to-monthly progress, milestone progress, annual progress, and optimistic-lock failure.

- [ ] **Step 2: Run tests and confirm failure**

Run: `mvn -pl ruoyi-lab -am -Dtest=LabGoalServiceTest,LabTaskServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

- [ ] **Step 3: Implement mappers and transactional services**

Use `@DataScope`-compatible query parameters, RuoYi logical deletion/audit conventions, server-side aggregate calculations, and block-event creation/closure in the same transaction as the current task flag.

Add `LabMapperMySqlIT` in `ruoyi-admin` so it can boot `RuoYiApplication` against a real MySQL 8 test schema using the `lab-it` profile. The `*IT` suffix deliberately keeps it out of Surefire's normal unit-test discovery. Apply `sql/ry_20240629.sql`, `sql/ailab.sql`, and `sql/test/ailab-mapper-fixture.sql` before the test. Verify owner/business-line/period queries, logical deletion, goal/task aggregation, and mapper result mappings; roll back each test method.

- [ ] **Step 4: Implement secured controllers**

Expose tree/list/detail/create/update/delete, plan activation, result submission, withdraw, review, reopen, evidence, quality gates, and block/unblock endpoints with individual `@PreAuthorize` strings and `@Log` annotations.

- [ ] **Step 5: Run tests and module package**

Run: `mvn -pl ruoyi-lab -am -Dtest=LabGoalServiceTest,LabTaskServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS.

With MySQL 8 and Redis available, run: `mvn -pl ruoyi-admin -am -Dspring.profiles.active=lab-it -Dtest=LabMapperMySqlIT -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS with the real MySQL mapper fixture.

Run: `mvn -pl ruoyi-admin -am -DskipTests package`

Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```powershell
git add ruoyi-lab ruoyi-admin/src/test sql/test
git commit -m "feat: implement connected goals and tasks"
```

### Task 5: Implement member, skill, asset, one-to-one, and IPR ledgers

**Files:**
- Create: `ruoyi-lab/src/main/java/com/ailab/system/domain/LabMember.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/domain/LabSkill.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/domain/LabMemberSkill.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/domain/LabAsset.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/domain/LabOne2One.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/domain/LabIpr.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/mapper/LabMemberMapper.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/mapper/LabLedgerMapper.java`
- Create: `ruoyi-lab/src/main/resources/mapper/lab/LabMemberMapper.xml`
- Create: `ruoyi-lab/src/main/resources/mapper/lab/LabLedgerMapper.xml`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/service/LabMemberService.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/service/LabLedgerService.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/service/impl/LabMemberServiceImpl.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/service/impl/LabLedgerServiceImpl.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/controller/LabMemberController.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/controller/LabAssetController.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/controller/LabIprController.java`
- Test: `ruoyi-lab/src/test/java/com/ailab/system/service/LabMemberServiceTest.java`
- Test: `ruoyi-lab/src/test/java/com/ailab/system/service/LabLedgerServiceTest.java`

- [ ] **Step 1: Write failing ledger rule tests**

Cover unique sys-user/member mapping, 1—5 skill levels, unique member-skill pairs, active/inactive preservation, asset owner/backup inequality, single-point-risk detection, and IPR stage/date validation.

- [ ] **Step 2: Verify failures**

Run: `mvn -pl ruoyi-lab -am -Dtest=LabMemberServiceTest,LabLedgerServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

- [ ] **Step 3: Implement focused domain services and mappers**

Member identity comes from `sys_user`; member deactivation never deletes historical links. Implement batched skill-matrix save and one-to-one visibility constraints.

- [ ] **Step 4: Implement controllers and permissions**

Expose member detail aggregate, skill catalog/matrix, one-to-one, asset, and IPR CRUD endpoints matching the authorization matrix.

- [ ] **Step 5: Run tests and commit**

Run: `mvn -pl ruoyi-lab -am -Dtest=LabMemberServiceTest,LabLedgerServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS.

```powershell
git add ruoyi-lab
git commit -m "feat: add team and technology ledgers"
```

### Task 6: Implement collaboration, performance calculation, and audited period close

**Files:**
- Create: `ruoyi-lab/src/main/java/com/ailab/system/domain/LabCollaborationRecord.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/domain/LabPerfScore.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/domain/LabPeriodClose.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/mapper/LabPerformanceMapper.java`
- Create: `ruoyi-lab/src/main/resources/mapper/lab/LabPerformanceMapper.xml`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/service/LabPerformanceService.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/service/impl/LabPerformanceServiceImpl.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/controller/LabPerformanceController.java`
- Test: `ruoyi-lab/src/test/java/com/ailab/system/service/LabPerformanceServiceTest.java`

- [ ] **Step 1: Write failing formula tests**

Cover delivery coefficients and cap, weighted quality gates, collaboration category caps and deductions, red lines, score detail snapshot, unsubmitted-at-close treatment, revision increments, audited reopen, and manager-only red-line revocation that requires corrective evidence URL plus reason and preserves the original trigger in the snapshot.

- [ ] **Step 2: Verify failures**

Run: `mvn -pl ruoyi-lab -am -Dtest=LabPerformanceServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

- [ ] **Step 3: Implement deterministic calculator and close transaction**

Separate pure calculation from persistence. Closing locks the period and writes immutable current revisions; reopening requires reason, keeps prior revisions, and unlocks tasks. Calibration cannot mask an active red line. Red-line revocation creates an audited corrective record containing original trigger, evidence URL, reason, manager ID, and timestamp; it never deletes or rewrites the triggering snapshot.

- [ ] **Step 4: Implement restricted APIs**

Add personal view, manager calculation preview, close/reopen, collaboration review, monthly confirmation, manager-only red-line revoke, and quarterly calibration with data-scope and operation logs.

- [ ] **Step 5: Run test and commit**

Run: `mvn -pl ruoyi-lab -am -Dtest=LabPerformanceServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS.

```powershell
git add ruoyi-lab
git commit -m "feat: add performance and period close workflow"
```

### Task 7: Implement reminders, escalation, dashboard queries, and Quartz entry points

**Files:**
- Create: `ruoyi-lab/src/main/java/com/ailab/system/domain/LabReminder.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/mapper/LabDashboardMapper.java`
- Create: `ruoyi-lab/src/main/resources/mapper/lab/LabDashboardMapper.xml`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/service/LabReminderService.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/service/LabDashboardService.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/service/impl/LabReminderServiceImpl.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/service/impl/LabDashboardServiceImpl.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/controller/LabDashboardController.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/quartz/LabScheduleTask.java`
- Test: `ruoyi-lab/src/test/java/com/ailab/system/service/LabReminderServiceTest.java`
- Test: `ruoyi-lab/src/test/java/com/ailab/system/service/LabDashboardServiceTest.java`

- [ ] **Step 1: Write failing idempotency and dashboard tests**

Cover 7/14-day escalation, repeated block episodes, daily recipient keys, read state, goal health thresholds, action-card counts, member load dimensions, data-scope differences, and metric drill-down filters.

- [ ] **Step 2: Run failing tests**

Run: `mvn -pl ruoyi-lab -am -Dtest=LabReminderServiceTest,LabDashboardServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

- [ ] **Step 3: Implement services, optimized aggregate SQL, and job facade**

Keep each implemented Quartz method idempotent and parameterless so RuoYi can invoke `labScheduleTask.scanBlocks()`, `scanPendingTasks()`, `closeDuePeriods()`, and `cleanReportTempFiles()`. The fifth seeded target, `labScheduleTask.recoverReportJobs()`, is implemented with the report worker in Task 12 when its dependencies exist.

- [ ] **Step 4: Run tests and commit**

Run: `mvn -pl ruoyi-lab -am -Dtest=LabReminderServiceTest,LabDashboardServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS.

```powershell
git add ruoyi-lab
git commit -m "feat: add management dashboard and reminders"
```

### Task 8: Define report configuration, data contracts, registries, and safe template handling

**Files:**
- Create: `ruoyi-lab/src/main/java/com/ailab/system/domain/LabReportTemplate.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/domain/LabReportSection.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/domain/LabReportSummary.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/domain/LabReportInstance.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/domain/LabReportJob.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/report/model/ReportContext.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/report/model/ReportData.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/report/model/ReportSectionData.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/report/provider/DataSourceProvider.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/report/renderer/SectionRenderer.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/report/exporter/ReportExporter.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/report/config/ReportConfigValidator.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/report/config/SafeFreemarkerFactory.java`
- Test: `ruoyi-lab/src/test/java/com/ailab/system/report/ReportConfigValidatorTest.java`
- Test: `ruoyi-lab/src/test/java/com/ailab/system/report/SafeFreemarkerFactoryTest.java`

- [ ] **Step 1: Write failing configuration tests**

Cover legal/illegal section type and provider combinations, filter/column JSON whitelist, template-family revisions, one default latest revision per report type, optimistic version rejection on stale template edits, forbidden FreeMarker object access, and sensitive-section marking.

- [ ] **Step 2: Verify failures**

Run: `mvn -pl ruoyi-lab -am -Dtest=ReportConfigValidatorTest,SafeFreemarkerFactoryTest -Dsurefire.failIfNoSpecifiedTests=false test`

- [ ] **Step 3: Implement immutable contracts and registries**

Use stable string identifiers and explicit registry maps. Reject duplicate providers/renderers/exporters at startup and reject unknown JSON fields at save/import time.

- [ ] **Step 4: Run tests and commit**

Run: `mvn -pl ruoyi-lab -am -Dtest=ReportConfigValidatorTest,SafeFreemarkerFactoryTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS.

```powershell
git add ruoyi-lab
git commit -m "feat: define configurable report contracts"
```

### Task 9: Implement all report data-source providers

**Files:**
- Create: `ruoyi-lab/src/main/java/com/ailab/system/report/provider/AbstractLabDataSourceProvider.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/report/provider/GoalProgressProvider.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/report/provider/TaskDetailProvider.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/report/provider/TaskStatProvider.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/report/provider/TaskUndoneProvider.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/report/provider/TaskNextProvider.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/report/provider/TaskCoordProvider.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/report/provider/TaskBlockProvider.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/report/provider/AssetSummaryProvider.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/report/provider/IprSummaryProvider.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/report/provider/PerfSummaryProvider.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/report/provider/ManualSummaryProvider.java`
- Test: `ruoyi-lab/src/test/java/com/ailab/system/report/ReportProviderContractTest.java`

- [ ] **Step 1: Write one failing contract test per provider**

Assert stable field names, period filtering, supported operators, grouping, empty data, data scope, and denial of `PERF_SUMMARY` without sensitive permission.

- [ ] **Step 2: Run and observe failure**

Run: `mvn -pl ruoyi-lab -am -Dtest=ReportProviderContractTest -Dsurefire.failIfNoSpecifiedTests=false test`

- [ ] **Step 3: Implement providers over existing services/mappers**

Providers may assemble projections but must not duplicate business calculations. Apply a whitelist of filter fields and operators before constructing query criteria.

- [ ] **Step 4: Run tests and commit**

Run: `mvn -pl ruoyi-lab -am -Dtest=ReportProviderContractTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS for all eleven providers.

```powershell
git add ruoyi-lab
git commit -m "feat: implement report data providers"
```

### Task 10: Implement six section renderers plus JSON and Markdown export

**Files:**
- Create: `ruoyi-lab/src/main/java/com/ailab/system/report/renderer/TableSectionRenderer.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/report/renderer/StatSectionRenderer.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/report/renderer/TextSectionRenderer.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/report/renderer/ManualSectionRenderer.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/report/renderer/GroupTextSectionRenderer.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/report/renderer/ChartSectionRenderer.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/report/exporter/JsonReportExporter.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/report/exporter/MarkdownReportExporter.java`
- Test: `ruoyi-lab/src/test/java/com/ailab/system/report/SectionRendererContractTest.java`
- Test: `ruoyi-lab/src/test/java/com/ailab/system/report/MarkdownReportExporterTest.java`

- [ ] **Step 1: Write failing renderer golden tests**

Use deterministic Chinese fixtures. Cover configured columns/alignment, statistics text, safe template variables, manual empty state, grouped data with summaries, chart specification/image contract, and markdown escaping.

- [ ] **Step 2: Verify failures**

Run: `mvn -pl ruoyi-lab -am -Dtest=SectionRendererContractTest,MarkdownReportExporterTest -Dsurefire.failIfNoSpecifiedTests=false test`

- [ ] **Step 3: Implement all renderers and exporters**

`ChartSectionRenderer` must output a chart-neutral model and a deterministic PNG for office export; no server-side browser is introduced.

- [ ] **Step 4: Run tests and commit**

Expected: PASS and golden files stable across two runs.

```powershell
git add ruoyi-lab
git commit -m "feat: render report sections and markdown"
```

### Task 11: Implement Word and PDF exporters

**Files:**
- Create: `ruoyi-lab/src/main/java/com/ailab/system/report/exporter/WordReportExporter.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/report/exporter/PdfReportExporter.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/report/exporter/LibreOfficeProcessRunner.java`
- Test: `ruoyi-lab/src/test/java/com/ailab/system/report/WordReportExporterTest.java`
- Test: `ruoyi-lab/src/test/java/com/ailab/system/report/PdfReportExporterTest.java`

- [ ] **Step 1: Write failing DOCX package tests**

Generate a fixed report and inspect `word/document.xml` and `word/styles.xml` for Microsoft YaHei east-Asia font, 10.5/8.5/15/12 pt sizes, `tblHeader`, shading, cell margins, vertical alignment, `keepNext`, numeric/text alignment, and embedded chart image.

- [ ] **Step 2: Write failing process-runner tests**

Cover executable paths with spaces, timeout, nonzero exit, missing output, isolated user profile, and cleanup. Use a harmless fake executable/script rather than requiring LibreOffice in unit tests.

- [ ] **Step 3: Implement Word and PDF export**

Never pass untrusted shell text; use `ProcessBuilder` arguments. Preserve successful Word output when PDF conversion fails and return a typed retryable error.

- [ ] **Step 4: Run tests and commit**

Run: `mvn -pl ruoyi-lab -am -Dtest=WordReportExporterTest,PdfReportExporterTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS.

```powershell
git add ruoyi-lab
git commit -m "feat: export reports to Word and PDF"
```

### Task 12: Implement template/report persistence, orchestration, retries, import, finalization, and APIs

**Files:**
- Create: `ruoyi-lab/src/main/java/com/ailab/system/mapper/LabReportMapper.java`
- Create: `ruoyi-lab/src/main/resources/mapper/lab/LabReportMapper.xml`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/service/LabReportTemplateService.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/service/LabReportService.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/service/impl/LabReportTemplateServiceImpl.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/service/impl/LabReportServiceImpl.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/report/ReportGenerationOrchestrator.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/report/ReportJobDispatcher.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/report/ReportGenerationWorker.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/controller/LabReportTemplateController.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/controller/LabReportController.java`
- Modify: `ruoyi-lab/src/main/java/com/ailab/system/quartz/LabScheduleTask.java`
- Test: `ruoyi-lab/src/test/java/com/ailab/system/report/ReportGenerationOrchestratorTest.java`
- Test: `ruoyi-lab/src/test/java/com/ailab/system/report/ReportJobDispatcherTest.java`
- Test: `ruoyi-lab/src/test/java/com/ailab/system/service/LabReportTemplateServiceTest.java`

- [ ] **Step 1: Write failing lifecycle tests**

Cover same-family revision, save-as-new family, default switch, JSON import/export, generation lock, optimistic version rejection on stale template saves and concurrent report finalization, immediate HTTP-facing queue return, worker execution on RuoYi's executor, stale-job recovery, independent artifact states, partial PDF failure, targeted retry, Markdown re-import as a new version, all-artifact finalization, supersession, immutability, source performance revision, and sensitive download checks.

- [ ] **Step 2: Run failures**

Run: `mvn -pl ruoyi-lab -am -Dtest=ReportGenerationOrchestratorTest,ReportJobDispatcherTest,LabReportTemplateServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

- [ ] **Step 3: Implement persistence and orchestrator**

Do not hold a database transaction while generating files. `ReportJobDispatcher` persists the queued job, then submits `ReportGenerationWorker` to RuoYi's existing `scheduledExecutorService`; the HTTP endpoint returns the instance/job ID immediately. The worker reloads state in a separate thread, persists step state before/after each exporter, uses Redis plus database state for duplicate suppression, sanitizes paths and filenames, and retains user-facing error summaries. Implement the seeded `LabScheduleTask.recoverReportJobs()` entry point; on application restart its Quartz recovery scan requeues jobs left in `QUEUED` or stale `RUNNING` state.

- [ ] **Step 4: Implement secured APIs**

Expose template tree/config/preview/version/default/import/export; summaries; generation status; artifact download; retry; Markdown import; finalize; and history. Reauthorize every download against the instance sensitive snapshot.

- [ ] **Step 5: Run tests and commit**

Expected: all report lifecycle tests PASS.

```powershell
git add ruoyi-lab
git commit -m "feat: orchestrate versioned report generation"
```

### Task 13: Build frontend design foundation and management dashboard

**Required skill:** `frontend-design`

**Files:**
- Create: `ruoyi-ui/src/api/lab/dashboard.js`
- Create: `ruoyi-ui/src/api/lab/common.js`
- Create: `ruoyi-ui/src/views/lab/dashboard/index.vue`
- Create: `ruoyi-ui/src/views/lab/dashboard/components/MetricCard.vue`
- Create: `ruoyi-ui/src/views/lab/dashboard/components/GoalHealthChart.vue`
- Create: `ruoyi-ui/src/views/lab/dashboard/components/MemberLoadMatrix.vue`
- Create: `ruoyi-ui/src/views/lab/dashboard/components/ActionQueue.vue`
- Create: `ruoyi-ui/src/assets/styles/lab-theme.scss`
- Modify: `ruoyi-ui/src/main.js`

- [ ] **Step 1: Define and implement the visual system**

Use a restrained deep-indigo/teal management palette, consistent 8px spacing, visible metric definitions, risk colors only for actionable exceptions, responsive desktop layout, keyboard focus, and accessible contrast. Import the lab theme once from `main.js`.

- [ ] **Step 2: Implement API adapters and dashboard states**

Handle loading, partial widget failure, empty states, last-updated labels, period selector, and data-scope-dependent content.

- [ ] **Step 3: Implement drill-down behavior**

Metric cards and charts route to task/goal/asset pages with explicit query filters; do not display non-actionable vanity charts.

- [ ] **Step 4: Run frontend checks**

Run: `npm run lint` in `ruoyi-ui`.

Expected: no new lint errors.

Run: `npm run build:prod`.

Expected: production build succeeds.

- [ ] **Step 5: Commit**

```powershell
git add ruoyi-ui
git commit -m "feat: add AI lab management dashboard"
```

### Task 14: Build goal and task workflow pages

**Required skill:** `frontend-design`

**Files:**
- Create: `ruoyi-ui/src/api/lab/goal.js`
- Create: `ruoyi-ui/src/api/lab/task.js`
- Create: `ruoyi-ui/src/views/lab/goal/index.vue`
- Create: `ruoyi-ui/src/views/lab/goal/components/GoalTree.vue`
- Create: `ruoyi-ui/src/views/lab/goal/components/GoalDetailDrawer.vue`
- Create: `ruoyi-ui/src/views/lab/task/index.vue`
- Create: `ruoyi-ui/src/views/lab/task/components/TaskFormDrawer.vue`
- Create: `ruoyi-ui/src/views/lab/task/components/EvidenceEditor.vue`
- Create: `ruoyi-ui/src/views/lab/task/components/TaskReviewPanel.vue`

- [ ] **Step 1: Implement the four-level goal drill-down**

Keep tree context visible while showing weight/progress/health and linked tasks. Enforce level-appropriate fields and display aggregate formulas.

- [ ] **Step 2: Implement task list and progressive form**

Support saved filters, my tasks, weekly quick-copy, status actions, conditional result/coordination/block fields, evidence uploads, quality gates, field-level backend errors, and optimistic-lock conflict recovery.

- [ ] **Step 3: Implement review and monthly weight readiness**

Show separate performance and goal weights with clear scope; include readiness bars for each 100% contract and prevent invalid plan activation before API call.

- [ ] **Step 4: Lint/build and commit**

Run: `npm run lint` then `npm run build:prod`.

Expected: success.

```powershell
git add ruoyi-ui/src/api/lab/goal.js ruoyi-ui/src/api/lab/task.js ruoyi-ui/src/views/lab/goal ruoyi-ui/src/views/lab/task
git commit -m "feat: add goal and task workflows"
```

### Task 15: Build team, ledgers, and performance feedback pages

**Required skill:** `frontend-design`

**Files:**
- Create: `ruoyi-ui/src/api/lab/member.js`
- Create: `ruoyi-ui/src/api/lab/asset.js`
- Create: `ruoyi-ui/src/api/lab/ipr.js`
- Create: `ruoyi-ui/src/api/lab/performance.js`
- Create: `ruoyi-ui/src/views/lab/member/index.vue`
- Create: `ruoyi-ui/src/views/lab/member/components/MemberDetailDrawer.vue`
- Create: `ruoyi-ui/src/views/lab/member/components/SkillMatrixEditor.vue`
- Create: `ruoyi-ui/src/views/lab/member/components/OneToOneTimeline.vue`
- Create: `ruoyi-ui/src/views/lab/asset/index.vue`
- Create: `ruoyi-ui/src/views/lab/ipr/index.vue`
- Create: `ruoyi-ui/src/views/lab/performance/index.vue`
- Create: `ruoyi-ui/src/views/lab/performance/components/ScoreBreakdown.vue`

- [ ] **Step 1: Implement member master-detail maintenance**

Use a wide detail drawer with profile, responsibilities, normalized skill matrix, primary/backup assets, current work, and one-to-one history. Batch-save skill changes and preserve inactive members.

- [ ] **Step 2: Implement assets and IPR ledgers**

Include single-point-risk filters, owner/backup validation, due-date emphasis, stage history cues, and evidence/document links.

- [ ] **Step 3: Implement non-ranking performance feedback**

Show personal breakdown, evidence trace, red-line state, revisions, manager period close/reopen, collaboration review, manager-only red-line revocation form with corrective evidence and reason, and quarterly calibration only under correct permissions. Do not add leaderboard UI.

- [ ] **Step 4: Lint/build and commit**

Run: `npm run lint` then `npm run build:prod`.

Expected: success.

```powershell
git add ruoyi-ui/src/api/lab ruoyi-ui/src/views/lab/member ruoyi-ui/src/views/lab/asset ruoyi-ui/src/views/lab/ipr ruoyi-ui/src/views/lab/performance
git commit -m "feat: add team ledgers and performance feedback"
```

### Task 16: Build report center and visual template editor

**Required skill:** `frontend-design`

**Files:**
- Create: `ruoyi-ui/src/api/lab/report.js`
- Create: `ruoyi-ui/src/api/lab/template.js`
- Create: `ruoyi-ui/src/views/lab/report/index.vue`
- Create: `ruoyi-ui/src/views/lab/report/components/ArtifactStatus.vue`
- Create: `ruoyi-ui/src/views/lab/report/components/ReportHistory.vue`
- Create: `ruoyi-ui/src/views/lab/report/components/ReportSummaryEditor.vue`
- Create: `ruoyi-ui/src/views/lab/template/index.vue`
- Create: `ruoyi-ui/src/views/lab/template/components/SectionTree.vue`
- Create: `ruoyi-ui/src/views/lab/template/components/SectionProperties.vue`
- Create: `ruoyi-ui/src/views/lab/template/components/FilterBuilder.vue`
- Create: `ruoyi-ui/src/views/lab/template/components/ColumnDesigner.vue`
- Create: `ruoyi-ui/src/views/lab/template/components/MarkdownPreview.vue`

- [ ] **Step 1: Implement the three-column editor**

Use draggable Element tree on the left, typed property editor in the middle, debounced Markdown preview on the right, and a sticky toolbar for save, save-as, publish/default, import/export, and test generation.

- [ ] **Step 2: Implement safe configuration UX**

Data-source choices must depend on section type; changing source clears incompatible filters/columns after explicit confirmation. Show allowed fields/operators and FreeMarker variables from server metadata, never arbitrary Java access.

- [ ] **Step 3: Implement report generation and history**

Before generation, `ReportSummaryEditor` loads enabled business lines and manual sections and supports reason analysis, next-step strategy, and business-line summaries with completeness indicators and batch save. Generation clearly distinguishes optional empty manual sections from template-required incomplete sections. Show independent JSON/Markdown/Word/PDF states, polling with backoff, partial downloads, targeted retry, Markdown re-import, finalization requirements, sensitive lock indicators, and immutable superseded versions.

- [ ] **Step 4: Lint/build and commit**

Run: `npm run lint` then `npm run build:prod`.

Expected: success.

```powershell
git add ruoyi-ui/src/api/lab/report.js ruoyi-ui/src/api/lab/template.js ruoyi-ui/src/views/lab/report ruoyi-ui/src/views/lab/template
git commit -m "feat: add report center and template designer"
```

### Task 17: Produce configuration, deployment documentation, demo artifacts, and acceptance scripts

**Files:**
- Modify: `ruoyi-admin/src/main/resources/application.yml`
- Modify: `ruoyi-admin/src/main/resources/application-druid.yml`
- Create: `ruoyi-admin/src/main/resources/application-demo.yml`
- Create: `docs/deployment.md`
- Create: `docs/acceptance-checklist.md`
- Create: `docs/data-dictionary.md`
- Create: `samples/2026-07-ai-lab-monthly-report.json`
- Create: `samples/2026-07-ai-lab-monthly-report.md`
- Create: `samples/2026-07-ai-lab-monthly-report.docx`
- Create: `samples/2026-07-ai-lab-monthly-report.pdf`
- Create: `scripts/verify-project.ps1`
- Test: `ruoyi-lab/src/test/java/com/ailab/system/report/DemoReportFixtureTest.java`

- [ ] **Step 1: Write the failing demo fixture test**

Build the exact demonstration `ReportData` from SQL seed semantics and assert deterministic JSON/Markdown plus required content in DOCX. If LibreOffice exists, assert a valid PDF; otherwise return an explicit skipped diagnostic from this integration-only check.

- [ ] **Step 2: Implement externalized configuration and docs**

Document JDK 8, Maven, compatible Node/npm, MySQL 8, Redis, LibreOffice, initialization order, Windows/Linux paths, permissions, backup, report retries, period reopen, default credentials rotation, and troubleshooting.

- [ ] **Step 3: Generate tracked sample artifacts through production exporters**

Do not hand-author DOCX/PDF. Use the fixed demo fixture and the real exporters; validate magic bytes and render/open them during final verification.

- [ ] **Step 4: Implement the one-command project verifier**

`scripts/verify-project.ps1` runs SQL contract, normal Maven unit tests/package (Surefire does not discover the separately named `*IT` real-database test), frontend lint/build, placeholder scan, and artifact checks; it fails fast but prints a final stage summary. The real MySQL/Redis integration test is an explicit mandatory step after services are available in Task 18.

- [ ] **Step 5: Run and commit**

Run: `powershell -ExecutionPolicy Bypass -File scripts/verify-project.ps1`

Expected: every mandatory stage PASS; PDF environment capability reported separately if unavailable.

```powershell
git add ruoyi-admin/src/main/resources docs samples scripts ruoyi-lab/src/test
git commit -m "docs: add deployment and acceptance deliverables"
```

### Task 18: Final integration, security, visual, and clean-tree verification

**Required skills:** `superpowers:verification-before-completion`, `superpowers:requesting-code-review`

**Files:**
- Modify only files implicated by verification failures

- [ ] **Step 1: Run the full automated verifier from a clean process**

Run: `powershell -ExecutionPolicy Bypass -File scripts/verify-project.ps1`.

Expected: SQL PASS, Maven tests PASS, backend package PASS, frontend lint/build PASS, placeholder scan PASS, artifacts PASS.

- [ ] **Step 2: Start MySQL, Redis, backend, and frontend with demo profile**

Initialize base RuoYi SQL followed by `sql/ailab.sql`; start backend and frontend. Record exact commands and observed ports in `docs/acceptance-checklist.md`.

Run: `mvn -pl ruoyi-admin -am -Dspring.profiles.active=lab-it -Dtest=LabMapperMySqlIT -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: real MySQL mapper integration PASS while MySQL and Redis are running.

- [ ] **Step 3: Exercise the acceptance path in a real browser**

Verify manager dashboard drill-down, member maintenance, task validation/review, data-scope differences, period close/reopen, red-line revocation with corrective evidence, manual reason/strategy/business-line summaries, template editing/preview, asynchronous queued/running polling, process-restart recovery, partial artifact retry, all-format generation, Markdown re-import, finalization, sensitive download denial, and report history.

- [ ] **Step 4: Inspect generated visuals and files**

Capture dashboard/template-editor screenshots for review; render/open DOCX and PDF pages and verify Chinese fonts, tables, charts, headings, and page breaks.

- [ ] **Step 5: Run independent code review and fix blocking findings**

Review security, authorization at query/download layers, formula correctness, idempotency, path/process safety, and absence of stub implementations. Repeat focused tests after every fix.

- [ ] **Step 6: Commit acceptance notes and verified fixes if any**

```powershell
git add --update
git commit -m "fix: resolve final integration findings"
```

If there are no tracked changes, do not create an empty commit.

- [ ] **Step 7: Re-run final verification and confirm clean status**

Run: `git status --short` and `powershell -ExecutionPolicy Bypass -File scripts/verify-project.ps1`.

Expected: clean worktree and all required checks PASS.
