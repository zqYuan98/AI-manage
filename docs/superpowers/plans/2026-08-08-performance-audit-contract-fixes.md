# Performance Audit Contract Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enforce the reviewed performance audit, locking, authorization, calibration, revision-history, and immutable-detail contracts.

**Architecture:** Keep formula evaluation deterministic in `LabPerformanceCalculator`, enforce pre-mutation validation and period-first locking in `LabPerformanceServiceImpl`, and express stable locking/current-history reads in `LabPerformanceMapper.xml`. Public APIs continue deriving actor scope from `LabAccessService`; immutable score details serialize all source facts relevant to the scored member with explicit inclusion decisions and never expose unrelated members' collaboration facts.

**Tech Stack:** Java 8-compatible Spring Boot, MyBatis XML, MySQL 8, JUnit 5, Mockito, Maven.

---

### Task 1: Quality-gate minimum contract

**Files:**
- Modify: `ruoyi-lab/src/test/java/com/ailab/system/service/LabPerformanceServiceTest.java`
- Modify: `ruoyi-lab/src/main/java/com/ailab/system/service/LabPerformanceCalculator.java`
- Modify: `ruoyi-lab/src/main/java/com/ailab/system/service/impl/LabPerformanceServiceImpl.java`

- [ ] Add failing preview/close tests proving a monthly key task without an applicable gate fails before period creation, deductions, scores, or locks.
- [ ] Add a failing calculator test proving one present but unpassed gate yields quality zero without validation failure.
- [ ] Run focused tests and confirm behavioral RED.
- [ ] Add deterministic preflight validation with task-specific error text and make focused tests GREEN.

### Task 2: Period-first collaboration locking

**Files:**
- Modify: `ruoyi-lab/src/test/java/com/ailab/system/service/LabPerformanceServiceTest.java`
- Modify: `ruoyi-lab/src/main/java/com/ailab/system/mapper/LabPerformanceMapper.java`
- Modify: `ruoyi-lab/src/main/resources/mapper/lab/LabPerformanceMapper.xml`
- Modify: `ruoyi-lab/src/main/java/com/ailab/system/service/impl/LabPerformanceServiceImpl.java`

- [ ] Add failing tests for create/review on CLOSED periods, reopen allowance, close snapshot locking, and period-before-collaboration order.
- [ ] Run focused tests and confirm failures are caused by missing period/collaboration locks.
- [ ] Add normal record lookup, period lock/create helper, full collaboration `FOR UPDATE` read, re-read checks, and affected-row guards.
- [ ] Run focused tests until GREEN.

### Task 3: Red-line monthly confirmation

**Files:**
- Modify: `ruoyi-lab/src/test/java/com/ailab/system/service/LabPerformanceServiceTest.java`
- Modify: `ruoyi-lab/src/main/java/com/ailab/system/service/impl/LabPerformanceServiceImpl.java`

- [ ] Add failing tests for member/lead rejection on active red lines, manager allowance, non-red-line self-confirmation, and revoked-red-line self-confirmation.
- [ ] Run focused tests and confirm behavioral RED.
- [ ] Add object-level active-red-line confirmation rule and rerun GREEN.

### Task 4: Server-owned calibration status

**Files:**
- Modify: `ruoyi-lab/src/test/java/com/ailab/system/service/LabPerformanceServiceTest.java`
- Modify: `ruoyi-lab/src/main/java/com/ailab/system/dto/CalibrationCommand.java`
- Modify: `ruoyi-lab/src/main/java/com/ailab/system/service/impl/LabPerformanceServiceImpl.java`

- [ ] Add failing reflection/behavior tests proving clients cannot set result status and active red lines cannot be calibrated.
- [ ] Run focused tests and confirm RED.
- [ ] Remove the writable status field, derive `NORMAL` on the server, and reject active red-line calibration.
- [ ] Run focused tests until GREEN.

### Task 5: Manager-only revision history

**Files:**
- Modify: `ruoyi-lab/src/test/java/com/ailab/system/service/LabPerformanceServiceTest.java`
- Modify: `ruoyi-lab/src/test/java/com/ailab/system/service/LabPerformanceContractTest.java`
- Modify: `ruoyi-lab/src/main/java/com/ailab/system/mapper/LabPerformanceMapper.java`
- Modify: `ruoyi-lab/src/main/resources/mapper/lab/LabPerformanceMapper.xml`
- Modify: `ruoyi-lab/src/main/java/com/ailab/system/service/LabPerformanceService.java`
- Modify: `ruoyi-lab/src/main/java/com/ailab/system/service/impl/LabPerformanceServiceImpl.java`
- Modify: `ruoyi-lab/src/main/java/com/ailab/system/controller/LabPerformanceController.java`

- [ ] Add failing manager/member/lead service tests and controller contract tests for a dedicated revision endpoint.
- [ ] Run focused tests and confirm RED.
- [ ] Add ordered mapper read, manager service gate, and the dedicated `lab:perf:history` endpoint/manager-only menu grant; keep the service gate as a second authorization boundary.
- [ ] Run focused tests until GREEN.

### Task 6: Complete immutable source-fact detail

**Files:**
- Modify: `ruoyi-lab/src/test/java/com/ailab/system/service/LabPerformanceServiceTest.java`
- Modify: `ruoyi-lab/src/test/java/com/ailab/system/service/LabPerformanceContractTest.java`
- Modify: `ruoyi-lab/src/main/java/com/ailab/system/service/LabPerformanceCalculator.java`
- Modify: `ruoyi-lab/src/main/resources/mapper/lab/LabPerformanceMapper.xml`
- Modify: `ruoyi-admin/src/test/java/com/ailab/system/mapper/LabMapperMySqlIT.java`

- [ ] Add failing snapshot tests covering full evidence audit metadata, complete quality-gate metadata/linkage, and every included/excluded collaboration candidate for the scored member while excluding unrelated members' facts.
- [ ] Add failing XML/IT contracts for full-fact reads and period-to-collaboration lock order.
- [ ] Run focused tests and confirm RED.
- [ ] Serialize stable ordered evidence and all collaboration facts with inclusion/exclusion reasons while preserving scoring rules.
- [ ] Extend real MySQL concurrency assertions, explicitly run `-Dtest=LabMapperMySqlIT` for RED/GREEN when MySQL is available, and otherwise record the unavailable database while still compiling the IT.
- [ ] Run focused tests until GREEN.

### Task 7: Final verification and review

**Files:**
- Verify all modified files above.

- [ ] Run focused performance tests.
- [ ] Run all `ruoyi-lab` tests.
- [ ] Run `scripts/verify-sql.ps1` and parse all seven mapper XML files.
- [ ] Run `ruoyi-admin` test compilation and package.
- [ ] Run an internal specification/code-quality review and address findings with new RED/GREEN cycles.
- [ ] Run `git diff --check`, confirm a clean staged scope, and commit `fix: enforce performance audit contracts`.
