# Quarterly Backup Evidence Snapshots Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve every in-scope quarterly BACKUP candidate and its qualification chain in deterministic monthly performance snapshots without exposing other members' records.

**Architecture:** Keep `LabPerformanceCalculator` pure and build a member-scoped `quarterBackupFacts` section from the bounded facts supplied by the service. Derive each asset's qualifying record IDs from the same evaluated candidates so red-line suppression and the immutable audit trail cannot diverge. Make both current-month and quarterly mapper projections join tasks for `related_asset_id`.

**Tech Stack:** Java 8-compatible Spring Boot, MyBatis XML, Fastjson2, JUnit 5, Mockito, MySQL 8, Maven.

---

### Task 1: Behavior and mapping RED

**Files:**
- Modify: `ruoyi-lab/src/test/java/com/ailab/system/service/LabPerformanceServiceTest.java`
- Modify: `ruoyi-lab/src/test/java/com/ailab/system/service/LabPerformanceContractTest.java`

- [x] Extend the July/August/September calculator test with a qualified July row, same-member decoys, and an unrelated-member secret row; assert stable `quarterBackupFacts`, full audit metadata, qualification/exclusion reasons, matched asset IDs, and `assetFacts.qualifyingCollaborationIds`.
- [x] Assert unrelated-member data is absent and two calculations produce identical JSON with stable record/asset ordering.
- [x] Add an XML contract requiring current-period collaboration queries to join `lab_task` and project `related_asset_id` as well as the complete quarterly result mapping.
- [x] Run focused tests and observe behavioral and mapping failures caused by the missing snapshot/projection.

### Task 2: Minimal pure snapshot implementation

**Files:**
- Modify: `ruoyi-lab/src/main/java/com/ailab/system/service/LabPerformanceCalculator.java`
- Modify: `ruoyi-lab/src/main/resources/mapper/lab/LabPerformanceMapper.xml`

- [x] Evaluate only facts targeting the scored member and at least one of that member's critical assets; preserve in-scope decoys with deterministic exclusion reasons and omit every unrelated member.
- [x] Serialize complete domain/audit fields, qualification/inclusion state, matched asset IDs, and stable ordering into `quarterBackupFacts`.
- [x] Add stable `qualifyingCollaborationIds` to each asset detail and make red-line suppression use the same evaluated facts.
- [x] Change both current-month collaboration selects to join tasks and project `related_asset_id` without changing their period/id locking order.
- [x] Run focused tests and confirm GREEN.

### Task 3: Real mapper integration and verification

**Files:**
- Modify: `ruoyi-admin/src/test/java/com/ailab/system/mapper/LabMapperMySqlIT.java`

- [x] Extend the existing MySQL mapper-plus-calculator test to assert the qualified July record ID appears in August detail, the future row is absent, and the unrelated row is excluded from the snapshot.
- [x] Run `ruoyi-admin` test compilation and explicitly attempt `LabMapperMySqlIT` against configured MySQL.
- [x] Run focused tests, all `ruoyi-lab` tests, SQL verification, all seven XML parses, admin package, and `git diff --check`.
- [x] Request independent code review and address every valid P1/P2 through RED/GREEN.
- [x] Commit exactly `fix: snapshot quarterly backup evidence` and confirm a clean worktree.
