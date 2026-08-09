# Lightweight Department Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the existing AI Lab ledger into a Chinese, role-specific lightweight commitment management system for a sub-10-person R&D department, with self-closed weekly commitments and formally accepted monthly results.

**Architecture:** Keep the existing Vue 2 + Spring Boot modular monolith. Add an independent weekly execution state/event model beside the existing monthly workflow, cut readers over through a guarded migration, and make Dashboard, performance, close-period, reminders and report providers consume one named calculation contract. Serve manager/member workbenches from trusted server-side scope and translate internal codes through one presentation dictionary.

**Tech Stack:** Java 8, Spring Boot, MyBatis XML, MySQL 8, Redis, Vue 2, Element UI, Maven Surefire 3.2.5, PowerShell verification scripts.

---

## 1. File and responsibility map

### Backend files to create

- `ruoyi-lab/src/main/java/com/ailab/system/domain/LabTaskExecutionEvent.java` — append-only weekly execution transition record.
- `ruoyi-lab/src/main/java/com/ailab/system/domain/LabTaskMigrationIssue.java` — quarantined legacy row that cannot be mapped deterministically.
- `ruoyi-lab/src/main/java/com/ailab/system/domain/LabManagementDecision.java` — minimal weekly meeting decision record.
- `ruoyi-lab/src/main/java/com/ailab/system/domain/LabPeriodCloseSnapshot.java` — immutable period/revision header and close metadata.
- `ruoyi-lab/src/main/java/com/ailab/system/domain/LabPeriodCloseFact.java` — versioned monthly result, weekly commitment, evidence, review, block and collaboration facts.
- `ruoyi-lab/src/main/java/com/ailab/system/domain/LabFormalAcceptanceRevision.java` — immutable revision created by each monthly acceptance.
- `ruoyi-lab/src/main/java/com/ailab/system/domain/LabFormalAcceptanceFact.java` — accepted result/weight/evidence/reviewer snapshot for open-period and close reads.
- `ruoyi-lab/src/main/java/com/ailab/system/domain/LabTaskWorkflowEvent.java` — append-only monthly submit/return/confirm/reopen/carry audit event.
- `ruoyi-lab/src/main/java/com/ailab/system/dto/WeeklyCommitmentCommand.java` — typed create/activate/complete/undo/carry commands.
- `ruoyi-lab/src/main/java/com/ailab/system/dto/CommitmentProgress.java` — named `executionAsOf`, numerator, denominator and status counts.
- `ruoyi-lab/src/main/java/com/ailab/system/dto/ManagerWorkbench.java` — manager action-oriented response.
- `ruoyi-lab/src/main/java/com/ailab/system/dto/MemberWorkbench.java` — member self-service response.
- `ruoyi-lab/src/main/java/com/ailab/system/dto/BusinessStatusDescriptor.java` — localized status label, explanation and next action.
- `ruoyi-lab/src/main/java/com/ailab/system/mapper/LabCommitmentMapper.java` — weekly execution/event/migration persistence contract.
- `ruoyi-lab/src/main/java/com/ailab/system/mapper/LabManagementDecisionMapper.java` — decision persistence contract.
- `ruoyi-lab/src/main/java/com/ailab/system/mapper/LabPeriodCloseSnapshotMapper.java` — append-only close revision persistence and reads.
- `ruoyi-lab/src/main/java/com/ailab/system/mapper/LabFormalAcceptanceMapper.java` — exact formal revision persistence and reads.
- `ruoyi-lab/src/main/java/com/ailab/system/mapper/LabTaskWorkflowEventMapper.java` — append-only monthly workflow history.
- `ruoyi-lab/src/main/resources/mapper/lab/LabCommitmentMapper.xml` — static SQL for commitments, events, migration and close locking.
- `ruoyi-lab/src/main/resources/mapper/lab/LabManagementDecisionMapper.xml` — scoped decision queries.
- `ruoyi-lab/src/main/resources/mapper/lab/LabPeriodCloseSnapshotMapper.xml` — static revision/fact queries; never falls back to live rows for formal reads.
- `ruoyi-lab/src/main/resources/mapper/lab/LabFormalAcceptanceMapper.xml` and `LabTaskWorkflowEventMapper.xml` — static revision/event SQL.
- `ruoyi-lab/src/main/java/com/ailab/system/service/LabCommitmentService.java` — weekly self-close use cases.
- `ruoyi-lab/src/main/java/com/ailab/system/service/LabWorkbenchService.java` — role-specific workbench contract.
- `ruoyi-lab/src/main/java/com/ailab/system/service/LabCommitmentCalculationService.java` — the single operational/formal/performance calculation contract.
- `ruoyi-lab/src/main/java/com/ailab/system/service/impl/LabCommitmentServiceImpl.java` — transaction and audit implementation.
- `ruoyi-lab/src/main/java/com/ailab/system/service/impl/LabWorkbenchServiceImpl.java` — trusted scope aggregation.
- `ruoyi-lab/src/main/java/com/ailab/system/service/impl/LabTaskExecutionMigrationService.java` — deterministic, idempotent cutover verification.
- `ruoyi-lab/src/main/java/com/ailab/system/service/impl/LabPeriodCloseSnapshotService.java` — writes a new immutable revision in the close transaction and reads by exact revision.
- `ruoyi-lab/src/main/java/com/ailab/system/service/impl/LabFormalAcceptanceService.java` — creates one immutable formal revision during review confirmation.
- `ruoyi-lab/src/main/java/com/ailab/system/service/impl/LabBusinessStatusService.java` — centralized Chinese business labels.
- `ruoyi-lab/src/main/java/com/ailab/system/controller/LabWorkbenchController.java` — manager/member/lead workbench endpoints.
- `ruoyi-lab/src/main/java/com/ailab/system/controller/LabManagementDecisionController.java` — minimal decision API.
- `ruoyi-lab/src/main/java/com/ailab/system/report/provider/ReportFactClassification.java` — provider fact-source classification enum.

### Backend files to modify

- `ruoyi-lab/src/main/java/com/ailab/system/domain/LabTask.java` — add `executionStatus`, `carriedFromId`, execution version fields.
- `ruoyi-lab/src/main/java/com/ailab/system/constant/LabConstants.java` — stable execution status and migration codes.
- `ruoyi-lab/src/main/java/com/ailab/system/config/LabProperties.java` — cutover read/write feature gates.
- `ruoyi-lab/src/main/java/com/ailab/system/mapper/LabTaskMapper.java` and `LabTaskMapper.xml` — projection and compatibility fields.
- `ruoyi-lab/src/main/java/com/ailab/system/service/impl/LabTaskServiceImpl.java` — route weekly tasks to commitment service and allow cross-month ISO weeks.
- `ruoyi-lab/src/main/java/com/ailab/system/service/impl/TaskWorkflowServiceImpl.java` — keep formal workflow month-only.
- `ruoyi-lab/src/main/java/com/ailab/system/service/impl/LabAccessServiceImpl.java` — resource/action/field authorization.
- `ruoyi-lab/src/main/java/com/ailab/system/mapper/LabAccessMapper.java` and `LabAccessMapper.xml` — trusted reviewer, member and business-line scope queries.
- `ruoyi-lab/src/main/java/com/ailab/system/controller/LabTaskController.java` — typed weekly endpoints; keep formal monthly endpoints.
- `ruoyi-lab/src/main/java/com/ailab/system/service/impl/LabGoalServiceImpl.java` — formal vs operational progress.
- `ruoyi-lab/src/main/java/com/ailab/system/service/impl/LabDashboardServiceImpl.java` — named dual progress and workbench signals.
- `ruoyi-lab/src/main/java/com/ailab/system/mapper/LabDashboardMapper.java` and `LabDashboardMapper.xml` — execution status calculation.
- `ruoyi-lab/src/main/java/com/ailab/system/service/impl/LabPerformanceServiceImpl.java` and `LabPerformanceCalculator.java` — prove weekly facts cannot enter performance.
- `ruoyi-lab/src/main/java/com/ailab/system/mapper/LabPerformanceMapper.java` and `LabPerformanceMapper.xml` — close locks weekly children by parent month.
- `ruoyi-lab/src/main/java/com/ailab/system/service/impl/LabReminderServiceImpl.java` — commitment deadlines and closed-period block exclusion.
- `ruoyi-lab/src/main/java/com/ailab/system/quartz/LabScheduleTask.java` — weekly close and decision reminders.
- `ruoyi-lab/src/main/java/com/ailab/system/report/ReportGenerationOrchestrator.java` — finalized report close/revision gate.
- `ruoyi-lab/src/main/java/com/ailab/system/service/impl/LabReportServiceImpl.java`, `LabReportMapper.java` and `LabReportMapper.xml` — enforce object-level list/detail/download authorization using current permissions.
- `ruoyi-lab/src/main/java/com/ailab/system/report/model/ReportContext.java` — formal close revision and execution cutoff metadata.
- `ruoyi-lab/src/main/java/com/ailab/system/mapper/LabReportDataMapper.java` and `LabReportDataMapper.xml` — provider data classifications.
- `DataSourceProvider.java` and `DataSourceProviderRegistry.java` — require a known fact classification, a unique provider code and complete startup registration.
- All 11 providers under `ruoyi-lab/src/main/java/com/ailab/system/report/provider/` — declare `FORMAL_SNAPSHOT`, `FORMAL_CLOSE_SNAPSHOT`, `CONTEXT_SNAPSHOT`, or `MANUAL_REVISION`.

### Frontend files to create

- `ruoyi-ui/src/api/lab/workbench.js` — role workbench and decision APIs.
- `ruoyi-ui/src/utils/lab-status.js` — centralized Chinese display dictionary.
- `ruoyi-ui/src/views/lab/dashboard/components/ManagerWorkbench.vue` — manager action-first home.
- `ruoyi-ui/src/views/lab/dashboard/components/MemberWorkbench.vue` — member commitments home.
- `ruoyi-ui/src/views/lab/dashboard/components/WeeklyMeetingPanel.vue` — deviations and decisions.
- `ruoyi-ui/src/views/lab/task/components/WeeklyCommitmentDrawer.vue` — minimal weekly create/close UI.

### Frontend files to modify

- `ruoyi-ui/src/views/lab/dashboard/index.vue` — select role-specific workbench.
- Existing Dashboard components — remove English eyebrow text and raw codes.
- `ruoyi-ui/src/views/lab/task/index.vue` — separate monthly formal workflow from weekly execution actions.
- `ruoyi-ui/src/views/lab/task/components/TaskFormDrawer.vue` — monthly fields vs weekly minimal fields.
- `ruoyi-ui/src/api/lab/task.js` — typed weekly execution endpoints.
- Goal/member/performance/report/template pages — apply dictionary and role-specific navigation wording.
- `ruoyi-ui/src/router/index.js` and seeded menu labels in `sql/ailab.sql` — Chinese action-oriented navigation.

### Database, scripts and documentation

- `sql/ailab.sql` — idempotent columns, tables, indexes, dictionaries, menu labels and demo data.
- `sql/test/ailab-legacy-fixture.sql` — ambiguous legacy state combinations.
- `sql/test/ailab-mapper-fixture.sql` — current and cross-month weekly facts.
- `scripts/verify-sql.ps1` — migration/order/index/dictionary contracts.
- `scripts/verify-project.ps1` — localization allowlist and full clean verification.
- `scripts/invoke-maven.ps1` — discover a compatible JDK/Maven on every invocation and forward arguments without relying on shell state.
- `scripts/accept-lab-workbench.ps1` — reproducible real-role browser acceptance and screenshot evidence.
- `docs/data-dictionary.md`, `docs/deployment.md`, `docs/acceptance-checklist.md` — new contracts and cutover instructions.

---

### Task 1: Lock baseline and add failing schema/migration contracts

**Files:**
- Modify: `ruoyi-lab/src/test/java/com/ailab/system/sql/LabSqlContractTest.java`
- Create: `ruoyi-lab/src/test/java/com/ailab/system/service/LabTaskExecutionMigrationTest.java`
- Create: `scripts/invoke-maven.ps1` and its discovery/argument-forwarding contract.
- Modify: `sql/test/ailab-legacy-fixture.sql`
- Modify: `ruoyi-admin/src/test/java/com/ailab/system/mapper/LabMapperMySqlIT.java`

- [ ] **Step 1: Add and prove the hermetic Maven launcher, then run the existing clean baseline**

The launcher resolves a Java 8-compatible JDK and Maven on every process invocation, sets `JAVA_HOME`/PATH only for the child process, prints the resolved versions, forwards every argument unchanged and exits with Maven's code. Test it from a clean PowerShell process before any focused command.

Run:

```powershell
& scripts/verify-project.ps1
```

Expected: all existing stages PASS before feature edits.

- [ ] **Step 2: Write failing SQL contracts**

Add assertions requiring:

```java
assertContains(sql, "execution_status");
assertContains(sql, "lab_task_execution_event");
assertContains(sql, "lab_task_migration_issue");
assertContains(sql, "carried_from_id");
assertContains(sql, "lab_management_decision");
assertContains(sql, "lab_period_close_snapshot");
assertContains(sql, "lab_period_close_fact");
assertContains(sql, "lab_formal_acceptance_revision");
assertContains(sql, "lab_formal_acceptance_fact");
assertContains(sql, "lab_task_workflow_event");
```

Add a contract that the backfill predicate uses `workflow_status`, `result_status`, `actual_finish_time`, `period_lock_flag`, and open block existence.

- [ ] **Step 3: Add legacy fixtures that expose wrong one-column mapping**

Create weekly rows for:

```text
CONFIRMED + UNDONE
CONFIRMED + ONTIME + actual_finish_time
PENDING_REVIEW + DELAYED + actual_finish_time
PENDING_REVIEW + EXCEEDED + actual_finish_time
ACTIVE + DELAYED
CONFIRMED + ONTIME + OPEN block
ACTIVE + DOING + OPEN block
locked week whose ISO week crosses a month boundary
```

- [ ] **Step 4: Run RED**

Run:

```powershell
& scripts/invoke-maven.ps1 -pl ruoyi-lab -am -Dtest=LabSqlContractTest,LabTaskExecutionMigrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: test compilation fails for missing migration types and SQL assertions fail for missing schema.

- [ ] **Step 5: Commit RED tests only**

```powershell
git add scripts/invoke-maven.ps1 ruoyi-lab/src/test sql/test ruoyi-admin/src/test
git commit -m "test: define commitment migration contracts"
```

### Task 2: Add execution schema, events and deterministic migration

**Files:**
- Create backend domain/mapper/migration files from the map above.
- Modify: `ruoyi-lab/src/main/java/com/ailab/system/domain/LabTask.java`
- Modify: `ruoyi-lab/src/main/java/com/ailab/system/constant/LabConstants.java`
- Modify: `sql/ailab.sql`
- Modify: `scripts/verify-sql.ps1`

- [ ] **Step 1: Add the minimal schema**

Use stable codes:

```java
public static final String EXEC_PLANNED = "PLANNED";
public static final String EXEC_ACTIVE = "ACTIVE";
public static final String EXEC_SELF_DONE = "SELF_DONE";
public static final String EXEC_SELF_UNDONE = "SELF_UNDONE";
public static final String EXEC_CANCELLED = "CANCELLED";
```

`lab_task_execution_event` must record task, from/to status, result status, finish time, actor, reason, task version, evidence version and event time. Add a unique idempotency key for carry-over and a unique `(carried_from_id, period)` constraint for new tasks.

- [ ] **Step 2: Implement combination-based migration**

Pseudo-code:

```java
if (hasOpenBlock && terminalCandidate(row)) return quarantine("TERMINAL_WITH_OPEN_BLOCK");
if (isConfirmedOrPending(row) && isCompleted(row) && row.getActualFinishTime() != null) return SELF_DONE;
if (isConfirmedOrPending(row) && isUndone(row)) return SELF_UNDONE;
if (isDraftDoingWithoutFinish(row)) return PLANNED;
if (isActiveDoingWithoutFinish(row)) return ACTIVE;
return quarantine("AMBIGUOUS_LEGACY_COMBINATION");
```

Do not update old workflow, review, evidence or audit fields.

For every successfully migrated row, append exactly one idempotent `MIGRATED_BASELINE` execution event whose resulting status/version equals the task row. Terminal candidates with an open block are quarantined until the episode is explicitly closed with `MIGRATION_TERMINAL_UNRESOLVED`; tests assert source episode count, close-event count and rerun idempotence.

- [ ] **Step 3: Implement idempotent stages and cutover metadata**

Add `READ_NEW_MODEL` and `WRITE_SELF_CLOSE` feature gates plus persistent cutover state. The migration service must refuse read/write cutover while quarantine rows exist, count groups before/after, and record the point of no return only after the first member action event written while `WRITE_SELF_CLOSE` is enabled; `MIGRATED_BASELINE` never advances that marker.

- [ ] **Step 4: Run focused GREEN**

Run the Task 1 focused Maven command.

Expected: all migration and SQL contracts PASS.

- [ ] **Step 5: Run SQL verifier**

```powershell
& scripts/verify-sql.ps1
```

Expected: PASS with new tables, indexes and dictionaries.

- [ ] **Step 6: Commit**

```powershell
git add sql scripts ruoyi-lab/src/main ruoyi-lab/src/test ruoyi-admin/src/test
git commit -m "feat: add weekly execution fact model"
```

### Task 3: Implement weekly commitment state machine with TDD

**Files:**
- Create: `ruoyi-lab/src/test/java/com/ailab/system/service/LabCommitmentServiceTest.java`
- Create: `ruoyi-lab/src/test/java/com/ailab/system/service/WeeklyCommitmentWorkflowTest.java`
- Create/modify commitment service, DTO, controller and mapper files.
- Modify: `LabTaskServiceImpl.java`, `TaskWorkflowServiceImpl.java`, `LabTaskController.java`, `task.js`.

- [ ] **Step 1: Write failing transition tests**

Cover:

```java
createWeeklyCommitmentStartsActive();
memberCompletesOwnCommitmentWithoutReviewer();
memberCannotCompleteAnotherMembersCommitment();
completeRequiresFinishTimeAndResultDescription();
undoneRequiresReasonAndNextAction();
memberCannotCancelActiveCommitment();
managerCancellationRequiresScopeChangeReason();
correctionResetsCurrentResultFieldsButKeepsEventHistory();
carryCreatesExactlyOneNewCommitmentAndKeepsOriginalUndone();
terminalTransitionClosesOpenBlockInSameTransaction();
```

- [ ] **Step 2: Run RED**

```powershell
& scripts/invoke-maven.ps1 -pl ruoyi-lab -am -Dtest=LabCommitmentServiceTest,WeeklyCommitmentWorkflowTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: failures for missing service and endpoints.

- [ ] **Step 3: Implement minimal state machine**

Use one transaction for current row CAS update plus append-only event. Normal member creation writes `ACTIVE`; `PLANNED` is only a system-generated candidate. All terminal correction resets current result fields to `DOING/null` and records old values in the event.

- [ ] **Step 4: Restrict formal workflow to monthly tasks**

`submitResult/reviewPass/reviewReturn` must reject `taskLevel=week`. Existing API compatibility methods should delegate weekly commands to commitment service only when the new write gate is enabled.

- [ ] **Step 5: Run GREEN and adjacent regression**

```powershell
& scripts/invoke-maven.ps1 -pl ruoyi-lab -am -Dtest=LabCommitmentServiceTest,WeeklyCommitmentWorkflowTest,TaskWorkflowServiceTest,LabTaskServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add ruoyi-lab/src/main ruoyi-lab/src/test ruoyi-ui/src/api/lab/task.js
git commit -m "feat: self-close weekly commitments"
```

### Task 4: Complete the monthly formal state machine and immutable close revisions

**Files:**
- Create: formal acceptance revision/fact, close snapshot/fact and monthly workflow event domains, mappers/XML, services and focused tests.
- Modify: `LabAccessMapper.java/xml`, `TaskWorkflowServiceImpl.java`, `LabTaskServiceImpl.java`, `LabPerformanceServiceImpl.java`, period close mapper/XML and controller tests.
- Modify: `sql/ailab.sql`, `scripts/verify-sql.ps1`, `docs/data-dictionary.md`.

- [ ] **Step 1: Write the complete monthly transition matrix as failing tests**

Cover every allowed edge and representative rejected edge:

```text
DRAFT → ACTIVE only after acceptance criteria, owner, due date and definition fields exist; goal_weight/perf_weight each total 100; and another eligible reviewer currently exists
ACTIVE → PENDING_REVIEW with DONE or UNDONE result facts
PENDING_REVIEW → ACTIVE only with a required, append-only return reason; current result resets to DOING/null
PENDING_REVIEW → CONFIRMED only by a different eligible reviewer
CONFIRMED → reopened ACTIVE only through period reopen, which increments periodVersion; later acceptance/close create new formal/close/performance revisions
CONFIRMED+UNDONE carry creates one new-month task and leaves the source unchanged
all illegal transitions, self-review and stale-version writes are rejected
```

Add the trusted reviewer query to this task and prove a manager-owned monthly result cannot activate unless `LabAccessMapper` finds another enabled, in-scope reviewer. Evidence is required only for result submission, and the actual reviewer is captured only at review time.

- [ ] **Step 2: Write failing workflow-event and formal-acceptance tests**

Every submit, return, confirm, reopen and carry appends a version-fenced `lab_task_workflow_event`; no update may overwrite history. Each `PENDING_REVIEW → CONFIRMED` transaction appends a `formalRevision` and immutable accepted facts containing result, definition/weights, evidence version and reviewer/time/comment. Dashboard/Goal open-period formal progress reads the latest applicable formal revision; a later edit/reopen never changes an old revision.

- [ ] **Step 3: Write failing immutable close-snapshot tests**

The close transaction must append one `closeRevision` header and typed facts containing the exact monthly definitions/results/weights, evidence versions, reviewer/time/comment, child weekly commitments and their events, unresolved-block-at-cutoff facts, collaboration facts, member/business-line identity, performance revision and calculation version. It pins the latest accepted `formalRevision`; unaccepted monthly objects get close-only UNDONE facts and never become fake CONFIRMED acceptances. Tests must prove:

```text
reopen increments `periodVersion` for fencing but creates neither a formal nor close revision by itself
the next successful acceptance appends a formal revision; the next successful close appends close revision N+1 and never updates N
exact revision reads survive later task/evidence/member edits
open-period formal progress reads latest acceptance; finalized reports read the exact formal revision pinned by close and cannot fall back to current rows
close refuses when a task state differs from its last execution event
close and reopen lock/unlock every weekly child by parent month, including cross-month ISO weeks
```

- [ ] **Step 4: Observe RED, implement the minimal state machine and snapshot persistence, then run GREEN**

```powershell
& scripts/invoke-maven.ps1 -pl ruoyi-lab -am -Dtest=TaskWorkflowServiceTest,LabTaskWorkflowEventTest,LabFormalAcceptanceServiceTest,LabPeriodCloseSnapshotServiceTest,LabPerformanceServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 5: Verify schema/idempotent migration and commit**

```powershell
& scripts/verify-sql.ps1
git add ruoyi-lab/src/main ruoyi-lab/src/test sql scripts docs/data-dictionary.md
git commit -m "feat: version formal period close facts"
```

### Task 5: Enforce object/action/field authorization

**Files:**
- Modify: `LabAccessService.java`, `LabAccessServiceImpl.java`, `LabAccessMapper.java/xml`, `LabTaskServiceImpl.java`.
- Modify: `LabReportServiceImpl.java`, `ReportGenerationOrchestrator.java`, `LabReportMapper.java/xml`.
- Modify/Create tests: `LabAccessServiceTest.java`, `LabCommitmentServiceTest.java`, `TaskWorkflowServiceTest.java`, `LabReportServiceTest.java`, `ReportGenerationOrchestratorTest.java`.

- [ ] **Step 1: Write failing authorization tests**

Required cases:

```text
member edits only own weekly execution fields
member cannot change parentId/ownerId/bizLine after activation
line lead reads same-line weekly facts but cannot rewrite member result
manager override requires reason and audit
monthly definition fields are manager-only
reviewer cannot equal owner
manager-owned monthly result cannot activate without an independent reviewer
member cannot forge ownerId/bizLine/parentId/weights in request bodies
activated definition edits require a change reason and append-only audit event
lead sees same-line non-sensitive finalized reports plus policy-enabled ALL non-sensitive finalized reports
member sees only allowed finalized reports
manager with live sensitive permission may read ALL; revocation immediately blocks list/detail/download
```

Express the report tests as a complete matrix across actor `{manager, lead, member}`, line `{same, other, ALL}`, lifecycle `{draft, finalized}` and sensitivity `{ordinary, sensitive}`. `ALL` non-sensitive finalized visibility follows the configured organization policy; no role inherits it accidentally from same-line logic. Sensitive permission is re-read for list, detail and download.

- [ ] **Step 2: Run RED**

```powershell
& scripts/invoke-maven.ps1 -pl ruoyi-lab -am -Dtest=LabAccessServiceTest,LabCommitmentServiceTest,TaskWorkflowServiceTest,LabReportServiceTest,ReportGenerationOrchestratorTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: object/field/report matrix assertions fail before access wiring.

- [ ] **Step 3: Add explicit access methods**

Example interface:

```java
void requireWeeklyWrite(LabTask task, Long actorId);
void requireMonthlyDefinitionWrite(LabTask task, Long actorId);
void requireMonthlyReview(LabTask task, Long actorId);
void requireReportRead(String reportBizLine, boolean sensitive, Long actorId);
```

Never infer authorization from request `ownerId`, attributes or snapshot JSON.

Apply these checks inside every list/detail/download/generate/finalize call, not only controller annotations. Report list SQL must project narrow public columns, use a fixed small-column sort allowlist and cap page size before PageHelper. The independent-reviewer query uses current enabled users and trusted business-line membership.

- [ ] **Step 4: Run GREEN**

Run the same command. Expected: all five access/workflow/report classes PASS.

- [ ] **Step 5: Commit**

```powershell
git add ruoyi-lab/src/main ruoyi-lab/src/test
git commit -m "fix: enforce commitment action boundaries"
```

### Task 6: Support cross-month ISO weeks, close locking and block lifecycle

**Files:**
- Modify: `LabPeriodUtils.java`, `LabTaskServiceImpl.java`, `LabPerformanceServiceImpl.java`, `LabPerformanceMapper.java/xml`, `LabReminderServiceImpl.java`, `LabScheduleTask.java`.
- Modify tests: `LabPeriodUtilsTest.java`, `LabTaskServiceTest.java`, `LabPerformanceServiceTest.java`, `LabReminderServiceTest.java`, `LabDashboardSqlContractTest.java`.

- [ ] **Step 1: Write failing boundary tests**

Cover an ISO week spanning two months where `planDate` belongs to the parent month. Assert close locks all weekly children by parent ID regardless of weekly period string; reopening the parent month unlocks current rows while preserving the old close snapshot and produces a new revision on the next close.

Add block cases:

```text
terminal transition closes OPEN episode
close snapshots unresolved episode then closes it as PERIOD_CLOSED_UNRESOLVED
carried commitment opens new episode with carried_from_event_id
reminder scan excludes locked/closed source episode
task update racing period close is fenced by close version
close rejects current execution state/last-event mismatch and queues repair
```

- [ ] **Step 2: Run RED**

```powershell
& scripts/invoke-maven.ps1 -pl ruoyi-lab -am -Dtest=LabPeriodUtilsTest,LabTaskServiceTest,LabPerformanceServiceTest,LabReminderServiceTest,LabDashboardSqlContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: cross-month close/reopen, block lifecycle and stale-event fencing assertions fail.

- [ ] **Step 3: Implement cross-month parent rule**

Replace the “whole ISO week must fit in month” rule with `planDate` inside parent month. Keep weekly period syntax validation.

- [ ] **Step 4: Implement fixed close lock order**

```text
period close → month tasks → weekly children → evidence/gates/blocks/collaboration → performance snapshot
```

All update paths fail with a Chinese “周期已关闭” error when the close version changes.

- [ ] **Step 5: Run the same focused command GREEN and commit**

```powershell
git add ruoyi-lab/src/main ruoyi-lab/src/test
git commit -m "fix: close weekly commitments by parent month"
```

### Task 7: Implement one calculation contract and dual-read projections

**Files:**
- Create: `CommitmentProgress.java`, `LabCommitmentCalculationService.java` and calculation tests.
- Modify Dashboard/Goal/Performance services and mappers listed above.
- Modify every legacy weekly reader, including `LabTaskService.calculateMonthProgress`, close, reminder and report projections.
- Modify tests: `LabDashboardServiceTest.java`, `LabGoalServiceTest.java`, `LabPerformanceContractTest.java`, `LabDashboardSqlContractTest.java`.

- [ ] **Step 1: Write golden-data failing tests**

Use one fixture with:

```text
6 due weekly commitments: SELF_DONE results EXCEEDED/ONTIME/DELAYED, 1 SELF_UNDONE, 2 ACTIVE
1 future commitment: excluded
1 manager-cancelled scope item: excluded
confirmed monthly EXCEEDED/ONTIME/DELAYED results across multiple goal_weight/perf_weight/quarter_weight groups
1 confirmed monthly UNDONE result
1 unconfirmed month at close
1 commitment activated after asOf: excluded
```

Assert:

```text
executionRate uses commitments due by asOf; empty denominator returns counts and no percentage
future/cancelled do not alter E
formal coefficients EXCEEDED/ONTIME/DELAYED are all 1
formal coefficient UNDONE = 0
unconfirmed close snapshot coefficient = 0 without forging CONFIRMED
weekly execution never changes performance result
performance alone keeps 1.2/1/0.7 and cannot leak into formal progress
milestone and annual operational/formal progress match explicit weighted golden values
expected progress and 5/15 deviation risk bands match the same asOf
status counts sum to the denominator
```

- [ ] **Step 2: Run RED**

```powershell
& scripts/invoke-maven.ps1 -pl ruoyi-lab -am -Dtest=LabCommitmentCalculationServiceTest,LabDashboardServiceTest,LabGoalServiceTest,LabPerformanceContractTest,LabDashboardSqlContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: calculation-version, coefficient, denominator/asOf or dual-projection assertions fail.

- [ ] **Step 3: Implement named calculations**

Implement `LabCommitmentCalculationService` as the only calculator used by Dashboard, Goal, close and report consumers. Return `executionAsOf`, nullable rate, numerator, denominator, status counts, expected progress, risk band, calculation version, `formalRevision` and `closeRevision`. Goal health uses operational progress; open-period formal progress reads the latest immutable acceptance revision, while closed-period formal progress/performance read the exact formal revision pinned by the selected close revision.

- [ ] **Step 4: Add dual-read projections without enabling cutover**

Keep both named reader projections temporarily. Add comparison APIs for Dashboard, Goal and `LabTaskService.calculateMonthProgress`, but leave feature defaults on old-read/no-self-write. This task proves calculation semantics only; it must not enable `READ_NEW_MODEL`, `WRITE_SELF_CLOSE` or the point of no return because later consumers are not adapted yet.

- [ ] **Step 5: Prove dual-read outputs on focused fixtures**

```powershell
& scripts/invoke-maven.ps1 -pl ruoyi-lab -am -Dtest=LabCommitmentCalculationServiceTest,LabDashboardServiceTest,LabGoalServiceTest,LabPerformanceContractTest,LabDashboardSqlContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: both projections are available, named and comparable; no production gate is enabled.

- [ ] **Step 6: Run GREEN and commit**

```powershell
git add ruoyi-lab/src/main ruoyi-lab/src/test
git commit -m "feat: separate operational and accepted progress"
```

### Task 8: Add trusted manager/member workbench APIs and decisions

**Files:**
- Create workbench/decision backend files from the map.
- Modify: `LabDashboardController.java` only if compatibility routes delegate to the new service.
- Create tests: `LabWorkbenchServiceTest.java`, `LabManagementDecisionServiceTest.java`, `LabWorkbenchControllerTest.java`.

- [ ] **Step 1: Write failing role-workbench tests**

Manager response must include pending decisions, new blocks, forecast delays, pending monthly acceptance, stale key results and team commitment counts. Member response must include own monthly results, own weekly commitments, due items, blocks and missing evidence only. The business-line lead endpoint is P0 and returns same-line non-sensitive facts plus decision follow-up, but never manager-only close/override actions.

- [ ] **Step 2: Write decision tests**

The minimal decision entity has problem, decision, owner, due date, related goal/task and completion state. Do not add approval chains or generic workflow.

- [ ] **Step 3: Run RED**

```powershell
& scripts/invoke-maven.ps1 -pl ruoyi-lab -am -Dtest=LabWorkbenchServiceTest,LabManagementDecisionServiceTest,LabWorkbenchControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: missing workbench/decision contracts or incorrect scope assertions fail.

- [ ] **Step 4: Implement server-scoped aggregation**

Resolve `LabAccessContext` from the authenticated user before every query. Mapper SQL must include member/biz-line scope and stable ordering; do not fetch wide rows and filter in Java.

- [ ] **Step 5: Run the same command GREEN and commit**

```powershell
git add ruoyi-lab/src/main ruoyi-lab/src/test
git commit -m "feat: add role-specific department workbenches"
```

### Task 9: Classify report providers and require formal close for finalization

**Files:**
- Create: `ReportFactClassification.java`.
- Modify: `DataSourceProvider.java`, `DataSourceProviderRegistry.java`, `ReportGenerationOrchestrator.java`, `ReportContext.java`, `LabReportDataMapper.java/xml`.
- Modify all 11 provider classes.
- Modify tests: `ReportProviderContractTest.java`, `ReportGenerationOrchestratorTest.java`, `ReportFencingContractTest.java`, `LabMapperMySqlIT.java`.

- [ ] **Step 1: Write failing provider classification tests**

Require each provider to return one known classification. Assert the exact 11-provider mapping from the specification. At Spring startup reject duplicate provider codes and missing/unknown classifications; multiple providers may legitimately share one classification.

- [ ] **Step 2: Write failing finalization tests**

```text
open period cannot finalize
final report pins close revision, performance revision, template revision and execution cutoff
reopen produces a new report version
old finalized content does not change
context sections never affect formal KPI/performance
live-preview report instance is never directly finalizable
final content JSON, Markdown, Word and PDF hashes all come from the pinned snapshot regeneration
```

- [ ] **Step 3: Run RED**

```powershell
& scripts/invoke-maven.ps1 -pl ruoyi-lab -am -Dtest=ReportProviderContractTest,ReportGenerationOrchestratorTest,ReportFencingContractTest -Dsurefire.failIfNoSpecifiedTests=false test
& scripts/invoke-maven.ps1 -pl ruoyi-admin -am -Dtest=LabMapperMySqlIT -Dsurefire.failIfNoSpecifiedTests=false test-compile
```

Expected: exact classification, preview-finalization and pinned-artifact assertions fail before implementation.

- [ ] **Step 4: Implement classification and gates**

Draft preview may use live execution but its instance is permanently preview-only. A short transaction creates a pinned final candidate and one dependency-tracked durable chain with only DATA claimable. Workers outside any database transaction reload every classified provider from the exact `closeRevision`/formal/performance/template revisions and execution cutoff: DATA atomically publishes JSON/Markdown and activates WORD; WORD verifies/reuses the exact DATA hashes, publishes Word and activates PDF; PDF verifies/reuses the Word hash and publishes PDF. Only then does a second short CAS transaction verify all four hashes and finalize the candidate. Tests prove downstream jobs cannot be claimed early and retries reuse only the pinned upstream hashes. Contextual providers label `asOf`; formal providers must fail rather than fall back to live rows. A reopen can only produce a new pinned instance/version and cannot alter old content or hashes.

- [ ] **Step 5: Run the same focused/test-compile commands GREEN and commit**

```powershell
git add ruoyi-lab/src/main ruoyi-lab/src/test ruoyi-admin/src/test
git commit -m "fix: bind reports to formal commitment snapshots"
```

### Task 10: Centralize Chinese business language

**Files:**
- Create: `LabBusinessStatusService.java`, `BusinessStatusDescriptor.java`, `ruoyi-ui/src/utils/lab-status.js`.
- Modify seeded dictionaries and menu labels in `sql/ailab.sql`.
- Modify Dashboard, goal, task, member, performance, report and template pages.
- Modify: `scripts/verify-project.ps1`.
- Create/modify backend localization/report-output contracts and `scripts/verify-lab-ui.ps1`.

- [ ] **Step 1: Add failing localization contract**

The verifier must scan server errors, reminders, Vue literals, seeded labels, Markdown/Word/PDF visible text and rendered business dictionaries. Allow technical English only through an explicit allowlist for code, links, report formats and audit detail.

Forbidden visible defaults include:

```text
Goal trajectory
Task composition
Capacity ledger
Personal inbox
ACTIVE
PENDING_REVIEW
CONFIRMED
RED_LINE
```

- [ ] **Step 2: Run RED**

```powershell
& scripts/verify-project.ps1
```

Expected: localization stage fails before UI changes.

- [ ] **Step 3: Implement one catalog and generated clients**

Example:

```js
export const TASK_STATUS = Object.freeze({
  ACTIVE: { label: '进行中', nextAction: '更新执行情况' },
  SELF_DONE: { label: '成员已完成', nextAction: '查看结果' },
  SELF_UNDONE: { label: '本周未完成', nextAction: '填写下一步' }
})
```

Keep one versioned backend presentation catalog and generate/verify the frontend export from it; a contract test fails if code/Chinese label/next action differs. No page-local duplicate status maps.

- [ ] **Step 4: Replace demo content and navigation text**

Use realistic Chinese department goals, tasks, reminders, members and reports without fixing template titles to one period.

- [ ] **Step 5: Generate all four report artifacts and run localization/UI contracts**

Generate JSON, Markdown, Word and PDF from the demo fixture, inspect visible strings and render PDF/Word with real LibreOffice plus PDF rendering. Missing LibreOffice is a completion blocker, not a permitted skip. Add RED/GREEN assertions before changing each visible workflow state; the automated UI contract must check loading/error/retry/drill-down/action labels, not only source substrings.

- [ ] **Step 6: Run frontend lint/build and commit**

```powershell
& scripts/verify-lab-ui.ps1
& scripts/verify-project.ps1
git add ruoyi-ui ruoyi-lab/src/main ruoyi-lab/src/test sql scripts samples
git commit -m "feat: localize AI Lab management experience"
```

### Task 11: Build manager, lead and member workbench UI

**Files:**
- Create frontend workbench components and API file from the map.
- Modify: `ruoyi-ui/src/views/lab/dashboard/index.vue` and existing Dashboard components.
- Modify: task page/drawers.
- Create: automated component/browser contracts for manager, lead and member role flows.

- [ ] **Step 1: Write RED workbench behavior contracts**

Before component edits, assert role routing, stale-response fencing, loading/error/retry states, exact drill filters, keyboard Enter/Space, drawer reset and hidden unauthorized actions. Execute once and record failures against the current UI.

```powershell
& scripts/verify-lab-ui.ps1 -Mode Contract
```

Expected: the new role/workbench behavior assertions fail before component changes.

- [ ] **Step 2: Implement manager layout against real API**

Order sections as:

```text
今日需要处理 → 本月目标态势 → 团队承诺与负载 → 周会工作区
```

Every count must have a filtered drill-down action. Loading failure must show retry, not an empty state.

- [ ] **Step 3: Implement lead and member layouts**

Lead shows same-line commitments, blocks and decision follow-up without manager close/override controls. Member shows own monthly results, weekly commitments, due items, blocks and missing evidence. Keep three member primary actions: 新增本周承诺、报告阻塞、提交交付结果.

- [ ] **Step 4: Split monthly and weekly task forms**

Weekly drawer exposes only parent monthly result, deliverable, due date and coordination. It must not expose performance/goal weights or reviewer controls.

- [ ] **Step 5: Run the same behavior contracts GREEN and verify responsive, keyboard and concurrency states**

Use request identity tokens for rapid role/period changes; actionable rows support Enter and Space; drawers clear stale data before load.

Run `& scripts/verify-lab-ui.ps1 -Mode Contract` again. Expected: the same behavior assertions PASS.

- [ ] **Step 6: Run lint and production build**

```powershell
& scripts/verify-project.ps1
```

Expected: frontend lint/build PASS.

- [ ] **Step 7: Commit**

```powershell
git add ruoyi-ui
git commit -m "feat: add manager lead and member workbenches"
```

### Task 12: Add reminder, weekly-close and decision scheduling

**Files:**
- Modify: `LabReminderServiceImpl.java`, `LabScheduleTask.java`.
- Modify: `LabReminderServiceTest.java`, `LabScheduleTaskTest.java`.
- Modify Quartz seed in `sql/ailab.sql` if a new job is required.

- [ ] **Step 1: Write failing reminder tests**

Cover missing weekly commitment after cutoff, due commitment not self-closed by next workday, new block, forecast delay, unresolved decision, and exclusion of closed source episodes.

- [ ] **Step 2: Run RED**

```powershell
& scripts/invoke-maven.ps1 -pl ruoyi-lab -am -Dtest=LabReminderServiceTest,LabScheduleTaskTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: candidate/status/closed-source and failure-propagation assertions fail.

- [ ] **Step 3: Implement idempotent candidates**

Use episode/recipient/level/date keys. Scanner failures must propagate to Quartz job logs; one bad manager mapping must not prevent other candidate batches.

- [ ] **Step 4: Run the same focused command GREEN and commit**

```powershell
git add ruoyi-lab/src/main ruoyi-lab/src/test sql/ailab.sql
git commit -m "feat: remind commitment and decision owners"
```

### Task 13: Exercise migration stages and real MySQL paths

**Files:**
- Extend: `LabMapperMySqlIT.java`.
- Create: `ruoyi-admin/src/test/java/com/ailab/system/mapper/CutoverLifecycleMySqlIT.java`.
- Modify: `sql/test/ailab-legacy-fixture.sql`, `sql/test/ailab-mapper-fixture.sql`.
- Create: `docs/commitment-cutover-runbook.md`.
- Create/modify: `scripts/invoke-maven.ps1`, `docs/deployment.md`, `docs/data-dictionary.md`.

- [ ] **Step 1: Add MySQL IT scenarios**

Verify:

```text
legacy group counts before/after migration
CONFIRMED+UNDONE maps SELF_UNDONE
terminal+OPEN is quarantined
manual MIGRATION_TERMINAL_UNRESOLVED close then rerun succeeds
repeat migration is unchanged
write cutover blocks old reader rollback
cross-month week locks with parent month
execution and formal golden values
report finalization requires close revision
close snapshot revision N remains byte-for-byte stable after reopen and revision N+1
monthly state matrix, independent reviewer query and report live-permission revocation
```

- [ ] **Step 2: Run test compilation**

```powershell
& scripts/invoke-maven.ps1 -pl ruoyi-admin -am test-compile
if (-not (Test-Path 'ruoyi-admin/target/test-classes/com/ailab/system/mapper/CutoverLifecycleMySqlIT.class')) {
  throw 'CutoverLifecycleMySqlIT was not compiled/discovered'
}
```

Expected: PASS.

- [ ] **Step 3: Provision and run the isolated real MySQL/Redis environment**

The runbook must create a disposable `ailab_commitment_it` database and least-privilege user from explicit environment variables, start an isolated Redis namespace, and initialize in exactly this order:

```text
create database/user
→ sql/ry_20240629.sql
→ sql/quartz.sql
→ sql/test/ailab-legacy-fixture.sql
→ sql/ailab.sql twice
→ sql/test/ailab-mapper-fixture.sql
→ enable acceptance users with independent passwords
```

`LabMapperMySqlIT.DatabaseInitializer` owns the same idempotent sequence for IT; the runbook calls that initializer path rather than resetting tables separately. Cleanup drops only this database/user and Redis namespace.

```powershell
& scripts/invoke-maven.ps1 -pl ruoyi-admin -am -Dtest=LabMapperMySqlIT -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all IT assertions PASS on the disposable database. A connection refusal or unavailable Docker/local MySQL is a completion blocker; record it and provision the required service rather than claiming delivery.

- [ ] **Step 4: Write cutover and rollback runbook**

Document expand → baseline events → quarantine resolution → all-consumer compare with writes frozen → atomic read cutover → rollback rehearsal → write cutover, point of no return, startup guard, forward-fix procedure and stop conditions. Include exact commands, expected counts and evidence locations.

- [ ] **Step 5: Execute the guarded cutover in the disposable environment**

After workbench, report and reminder adaptations are complete: freeze commitment writes; backfill baseline events; require zero quarantines; compare old/new group counts and outputs for Dashboard, Goal, `LabTaskService.calculateMonthProgress`, close, reminders, workbenches and all 11 providers. Persist evidence, atomically enable `READ_NEW_MODEL`, rehearse rollback while writes remain frozen, then enable `WRITE_SELF_CLOSE`. The first real member action writes the persistent point of no return. Verify application startup rejects old-read configuration afterward and use forward-fix only.

```powershell
& scripts/invoke-maven.ps1 -pl ruoyi-admin -am -Dtest=LabMapperMySqlIT,CutoverLifecycleMySqlIT -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: every consumer comparison, rollback rehearsal, startup guard and post-PONR refusal passes against real MySQL.

- [ ] **Step 6: Commit**

```powershell
git add ruoyi-admin/src/test sql/test scripts/invoke-maven.ps1 docs/commitment-cutover-runbook.md docs/deployment.md docs/data-dictionary.md
git commit -m "test: verify commitment migration cutover"
```

### Task 14: Full verification and interactive acceptance

**Files:**
- Modify: `docs/acceptance-checklist.md`, `docs/deployment.md`, `docs/data-dictionary.md` and `scripts/accept-lab-workbench.ps1`.

- [ ] **Step 1: Run focused business tests**

```powershell
& scripts/invoke-maven.ps1 -pl ruoyi-lab -am -Dtest=LabCommitmentServiceTest,WeeklyCommitmentWorkflowTest,LabTaskExecutionMigrationTest,LabPeriodUtilsTest,LabTaskWorkflowEventTest,LabFormalAcceptanceServiceTest,LabPeriodCloseSnapshotServiceTest,TaskWorkflowServiceTest,LabAccessServiceTest,LabReportServiceTest,LabDashboardServiceTest,LabGoalServiceTest,LabPerformanceContractTest,LabReminderServiceTest,LabScheduleTaskTest,ReportProviderContractTest,ReportGenerationOrchestratorTest,ReportFencingContractTest,LabWorkbenchServiceTest,LabManagementDecisionServiceTest,LabWorkbenchControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all focused tests PASS.

- [ ] **Step 2: Run the mandatory clean verifier**

```powershell
& scripts/verify-project.ps1
```

Expected: SQL, XML, clean backend test, package, frontend lint/build, placeholders and artifacts all PASS.

- [ ] **Step 3: Start the reproducible acceptance environment**

Use the disposable MySQL/Redis profile from Task 13, start backend and frontend with recorded commands, verify health endpoints, and enable only the manager, lead and two member acceptance accounts with independent credentials. The script records service PIDs, URLs and an evidence directory and always stops only those processes it started.

- [ ] **Step 4: Run automated browser acceptance with real roles**

Manager flow:

```text
publish monthly result → see pending member commitment → receive block → accept monthly result → close period → finalize report
```

Member flow:

```text
open member workbench → create 3 weekly commitments in under 3 minutes → report block → self-complete one → mark one undone → submit monthly result
```

Verify all visible business labels are Chinese, drill-down filters are exact, member cannot access manager actions, and errors show retry rather than empty data.

Also execute the lead same-line/non-sensitive flow, live sensitive-permission revocation across report list/detail/download, close/reopen cross-month locking, preview-only report rejection, snapshot-based final regeneration and responsive/keyboard checks. Save screenshots plus request/response assertions under the ignored acceptance evidence directory.

```powershell
& scripts/accept-lab-workbench.ps1 -BaseUrl 'http://127.0.0.1:1024' -ApiUrl 'http://127.0.0.1:8080' -EvidenceDir '.acceptance/lightweight-management'
```

PASS requires every manager/lead/member assertion, real MySQL/Redis mutations, a real LibreOffice conversion and rendered PDF/Word visual checks to complete; no conditional skip is accepted.

- [ ] **Step 5: Verify documentation contracts and repository hygiene**

Update the data dictionary with execution/event/snapshot/classification formulas, deployment with exact cutover/provisioning commands and acceptance checklist with quantitative <10-person success measures. Run the documentation/static contracts before status checks.

```powershell
git diff --check
git status --short
```

Expected: no unintended or untracked artifacts.

- [ ] **Step 6: Request final code review and fix all Critical/Important findings**

Provide reviewers the specification, plan, baseline commit and complete diff. Re-run the relevant RED/GREEN tests for every accepted fix.

- [ ] **Step 7: Final commit**

```powershell
git add -A
git commit -m "feat: deliver lightweight department management"
```

---

## Execution policy

- Execute inline in this worktree because the user requested completion in the current task and no delegation was requested.
- Use `superpowers:executing-plans` before implementation.
- For every behavioral change, use `superpowers:test-driven-development`: observe a meaningful RED before production code and the same test GREEN afterward.
- When a failure is unexpected, use `superpowers:systematic-debugging` before editing.
- Before any completion claim, use `superpowers:verification-before-completion` and run the clean project verifier.
- Preserve unrelated user changes; stop if a dirty overlapping file appears.
