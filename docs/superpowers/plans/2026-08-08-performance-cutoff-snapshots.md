# Performance Cutoff Snapshots Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make monthly performance snapshots use only locked collaboration facts at or before the close month, serialize every collaboration audit field deterministically, and prevent integration-test executor leaks.

**Architecture:** Keep `LabPerformanceCalculator` pure by deriving asset backup-training flags from collaboration rows already supplied in `PerformanceCalculationInput`. Add bounded, stable MyBatis reads for quarter collaboration facts and make close acquire them after the close-period row and before asset facts. Preview uses the same quarter-start-through-close-period boundary without locking.

**Tech Stack:** Java 8-compatible Spring Boot, MyBatis XML, MySQL 8, JUnit 5, Mockito, Maven.

---

### Task 1: Quarter backup cutoff and stable locks

**Files:**
- Modify: `ruoyi-lab/src/test/java/com/ailab/system/service/LabPerformanceServiceTest.java`
- Modify: `ruoyi-lab/src/test/java/com/ailab/system/service/LabPerformanceContractTest.java`
- Modify: `ruoyi-lab/src/main/java/com/ailab/system/domain/LabCollaborationRecord.java`
- Modify: `ruoyi-lab/src/main/java/com/ailab/system/dto/PerformanceCalculationInput.java`
- Modify: `ruoyi-lab/src/main/java/com/ailab/system/mapper/LabPerformanceMapper.java`
- Modify: `ruoyi-lab/src/main/resources/mapper/lab/LabPerformanceMapper.xml`
- Modify: `ruoyi-lab/src/main/java/com/ailab/system/service/impl/LabPerformanceServiceImpl.java`
- Modify: `ruoyi-lab/src/main/java/com/ailab/system/service/LabPerformanceCalculator.java`

- [x] Add calculator tests with July/August/September facts proving August close includes approved evidence-backed July/August BACKUP rows, excludes September, and matching asset/member decoys that are pending/rejected, lack evidence, use another category, target another task asset, or target another `toMemberId` do not suppress the primary owner's red line.
- [x] Add service/contract tests requiring `selectQuarterCollaborationFactsForUpdate(quarterStart, closePeriod)`, `ORDER BY period,id FOR UPDATE`, and period-to-collaboration-to-asset lock order; require preview's non-locking query to use the same upper bound.
- [x] Run `mvn -pl ruoyi-lab -am -Dtest=LabPerformanceServiceTest,LabPerformanceContractTest -Dsurefire.failIfNoSpecifiedTests=false test` and confirm failures are caused by missing bounded collaboration input/mapper methods.
- [x] Add a projection-only `relatedAssetId` field to `LabCollaborationRecord`; make bounded mapper reads join `lab_task` and map `t.asset_id` into that field, while `PerformanceCalculationInput` carries quarter facts separately from current-month scoring facts. Remove collaboration `EXISTS` from both asset queries; derive `quarterBackupTraining` in the pure calculator only when `category=BACKUP`, `reviewStatus=APPROVED`, evidence URL is nonblank, period is inside quarter-start through close-period, and the fact's asset and `toMemberId` match the critical asset and primary owner.
- [x] Rerun the focused tests and confirm GREEN.

### Task 2: Complete deterministic collaboration detail

**Files:**
- Modify: `ruoyi-lab/src/test/java/com/ailab/system/service/LabPerformanceServiceTest.java`
- Modify: `ruoyi-lab/src/main/java/com/ailab/system/service/LabPerformanceCalculator.java`

- [x] Extend the existing detail test to populate and assert `id`, `period`, `reviewComment`, `idempotencyKey`, `version`, `createBy`, ISO `createTime`, `updateBy`, ISO `updateTime`, `delFlag`, and `remark`, while preserving every existing domain field, reviewer/time/evidence/inclusion fields, and deterministic JSON equality.
- [x] Run the focused test and confirm RED on the first missing field.
- [x] Serialize the fields in a fixed `LinkedHashMap` order using the existing ISO helper.
- [x] Rerun focused tests and confirm GREEN, including independent identification of `PERIOD_OVERDUE:<period>:<taskId>`.

### Task 3: Integration-test executor lifecycle

**Files:**
- Modify: `ruoyi-lab/src/test/java/com/ailab/system/service/LabPerformanceContractTest.java`
- Modify: `ruoyi-admin/src/test/java/com/ailab/system/mapper/LabMapperMySqlIT.java`

- [x] Add a source contract that requires each executor in the concurrency IT to be closed by `finally`, call `shutdownNow()`, and await termination even when `Future.get()` or assertions fail.
- [x] Run the contract test and confirm RED against the current straight-line shutdown calls.
- [x] Wrap both executor sections in `try/finally`, always `shutdownNow()`, and call bounded `awaitTermination` while restoring interrupt status when interrupted.
- [x] Rerun contract tests and compile `LabMapperMySqlIT` through `ruoyi-admin` test compilation.

### Task 4: MySQL coverage and final verification

**Files:**
- Modify: `ruoyi-admin/src/test/java/com/ailab/system/mapper/LabMapperMySqlIT.java`

- [x] Extend the real MySQL fixture with July/August/September BACKUP facts tied to different assets/members and assert August close only honors eligible July/August rows.
- [x] Explicitly run `mvn -pl ruoyi-admin -am -Dtest=LabMapperMySqlIT -Dsurefire.failIfNoSpecifiedTests=false test`; record MySQL availability separately from behavioral failures.
- [x] Run focused tests, all `ruoyi-lab` tests, `scripts/verify-sql.ps1`, parse all seven mapper XML files, and run `ruoyi-admin` test compilation/package.
- [x] Run independent code review, fix every valid P1/P2 through new RED/GREEN cycles, then run `git diff --check`.
- [x] Commit exactly `fix: complete performance cutoff snapshots` and confirm the worktree is clean.
