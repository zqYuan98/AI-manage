# Goal Termination Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement safe manager-only termination and archive behavior for active goals while retaining formal business history.

**Architecture:** Extend the existing goal state machine with one optimistic, audited `ACTIVE -> TERMINATED` transition. The service locks the goal hierarchy and linked tasks, refuses unresolved work, cascades only status changes to child milestones, and exposes the transition through a dedicated secured REST endpoint and explicit Chinese UI.

**Tech Stack:** Java 8, Spring Boot/Security/Transactions, MyBatis/MySQL 8, Vue 2/Element UI, JUnit 5, Maven, ESLint.

---

### Task 1: State-machine contracts

**Files:**
- Modify: `ruoyi-lab/src/test/java/com/ailab/system/service/LabGoalServiceTest.java`
- Modify: `ruoyi-lab/src/test/java/com/ailab/system/sql/LabSqlContractTest.java`

- [ ] Add failing tests for manager permission, valid reason, optimistic version, only `ACTIVE` input, settled task rules, unresolved-count rejection, child cascade, and audit fields.
- [ ] Add failing reflection tests for `PUT /lab/goal/{id}/terminate` and `lab:goal:terminate`.
- [ ] Add failing SQL contracts for audit columns, migration, permission seed, and manager-only role grant.
- [ ] Run focused Maven tests and record the expected RED result.

### Task 2: Backend lifecycle and persistence

**Files:**
- Modify: `ruoyi-lab/src/main/java/com/ailab/system/domain/LabGoal.java`
- Create: `ruoyi-lab/src/main/java/com/ailab/system/dto/GoalTerminationRequest.java`
- Modify: `ruoyi-lab/src/main/java/com/ailab/system/mapper/LabGoalMapper.java`
- Modify: `ruoyi-lab/src/main/resources/mapper/lab/LabGoalMapper.xml`
- Modify: `ruoyi-lab/src/main/java/com/ailab/system/service/LabGoalService.java`
- Modify: `ruoyi-lab/src/main/java/com/ailab/system/service/impl/LabGoalServiceImpl.java`
- Modify: `ruoyi-lab/src/main/java/com/ailab/system/controller/LabGoalController.java`

- [ ] Add termination audit fields and the request DTO.
- [ ] Add a fenced mapper update for `ACTIVE -> TERMINATED`.
- [ ] Implement deterministic locks, settled-task validation, annual-child cascade, and manager authorization.
- [ ] Expose the secured REST endpoint with update audit logging.
- [ ] Run focused tests until GREEN.

### Task 3: Bootstrap and permission contract

**Files:**
- Modify: `sql/ailab.sql`
- Modify: `scripts/verify-sql.ps1`

- [ ] Add fresh-schema audit columns and idempotent legacy upgrades.
- [ ] Seed menu `31024` with `lab:goal:terminate` and grant it only to `lab_manager`.
- [ ] Update static SQL permission verification.
- [ ] Run JUnit SQL contracts and `scripts/verify-sql.ps1` until GREEN.

### Task 4: Chinese goal-page interaction

**Files:**
- Modify: `ruoyi-ui/src/api/lab/goal.js`
- Modify: `ruoyi-ui/src/views/lab/goal/index.vue`
- Modify: `ruoyi-ui/src/views/lab/goal/components/GoalDetailDrawer.vue`

- [ ] Add the termination API call and manager-only action.
- [ ] Require a reason and show an explicit preservation warning.
- [ ] Replace the permanently disabled delete affordance with state-specific actions and explanatory copy.
- [ ] Default the page to active goals and add draft/completed/terminated/all filters.
- [ ] Display the termination reason and time on archived goal detail.
- [ ] Run targeted ESLint and the production frontend build.

### Task 5: Fresh verification and delivery

**Files:**
- Verify all modified files.

- [ ] Parse all lab mapper XML and confirm zero raw `${}` expansions in the goal mapper.
- [ ] Run `mvn -pl ruoyi-lab -am clean test`.
- [ ] Run `mvn -pl ruoyi-admin -am -DskipTests package`.
- [ ] Run the SQL verifier, targeted frontend lint, and `npm run build:prod`.
- [ ] Run `git diff --check`, review the complete diff, and commit only after all evidence is fresh.
