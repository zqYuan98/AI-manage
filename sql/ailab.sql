-- AI laboratory management bootstrap.  MySQL 8.0+, UTF-8, safe to re-run for the demo rows.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `lab_goal` (
 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key', `parent_id` bigint DEFAULT 0 COMMENT 'parent goal', `goal_level` varchar(16) NOT NULL COMMENT 'YEAR or QUARTER', `year` int NOT NULL COMMENT 'goal year', `period` varchar(16) DEFAULT NULL COMMENT 'quarter period', `goal_no` varchar(64) NOT NULL COMMENT 'business number', `title` varchar(200) NOT NULL COMMENT 'goal title', `target_value` varchar(500) DEFAULT NULL COMMENT 'target value', `accept_criteria` varchar(1000) DEFAULT NULL COMMENT 'acceptance criteria', `owner_id` bigint NOT NULL COMMENT 'member owner', `weight` decimal(8,2) DEFAULT 0 COMMENT 'weight', `progress_mode` varchar(16) DEFAULT 'MANUAL' COMMENT 'progress mode', `progress_rate` decimal(5,2) DEFAULT 0 COMMENT 'progress percent', `progress_desc` varchar(1000) DEFAULT NULL COMMENT 'progress description', `status` varchar(16) DEFAULT 'ACTIVE' COMMENT 'status', `version` int DEFAULT 0 COMMENT 'optimistic version', `del_flag` char(1) DEFAULT '0' COMMENT 'delete flag', `create_by` varchar(64) DEFAULT '' COMMENT 'creator', `create_time` datetime DEFAULT NULL COMMENT 'created time', `update_by` varchar(64) DEFAULT '' COMMENT 'updater', `update_time` datetime DEFAULT NULL COMMENT 'updated time', `remark` varchar(500) DEFAULT NULL COMMENT 'remark',
 `active_unique_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `del_flag` = '0' THEN 1 ELSE NULL END) STORED COMMENT 'active record unique marker',
 PRIMARY KEY (`id`), UNIQUE KEY `uk_lab_goal_year_no` (`year`,`goal_no`,`active_unique_flag`), KEY `idx_lab_goal_parent` (`parent_id`), KEY `idx_lab_goal_owner_status` (`owner_id`,`status`), KEY `idx_lab_goal_year_status` (`year`,`status`), KEY `idx_lab_goal_period` (`year`,`period`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='laboratory goal';

CREATE TABLE IF NOT EXISTS `lab_task` (
 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key', `parent_id` bigint DEFAULT 0 COMMENT 'parent task', `goal_id` bigint DEFAULT NULL COMMENT 'goal reference', `milestone_id` bigint DEFAULT NULL COMMENT 'milestone reference', `task_level` varchar(16) NOT NULL COMMENT 'month or week', `period` varchar(16) NOT NULL COMMENT 'business period', `biz_line` varchar(32) NOT NULL COMMENT 'business line', `task_type` varchar(16) NOT NULL COMMENT 'key or daily', `title` varchar(200) NOT NULL COMMENT 'task title', `owner_id` bigint NOT NULL COMMENT 'member owner', `dept_id` bigint DEFAULT NULL COMMENT 'department', `plan_date` date DEFAULT NULL COMMENT 'planned finish date', `actual_finish_time` datetime DEFAULT NULL COMMENT 'actual finish time', `deliverable` varchar(1000) DEFAULT NULL COMMENT 'deliverable', `perf_weight` decimal(8,2) DEFAULT 0 COMMENT 'performance weight', `goal_weight` decimal(8,2) DEFAULT 0 COMMENT 'goal contribution weight', `workflow_status` varchar(32) DEFAULT 'DRAFT' COMMENT 'workflow status', `result_status` varchar(32) DEFAULT 'DOING' COMMENT 'result status', `execution_status` varchar(24) DEFAULT NULL COMMENT 'independent weekly execution status', `carried_from_id` bigint DEFAULT NULL COMMENT 'source commitment for carry over', `execution_version` int NOT NULL DEFAULT 0 COMMENT 'weekly execution optimistic version', `result_desc` varchar(1000) DEFAULT NULL COMMENT 'result description', `fail_reason` varchar(1000) DEFAULT NULL COMMENT 'failure reason', `next_action` varchar(1000) DEFAULT NULL COMMENT 'next action', `asset_id` bigint DEFAULT NULL COMMENT 'related asset', `coordination_required` char(1) DEFAULT '0' COMMENT 'requires coordination', `coordination_owner_id` bigint DEFAULT NULL COMMENT 'coordination owner', `coordination_dept_id` bigint DEFAULT NULL COMMENT 'coordination department', `coordination_content` varchar(1000) DEFAULT NULL COMMENT 'coordination content', `coordination_support` varchar(1000) DEFAULT NULL COMMENT 'requested support', `coordination_desc` varchar(1000) DEFAULT NULL COMMENT 'coordination description', `current_block_flag` char(1) DEFAULT '0' COMMENT 'currently blocked', `current_block_start` datetime DEFAULT NULL COMMENT 'current block start', `period_lock_flag` char(1) DEFAULT '0' COMMENT 'period locked', `version` int DEFAULT 0 COMMENT 'optimistic version', `del_flag` char(1) DEFAULT '0' COMMENT 'delete flag', `create_by` varchar(64) DEFAULT '' COMMENT 'creator', `create_time` datetime DEFAULT NULL COMMENT 'created time', `update_by` varchar(64) DEFAULT '' COMMENT 'updater', `update_time` datetime DEFAULT NULL COMMENT 'updated time', `remark` varchar(500) DEFAULT NULL COMMENT 'remark',
 PRIMARY KEY (`id`), KEY `idx_lab_task_parent` (`parent_id`), KEY `idx_lab_task_goal` (`goal_id`), KEY `idx_lab_task_milestone` (`milestone_id`), KEY `idx_lab_task_owner_period_workflow` (`owner_id`,`period`,`workflow_status`), KEY `idx_lab_task_owner_plan_level` (`owner_id`,`plan_date`,`task_level`), KEY `idx_lab_task_period_workflow` (`period`,`workflow_status`,`period_lock_flag`), KEY `idx_lab_task_dept` (`dept_id`), KEY `idx_lab_task_coordination_owner` (`coordination_required`,`coordination_owner_id`), KEY `idx_lab_task_owner_status` (`owner_id`,`workflow_status`,`result_status`), KEY `idx_lab_task_period_line` (`period`,`biz_line`), KEY `idx_lab_task_asset` (`asset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='laboratory task';

CREATE TABLE IF NOT EXISTS `lab_task_execution_event` (
 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key', `task_id` bigint NOT NULL COMMENT 'task reference', `from_status` varchar(24) DEFAULT NULL COMMENT 'prior execution status', `to_status` varchar(24) NOT NULL COMMENT 'new execution status', `result_status` varchar(32) DEFAULT NULL COMMENT 'result fact at transition', `actual_finish_time` datetime DEFAULT NULL COMMENT 'finish fact at transition', `actor_id` bigint DEFAULT NULL COMMENT 'member actor', `event_type` varchar(32) NOT NULL COMMENT 'event type', `reason` varchar(1000) DEFAULT NULL COMMENT 'transition reason', `task_version` int NOT NULL COMMENT 'task version at event', `evidence_version` int NOT NULL DEFAULT 0 COMMENT 'evidence version at event', `idempotency_key` varchar(128) NOT NULL COMMENT 'event idempotency key', `event_time` datetime NOT NULL COMMENT 'event time', `del_flag` char(1) DEFAULT '0' COMMENT 'delete flag', `create_by` varchar(64) DEFAULT '' COMMENT 'creator', `create_time` datetime DEFAULT NULL COMMENT 'created time', `update_by` varchar(64) DEFAULT '' COMMENT 'updater', `update_time` datetime DEFAULT NULL COMMENT 'updated time', `remark` varchar(500) DEFAULT NULL COMMENT 'remark',
 PRIMARY KEY (`id`), UNIQUE KEY `uk_lab_execution_event_key` (`idempotency_key`), KEY `idx_lab_execution_event_task_time` (`task_id`,`event_time`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='append only weekly execution event';

CREATE TABLE IF NOT EXISTS `lab_task_migration_issue` (
 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key', `task_id` bigint NOT NULL COMMENT 'legacy task reference', `issue_code` varchar(64) NOT NULL COMMENT 'migration issue code', `source_state_json` json NOT NULL COMMENT 'immutable source state', `resolution_status` varchar(16) NOT NULL DEFAULT 'OPEN' COMMENT 'resolution status', `resolution_code` varchar(64) DEFAULT NULL COMMENT 'resolution code such as MIGRATION_TERMINAL_UNRESOLVED', `resolved_by` bigint DEFAULT NULL COMMENT 'resolver member', `resolved_time` datetime DEFAULT NULL COMMENT 'resolved time', `version` int NOT NULL DEFAULT 0 COMMENT 'optimistic version', `del_flag` char(1) DEFAULT '0' COMMENT 'delete flag', `create_by` varchar(64) DEFAULT '' COMMENT 'creator', `create_time` datetime DEFAULT NULL COMMENT 'created time', `update_by` varchar(64) DEFAULT '' COMMENT 'updater', `update_time` datetime DEFAULT NULL COMMENT 'updated time', `remark` varchar(500) DEFAULT NULL COMMENT 'remark',
 `active_unique_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `del_flag`='0' AND `resolution_status`='OPEN' THEN 1 ELSE NULL END) STORED COMMENT 'open issue unique marker',
 PRIMARY KEY (`id`), UNIQUE KEY `uk_lab_migration_issue_task` (`task_id`,`active_unique_flag`), KEY `idx_lab_migration_issue_status` (`resolution_status`,`issue_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='quarantined legacy weekly state';

CREATE TABLE IF NOT EXISTS `lab_task_workflow_event` (
 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key', `task_id` bigint NOT NULL COMMENT 'monthly task reference', `from_status` varchar(32) DEFAULT NULL COMMENT 'prior workflow status', `to_status` varchar(32) NOT NULL COMMENT 'new workflow status', `result_status` varchar(32) DEFAULT NULL COMMENT 'result status at event', `actor_id` bigint NOT NULL COMMENT 'actor member', `event_type` varchar(32) NOT NULL COMMENT 'workflow event type', `reason` varchar(1000) DEFAULT NULL COMMENT 'required reason when applicable', `task_version` int NOT NULL COMMENT 'task version at event', `event_time` datetime NOT NULL COMMENT 'event time', `idempotency_key` varchar(128) NOT NULL COMMENT 'event idempotency key', `del_flag` char(1) DEFAULT '0' COMMENT 'delete flag', `create_by` varchar(64) DEFAULT '' COMMENT 'creator', `create_time` datetime DEFAULT NULL COMMENT 'created time', `update_by` varchar(64) DEFAULT '' COMMENT 'updater', `update_time` datetime DEFAULT NULL COMMENT 'updated time', `remark` varchar(500) DEFAULT NULL COMMENT 'remark',
 PRIMARY KEY (`id`), UNIQUE KEY `uk_lab_workflow_event_key` (`idempotency_key`), KEY `idx_lab_workflow_event_task_time` (`task_id`,`event_time`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='append only monthly workflow event';

CREATE TABLE IF NOT EXISTS `lab_formal_acceptance_revision` (
 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key', `period` varchar(16) NOT NULL COMMENT 'monthly period', `biz_line` varchar(32) NOT NULL COMMENT 'business line or ALL', `revision_no` int NOT NULL COMMENT 'formal acceptance revision', `accepted_by` bigint NOT NULL COMMENT 'acceptance actor', `accepted_time` datetime NOT NULL COMMENT 'acceptance time', `calculation_version` varchar(64) NOT NULL COMMENT 'calculation contract version', `del_flag` char(1) DEFAULT '0' COMMENT 'delete flag', `create_by` varchar(64) DEFAULT '' COMMENT 'creator', `create_time` datetime DEFAULT NULL COMMENT 'created time', `update_by` varchar(64) DEFAULT '' COMMENT 'updater', `update_time` datetime DEFAULT NULL COMMENT 'updated time', `remark` varchar(500) DEFAULT NULL COMMENT 'remark',
 PRIMARY KEY (`id`), UNIQUE KEY `uk_lab_formal_revision` (`period`,`biz_line`,`revision_no`), KEY `idx_lab_formal_period_latest` (`period`,`biz_line`,`revision_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='formal monthly acceptance revision';

CREATE TABLE IF NOT EXISTS `lab_formal_acceptance_fact` (
 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key', `formal_revision_id` bigint NOT NULL COMMENT 'formal revision reference', `task_id` bigint NOT NULL COMMENT 'accepted monthly task', `fact_json` json NOT NULL COMMENT 'immutable accepted fact', `evidence_version` int NOT NULL COMMENT 'evidence version', `reviewer_id` bigint NOT NULL COMMENT 'reviewer member', `review_time` datetime NOT NULL COMMENT 'review time', `del_flag` char(1) DEFAULT '0' COMMENT 'delete flag', `create_by` varchar(64) DEFAULT '' COMMENT 'creator', `create_time` datetime DEFAULT NULL COMMENT 'created time', `update_by` varchar(64) DEFAULT '' COMMENT 'updater', `update_time` datetime DEFAULT NULL COMMENT 'updated time', `remark` varchar(500) DEFAULT NULL COMMENT 'remark',
 PRIMARY KEY (`id`), UNIQUE KEY `uk_lab_formal_fact_task` (`formal_revision_id`,`task_id`), KEY `idx_lab_formal_fact_task` (`task_id`,`formal_revision_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='immutable accepted monthly fact';

CREATE TABLE IF NOT EXISTS `lab_period_close_snapshot` (
 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key', `period` varchar(16) NOT NULL COMMENT 'monthly period', `revision_no` int NOT NULL COMMENT 'close revision', `period_version` int NOT NULL COMMENT 'period fencing version', `formal_revision_id` bigint DEFAULT NULL COMMENT 'pinned formal revision', `performance_revision` int NOT NULL COMMENT 'performance revision', `closed_by` bigint NOT NULL COMMENT 'close actor', `closed_time` datetime NOT NULL COMMENT 'close time', `calculation_version` varchar(64) NOT NULL COMMENT 'calculation contract version', `del_flag` char(1) DEFAULT '0' COMMENT 'delete flag', `create_by` varchar(64) DEFAULT '' COMMENT 'creator', `create_time` datetime DEFAULT NULL COMMENT 'created time', `update_by` varchar(64) DEFAULT '' COMMENT 'updater', `update_time` datetime DEFAULT NULL COMMENT 'updated time', `remark` varchar(500) DEFAULT NULL COMMENT 'remark',
 PRIMARY KEY (`id`), UNIQUE KEY `uk_lab_close_snapshot_revision` (`period`,`revision_no`), KEY `idx_lab_close_snapshot_period` (`period`,`revision_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='immutable period close revision';

CREATE TABLE IF NOT EXISTS `lab_period_close_fact` (
 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key', `close_snapshot_id` bigint NOT NULL COMMENT 'close snapshot reference', `fact_type` varchar(32) NOT NULL COMMENT 'fact type', `business_id` bigint NOT NULL COMMENT 'source business id', `fact_json` json NOT NULL COMMENT 'immutable close fact', `del_flag` char(1) DEFAULT '0' COMMENT 'delete flag', `create_by` varchar(64) DEFAULT '' COMMENT 'creator', `create_time` datetime DEFAULT NULL COMMENT 'created time', `update_by` varchar(64) DEFAULT '' COMMENT 'updater', `update_time` datetime DEFAULT NULL COMMENT 'updated time', `remark` varchar(500) DEFAULT NULL COMMENT 'remark',
 PRIMARY KEY (`id`), UNIQUE KEY `uk_lab_close_fact_business` (`close_snapshot_id`,`fact_type`,`business_id`), KEY `idx_lab_close_fact_snapshot` (`close_snapshot_id`,`fact_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='immutable period close fact';

CREATE TABLE IF NOT EXISTS `lab_management_decision` (
 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key', `period` varchar(16) NOT NULL COMMENT 'decision period', `biz_line` varchar(32) NOT NULL COMMENT 'business line', `problem` varchar(1000) NOT NULL COMMENT 'problem statement', `decision_content` varchar(2000) NOT NULL COMMENT 'decision', `owner_id` bigint NOT NULL COMMENT 'decision owner', `due_date` date NOT NULL COMMENT 'due date', `related_goal_id` bigint DEFAULT NULL COMMENT 'related goal', `related_task_id` bigint DEFAULT NULL COMMENT 'related task', `decision_status` varchar(16) NOT NULL DEFAULT 'OPEN' COMMENT 'decision status', `version` int NOT NULL DEFAULT 0 COMMENT 'optimistic version', `del_flag` char(1) DEFAULT '0' COMMENT 'delete flag', `create_by` varchar(64) DEFAULT '' COMMENT 'creator', `create_time` datetime DEFAULT NULL COMMENT 'created time', `update_by` varchar(64) DEFAULT '' COMMENT 'updater', `update_time` datetime DEFAULT NULL COMMENT 'updated time', `remark` varchar(500) DEFAULT NULL COMMENT 'remark',
 PRIMARY KEY (`id`), KEY `idx_lab_decision_owner_status` (`owner_id`,`decision_status`,`due_date`), KEY `idx_lab_decision_period_line` (`period`,`biz_line`,`decision_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='weekly management decision';

SET @ailab_ddl = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `lab_task` ADD COLUMN `execution_status` varchar(24) NULL COMMENT ''independent weekly execution status'' AFTER `result_status`', 'SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_task' AND column_name='execution_status');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `lab_task` ADD COLUMN `carried_from_id` bigint NULL COMMENT ''source commitment for carry over'' AFTER `execution_status`', 'SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_task' AND column_name='carried_from_id');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `lab_task` ADD COLUMN `execution_version` int NOT NULL DEFAULT 0 COMMENT ''weekly execution optimistic version'' AFTER `carried_from_id`', 'SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_task' AND column_name='execution_version');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `lab_task` ADD INDEX `idx_lab_task_execution_due` (`task_level`,`execution_status`,`plan_date`,`owner_id`)', 'SELECT 1') FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='lab_task' AND index_name='idx_lab_task_execution_due');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl = (SELECT IF(COUNT(*)=0, 'ALTER TABLE `lab_task` ADD UNIQUE INDEX `uk_lab_task_carry_period` (`carried_from_id`,`period`)', 'SELECT 1') FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='lab_task' AND index_name='uk_lab_task_carry_period');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;

CREATE TABLE IF NOT EXISTS `lab_task_evidence` (
 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key', `task_id` bigint NOT NULL COMMENT 'task reference', `evidence_type` varchar(32) NOT NULL COMMENT 'evidence type', `evidence_title` varchar(200) NOT NULL COMMENT 'evidence title', `evidence_url` varchar(1000) DEFAULT NULL COMMENT 'evidence URL', `evidence_json` json DEFAULT NULL COMMENT 'evidence metadata', `submitter_id` bigint NOT NULL COMMENT 'submitter member', `submit_time` datetime DEFAULT NULL COMMENT 'submit time', `audit_status` varchar(16) DEFAULT 'PENDING' COMMENT 'audit status', `auditor_id` bigint DEFAULT NULL COMMENT 'auditor member', `audit_time` datetime DEFAULT NULL COMMENT 'audit time', `audit_comment` varchar(1000) DEFAULT NULL COMMENT 'audit comment', `del_flag` char(1) DEFAULT '0' COMMENT 'delete flag', `create_by` varchar(64) DEFAULT '' COMMENT 'creator', `create_time` datetime DEFAULT NULL COMMENT 'created time', `update_by` varchar(64) DEFAULT '' COMMENT 'updater', `update_time` datetime DEFAULT NULL COMMENT 'updated time', `remark` varchar(500) DEFAULT NULL COMMENT 'remark',
 PRIMARY KEY (`id`), KEY `idx_lab_evidence_task` (`task_id`), KEY `idx_lab_evidence_submitter` (`submitter_id`), KEY `idx_lab_evidence_status` (`audit_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='task evidence';

CREATE TABLE IF NOT EXISTS `lab_task_quality_gate` (
 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key', `task_id` bigint NOT NULL COMMENT 'task reference', `gate_no` varchar(32) NOT NULL COMMENT 'gate number', `gate_name` varchar(100) NOT NULL COMMENT 'gate name', `gate_status` varchar(16) DEFAULT 'PENDING' COMMENT 'gate status', `evidence_id` bigint DEFAULT NULL COMMENT 'approved evidence used to pass gate', `checker_id` bigint DEFAULT NULL COMMENT 'checker member', `check_time` datetime DEFAULT NULL COMMENT 'check time', `check_result` varchar(1000) DEFAULT NULL COMMENT 'check result', `del_flag` char(1) DEFAULT '0' COMMENT 'delete flag', `create_by` varchar(64) DEFAULT '' COMMENT 'creator', `create_time` datetime DEFAULT NULL COMMENT 'created time', `update_by` varchar(64) DEFAULT '' COMMENT 'updater', `update_time` datetime DEFAULT NULL COMMENT 'updated time', `remark` varchar(500) DEFAULT NULL COMMENT 'remark',
 `active_unique_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `del_flag` = '0' THEN 1 ELSE NULL END) STORED COMMENT 'active record unique marker',
 PRIMARY KEY (`id`), UNIQUE KEY `uk_lab_gate_task_no` (`task_id`,`gate_no`,`active_unique_flag`), KEY `idx_lab_gate_task_status` (`task_id`,`gate_status`), KEY `idx_lab_gate_evidence` (`evidence_id`), KEY `idx_lab_gate_checker_status` (`checker_id`,`gate_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='task quality gate';

CREATE TABLE IF NOT EXISTS `lab_task_block_event` (
 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key', `task_id` bigint NOT NULL COMMENT 'task reference', `episode_no` int NOT NULL COMMENT 'monotonic task blocking episode number', `block_type` varchar(32) NOT NULL COMMENT 'block type', `block_reason` varchar(1000) NOT NULL COMMENT 'block reason', `block_start_time` datetime NOT NULL COMMENT 'episode start', `block_end_time` datetime DEFAULT NULL COMMENT 'episode end', `block_status` varchar(16) DEFAULT 'OPEN' COMMENT 'episode status', `resolver_id` bigint DEFAULT NULL COMMENT 'resolver member', `resolution` varchar(1000) DEFAULT NULL COMMENT 'resolution', `del_flag` char(1) DEFAULT '0' COMMENT 'delete flag', `create_by` varchar(64) DEFAULT '' COMMENT 'creator', `create_time` datetime DEFAULT NULL COMMENT 'created time', `update_by` varchar(64) DEFAULT '' COMMENT 'updater', `update_time` datetime DEFAULT NULL COMMENT 'updated time', `remark` varchar(500) DEFAULT NULL COMMENT 'remark',
 PRIMARY KEY (`id`), UNIQUE KEY `uk_lab_block_task_episode` (`task_id`,`episode_no`), KEY `idx_lab_block_task_open` (`task_id`,`block_status`,`block_start_time`), KEY `idx_lab_block_status_start` (`block_status`,`block_start_time`,`task_id`,`episode_no`), KEY `idx_lab_block_start` (`block_start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='task blocking episode';

-- Upgrade the immediately preceding task-review schema without relying on
-- ALTER TABLE ... IF NOT EXISTS syntax (not consistently available in MySQL 8 minors).
SET @ailab_ddl = (SELECT IF(COUNT(*) = 0,
 'ALTER TABLE `lab_task_quality_gate` ADD COLUMN `evidence_id` bigint NULL COMMENT ''approved evidence used to pass gate'' AFTER `gate_status`',
 'SELECT 1') FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'lab_task_quality_gate' AND column_name = 'evidence_id');
PREPARE ailab_ddl FROM @ailab_ddl;
EXECUTE ailab_ddl;
DEALLOCATE PREPARE ailab_ddl;

SET @ailab_ddl = (SELECT IF(COUNT(*) = 0,
 'ALTER TABLE `lab_task_quality_gate` ADD INDEX `idx_lab_gate_evidence` (`evidence_id`)',
 'SELECT 1') FROM information_schema.statistics
 WHERE table_schema = DATABASE() AND table_name = 'lab_task_quality_gate' AND index_name = 'idx_lab_gate_evidence');
PREPARE ailab_ddl FROM @ailab_ddl;
EXECUTE ailab_ddl;
DEALLOCATE PREPARE ailab_ddl;

SET @ailab_ddl = (SELECT IF(COUNT(*) = 0,
 'ALTER TABLE `lab_task_block_event` ADD COLUMN `episode_no` int NULL COMMENT ''monotonic task blocking episode number'' AFTER `task_id`',
 'SELECT 1') FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'lab_task_block_event' AND column_name = 'episode_no');
PREPARE ailab_ddl FROM @ailab_ddl;
EXECUTE ailab_ddl;
DEALLOCATE PREPARE ailab_ddl;

UPDATE `lab_task_block_event` target
JOIN (
 SELECT `id`, ROW_NUMBER() OVER(PARTITION BY `task_id` ORDER BY `block_start_time`,`id`) AS computed_episode_no
 FROM `lab_task_block_event`
) ranked ON ranked.id = target.id
SET target.episode_no = ranked.computed_episode_no
WHERE target.episode_no IS NULL;

SET @ailab_ddl = (SELECT IF(COUNT(*) = 1,
 'ALTER TABLE `lab_task_block_event` MODIFY COLUMN `episode_no` int NOT NULL COMMENT ''monotonic task blocking episode number''',
 'SELECT 1') FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'lab_task_block_event' AND column_name = 'episode_no' AND is_nullable = 'YES');
PREPARE ailab_ddl FROM @ailab_ddl;
EXECUTE ailab_ddl;
DEALLOCATE PREPARE ailab_ddl;

SET @ailab_ddl = (SELECT IF(COUNT(*) = 0,
 'ALTER TABLE `lab_task_block_event` ADD UNIQUE INDEX `uk_lab_block_task_episode` (`task_id`,`episode_no`)',
 'SELECT 1') FROM information_schema.statistics
 WHERE table_schema = DATABASE() AND table_name = 'lab_task_block_event' AND index_name = 'uk_lab_block_task_episode');
PREPARE ailab_ddl FROM @ailab_ddl;
EXECUTE ailab_ddl;
DEALLOCATE PREPARE ailab_ddl;

SET @ailab_ddl = (SELECT IF(COUNT(*) = 0,
 'ALTER TABLE `lab_task` ADD INDEX `idx_lab_task_period_workflow` (`period`,`workflow_status`,`period_lock_flag`)',
 'SELECT 1') FROM information_schema.statistics
 WHERE table_schema = DATABASE() AND table_name = 'lab_task' AND index_name = 'idx_lab_task_period_workflow');
PREPARE ailab_ddl FROM @ailab_ddl;
EXECUTE ailab_ddl;
DEALLOCATE PREPARE ailab_ddl;

SET @ailab_ddl = (SELECT IF(COUNT(*) = 0,
 'ALTER TABLE `lab_task` ADD INDEX `idx_lab_task_owner_plan_level` (`owner_id`,`plan_date`,`task_level`)',
 'SELECT 1') FROM information_schema.statistics
 WHERE table_schema = DATABASE() AND table_name = 'lab_task' AND index_name = 'idx_lab_task_owner_plan_level');
PREPARE ailab_ddl FROM @ailab_ddl;
EXECUTE ailab_ddl;
DEALLOCATE PREPARE ailab_ddl;

SET @ailab_ddl = (SELECT IF(COUNT(*) = 0,
 'ALTER TABLE `lab_task_block_event` ADD INDEX `idx_lab_block_status_start` (`block_status`,`block_start_time`,`task_id`,`episode_no`)',
 'SELECT 1') FROM information_schema.statistics
 WHERE table_schema = DATABASE() AND table_name = 'lab_task_block_event' AND index_name = 'idx_lab_block_status_start');
PREPARE ailab_ddl FROM @ailab_ddl;
EXECUTE ailab_ddl;
DEALLOCATE PREPARE ailab_ddl;

-- Expand-stage legacy classification. Invalid combinations are quarantined before
-- any current-row mutation; valid rows receive one immutable baseline event.
INSERT IGNORE INTO `lab_task_migration_issue`
 (`task_id`,`issue_code`,`source_state_json`,`resolution_status`,`version`,`del_flag`,`create_by`,`create_time`)
SELECT t.id,
 CASE WHEN EXISTS(SELECT 1 FROM `lab_task_block_event` b WHERE b.task_id=t.id AND b.block_status='OPEN' AND b.del_flag='0')
       AND t.result_status IN ('EXCEEDED','ONTIME','DELAYED','UNDONE')
      THEN 'TERMINAL_WITH_OPEN_BLOCK' ELSE 'AMBIGUOUS_LEGACY_COMBINATION' END,
 JSON_OBJECT('workflowStatus',t.workflow_status,'resultStatus',t.result_status,'actualFinishTime',t.actual_finish_time,
             'periodLockFlag',t.period_lock_flag,'hasOpenBlock',EXISTS(SELECT 1 FROM `lab_task_block_event` bx WHERE bx.task_id=t.id AND bx.block_status='OPEN' AND bx.del_flag='0')),
 'OPEN',0,'0','migration',NOW()
FROM `lab_task` t
WHERE t.task_level='week' AND t.del_flag='0' AND t.execution_status IS NULL
 AND (
   (EXISTS(SELECT 1 FROM `lab_task_block_event` b WHERE b.task_id=t.id AND b.block_status='OPEN' AND b.del_flag='0')
      AND t.result_status IN ('EXCEEDED','ONTIME','DELAYED','UNDONE'))
   OR NOT (
      (t.workflow_status IN ('CONFIRMED','PENDING_REVIEW') AND t.result_status IN ('EXCEEDED','ONTIME','DELAYED') AND t.actual_finish_time IS NOT NULL)
      OR (t.workflow_status IN ('CONFIRMED','PENDING_REVIEW') AND t.result_status='UNDONE')
      OR (t.workflow_status='DRAFT' AND t.result_status='DOING' AND t.actual_finish_time IS NULL)
      OR (t.workflow_status='ACTIVE' AND t.result_status='DOING' AND t.actual_finish_time IS NULL)
   )
 );

INSERT IGNORE INTO `lab_task_execution_event`
 (`task_id`,`from_status`,`to_status`,`result_status`,`actual_finish_time`,`actor_id`,`event_type`,`reason`,`task_version`,`evidence_version`,`idempotency_key`,`event_time`,`del_flag`,`create_by`,`create_time`)
SELECT t.id,NULL,
 CASE
  WHEN t.workflow_status IN ('CONFIRMED','PENDING_REVIEW') AND t.result_status IN ('EXCEEDED','ONTIME','DELAYED') AND t.actual_finish_time IS NOT NULL THEN 'SELF_DONE'
  WHEN t.workflow_status IN ('CONFIRMED','PENDING_REVIEW') AND t.result_status='UNDONE' THEN 'SELF_UNDONE'
  WHEN t.workflow_status='DRAFT' AND t.result_status='DOING' AND t.actual_finish_time IS NULL THEN 'PLANNED'
  ELSE 'ACTIVE'
 END,
 t.result_status,t.actual_finish_time,NULL,'MIGRATED_BASELINE','Legacy workflow baseline',COALESCE(t.version,0),0,
 CONCAT('MIGRATED_BASELINE:',t.id),COALESCE(t.update_time,t.create_time,NOW()),'0','migration',NOW()
FROM `lab_task` t
WHERE t.task_level='week' AND t.del_flag='0' AND t.execution_status IS NULL
 AND NOT EXISTS(SELECT 1 FROM `lab_task_migration_issue` mi WHERE mi.task_id=t.id AND mi.resolution_status='OPEN' AND mi.del_flag='0')
 AND (
      (t.workflow_status IN ('CONFIRMED','PENDING_REVIEW') AND t.result_status IN ('EXCEEDED','ONTIME','DELAYED') AND t.actual_finish_time IS NOT NULL)
      OR (t.workflow_status IN ('CONFIRMED','PENDING_REVIEW') AND t.result_status='UNDONE')
      OR (t.workflow_status='DRAFT' AND t.result_status='DOING' AND t.actual_finish_time IS NULL)
      OR (t.workflow_status='ACTIVE' AND t.result_status='DOING' AND t.actual_finish_time IS NULL)
 );

UPDATE `lab_task` t
JOIN `lab_task_execution_event` e ON e.task_id=t.id AND e.event_type='MIGRATED_BASELINE' AND e.del_flag='0'
SET t.execution_status=e.to_status,t.execution_version=0,t.update_by='migration',t.update_time=COALESCE(t.update_time,NOW())
WHERE t.task_level='week' AND t.execution_status IS NULL AND t.del_flag='0';

CREATE TABLE IF NOT EXISTS `lab_reminder` (
 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key', `task_id` bigint DEFAULT NULL COMMENT 'task reference', `business_type` varchar(32) NOT NULL DEFAULT 'TASK' COMMENT 'business object type', `business_id` bigint DEFAULT NULL COMMENT 'business object reference', `episode_no` int DEFAULT NULL COMMENT 'block episode when applicable', `recipient_id` bigint NOT NULL COMMENT 'recipient member', `reminder_type` varchar(32) NOT NULL COMMENT 'reminder type', `reminder_level` varchar(16) NOT NULL DEFAULT 'INFO' COMMENT 'reminder severity', `reminder_date` date NOT NULL COMMENT 'business reminder date', `title` varchar(200) NOT NULL COMMENT 'reminder title', `reminder_content` varchar(1000) NOT NULL COMMENT 'content', `read_flag` char(1) DEFAULT '0' COMMENT 'read flag', `read_time` datetime DEFAULT NULL COMMENT 'read time', `send_time` datetime DEFAULT NULL COMMENT 'send time', `idempotency_key` varchar(128) NOT NULL COMMENT 'idempotency key', `version` int NOT NULL DEFAULT 0 COMMENT 'optimistic version', `del_flag` char(1) DEFAULT '0' COMMENT 'delete flag', `create_by` varchar(64) DEFAULT '' COMMENT 'creator', `create_time` datetime DEFAULT NULL COMMENT 'created time', `update_by` varchar(64) DEFAULT '' COMMENT 'updater', `update_time` datetime DEFAULT NULL COMMENT 'updated time', `remark` varchar(500) DEFAULT NULL COMMENT 'remark',
 `active_unique_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `del_flag` = '0' THEN 1 ELSE NULL END) STORED COMMENT 'active record unique marker',
 PRIMARY KEY (`id`), UNIQUE KEY `uk_lab_reminder_idempotency` (`idempotency_key`,`active_unique_flag`), KEY `idx_lab_reminder_recipient_read_date` (`recipient_id`,`read_flag`,`reminder_date`), KEY `idx_lab_reminder_task_episode` (`task_id`,`episode_no`,`reminder_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='laboratory reminder';

-- Upgrade reminder rows created by the earlier minimal notification schema.
SET @ailab_ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE `lab_reminder` ADD COLUMN `business_type` varchar(32) NOT NULL DEFAULT ''TASK'' COMMENT ''business object type'' AFTER `task_id`', 'SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_reminder' AND column_name='business_type');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE `lab_reminder` ADD COLUMN `business_id` bigint NULL COMMENT ''business object reference'' AFTER `business_type`', 'SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_reminder' AND column_name='business_id');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE `lab_reminder` ADD COLUMN `episode_no` int NULL COMMENT ''block episode when applicable'' AFTER `business_id`', 'SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_reminder' AND column_name='episode_no');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE `lab_reminder` ADD COLUMN `reminder_level` varchar(16) NOT NULL DEFAULT ''INFO'' COMMENT ''reminder severity'' AFTER `reminder_type`', 'SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_reminder' AND column_name='reminder_level');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE `lab_reminder` ADD COLUMN `reminder_date` date NULL COMMENT ''business reminder date'' AFTER `reminder_level`', 'SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_reminder' AND column_name='reminder_date');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE `lab_reminder` ADD COLUMN `title` varchar(200) NULL COMMENT ''reminder title'' AFTER `reminder_date`', 'SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_reminder' AND column_name='title');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE `lab_reminder` ADD COLUMN `version` int NOT NULL DEFAULT 0 COMMENT ''optimistic version'' AFTER `idempotency_key`', 'SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_reminder' AND column_name='version');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
UPDATE `lab_reminder` SET `business_id`=COALESCE(`business_id`,`task_id`),`reminder_date`=COALESCE(`reminder_date`,DATE(`send_time`),DATE(`create_time`),CURRENT_DATE),`title`=COALESCE(NULLIF(`title`,''),'AI Lab reminder') WHERE `business_id` IS NULL OR `reminder_date` IS NULL OR `title` IS NULL OR `title`='';
SET @ailab_ddl = (SELECT IF(COUNT(*) = 1, 'ALTER TABLE `lab_reminder` MODIFY COLUMN `reminder_date` date NOT NULL COMMENT ''business reminder date'', MODIFY COLUMN `title` varchar(200) NOT NULL COMMENT ''reminder title''', 'SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_reminder' AND column_name='reminder_date' AND is_nullable='YES');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE `lab_reminder` ADD INDEX `idx_lab_reminder_recipient_read_date` (`recipient_id`,`read_flag`,`reminder_date`)', 'SELECT 1') FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='lab_reminder' AND index_name='idx_lab_reminder_recipient_read_date');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE `lab_reminder` ADD INDEX `idx_lab_reminder_task_episode` (`task_id`,`episode_no`,`reminder_level`)', 'SELECT 1') FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='lab_reminder' AND index_name='idx_lab_reminder_task_episode');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;

CREATE TABLE IF NOT EXISTS `lab_asset` (
 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key', `asset_no` varchar(64) NOT NULL COMMENT 'asset number', `asset_name` varchar(200) NOT NULL COMMENT 'asset name', `asset_version` varchar(64) DEFAULT '' COMMENT 'business version', `asset_type` varchar(32) NOT NULL COMMENT 'asset type', `asset_stage` varchar(32) DEFAULT 'VERIFYING' COMMENT 'asset stage', `primary_owner_id` bigint NOT NULL COMMENT 'primary owner', `backup_owner_id` bigint DEFAULT NULL COMMENT 'backup owner', `test_set_url` varchar(1000) DEFAULT NULL COMMENT 'test set URL', `deploy_package_url` varchar(1000) DEFAULT NULL COMMENT 'deployment package URL', `document_url` varchar(1000) DEFAULT NULL COMMENT 'documentation URL', `resource_url` varchar(1000) DEFAULT NULL COMMENT 'resource URL', `repository_url` varchar(1000) DEFAULT NULL COMMENT 'repository URL', `capacity_desc` varchar(1000) DEFAULT NULL COMMENT 'capacity description', `reuse_count` int DEFAULT 0 COMMENT 'reuse count', `critical_flag` char(1) DEFAULT '0' COMMENT 'critical asset flag', `status` varchar(16) DEFAULT 'ACTIVE' COMMENT 'asset status', `version` int DEFAULT 0 COMMENT 'optimistic version', `del_flag` char(1) DEFAULT '0' COMMENT 'delete flag', `create_by` varchar(64) DEFAULT '' COMMENT 'creator', `create_time` datetime DEFAULT NULL COMMENT 'created time', `update_by` varchar(64) DEFAULT '' COMMENT 'updater', `update_time` datetime DEFAULT NULL COMMENT 'updated time', `remark` varchar(500) DEFAULT NULL COMMENT 'remark',
 `active_unique_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `del_flag` = '0' AND `status` = 'ACTIVE' THEN 1 ELSE NULL END) STORED COMMENT 'active record unique marker',
 PRIMARY KEY (`id`), UNIQUE KEY `uk_lab_asset_no` (`asset_no`,`active_unique_flag`), UNIQUE KEY `uk_lab_asset_business` (`asset_name`,`asset_version`,`asset_type`,`active_unique_flag`), KEY `idx_lab_asset_primary_status` (`primary_owner_id`,`status`), KEY `idx_lab_asset_backup` (`backup_owner_id`), KEY `idx_lab_asset_type_stage` (`asset_type`,`asset_stage`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='laboratory asset';

CREATE TABLE IF NOT EXISTS `lab_member` (
 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key', `user_id` bigint NOT NULL COMMENT 'sys user reference', `member_no` varchar(64) NOT NULL COMMENT 'member number', `position` varchar(100) NOT NULL COMMENT 'business position', `biz_line` varchar(32) NOT NULL COMMENT 'business line', `role_type` varchar(32) NOT NULL COMMENT 'lab role', `leader_id` bigint DEFAULT NULL COMMENT 'line lead', `primary_responsibilities` varchar(2000) DEFAULT NULL COMMENT 'primary responsibilities', `backup_responsibilities` varchar(2000) DEFAULT NULL COMMENT 'backup responsibilities', `join_date` date DEFAULT NULL COMMENT 'join date', `member_status` varchar(16) DEFAULT 'ACTIVE' COMMENT 'member status', `version` int DEFAULT 0 COMMENT 'optimistic version', `del_flag` char(1) DEFAULT '0' COMMENT 'delete flag', `create_by` varchar(64) DEFAULT '' COMMENT 'creator', `create_time` datetime DEFAULT NULL COMMENT 'created time', `update_by` varchar(64) DEFAULT '' COMMENT 'updater', `update_time` datetime DEFAULT NULL COMMENT 'updated time', `remark` varchar(500) DEFAULT NULL COMMENT 'remark',
 `active_unique_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `del_flag` = '0' THEN 1 ELSE NULL END) STORED COMMENT 'active record unique marker',
 PRIMARY KEY (`id`), UNIQUE KEY `uk_lab_member_user` (`user_id`,`active_unique_flag`), UNIQUE KEY `uk_lab_member_no` (`member_no`,`active_unique_flag`), KEY `idx_lab_member_line_status` (`biz_line`,`member_status`), KEY `idx_lab_member_leader` (`leader_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='laboratory member';

CREATE TABLE IF NOT EXISTS `lab_skill` (
 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key', `skill_code` varchar(64) NOT NULL COMMENT 'skill code', `skill_name` varchar(100) NOT NULL COMMENT 'skill name', `skill_category` varchar(64) DEFAULT NULL COMMENT 'skill category', `skill_desc` varchar(1000) DEFAULT NULL COMMENT 'skill description', `status` varchar(16) DEFAULT 'ACTIVE' COMMENT 'status', `version` int DEFAULT 0 COMMENT 'optimistic version', `del_flag` char(1) DEFAULT '0' COMMENT 'delete flag', `create_by` varchar(64) DEFAULT '' COMMENT 'creator', `create_time` datetime DEFAULT NULL COMMENT 'created time', `update_by` varchar(64) DEFAULT '' COMMENT 'updater', `update_time` datetime DEFAULT NULL COMMENT 'updated time', `remark` varchar(500) DEFAULT NULL COMMENT 'remark',
 `active_unique_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `del_flag` = '0' THEN 1 ELSE NULL END) STORED COMMENT 'active record unique marker',
 PRIMARY KEY (`id`), UNIQUE KEY `uk_lab_skill_code` (`skill_code`,`active_unique_flag`), UNIQUE KEY `uk_lab_skill_name` (`skill_name`,`active_unique_flag`), KEY `idx_lab_skill_category` (`skill_category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='skill dictionary';

CREATE TABLE IF NOT EXISTS `lab_member_skill` (
 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key', `member_id` bigint NOT NULL COMMENT 'member reference', `skill_id` bigint NOT NULL COMMENT 'skill reference', `skill_level` tinyint NOT NULL COMMENT 'skill level from 1 to 5', `last_verified_date` date DEFAULT NULL COMMENT 'last verified date', `evidence_url` varchar(1000) DEFAULT NULL COMMENT 'verification evidence URL', `version` int DEFAULT 0 COMMENT 'optimistic version', `del_flag` char(1) DEFAULT '0' COMMENT 'delete flag', `create_by` varchar(64) DEFAULT '' COMMENT 'creator', `create_time` datetime DEFAULT NULL COMMENT 'created time', `update_by` varchar(64) DEFAULT '' COMMENT 'updater', `update_time` datetime DEFAULT NULL COMMENT 'updated time', `remark` varchar(500) DEFAULT NULL COMMENT 'remark',
 `active_unique_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `del_flag` = '0' THEN 1 ELSE NULL END) STORED COMMENT 'active record unique marker',
 PRIMARY KEY (`id`), UNIQUE KEY `uk_lab_member_skill` (`member_id`,`skill_id`,`active_unique_flag`), KEY `idx_lab_member_skill_skill` (`skill_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='member skill matrix';

CREATE TABLE IF NOT EXISTS `lab_one2one` (
 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key', `member_id` bigint NOT NULL COMMENT 'member reference', `leader_id` bigint NOT NULL COMMENT 'manager reference', `meeting_date` date NOT NULL COMMENT 'meeting date', `topic` varchar(500) DEFAULT NULL COMMENT 'topic', `facts_evidence` varchar(2000) DEFAULT NULL COMMENT 'facts and evidence', `difficulties` varchar(2000) DEFAULT NULL COMMENT 'difficulties', `next_action` varchar(2000) DEFAULT NULL COMMENT 'next action', `manager_comment` varchar(2000) DEFAULT NULL COMMENT 'manager comment', `status` varchar(16) DEFAULT 'OPEN' COMMENT 'status', `version` int DEFAULT 0 COMMENT 'optimistic version', `del_flag` char(1) DEFAULT '0' COMMENT 'delete flag', `create_by` varchar(64) DEFAULT '' COMMENT 'creator', `create_time` datetime DEFAULT NULL COMMENT 'created time', `update_by` varchar(64) DEFAULT '' COMMENT 'updater', `update_time` datetime DEFAULT NULL COMMENT 'updated time', `remark` varchar(500) DEFAULT NULL COMMENT 'remark',
 PRIMARY KEY (`id`), KEY `idx_lab_one2one_member_date` (`member_id`,`meeting_date`), KEY `idx_lab_one2one_leader` (`leader_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='one to one record';

CREATE TABLE IF NOT EXISTS `lab_ipr` (
 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key', `ipr_no` varchar(64) NOT NULL COMMENT 'IPR number', `ipr_name` varchar(300) NOT NULL COMMENT 'IPR name', `ipr_type` varchar(32) NOT NULL COMMENT 'IPR type', `ipr_stage` varchar(32) DEFAULT 'DRAFT' COMMENT 'IPR stage', `owner_id` bigint NOT NULL COMMENT 'member owner', `planned_submit_date` date DEFAULT NULL COMMENT 'planned submit date', `actual_submit_date` date DEFAULT NULL COMMENT 'actual submit date', `acceptance_no` varchar(128) DEFAULT NULL COMMENT 'acceptance number', `certificate_no` varchar(128) DEFAULT NULL COMMENT 'certificate number', `authorized_date` date DEFAULT NULL COMMENT 'authorized date', `evidence_url` varchar(1000) DEFAULT NULL COMMENT 'evidence URL', `status` varchar(16) DEFAULT 'ACTIVE' COMMENT 'record status', `stage_change_reason` varchar(1000) DEFAULT NULL COMMENT 'audited stage change reason', `version` int DEFAULT 0 COMMENT 'optimistic version', `del_flag` char(1) DEFAULT '0' COMMENT 'delete flag', `create_by` varchar(64) DEFAULT '' COMMENT 'creator', `create_time` datetime DEFAULT NULL COMMENT 'created time', `update_by` varchar(64) DEFAULT '' COMMENT 'updater', `update_time` datetime DEFAULT NULL COMMENT 'updated time', `remark` varchar(500) DEFAULT NULL COMMENT 'remark',
 `active_unique_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `del_flag` = '0' THEN 1 ELSE NULL END) STORED COMMENT 'active record unique marker',
 PRIMARY KEY (`id`), UNIQUE KEY `uk_lab_ipr_no` (`ipr_no`,`active_unique_flag`), KEY `idx_lab_ipr_owner_stage` (`owner_id`,`ipr_stage`), KEY `idx_lab_ipr_type` (`ipr_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='intellectual property right';

-- Task 5 ledger upgrade. Each group is guarded by its optimistic version column,
-- so databases initialized by an earlier bootstrap can be upgraded and rerun safely.
SET @ailab_ddl = (SELECT IF(COUNT(*) = 0,
 'ALTER TABLE `lab_asset` ADD COLUMN `asset_version` varchar(64) DEFAULT '''' COMMENT ''business version'' AFTER `asset_name`, ADD COLUMN `test_set_url` varchar(1000) NULL COMMENT ''test set URL'' AFTER `backup_owner_id`, ADD COLUMN `deploy_package_url` varchar(1000) NULL COMMENT ''deployment package URL'' AFTER `test_set_url`, ADD COLUMN `document_url` varchar(1000) NULL COMMENT ''documentation URL'' AFTER `deploy_package_url`, ADD COLUMN `reuse_count` int DEFAULT 0 COMMENT ''reuse count'' AFTER `capacity_desc`, ADD COLUMN `critical_flag` char(1) DEFAULT ''0'' COMMENT ''critical asset flag'' AFTER `reuse_count`, ADD COLUMN `version` int DEFAULT 0 COMMENT ''optimistic version'' AFTER `status`',
 'SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_asset' AND column_name='version');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;

SET @ailab_ddl = (SELECT IF(COUNT(*) = 1,
 'ALTER TABLE `lab_asset` MODIFY COLUMN `active_unique_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `del_flag` = ''0'' AND `status` = ''ACTIVE'' THEN 1 ELSE NULL END) STORED COMMENT ''active record unique marker''',
 'SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_asset' AND column_name='active_unique_flag' AND generation_expression NOT LIKE '%status%');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;

SET @ailab_ddl = (SELECT IF(COUNT(*) = 0,
 'ALTER TABLE `lab_member` ADD COLUMN `position` varchar(100) NULL COMMENT ''business position'' AFTER `member_no`, ADD COLUMN `primary_responsibilities` varchar(2000) NULL COMMENT ''primary responsibilities'' AFTER `leader_id`, ADD COLUMN `backup_responsibilities` varchar(2000) NULL COMMENT ''backup responsibilities'' AFTER `primary_responsibilities`, ADD COLUMN `version` int DEFAULT 0 COMMENT ''optimistic version'' AFTER `member_status`',
 'SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_member' AND column_name='version');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl = (SELECT IF(COUNT(*) = 1,
 'ALTER TABLE `lab_member` MODIFY COLUMN `member_name` varchar(100) NULL COMMENT ''deprecated identity cache; do not write''',
 'SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_member' AND column_name='member_name' AND is_nullable='NO');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;

SET @ailab_ddl = (SELECT IF(COUNT(*) = 0,
 'ALTER TABLE `lab_skill` ADD COLUMN `version` int DEFAULT 0 COMMENT ''optimistic version'' AFTER `status`',
 'SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_skill' AND column_name='version');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl = (SELECT IF(COUNT(*) = 0,
 'ALTER TABLE `lab_member_skill` ADD COLUMN `skill_level` tinyint NULL COMMENT ''skill level from 1 to 5'' AFTER `skill_id`, ADD COLUMN `last_verified_date` date NULL COMMENT ''last verified date'' AFTER `skill_level`, ADD COLUMN `evidence_url` varchar(1000) NULL COMMENT ''verification evidence URL'' AFTER `last_verified_date`, ADD COLUMN `version` int DEFAULT 0 COMMENT ''optimistic version'' AFTER `evidence_url`',
 'SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_member_skill' AND column_name='version');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl = (SELECT IF(COUNT(*) = 1,
 'UPDATE `lab_member_skill` SET `skill_level`=CASE `proficiency_level` WHEN ''BASIC'' THEN 1 WHEN ''INTERMEDIATE'' THEN 2 WHEN ''ADVANCED'' THEN 4 WHEN ''EXPERT'' THEN 5 ELSE 3 END WHERE `skill_level` IS NULL',
 'UPDATE `lab_member_skill` SET `skill_level`=1 WHERE `skill_level` IS NULL') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_member_skill' AND column_name='proficiency_level');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl = (SELECT IF(COUNT(*) = 1,
 'ALTER TABLE `lab_member_skill` MODIFY COLUMN `skill_level` tinyint NOT NULL COMMENT ''skill level from 1 to 5''',
 'SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_member_skill' AND column_name='skill_level' AND is_nullable='YES');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;

SET @ailab_ddl = (SELECT IF(COUNT(*) = 0,
 'ALTER TABLE `lab_one2one` ADD COLUMN `facts_evidence` varchar(2000) NULL COMMENT ''facts and evidence'' AFTER `topic`, ADD COLUMN `difficulties` varchar(2000) NULL COMMENT ''difficulties'' AFTER `facts_evidence`, ADD COLUMN `next_action` varchar(2000) NULL COMMENT ''next action'' AFTER `difficulties`, ADD COLUMN `manager_comment` varchar(2000) NULL COMMENT ''manager comment'' AFTER `next_action`, ADD COLUMN `version` int DEFAULT 0 COMMENT ''optimistic version'' AFTER `status`',
 'SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_one2one' AND column_name='version');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl = (SELECT IF(COUNT(*) = 1,'ALTER TABLE `lab_one2one` MODIFY COLUMN `topic` varchar(500) NULL COMMENT ''topic''','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_one2one' AND column_name='topic' AND is_nullable='NO');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl = (SELECT IF(
 (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_one2one' AND column_name='feedback') = 1,
 'UPDATE `lab_one2one` SET `facts_evidence`=COALESCE(`facts_evidence`,`feedback`)',
 'SELECT 1'));
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl = (SELECT IF(
 (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_one2one' AND column_name='action_items') = 1,
 'UPDATE `lab_one2one` SET `next_action`=COALESCE(`next_action`,`action_items`)',
 'SELECT 1'));
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;

SET @ailab_ddl = (SELECT IF(COUNT(*) = 0,
 'ALTER TABLE `lab_ipr` ADD COLUMN `planned_submit_date` date NULL COMMENT ''planned submit date'' AFTER `owner_id`, ADD COLUMN `actual_submit_date` date NULL COMMENT ''actual submit date'' AFTER `planned_submit_date`, ADD COLUMN `acceptance_no` varchar(128) NULL COMMENT ''acceptance number'' AFTER `actual_submit_date`, ADD COLUMN `certificate_no` varchar(128) NULL COMMENT ''certificate number'' AFTER `acceptance_no`, ADD COLUMN `status` varchar(16) DEFAULT ''ACTIVE'' COMMENT ''record status'' AFTER `evidence_url`, ADD COLUMN `stage_change_reason` varchar(1000) NULL COMMENT ''audited stage change reason'' AFTER `status`, ADD COLUMN `version` int DEFAULT 0 COMMENT ''optimistic version'' AFTER `stage_change_reason`',
 'SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_ipr' AND column_name='version');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl = (SELECT IF(
 (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_ipr' AND column_name='application_no') = 1,
 'UPDATE `lab_ipr` SET `acceptance_no`=COALESCE(`acceptance_no`,`application_no`)',
 'SELECT 1'));
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl = (SELECT IF(
 (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_ipr' AND column_name='submit_date') = 1,
 'UPDATE `lab_ipr` SET `actual_submit_date`=COALESCE(`actual_submit_date`,`submit_date`)',
 'SELECT 1'));
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
UPDATE `lab_ipr` SET `ipr_stage`='DRAFT' WHERE `ipr_stage`='DRAFTING';
SET @ailab_ddl = (SELECT IF(COUNT(*) = 1,
 'ALTER TABLE `lab_ipr` ALTER COLUMN `ipr_stage` SET DEFAULT ''DRAFT''',
 'SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_ipr' AND column_name='ipr_stage' AND column_default='DRAFTING');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;

SET @ailab_asset_business_index_missing = (SELECT IF(COUNT(*) = 0,1,0) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='lab_asset' AND index_name='uk_lab_asset_business');
UPDATE `lab_asset` SET `asset_version`=`asset_no`
WHERE (`asset_version` IS NULL OR TRIM(`asset_version`)='') AND @ailab_asset_business_index_missing=1;

SET @ailab_skill_name_index_missing = (SELECT IF(COUNT(*) = 0,1,0) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='lab_skill' AND index_name='uk_lab_skill_name');
SET @ailab_skill_code_index_missing = (SELECT IF(COUNT(*) = 0,1,0) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='lab_skill' AND index_name='uk_lab_skill_code');
SET @ailab_skill_name_repair_required = (SELECT IF(@ailab_skill_name_index_missing=1 OR @ailab_skill_code_index_missing=1 OR COUNT(*)=1,1,0)
 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_skill' AND column_name='active_unique_flag' AND generation_expression LIKE '%status%');
SET @ailab_ddl = (SELECT IF(@ailab_skill_name_repair_required=1 AND COUNT(*) > 0,
 'ALTER TABLE `lab_skill` DROP INDEX `uk_lab_skill_name`',
 'SELECT 1') FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='lab_skill' AND index_name='uk_lab_skill_name');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl = (SELECT IF(@ailab_skill_name_repair_required=1 AND COUNT(*) > 0,
 'ALTER TABLE `lab_skill` DROP INDEX `uk_lab_skill_code`',
 'SELECT 1') FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='lab_skill' AND index_name='uk_lab_skill_code');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
UPDATE `lab_skill` target
JOIN (
 SELECT ranked.id AS duplicate_skill_id
 FROM (
  SELECT id,ROW_NUMBER() OVER(PARTITION BY skill_name ORDER BY id) AS duplicate_rank
  FROM `lab_skill` WHERE del_flag='0'
 ) ranked
 WHERE ranked.duplicate_rank>1
) duplicates ON duplicates.duplicate_skill_id=target.id
SET target.skill_name=CONCAT(LEFT(skill_name,35),' [',LEFT(skill_code,40),':',id,']')
WHERE @ailab_skill_name_repair_required=1;

UPDATE `lab_skill` target
JOIN (
 SELECT ranked.id AS duplicate_skill_id
 FROM (
  SELECT id,ROW_NUMBER() OVER(PARTITION BY skill_name ORDER BY id) AS duplicate_rank
  FROM `lab_skill` WHERE del_flag='0'
 ) ranked
 WHERE ranked.duplicate_rank>1
) collisions ON collisions.duplicate_skill_id=target.id
SET target.skill_name=CONCAT('[ailab-legacy:',id,':',LEFT(skill_code,32),'] ',LEFT(skill_name,30))
WHERE @ailab_skill_name_repair_required=1;

UPDATE `lab_skill` target
JOIN (
 SELECT ranked.id AS duplicate_skill_id
 FROM (
  SELECT id,ROW_NUMBER() OVER(PARTITION BY skill_code ORDER BY id) AS duplicate_rank
  FROM `lab_skill` WHERE del_flag='0'
 ) ranked
 WHERE ranked.duplicate_rank>1
) duplicate_codes ON duplicate_codes.duplicate_skill_id=target.id
SET target.skill_code=CONCAT(LEFT(skill_code,35),'-LEGACY-',id)
WHERE @ailab_skill_name_repair_required=1;

UPDATE `lab_skill` target
JOIN (
 SELECT ranked.id AS duplicate_skill_id
 FROM (
  SELECT id,ROW_NUMBER() OVER(PARTITION BY skill_code ORDER BY id) AS duplicate_rank
  FROM `lab_skill` WHERE del_flag='0'
 ) ranked
 WHERE ranked.duplicate_rank>1
) code_collisions ON code_collisions.duplicate_skill_id=target.id
SET target.skill_code=CONCAT('AILAB-LEGACY-',id,'-',LEFT(skill_code,29))
WHERE @ailab_skill_name_repair_required=1;

SET @ailab_ddl = (SELECT IF(COUNT(*) = 1,
 'ALTER TABLE `lab_skill` MODIFY COLUMN `active_unique_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `del_flag` = ''0'' THEN 1 ELSE NULL END) STORED COMMENT ''active record unique marker''',
 'SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_skill' AND column_name='active_unique_flag' AND generation_expression LIKE '%status%');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;

SET @ailab_ddl = (SELECT IF(COUNT(*) = 0,'ALTER TABLE `lab_asset` ADD UNIQUE INDEX `uk_lab_asset_business` (`asset_name`,`asset_version`,`asset_type`,`active_unique_flag`)','SELECT 1') FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='lab_asset' AND index_name='uk_lab_asset_business');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl = (SELECT IF(COUNT(*) = 0,'ALTER TABLE `lab_skill` ADD UNIQUE INDEX `uk_lab_skill_code` (`skill_code`,`active_unique_flag`)','SELECT 1') FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='lab_skill' AND index_name='uk_lab_skill_code');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl = (SELECT IF(COUNT(*) = 0,'ALTER TABLE `lab_skill` ADD UNIQUE INDEX `uk_lab_skill_name` (`skill_name`,`active_unique_flag`)','SELECT 1') FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='lab_skill' AND index_name='uk_lab_skill_name');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;

CREATE TABLE IF NOT EXISTS `lab_collaboration_record` (
 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key', `task_id` bigint DEFAULT NULL COMMENT 'task reference', `period` varchar(16) DEFAULT NULL COMMENT 'collaboration period', `from_member_id` bigint NOT NULL COMMENT 'source member', `to_member_id` bigint NOT NULL COMMENT 'target member', `category` varchar(32) NOT NULL COMMENT 'collaboration category', `signed_score` decimal(8,2) DEFAULT 0 COMMENT 'signed contribution score', `evidence_url` varchar(1000) DEFAULT NULL COMMENT 'evidence URL', `reviewer_id` bigint DEFAULT NULL COMMENT 'reviewer member', `review_status` varchar(16) DEFAULT 'PENDING' COMMENT 'review status', `review_time` datetime DEFAULT NULL COMMENT 'review time', `review_comment` varchar(1000) DEFAULT NULL COMMENT 'review comment', `idempotency_key` varchar(160) DEFAULT NULL COMMENT 'system fact idempotency key', `version` int NOT NULL DEFAULT 0 COMMENT 'optimistic version', `del_flag` char(1) DEFAULT '0' COMMENT 'delete flag', `create_by` varchar(64) DEFAULT '' COMMENT 'creator', `create_time` datetime DEFAULT NULL COMMENT 'created time', `update_by` varchar(64) DEFAULT '' COMMENT 'updater', `update_time` datetime DEFAULT NULL COMMENT 'updated time', `remark` varchar(500) DEFAULT NULL COMMENT 'remark',
 `idempotency_unique_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `del_flag`='0' AND `idempotency_key` IS NOT NULL THEN 1 ELSE NULL END) STORED COMMENT 'active idempotency uniqueness marker',
 PRIMARY KEY (`id`), UNIQUE KEY `uk_lab_collab_idempotency` (`idempotency_key`,`idempotency_unique_flag`), KEY `idx_lab_collab_task` (`task_id`), KEY `idx_lab_collab_period_id` (`period`,`id`), KEY `idx_lab_collab_member_period_status` (`to_member_id`,`period`,`review_status`), KEY `idx_lab_collab_reviewer` (`reviewer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='collaboration contribution record';

CREATE TABLE IF NOT EXISTS `lab_perf_score` (
 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key', `member_id` bigint NOT NULL COMMENT 'member reference', `period` varchar(16) NOT NULL COMMENT 'assessment period', `revision_no` int NOT NULL DEFAULT 1 COMMENT 'revision number', `current_flag` char(1) DEFAULT '1' COMMENT 'current revision', `delivery_score` decimal(10,2) DEFAULT 0 COMMENT 'delivery component score', `quality_score` decimal(10,2) DEFAULT 0 COMMENT 'quality component score', `collaboration_score` decimal(10,2) DEFAULT 0 COMMENT 'collaboration component score', `score` decimal(10,2) DEFAULT 0 COMMENT 'final score', `detail_json` json DEFAULT NULL COMMENT 'immutable calculation detail JSON', `calculation_version` varchar(64) DEFAULT NULL COMMENT 'calculation formula version', `cutoff_time` datetime DEFAULT NULL COMMENT 'calculation cutoff time', `result_status` varchar(32) DEFAULT 'NORMAL' COMMENT 'performance result status', `red_line_flag` char(1) DEFAULT '0' COMMENT 'red line flag', `red_line_reason` varchar(1000) DEFAULT NULL COMMENT 'red line reason', `revoked_flag` char(1) DEFAULT '0' COMMENT 'revoked flag', `revoke_reason` varchar(1000) DEFAULT NULL COMMENT 'revoke reason', `red_line_correction_json` json DEFAULT NULL COMMENT 'audited corrective record preserving original triggers', `confirmation_status` varchar(16) DEFAULT 'PENDING' COMMENT 'monthly feedback confirmation status', `confirmed_by` bigint DEFAULT NULL COMMENT 'confirmation member', `confirmed_time` datetime DEFAULT NULL COMMENT 'confirmation time', `calibration_status` varchar(16) DEFAULT 'PENDING' COMMENT 'calibration status', `calibrate_score` decimal(10,2) DEFAULT NULL COMMENT 'manual quarter calibration score', `calibrator_id` bigint DEFAULT NULL COMMENT 'calibrator member', `calibration_note` varchar(1000) DEFAULT NULL COMMENT 'calibration note', `calibration_time` datetime DEFAULT NULL COMMENT 'calibration time', `version` int NOT NULL DEFAULT 0 COMMENT 'optimistic version', `del_flag` char(1) DEFAULT '0' COMMENT 'delete flag', `create_by` varchar(64) DEFAULT '' COMMENT 'creator', `create_time` datetime DEFAULT NULL COMMENT 'created time', `update_by` varchar(64) DEFAULT '' COMMENT 'updater', `update_time` datetime DEFAULT NULL COMMENT 'updated time', `remark` varchar(500) DEFAULT NULL COMMENT 'remark',
 `active_unique_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `del_flag` = '0' THEN 1 ELSE NULL END) STORED COMMENT 'active record unique marker',
 `current_unique_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `del_flag`='0' AND `current_flag`='1' THEN 1 ELSE NULL END) STORED COMMENT 'single current revision marker',
 PRIMARY KEY (`id`), UNIQUE KEY `uk_lab_perf_member_period_rev` (`member_id`,`period`,`revision_no`,`active_unique_flag`), UNIQUE KEY `uk_lab_perf_member_period_current` (`member_id`,`period`,`current_unique_flag`), KEY `idx_lab_perf_member_period_current` (`member_id`,`period`,`current_flag`), KEY `idx_lab_perf_period_current` (`period`,`current_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='performance score revision';

CREATE TABLE IF NOT EXISTS `lab_period_close` (
 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key', `period` varchar(16) NOT NULL COMMENT 'closed period', `close_status` varchar(16) DEFAULT 'OPEN' COMMENT 'close status', `close_by` varchar(64) DEFAULT NULL COMMENT 'closer', `close_time` datetime DEFAULT NULL COMMENT 'close time', `close_reason` varchar(1000) DEFAULT NULL COMMENT 'close reason', `reopen_by` varchar(64) DEFAULT NULL COMMENT 'reopener', `reopen_time` datetime DEFAULT NULL COMMENT 'reopen time', `reopen_reason` varchar(1000) DEFAULT NULL COMMENT 'reopen reason', `reopen_history_json` json DEFAULT NULL COMMENT 'append-only reopen audit entries', `period_version` int NOT NULL DEFAULT 0 COMMENT 'period reopen fencing version', `version` int DEFAULT 0 COMMENT 'optimistic version', `del_flag` char(1) DEFAULT '0' COMMENT 'delete flag', `create_by` varchar(64) DEFAULT '' COMMENT 'creator', `create_time` datetime DEFAULT NULL COMMENT 'created time', `update_by` varchar(64) DEFAULT '' COMMENT 'updater', `update_time` datetime DEFAULT NULL COMMENT 'updated time', `remark` varchar(500) DEFAULT NULL COMMENT 'remark',
 `active_unique_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `del_flag` = '0' THEN 1 ELSE NULL END) STORED COMMENT 'active record unique marker',
 PRIMARY KEY (`id`), UNIQUE KEY `uk_lab_period_close_period` (`period`,`active_unique_flag`), KEY `idx_lab_period_close_status` (`close_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='period close audit';

-- Task 6 upgrades: every change is conditional so existing databases converge without dropping history.
SET @ailab_ddl=(SELECT IF(COUNT(*)=0,'ALTER TABLE `lab_collaboration_record` ADD COLUMN `idempotency_key` varchar(160) DEFAULT NULL COMMENT ''system fact idempotency key'' AFTER `review_comment`','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_collaboration_record' AND column_name='idempotency_key');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl=(SELECT IF(COUNT(*)=0,'ALTER TABLE `lab_collaboration_record` ADD COLUMN `version` int NOT NULL DEFAULT 0 COMMENT ''optimistic version'' AFTER `idempotency_key`','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_collaboration_record' AND column_name='version');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl=(SELECT IF(COUNT(*)=0,'ALTER TABLE `lab_collaboration_record` ADD COLUMN `idempotency_unique_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `del_flag`=''0'' AND `idempotency_key` IS NOT NULL THEN 1 ELSE NULL END) STORED COMMENT ''active idempotency uniqueness marker''','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_collaboration_record' AND column_name='idempotency_unique_flag');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl=(SELECT IF(COUNT(*)=0,'ALTER TABLE `lab_collaboration_record` ADD UNIQUE INDEX `uk_lab_collab_idempotency` (`idempotency_key`,`idempotency_unique_flag`)','SELECT 1') FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='lab_collaboration_record' AND index_name='uk_lab_collab_idempotency');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl=(SELECT IF(COUNT(*)=0,'ALTER TABLE `lab_collaboration_record` ADD INDEX `idx_lab_collab_period_id` (`period`,`id`)','SELECT 1') FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='lab_collaboration_record' AND index_name='idx_lab_collab_period_id');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;

SET @ailab_ddl=(SELECT IF(COUNT(*)=0,'ALTER TABLE `lab_perf_score` ADD COLUMN `delivery_score` decimal(10,2) DEFAULT 0 COMMENT ''delivery component score'' AFTER `current_flag`','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_perf_score' AND column_name='delivery_score');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl=(SELECT IF(COUNT(*)=0,'ALTER TABLE `lab_perf_score` ADD COLUMN `quality_score` decimal(10,2) DEFAULT 0 COMMENT ''quality component score'' AFTER `delivery_score`','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_perf_score' AND column_name='quality_score');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl=(SELECT IF(COUNT(*)=0,'ALTER TABLE `lab_perf_score` ADD COLUMN `collaboration_score` decimal(10,2) DEFAULT 0 COMMENT ''collaboration component score'' AFTER `quality_score`','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_perf_score' AND column_name='collaboration_score');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl=(SELECT IF(COUNT(*)=0,'ALTER TABLE `lab_perf_score` ADD COLUMN `calculation_version` varchar(64) DEFAULT NULL COMMENT ''calculation formula version'' AFTER `detail_json`','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_perf_score' AND column_name='calculation_version');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl=(SELECT IF(COUNT(*)=0,'ALTER TABLE `lab_perf_score` ADD COLUMN `cutoff_time` datetime DEFAULT NULL COMMENT ''calculation cutoff time'' AFTER `calculation_version`','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_perf_score' AND column_name='cutoff_time');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl=(SELECT IF(COUNT(*)=0,'ALTER TABLE `lab_perf_score` ADD COLUMN `result_status` varchar(32) DEFAULT ''NORMAL'' COMMENT ''performance result status'' AFTER `cutoff_time`','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_perf_score' AND column_name='result_status');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl=(SELECT IF(COUNT(*)=0,'ALTER TABLE `lab_perf_score` ADD COLUMN `red_line_correction_json` json DEFAULT NULL COMMENT ''audited corrective record preserving original triggers'' AFTER `revoke_reason`','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_perf_score' AND column_name='red_line_correction_json');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl=(SELECT IF(COUNT(*)=0,'ALTER TABLE `lab_perf_score` ADD COLUMN `confirmation_status` varchar(16) DEFAULT ''PENDING'' COMMENT ''monthly feedback confirmation status'' AFTER `red_line_correction_json`','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_perf_score' AND column_name='confirmation_status');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl=(SELECT IF(COUNT(*)=0,'ALTER TABLE `lab_perf_score` ADD COLUMN `confirmed_by` bigint DEFAULT NULL COMMENT ''confirmation member'' AFTER `confirmation_status`','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_perf_score' AND column_name='confirmed_by');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl=(SELECT IF(COUNT(*)=0,'ALTER TABLE `lab_perf_score` ADD COLUMN `confirmed_time` datetime DEFAULT NULL COMMENT ''confirmation time'' AFTER `confirmed_by`','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_perf_score' AND column_name='confirmed_time');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl=(SELECT IF(COUNT(*)=0,'ALTER TABLE `lab_perf_score` ADD COLUMN `calibrate_score` decimal(10,2) DEFAULT NULL COMMENT ''manual quarter calibration score'' AFTER `calibration_status`','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_perf_score' AND column_name='calibrate_score');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl=(SELECT IF(COUNT(*)=0,'ALTER TABLE `lab_perf_score` ADD COLUMN `calibration_time` datetime DEFAULT NULL COMMENT ''calibration time'' AFTER `calibration_note`','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_perf_score' AND column_name='calibration_time');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl=(SELECT IF(COUNT(*)=0,'ALTER TABLE `lab_perf_score` ADD COLUMN `version` int NOT NULL DEFAULT 0 COMMENT ''optimistic version'' AFTER `calibration_time`','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_perf_score' AND column_name='version');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;

DROP TEMPORARY TABLE IF EXISTS `ailab_perf_current_keep`;
CREATE TEMPORARY TABLE `ailab_perf_current_keep` AS
SELECT `id`,ROW_NUMBER() OVER(PARTITION BY `member_id`,`period` ORDER BY `revision_no` DESC,`id` DESC) AS `rn`
FROM `lab_perf_score` WHERE `del_flag`='0' AND `current_flag`='1';
UPDATE `lab_perf_score` p JOIN `ailab_perf_current_keep` k ON k.`id`=p.`id` SET p.`current_flag`='0',p.`update_by`='ailab-migration',p.`update_time`=NOW(),p.`version`=p.`version`+1 WHERE k.`rn`>1;
DROP TEMPORARY TABLE `ailab_perf_current_keep`;
SET @ailab_ddl=(SELECT IF(COUNT(*)=0,'ALTER TABLE `lab_perf_score` ADD COLUMN `current_unique_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `del_flag`=''0'' AND `current_flag`=''1'' THEN 1 ELSE NULL END) STORED COMMENT ''single current revision marker''','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_perf_score' AND column_name='current_unique_flag');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl=(SELECT IF(COUNT(*)=0,'ALTER TABLE `lab_perf_score` ADD UNIQUE INDEX `uk_lab_perf_member_period_current` (`member_id`,`period`,`current_unique_flag`)','SELECT 1') FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='lab_perf_score' AND index_name='uk_lab_perf_member_period_current');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;

SET @ailab_ddl=(SELECT IF(COUNT(*)=0,'ALTER TABLE `lab_period_close` ADD COLUMN `reopen_history_json` json DEFAULT NULL COMMENT ''append-only reopen audit entries'' AFTER `reopen_reason`','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_period_close' AND column_name='reopen_history_json');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl=(SELECT IF(COUNT(*)=0,'ALTER TABLE `lab_period_close` ADD COLUMN `period_version` int NOT NULL DEFAULT 0 COMMENT ''period reopen fencing version'' AFTER `reopen_history_json`','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_period_close' AND column_name='period_version');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;

CREATE TABLE IF NOT EXISTS `lab_report_template` (
 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key', `template_code` varchar(64) NOT NULL COMMENT 'template code', `template_name` varchar(200) NOT NULL COMMENT 'template name', `period_type` varchar(16) NOT NULL COMMENT 'period type', `revision_no` int NOT NULL DEFAULT 1 COMMENT 'revision number', `latest_flag` char(1) DEFAULT '1' COMMENT 'latest flag', `default_flag` char(1) DEFAULT '0' COMMENT 'default flag', `status` varchar(16) DEFAULT 'ENABLED' COMMENT 'template status', `header_json` json DEFAULT NULL COMMENT 'header JSON', `style_json` json DEFAULT NULL COMMENT 'style JSON', `version` int DEFAULT 0 COMMENT 'optimistic version', `del_flag` char(1) DEFAULT '0' COMMENT 'delete flag', `create_by` varchar(64) DEFAULT '' COMMENT 'creator', `create_time` datetime DEFAULT NULL COMMENT 'created time', `update_by` varchar(64) DEFAULT '' COMMENT 'updater', `update_time` datetime DEFAULT NULL COMMENT 'updated time', `remark` varchar(500) DEFAULT NULL COMMENT 'remark',
 `active_unique_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `del_flag` = '0' THEN 1 ELSE NULL END) STORED COMMENT 'active record unique marker',
 `default_unique_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `del_flag`='0' AND `latest_flag`='1' AND `status`='ENABLED' AND `default_flag`='1' THEN 1 ELSE NULL END) STORED COMMENT 'single report type default',
 PRIMARY KEY (`id`), UNIQUE KEY `uk_lab_report_tpl_code_rev` (`template_code`,`revision_no`,`active_unique_flag`), UNIQUE KEY `uk_lab_report_tpl_period_default` (`period_type`,`default_unique_flag`), KEY `idx_lab_report_tpl_default` (`period_type`,`default_flag`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='report template revision';

CREATE TABLE IF NOT EXISTS `lab_report_section` (
 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key', `template_id` bigint NOT NULL COMMENT 'template reference', `section_code` varchar(64) NOT NULL COMMENT 'section code', `section_name` varchar(200) NOT NULL COMMENT 'section name', `section_type` varchar(32) NOT NULL COMMENT 'renderer type', `sort_no` int NOT NULL COMMENT 'display order', `data_source` varchar(128) DEFAULT NULL COMMENT 'built-in data source', `query_config_json` json DEFAULT NULL COMMENT 'query configuration', `render_config_json` json DEFAULT NULL COMMENT 'renderer configuration', `style_config_json` json DEFAULT NULL COMMENT 'style configuration', `manual_flag` char(1) DEFAULT '0' COMMENT 'manual input flag', `visible_flag` char(1) DEFAULT '1' COMMENT 'visible flag', `sensitive_flag` char(1) DEFAULT '0' COMMENT 'sensitive flag', `sensitive_permission` varchar(128) DEFAULT NULL COMMENT 'required sensitive-report permission', `version` int DEFAULT 0 COMMENT 'optimistic version', `del_flag` char(1) DEFAULT '0' COMMENT 'delete flag', `create_by` varchar(64) DEFAULT '' COMMENT 'creator', `create_time` datetime DEFAULT NULL COMMENT 'created time', `update_by` varchar(64) DEFAULT '' COMMENT 'updater', `update_time` datetime DEFAULT NULL COMMENT 'updated time', `remark` varchar(500) DEFAULT NULL COMMENT 'remark',
 `active_unique_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `del_flag` = '0' THEN 1 ELSE NULL END) STORED COMMENT 'active record unique marker',
 PRIMARY KEY (`id`), UNIQUE KEY `uk_lab_report_section` (`template_id`,`section_code`,`active_unique_flag`), KEY `idx_lab_report_section_tpl_sort` (`template_id`,`sort_no`), KEY `idx_lab_report_section_type` (`section_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='report template section';

CREATE TABLE IF NOT EXISTS `lab_report_summary` (
 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key', `period` varchar(16) NOT NULL COMMENT 'report period', `biz_line` varchar(32) NOT NULL COMMENT 'business line', `section_code` varchar(64) NOT NULL COMMENT 'section code', `summary_json` json DEFAULT NULL COMMENT 'summary JSON', `summary_text` text COMMENT 'summary text', `source_revision` int DEFAULT 1 COMMENT 'source revision', `del_flag` char(1) DEFAULT '0' COMMENT 'delete flag', `create_by` varchar(64) DEFAULT '' COMMENT 'creator', `create_time` datetime DEFAULT NULL COMMENT 'created time', `update_by` varchar(64) DEFAULT '' COMMENT 'updater', `update_time` datetime DEFAULT NULL COMMENT 'updated time', `remark` varchar(500) DEFAULT NULL COMMENT 'remark',
 `active_unique_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `del_flag` = '0' THEN 1 ELSE NULL END) STORED COMMENT 'active record unique marker',
 PRIMARY KEY (`id`), UNIQUE KEY `uk_lab_report_summary` (`period`,`biz_line`,`section_code`,`active_unique_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='report precomputed summary';

CREATE TABLE IF NOT EXISTS `lab_report_instance` (
 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key', `report_no` varchar(64) NOT NULL COMMENT 'report number', `template_id` bigint NOT NULL COMMENT 'template reference', `template_code` varchar(64) NOT NULL COMMENT 'pinned template family', `template_revision` int NOT NULL COMMENT 'pinned template revision', `period` varchar(16) NOT NULL COMMENT 'report period', `biz_line` varchar(32) DEFAULT 'ALL' COMMENT 'business line', `revision_no` int NOT NULL DEFAULT 1 COMMENT 'report revision number', `lifecycle_status` varchar(32) DEFAULT 'DRAFT' COMMENT 'lifecycle status', `current_flag` char(1) DEFAULT '0' COMMENT 'current finalized flag', `final_flag` char(1) DEFAULT '0' COMMENT 'final flag', `sensitive_flag` char(1) DEFAULT '0' COMMENT 'sensitive flag', `source_type` varchar(32) NOT NULL DEFAULT 'AUTO' COMMENT 'AUTO or MANUAL_IMPORT', `source_data_json` json DEFAULT NULL COMMENT 'source data JSON', `source_perf_revision` int DEFAULT 0 COMMENT 'source performance revision', `content_json` json DEFAULT NULL COMMENT 'content JSON', `content_markdown` longtext COMMENT 'markdown content', `json_status` varchar(16) DEFAULT 'PENDING' COMMENT 'JSON artifact status', `json_path` varchar(1000) DEFAULT NULL COMMENT 'JSON artifact path', `json_error` varchar(2000) DEFAULT NULL COMMENT 'JSON artifact error', `markdown_status` varchar(16) DEFAULT 'PENDING' COMMENT 'Markdown artifact status', `markdown_path` varchar(1000) DEFAULT NULL COMMENT 'Markdown artifact path', `markdown_error` varchar(2000) DEFAULT NULL COMMENT 'Markdown artifact error', `word_status` varchar(16) DEFAULT 'NOT_REQUESTED' COMMENT 'Word artifact status', `word_path` varchar(1000) DEFAULT NULL COMMENT 'Word artifact path', `word_error` varchar(2000) DEFAULT NULL COMMENT 'Word artifact error', `pdf_status` varchar(16) DEFAULT 'NOT_REQUESTED' COMMENT 'PDF artifact status', `pdf_path` varchar(1000) DEFAULT NULL COMMENT 'PDF artifact path', `pdf_error` varchar(2000) DEFAULT NULL COMMENT 'PDF artifact error', `version` int DEFAULT 0 COMMENT 'optimistic version', `del_flag` char(1) DEFAULT '0' COMMENT 'delete flag', `create_by` varchar(64) DEFAULT '' COMMENT 'creator', `create_time` datetime DEFAULT NULL COMMENT 'created time', `update_by` varchar(64) DEFAULT '' COMMENT 'updater', `update_time` datetime DEFAULT NULL COMMENT 'updated time', `remark` varchar(500) DEFAULT NULL COMMENT 'remark',
 `active_unique_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `del_flag` = '0' THEN 1 ELSE NULL END) STORED COMMENT 'active record unique marker',
 `current_final_unique_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `del_flag`='0' AND `lifecycle_status`='FINALIZED' AND `current_flag`='1' THEN 1 ELSE NULL END) STORED COMMENT 'single current finalized version',
 PRIMARY KEY (`id`), UNIQUE KEY `uk_lab_report_instance_no` (`report_no`,`active_unique_flag`), UNIQUE KEY `uk_lab_report_instance_period_rev` (`template_code`,`period`,`biz_line`,`revision_no`,`active_unique_flag`), UNIQUE KEY `uk_lab_report_current_final` (`template_code`,`period`,`biz_line`,`current_final_unique_flag`), KEY `idx_lab_report_instance_template_pin` (`template_code`,`template_revision`), KEY `idx_lab_report_instance_tpl_period_lifecycle` (`template_id`,`period`,`lifecycle_status`), KEY `idx_lab_report_instance_period` (`period`,`biz_line`,`lifecycle_status`), KEY `idx_lab_report_instance_final` (`final_flag`,`current_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='generated report instance';

CREATE TABLE IF NOT EXISTS `lab_report_job` (
 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key', `job_no` varchar(64) NOT NULL COMMENT 'job number', `report_id` bigint DEFAULT NULL COMMENT 'report reference', `job_type` varchar(32) NOT NULL COMMENT 'job type', `job_status` varchar(32) DEFAULT 'QUEUED' COMMENT 'job status', `progress_rate` decimal(5,2) DEFAULT 0 COMMENT 'progress percent', `attempt_count` int NOT NULL DEFAULT 0 COMMENT 'attempt count', `error_message` varchar(2000) DEFAULT NULL COMMENT 'error message', `started_time` datetime DEFAULT NULL COMMENT 'started time', `finished_time` datetime DEFAULT NULL COMMENT 'finished time', `idempotency_key` varchar(128) NOT NULL COMMENT 'idempotency key', `run_token` varchar(128) DEFAULT NULL COMMENT 'durable execution fence', `version` int DEFAULT 0 COMMENT 'optimistic version', `del_flag` char(1) DEFAULT '0' COMMENT 'delete flag', `create_by` varchar(64) DEFAULT '' COMMENT 'creator', `create_time` datetime DEFAULT NULL COMMENT 'created time', `update_by` varchar(64) DEFAULT '' COMMENT 'updater', `update_time` datetime DEFAULT NULL COMMENT 'updated time', `remark` varchar(500) DEFAULT NULL COMMENT 'remark',
 `active_unique_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `del_flag` = '0' THEN 1 ELSE NULL END) STORED COMMENT 'active record unique marker',
 `active_step_unique_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `del_flag`='0' AND `job_status` IN ('QUEUED','RUNNING') THEN 1 ELSE NULL END) STORED COMMENT 'single active report step',
 PRIMARY KEY (`id`), UNIQUE KEY `uk_lab_report_job_no` (`job_no`,`active_unique_flag`), UNIQUE KEY `uk_lab_report_job_idempotency` (`idempotency_key`,`active_unique_flag`), UNIQUE KEY `uk_lab_report_job_active_step` (`report_id`,`job_type`,`active_step_unique_flag`), KEY `idx_lab_report_job_instance_status` (`report_id`,`job_status`), KEY `idx_lab_report_job_type_status` (`job_type`,`job_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='report generation job';

UPDATE `lab_report_job` SET `attempt_count`=0 WHERE `attempt_count` IS NULL;
SET @ailab_ddl=(SELECT IF(COUNT(*)>0,'ALTER TABLE `lab_report_job` MODIFY COLUMN `attempt_count` int NOT NULL DEFAULT 0 COMMENT ''attempt count''','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_report_job' AND (is_nullable='YES' OR column_default IS NULL OR column_default<>'0'));
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;

-- Report template pins are upgraded here so new, legacy and repeat bootstrap runs share one ordered script.
SET @ailab_ddl=(SELECT IF(COUNT(*)=0,'ALTER TABLE `lab_report_section` ADD COLUMN `sensitive_permission` varchar(128) NULL COMMENT ''required sensitive-report permission'' AFTER `sensitive_flag`','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_report_section' AND column_name='sensitive_permission');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
UPDATE `lab_report_section` SET `sensitive_flag`='1', `sensitive_permission`=COALESCE(NULLIF(TRIM(`sensitive_permission`),''),'lab:report:sensitive') WHERE `data_source`='PERF_SUMMARY' OR `sensitive_flag`='1' OR (`sensitive_permission` IS NOT NULL AND TRIM(`sensitive_permission`)<>'');
UPDATE `lab_report_template` old JOIN `lab_report_template` latest ON latest.`template_code`=old.`template_code` AND latest.`latest_flag`='1' AND latest.`status`='ENABLED' AND latest.`del_flag`='0' SET latest.`default_flag`='1',latest.`update_by`='ailab-migration',latest.`update_time`=NOW(),latest.`version`=latest.`version`+1,old.`default_flag`='0',old.`update_by`='ailab-migration',old.`update_time`=NOW(),old.`version`=old.`version`+1 WHERE old.`default_flag`='1' AND COALESCE(old.`latest_flag`,'0')<>'1' AND COALESCE(old.`del_flag`,'0')='0';
UPDATE `lab_report_template` SET `default_flag`='0',`update_by`='ailab-migration',`update_time`=NOW(),`version`=`version`+1 WHERE `default_flag`='1' AND (COALESCE(`latest_flag`,'0')<>'1' OR COALESCE(`status`,'')<>'ENABLED' OR COALESCE(`del_flag`,'0')<>'0');
DROP TEMPORARY TABLE IF EXISTS `ailab_report_default_keep`;
CREATE TEMPORARY TABLE `ailab_report_default_keep` AS SELECT `id`,ROW_NUMBER() OVER(PARTITION BY `period_type` ORDER BY `revision_no` DESC,`id` DESC) `rn` FROM `lab_report_template` WHERE `del_flag`='0' AND `latest_flag`='1' AND `status`='ENABLED' AND `default_flag`='1';
UPDATE `lab_report_template` t JOIN `ailab_report_default_keep` k ON k.`id`=t.`id` SET t.`default_flag`='0',t.`update_by`='ailab-migration',t.`update_time`=NOW(),t.`version`=t.`version`+1 WHERE k.`rn`>1;
DROP TEMPORARY TABLE `ailab_report_default_keep`;
DROP TEMPORARY TABLE IF EXISTS `ailab_report_missing_default`;
CREATE TEMPORARY TABLE `ailab_report_missing_default` AS SELECT `id` FROM (SELECT `id`,`period_type`,ROW_NUMBER() OVER(PARTITION BY `period_type` ORDER BY `revision_no` DESC,`id` DESC) `rn`,MAX(CASE WHEN `default_flag`='1' THEN 1 ELSE 0 END) OVER(PARTITION BY `period_type`) `has_default` FROM `lab_report_template` WHERE `del_flag`='0' AND `latest_flag`='1' AND `status`='ENABLED') ranked WHERE `rn`=1 AND `has_default`=0;
UPDATE `lab_report_template` t JOIN `ailab_report_missing_default` d ON d.`id`=t.`id` SET t.`default_flag`='1',t.`update_by`='ailab-migration',t.`update_time`=NOW(),t.`version`=t.`version`+1;
DROP TEMPORARY TABLE `ailab_report_missing_default`;
SET @ailab_ddl=(SELECT IF(COUNT(*)=0,'ALTER TABLE `lab_report_template` ADD COLUMN `default_unique_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `del_flag`=''0'' AND `latest_flag`=''1'' AND `status`=''ENABLED'' AND `default_flag`=''1'' THEN 1 ELSE NULL END) STORED COMMENT ''single report type default''','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_report_template' AND column_name='default_unique_flag');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl=(SELECT IF(COUNT(*)=0,'ALTER TABLE `lab_report_template` ADD UNIQUE INDEX `uk_lab_report_tpl_period_default` (`period_type`,`default_unique_flag`)','SELECT 1') FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='lab_report_template' AND index_name='uk_lab_report_tpl_period_default');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl=(SELECT IF(COUNT(*)=0,'ALTER TABLE `lab_report_instance` ADD COLUMN `template_code` varchar(64) NULL COMMENT ''pinned template family'' AFTER `template_id`','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_report_instance' AND column_name='template_code');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl=(SELECT IF(COUNT(*)=0,'ALTER TABLE `lab_report_instance` ADD COLUMN `template_revision` int NULL COMMENT ''pinned template revision'' AFTER `template_code`','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_report_instance' AND column_name='template_revision');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
UPDATE `lab_report_instance` r LEFT JOIN `lab_report_template` t ON t.`id`=r.`template_id`
SET r.`template_code`=COALESCE(r.`template_code`,t.`template_code`,CONCAT('legacy-template-',r.`template_id`)), r.`template_revision`=COALESCE(r.`template_revision`,t.`revision_no`,1)
WHERE r.`template_code` IS NULL OR r.`template_revision` IS NULL;
UPDATE `lab_report_instance` r JOIN `lab_report_template` t ON t.`template_code`=r.`template_code` AND t.`revision_no`=r.`template_revision` JOIN `lab_report_section` s ON s.`template_id`=t.`id` SET r.`sensitive_flag`='1',r.`update_by`='ailab-migration',r.`update_time`=NOW(),r.`version`=r.`version`+1 WHERE r.`del_flag`='0' AND COALESCE(r.`sensitive_flag`,'0')<>'1' AND (s.`data_source`='PERF_SUMMARY' OR s.`sensitive_flag`='1' OR (s.`sensitive_permission` IS NOT NULL AND TRIM(s.`sensitive_permission`)<>''));
SET @ailab_ddl=(SELECT IF(COUNT(*)>0,'ALTER TABLE `lab_report_instance` MODIFY COLUMN `template_code` varchar(64) NOT NULL COMMENT ''pinned template family''','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_report_instance' AND column_name='template_code' AND is_nullable='YES');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl=(SELECT IF(COUNT(*)>0,'ALTER TABLE `lab_report_instance` MODIFY COLUMN `template_revision` int NOT NULL COMMENT ''pinned template revision''','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_report_instance' AND column_name='template_revision' AND is_nullable='YES');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl=(SELECT IF(COUNT(*)=0,'ALTER TABLE `lab_report_instance` ADD INDEX `idx_lab_report_instance_template_pin` (`template_code`,`template_revision`)','SELECT 1') FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='lab_report_instance' AND index_name='idx_lab_report_instance_template_pin');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl=(SELECT IF(COUNT(*)=0,'ALTER TABLE `lab_report_instance` ADD COLUMN `active_unique_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `del_flag`=''0'' THEN 1 ELSE NULL END) STORED COMMENT ''active record unique marker''','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_report_instance' AND column_name='active_unique_flag');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl=(SELECT IF(COUNT(*)=0,'ALTER TABLE `lab_report_instance` ADD UNIQUE INDEX `uk_lab_report_instance_no` (`report_no`,`active_unique_flag`)','SELECT 1') FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='lab_report_instance' AND index_name='uk_lab_report_instance_no');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl=(SELECT IF(COUNT(*)=0,'ALTER TABLE `lab_report_instance` ADD COLUMN `source_type` varchar(32) NOT NULL DEFAULT ''AUTO'' COMMENT ''AUTO or MANUAL_IMPORT'' AFTER `sensitive_flag`','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_report_instance' AND column_name='source_type');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
DROP TEMPORARY TABLE IF EXISTS `ailab_report_family_revision`;
CREATE TEMPORARY TABLE `ailab_report_family_revision` AS SELECT `id`,`revision_no` `original_revision`,ROW_NUMBER() OVER(PARTITION BY `template_code`,`period`,`biz_line` ORDER BY `revision_no`,`id`) `family_revision` FROM `lab_report_instance` WHERE `del_flag`='0';
START TRANSACTION;
UPDATE `lab_report_instance` r JOIN `ailab_report_family_revision` f ON f.`id`=r.`id` SET r.`revision_no`=-f.`family_revision`;
UPDATE `lab_report_instance` r JOIN `ailab_report_family_revision` f ON f.`id`=r.`id` SET r.`revision_no`=f.`family_revision`,r.`update_by`=IF(f.`original_revision`<>f.`family_revision`,'ailab-migration',r.`update_by`),r.`update_time`=IF(f.`original_revision`<>f.`family_revision`,NOW(),r.`update_time`),r.`version`=r.`version`+IF(f.`original_revision`<>f.`family_revision`,1,0);
COMMIT;
DROP TEMPORARY TABLE `ailab_report_family_revision`;
SET @ailab_ddl=(SELECT IF(COALESCE(GROUP_CONCAT(column_name ORDER BY seq_in_index),'')='template_id,period,biz_line,revision_no,active_unique_flag','ALTER TABLE `lab_report_instance` DROP INDEX `uk_lab_report_instance_period_rev`','SELECT 1') FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='lab_report_instance' AND index_name='uk_lab_report_instance_period_rev');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl=(SELECT IF(COUNT(*)=0,'ALTER TABLE `lab_report_instance` ADD UNIQUE INDEX `uk_lab_report_instance_period_rev` (`template_code`,`period`,`biz_line`,`revision_no`,`active_unique_flag`)','SELECT 1') FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='lab_report_instance' AND index_name='uk_lab_report_instance_period_rev');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
UPDATE `lab_report_instance` SET `lifecycle_status`='FINALIZED' WHERE `lifecycle_status`='FINAL';
UPDATE `lab_report_instance` SET `json_status`=IF(`json_status`='READY','SUCCESS',`json_status`),`markdown_status`=IF(`markdown_status`='READY','SUCCESS',`markdown_status`),`word_status`=IF(`word_status`='READY','SUCCESS',`word_status`),`pdf_status`=IF(`pdf_status`='READY','SUCCESS',`pdf_status`);
SET @ailab_ddl=(SELECT IF(COALESCE(column_default,'')<>'NOT_REQUESTED','ALTER TABLE `lab_report_instance` MODIFY COLUMN `word_status` varchar(16) DEFAULT ''NOT_REQUESTED'' COMMENT ''Word artifact status''','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_report_instance' AND column_name='word_status');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl=(SELECT IF(COALESCE(column_default,'')<>'NOT_REQUESTED','ALTER TABLE `lab_report_instance` MODIFY COLUMN `pdf_status` varchar(16) DEFAULT ''NOT_REQUESTED'' COMMENT ''PDF artifact status''','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_report_instance' AND column_name='pdf_status');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
UPDATE `lab_report_instance` SET `word_status`='NOT_REQUESTED',`word_error`=NULL WHERE `lifecycle_status`='DRAFT' AND `word_status`='PENDING' AND `word_path` IS NULL AND (`json_status`<>'SUCCESS' OR `markdown_status`<>'SUCCESS');
UPDATE `lab_report_instance` SET `pdf_status`='NOT_REQUESTED',`pdf_error`=NULL WHERE `lifecycle_status`='DRAFT' AND `pdf_status`='PENDING' AND `pdf_path` IS NULL AND `word_status`<>'SUCCESS';
DROP TEMPORARY TABLE IF EXISTS `ailab_report_current_keep`;
CREATE TEMPORARY TABLE `ailab_report_current_keep` AS SELECT `id`,ROW_NUMBER() OVER(PARTITION BY `template_code`,`period`,`biz_line` ORDER BY `revision_no` DESC,`id` DESC) `rn` FROM `lab_report_instance` WHERE `del_flag`='0' AND `lifecycle_status`='FINALIZED' AND `current_flag`='1';
UPDATE `lab_report_instance` r JOIN `ailab_report_current_keep` k ON k.`id`=r.`id` SET r.`lifecycle_status`='SUPERSEDED',r.`current_flag`='0',r.`update_by`='ailab-migration',r.`update_time`=NOW(),r.`version`=r.`version`+1 WHERE k.`rn`>1;
DROP TEMPORARY TABLE `ailab_report_current_keep`;
SET @ailab_ddl=(SELECT IF(COUNT(*)=0,'ALTER TABLE `lab_report_instance` ADD COLUMN `current_final_unique_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `del_flag`=''0'' AND `lifecycle_status`=''FINALIZED'' AND `current_flag`=''1'' THEN 1 ELSE NULL END) STORED COMMENT ''single current finalized version''','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_report_instance' AND column_name='current_final_unique_flag');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl=(SELECT IF(COUNT(*)=0,'ALTER TABLE `lab_report_instance` ADD UNIQUE INDEX `uk_lab_report_current_final` (`template_code`,`period`,`biz_line`,`current_final_unique_flag`)','SELECT 1') FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='lab_report_instance' AND index_name='uk_lab_report_current_final');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
UPDATE `lab_report_job` SET `job_status`='QUEUED' WHERE `job_status`='PENDING';
SET @ailab_ddl=(SELECT IF(COUNT(*)=0,'ALTER TABLE `lab_report_job` ADD COLUMN `run_token` varchar(128) NULL COMMENT ''durable execution fence'' AFTER `idempotency_key`','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_report_job' AND column_name='run_token');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
UPDATE `lab_report_job` SET `run_token`=NULL WHERE `job_status`='QUEUED';
UPDATE `lab_report_job` SET `run_token`=CONCAT('legacy-run-',`id`,'-',`version`) WHERE `job_status`='RUNNING' AND (`run_token` IS NULL OR `run_token`='');
UPDATE `lab_report_job` j LEFT JOIN `lab_report_instance` r ON r.`id`=j.`report_id` AND r.`del_flag`='0' SET j.`job_status`='FAILED',j.`run_token`=NULL,j.`finished_time`=NOW(),j.`error_message`='REPORT_JOB_ORPHANED: The mutable report no longer exists.',j.`update_by`='ailab-migration',j.`update_time`=NOW(),j.`version`=j.`version`+1 WHERE j.`del_flag`='0' AND j.`job_status` IN ('QUEUED','RUNNING') AND (r.`id` IS NULL OR COALESCE(r.`lifecycle_status`,'')<>'DRAFT');
DROP TEMPORARY TABLE IF EXISTS `ailab_report_job_active_keep`;
CREATE TEMPORARY TABLE `ailab_report_job_active_keep` AS SELECT `id`,ROW_NUMBER() OVER(PARTITION BY `report_id`,`job_type` ORDER BY `id` DESC) `rn` FROM `lab_report_job` WHERE `del_flag`='0' AND `job_status` IN ('QUEUED','RUNNING');
UPDATE `lab_report_job` j JOIN `ailab_report_job_active_keep` k ON k.`id`=j.`id` SET j.`job_status`='FAILED',j.`error_message`='Superseded duplicate active step during migration',j.`finished_time`=NOW(),j.`update_by`='ailab-migration',j.`update_time`=NOW(),j.`version`=j.`version`+1 WHERE k.`rn`>1;
DROP TEMPORARY TABLE `ailab_report_job_active_keep`;
SET @ailab_ddl=(SELECT IF(COUNT(*)=0,'ALTER TABLE `lab_report_job` ADD COLUMN `active_step_unique_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `del_flag`=''0'' AND `job_status` IN (''QUEUED'',''RUNNING'') THEN 1 ELSE NULL END) STORED COMMENT ''single active report step''','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_report_job' AND column_name='active_step_unique_flag');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;
SET @ailab_ddl=(SELECT IF(COUNT(*)=0,'ALTER TABLE `lab_report_job` ADD UNIQUE INDEX `uk_lab_report_job_active_step` (`report_id`,`job_type`,`active_step_unique_flag`)','SELECT 1') FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='lab_report_job' AND index_name='uk_lab_report_job_active_step');
PREPARE ailab_ddl FROM @ailab_ddl; EXECUTE ailab_ddl; DEALLOCATE PREPARE ailab_ddl;


-- Dictionary initialization deliberately replaces only AI-lab dictionary types.
DELETE FROM `sys_dict_data` WHERE `dict_type` LIKE 'lab_%';
INSERT INTO `sys_config`
 (`config_name`,`config_key`,`config_value`,`config_type`,`create_by`,`create_time`,`remark`)
SELECT '周承诺读取新事实','lab.commitment.readNewModel','false','Y','admin',NOW(),'读切换完成后由受控流程修改'
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key`='lab.commitment.readNewModel');
INSERT INTO `sys_config`
 (`config_name`,`config_key`,`config_value`,`config_type`,`create_by`,`create_time`,`remark`)
SELECT '周承诺成员自主闭环','lab.commitment.writeSelfClose','false','Y','admin',NOW(),'写切换完成后由受控流程修改'
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key`='lab.commitment.writeSelfClose');
INSERT INTO `sys_config`
 (`config_name`,`config_key`,`config_value`,`config_type`,`create_by`,`create_time`,`remark`)
SELECT '周承诺不可回退点','lab.commitment.pointOfNoReturn','false','Y','admin',NOW(),'首个非迁移成员执行事件后永久置为 true'
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key`='lab.commitment.pointOfNoReturn');

DELETE FROM `sys_dict_type` WHERE `dict_type` LIKE 'lab_%';
INSERT INTO `sys_dict_type` (`dict_id`,`dict_name`,`dict_type`,`status`,`create_by`,`create_time`,`remark`) VALUES
(30001,'Business line','lab_biz_line','0','admin',NOW(),'AI lab'),(30002,'Task workflow','lab_task_workflow_status','0','admin',NOW(),'AI lab'),(30003,'Task result','lab_task_result_status','0','admin',NOW(),'AI lab'),(30004,'Task type','lab_task_type','0','admin',NOW(),'AI lab'),(30005,'Task level','lab_task_level','0','admin',NOW(),'AI lab'),(30006,'Asset type','lab_asset_type','0','admin',NOW(),'AI lab'),(30007,'Asset stage','lab_asset_stage','0','admin',NOW(),'AI lab'),(30008,'IPR type','lab_ipr_type','0','admin',NOW(),'AI lab'),(30009,'IPR stage','lab_ipr_stage','0','admin',NOW(),'AI lab'),(30010,'Report section','lab_section_type','0','admin',NOW(),'AI lab'),(30011,'Goal status','lab_goal_status','0','admin',NOW(),'AI lab'),(30012,'Report status','lab_report_status','0','admin',NOW(),'AI lab'),(30013,'Report job status','lab_report_job_status','0','admin',NOW(),'AI lab'),(30014,'Artifact status','lab_artifact_status','0','admin',NOW(),'AI lab'),(30015,'Review status','lab_review_status','0','admin',NOW(),'AI lab'),(30016,'Collaboration category','lab_collaboration_category','0','admin',NOW(),'AI lab'),(30017,'Weekly execution status','lab_task_execution_status','0','admin',NOW(),'AI lab');
INSERT INTO `sys_dict_data` (`dict_code`,`dict_sort`,`dict_label`,`dict_value`,`dict_type`,`is_default`,`status`,`create_by`,`create_time`) VALUES
(30101,1,'Hardware','hardware','lab_biz_line','N','0','admin',NOW()),(30102,2,'Platform','platform','lab_biz_line','N','0','admin',NOW()),(30103,3,'Algorithm','algorithm','lab_biz_line','N','0','admin',NOW()),(30104,4,'Management','manage','lab_biz_line','N','0','admin',NOW()),
(30110,1,'Draft','DRAFT','lab_task_workflow_status','N','0','admin',NOW()),(30111,2,'Active','ACTIVE','lab_task_workflow_status','N','0','admin',NOW()),(30112,3,'Pending review','PENDING_REVIEW','lab_task_workflow_status','N','0','admin',NOW()),(30113,4,'Confirmed','CONFIRMED','lab_task_workflow_status','N','0','admin',NOW()),
(30120,1,'Doing','DOING','lab_task_result_status','N','0','admin',NOW()),(30121,2,'Exceeded','EXCEEDED','lab_task_result_status','N','0','admin',NOW()),(30122,3,'On time','ONTIME','lab_task_result_status','N','0','admin',NOW()),(30123,4,'Delayed','DELAYED','lab_task_result_status','N','0','admin',NOW()),(30124,5,'Undone','UNDONE','lab_task_result_status','N','0','admin',NOW()),
(30260,1,'待激活','PLANNED','lab_task_execution_status','N','0','admin',NOW()),(30261,2,'进行中','ACTIVE','lab_task_execution_status','N','0','admin',NOW()),(30262,3,'成员已完成','SELF_DONE','lab_task_execution_status','N','0','admin',NOW()),(30263,4,'本周未完成','SELF_UNDONE','lab_task_execution_status','N','0','admin',NOW()),(30264,5,'已取消','CANCELLED','lab_task_execution_status','N','0','admin',NOW()),
(30130,1,'Key','key','lab_task_type','N','0','admin',NOW()),(30131,2,'Daily','daily','lab_task_type','N','0','admin',NOW()),(30140,1,'Monthly','month','lab_task_level','N','0','admin',NOW()),(30141,2,'Weekly','week','lab_task_level','N','0','admin',NOW()),
(30150,1,'Hardware','hardware','lab_asset_type','N','0','admin',NOW()),(30151,2,'Algorithm','algorithm','lab_asset_type','N','0','admin',NOW()),(30152,3,'Platform','platform','lab_asset_type','N','0','admin',NOW()),
(30160,1,'Verifying','VERIFYING','lab_asset_stage','N','0','admin',NOW()),(30161,2,'Deployed','DEPLOYED','lab_asset_stage','N','0','admin',NOW()),(30162,3,'Accepted','ACCEPTED','lab_asset_stage','N','0','admin',NOW()),
(30170,1,'Software copyright','SOFTWARE_COPYRIGHT','lab_ipr_type','N','0','admin',NOW()),(30171,2,'Patent','PATENT','lab_ipr_type','N','0','admin',NOW()),(30172,3,'Certification','CERTIFICATION','lab_ipr_type','N','0','admin',NOW()),
(30180,1,'Draft','DRAFT','lab_ipr_stage','N','0','admin',NOW()),(30181,2,'Preparing','PREPARING','lab_ipr_stage','N','0','admin',NOW()),(30182,3,'Submitted','SUBMITTED','lab_ipr_stage','N','0','admin',NOW()),(30183,4,'Accepted','ACCEPTED','lab_ipr_stage','N','0','admin',NOW()),(30184,5,'Authorized','AUTHORIZED','lab_ipr_stage','N','0','admin',NOW()),
(30190,1,'Table','TABLE','lab_section_type','N','0','admin',NOW()),(30191,2,'Statistic','STAT','lab_section_type','N','0','admin',NOW()),(30192,3,'Text','TEXT','lab_section_type','N','0','admin',NOW()),(30193,4,'Manual','MANUAL','lab_section_type','N','0','admin',NOW()),(30194,5,'Grouped text','GROUP_TEXT','lab_section_type','N','0','admin',NOW()),(30195,6,'Chart','CHART','lab_section_type','N','0','admin',NOW()),
(30200,1,'Active','ACTIVE','lab_goal_status','N','0','admin',NOW()),(30201,2,'Completed','COMPLETED','lab_goal_status','N','0','admin',NOW()),(30202,3,'Terminated','TERMINATED','lab_goal_status','N','0','admin',NOW()),
(30210,1,'Draft','DRAFT','lab_report_status','N','0','admin',NOW()),(30211,2,'Finalized','FINALIZED','lab_report_status','N','0','admin',NOW()),(30212,3,'Superseded','SUPERSEDED','lab_report_status','N','0','admin',NOW()),(30220,1,'Queued','QUEUED','lab_report_job_status','N','0','admin',NOW()),(30221,2,'Running','RUNNING','lab_report_job_status','N','0','admin',NOW()),(30222,3,'Success','SUCCESS','lab_report_job_status','N','0','admin',NOW()),(30223,4,'Failed','FAILED','lab_report_job_status','N','0','admin',NOW()),(30230,1,'Pending','PENDING','lab_artifact_status','N','0','admin',NOW()),(30231,2,'Success','SUCCESS','lab_artifact_status','N','0','admin',NOW()),(30232,3,'Failed','FAILED','lab_artifact_status','N','0','admin',NOW()),(30233,4,'Not requested','NOT_REQUESTED','lab_artifact_status','N','0','admin',NOW()),(30240,1,'Pending','PENDING','lab_review_status','N','0','admin',NOW()),(30241,2,'Approved','APPROVED','lab_review_status','N','0','admin',NOW()),(30242,3,'Rejected','REJECTED','lab_review_status','N','0','admin',NOW()),
(30250,1,'Cross-department support','CROSS_DEPT','lab_collaboration_category','N','0','admin',NOW()),(30251,2,'Knowledge sharing','KNOWLEDGE','lab_collaboration_category','N','0','admin',NOW()),(30252,3,'Backup development','BACKUP','lab_collaboration_category','N','0','admin',NOW()),(30253,4,'Overdue deduction','OVERDUE','lab_collaboration_category','N','0','admin',NOW()),(30254,5,'Manual deduction','DEDUCTION','lab_collaboration_category','N','0','admin',NOW());

-- Menu and permission tree; the 300xx range does not overlap the RuoYi baseline seed.
DELETE FROM `sys_role_menu` WHERE `menu_id` IN (31000,31001,31002,31003,31004,31005,31006,31007,31008,31009,31010,31011,31020,31021,31022,31023,31030,31031,31032,31033,31034,31040,31041,31042,31050,31060,31061,31070,31071,31072,31080,31081,31082,31090,31091,31092,31093,31094,31095,31100,31101,31102,31110,31111,31112,31113,31114,31120,31121);
DELETE FROM `sys_menu` WHERE `menu_id` IN (31000,31001,31002,31003,31004,31005,31006,31007,31008,31009,31010,31011,31020,31021,31022,31023,31030,31031,31032,31033,31034,31040,31041,31042,31050,31060,31061,31070,31071,31072,31080,31081,31082,31090,31091,31092,31093,31094,31095,31100,31101,31102,31110,31111,31112,31113,31114,31120,31121);
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`remark`) VALUES
(31000,'AI Lab',0,10,'lab',NULL,'','',1,0,'M','0','0','','dashboard','admin',NOW(),'AI lab root'),
(31001,'Dashboard',31000,1,'dashboard','lab/dashboard/index','','',1,0,'C','0','0','lab:dashboard:view','dashboard','admin',NOW(),''),(31002,'Goals',31000,2,'goal','lab/goal/index','','',1,0,'C','0','0','lab:goal:list','target','admin',NOW(),''),(31003,'Tasks',31000,3,'task','lab/task/index','','',1,0,'C','0','0','lab:task:list','list','admin',NOW(),''),(31004,'Members',31000,4,'member','lab/member/index','','',1,0,'C','0','0','lab:member:list','user','admin',NOW(),''),(31005,'Skills',31000,5,'skill','lab/member/index','','',1,0,'C','0','0','lab:skill:list','star','admin',NOW(),''),(31006,'One-to-one',31000,6,'one2one','lab/member/index','','',1,0,'C','0','0','lab:one2one:list','chat-dot-round','admin',NOW(),''),(31007,'Assets',31000,7,'asset','lab/asset/index','','',1,0,'C','0','0','lab:asset:list','cpu','admin',NOW(),''),(31008,'IPR',31000,8,'ipr','lab/ipr/index','','',1,0,'C','0','0','lab:ipr:list','documentation','admin',NOW(),''),(31009,'Performance',31000,9,'perf','lab/performance/index','','',1,0,'C','0','0','lab:perf:list','trend-charts','admin',NOW(),''),(31010,'Templates',31000,10,'template','lab/template/index','','',1,0,'C','0','0','lab:template:list','form','admin',NOW(),''),(31011,'Reports',31000,11,'report','lab/report/index','','',1,0,'C','0','0','lab:report:list','document','admin',NOW(),''),
(31020,'Goal add',31002,1,'#','','','',1,0,'F','0','0','lab:goal:add','#','admin',NOW(),''),(31021,'Goal edit',31002,2,'#','','','',1,0,'F','0','0','lab:goal:edit','#','admin',NOW(),''),(31022,'Goal delete',31002,3,'#','','','',1,0,'F','0','0','lab:goal:remove','#','admin',NOW(),''),(31023,'Goal activate',31002,4,'#','','','',1,0,'F','0','0','lab:goal:activate','#','admin',NOW(),''),
(31030,'Task add',31003,1,'#','','','',1,0,'F','0','0','lab:task:add','#','admin',NOW(),''),(31031,'Task edit',31003,2,'#','','','',1,0,'F','0','0','lab:task:edit','#','admin',NOW(),''),(31032,'Task delete',31003,3,'#','','','',1,0,'F','0','0','lab:task:remove','#','admin',NOW(),''),(31033,'Evidence',31003,4,'#','','','',1,0,'F','0','0','lab:task:evidence','#','admin',NOW(),''),(31034,'Review',31003,5,'#','','','',1,0,'F','0','0','lab:task:review','#','admin',NOW(),''),
(31040,'Member add',31004,1,'#','','','',1,0,'F','0','0','lab:member:add','#','admin',NOW(),''),(31041,'Member edit',31004,2,'#','','','',1,0,'F','0','0','lab:member:edit','#','admin',NOW(),''),(31042,'Member delete',31004,3,'#','','','',1,0,'F','0','0','lab:member:remove','#','admin',NOW(),''),(31050,'Skill config',31005,1,'#','','','',1,0,'F','0','0','lab:skill:config','#','admin',NOW(),''),(31060,'One-to-one add',31006,1,'#','','','',1,0,'F','0','0','lab:one2one:add','#','admin',NOW(),''),(31061,'One-to-one edit',31006,2,'#','','','',1,0,'F','0','0','lab:one2one:edit','#','admin',NOW(),''),
(31070,'Asset add',31007,1,'#','','','',1,0,'F','0','0','lab:asset:add','#','admin',NOW(),''),(31071,'Asset edit',31007,2,'#','','','',1,0,'F','0','0','lab:asset:edit','#','admin',NOW(),''),(31072,'Asset deactivate',31007,3,'#','','','',1,0,'F','0','0','lab:asset:remove','#','admin',NOW(),''),(31080,'IPR add',31008,1,'#','','','',1,0,'F','0','0','lab:ipr:add','#','admin',NOW(),''),(31081,'IPR edit',31008,2,'#','','','',1,0,'F','0','0','lab:ipr:edit','#','admin',NOW(),''),(31082,'IPR deactivate',31008,3,'#','','','',1,0,'F','0','0','lab:ipr:remove','#','admin',NOW(),''),
(31090,'Close period',31009,1,'#','','','',1,0,'F','0','0','lab:perf:close','#','admin',NOW(),''),(31091,'Reopen period',31009,2,'#','','','',1,0,'F','0','0','lab:perf:reopen','#','admin',NOW(),''),(31092,'Red line',31009,3,'#','','','',1,0,'F','0','0','lab:perf:redline','#','admin',NOW(),''),(31093,'Revoke',31009,4,'#','','','',1,0,'F','0','0','lab:perf:revoke','#','admin',NOW(),''),(31094,'Calibrate',31009,5,'#','','','',1,0,'F','0','0','lab:perf:calibrate','#','admin',NOW(),''),(31095,'Performance history',31009,6,'#','','','',1,0,'F','0','0','lab:perf:history','#','admin',NOW(),''),
(31100,'Template config',31010,1,'#','','','',1,0,'F','0','0','lab:template:config','#','admin',NOW(),''),(31101,'Template import',31010,2,'#','','','',1,0,'F','0','0','lab:template:import','#','admin',NOW(),''),(31102,'Template export',31010,3,'#','','','',1,0,'F','0','0','lab:template:export','#','admin',NOW(),''),(31110,'Generate report',31011,1,'#','','','',1,0,'F','0','0','lab:report:generate','#','admin',NOW(),''),(31111,'Retry report',31011,2,'#','','','',1,0,'F','0','0','lab:report:retry','#','admin',NOW(),''),(31112,'Download report',31011,3,'#','','','',1,0,'F','0','0','lab:report:download','#','admin',NOW(),''),(31113,'Finalize report',31011,4,'#','','','',1,0,'F','0','0','lab:report:finalize','#','admin',NOW(),''),(31114,'Sensitive report',31011,5,'#','','','',1,0,'F','0','0','lab:report:sensitive','#','admin',NOW(),''),
(31120,'Reminder list',31001,1,'#','','','',1,0,'F','0','0','lab:reminder:list','#','admin',NOW(),''),(31121,'Reminder read',31001,2,'#','','','',1,0,'F','0','0','lab:reminder:read','#','admin',NOW(),'');

DELETE FROM `sys_role_menu` WHERE `role_id` IN (30001,30002,30003);
DELETE FROM `sys_role_dept` WHERE `role_id` IN (30001,30002,30003);
DELETE FROM `sys_role` WHERE `role_id` IN (30001,30002,30003);
INSERT INTO `sys_role` (`role_id`,`role_name`,`role_key`,`role_sort`,`data_scope`,`menu_check_strictly`,`dept_check_strictly`,`status`,`del_flag`,`create_by`,`create_time`,`remark`) VALUES (30001,'AI Lab Manager','lab_manager',1,1,1,1,'0','0','admin',NOW(),'Lab full administration'),(30002,'AI Lab Line Lead','lab_lead',2,2,1,1,'0','0','admin',NOW(),'Lab line lead'),(30003,'AI Lab Member','lab_member',3,5,1,1,'0','0','admin',NOW(),'Lab member');
INSERT INTO `sys_role_dept` (`role_id`,`dept_id`) VALUES (30002,101);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) SELECT 30001,`menu_id` FROM `sys_menu` WHERE `menu_id` IN (31000,31001,31002,31003,31004,31005,31006,31007,31008,31009,31010,31011,31020,31021,31022,31023,31030,31031,31032,31033,31034,31040,31041,31042,31050,31060,31061,31070,31071,31072,31080,31081,31082,31090,31091,31092,31093,31094,31095,31100,31101,31102,31110,31111,31112,31113,31114,31120,31121);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) SELECT 30002,`menu_id` FROM `sys_menu` WHERE `menu_id` IN (31000,31001,31002,31003,31004,31005,31006,31007,31008,31009,31011,31020,31021,31030,31031,31032,31033,31034,31070,31071,31072,31080,31081,31082,31112,31120,31121);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) SELECT 30003,`menu_id` FROM `sys_menu` WHERE `menu_id` IN (31000,31001,31002,31003,31004,31005,31006,31007,31008,31009,31011,31030,31031,31032,31033,31070,31071,31072,31080,31081,31082,31112,31120,31121);

-- The RuoYi baseline BCrypt hash is used for demo accounts; never use demo accounts in production.
DELETE FROM `sys_user_role` WHERE `user_id` IN (30001,30002,30003,30004,30005,30006);
DELETE FROM `sys_user` WHERE `user_id` IN (30001,30002,30003,30004,30005,30006);
INSERT INTO `sys_user` (`user_id`,`dept_id`,`user_name`,`nick_name`,`user_type`,`email`,`phonenumber`,`sex`,`avatar`,`password`,`status`,`del_flag`,`create_by`,`create_time`,`remark`) VALUES
(30001,100,'lab_manager','Lab Manager','00','lab.manager@example.test','','0','','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu0','1','0','admin',NOW(),'Disabled demo account - administrator must reset password and explicitly enable'),
(30002,101,'lab_algorithm','Algorithm Lead','00','algorithm@example.test','','0','','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu1','1','0','admin',NOW(),'Disabled demo account - administrator must reset password and explicitly enable'),
(30003,101,'lab_platform','Platform Lead','00','platform@example.test','','0','','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu3','1','0','admin',NOW(),'Disabled demo account - administrator must reset password and explicitly enable'),
(30004,101,'lab_hardware','Hardware Lead','00','hardware@example.test','','0','','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu4','1','0','admin',NOW(),'Disabled demo account - administrator must reset password and explicitly enable'),
(30005,101,'lab_developer','Platform Engineer','00','developer@example.test','','0','','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu5','1','0','admin',NOW(),'Disabled demo account - administrator must reset password and explicitly enable'),
(30006,101,'lab_researcher','Algorithm Engineer','00','researcher@example.test','','0','','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu6','1','0','admin',NOW(),'Disabled demo account - administrator must reset password and explicitly enable');
INSERT INTO `sys_user_role` (`user_id`,`role_id`) VALUES (30001,30001),(30002,30002),(30003,30002),(30004,30002),(30005,30003),(30006,30003);

-- Deterministic demo business data.  Child rows are removed before their parents.
DELETE FROM `lab_report_job` WHERE `id` IN (30001);
DELETE FROM `lab_report_instance` WHERE `id` IN (30001);
DELETE FROM `lab_report_summary` WHERE `id` IN (30001,30002);
DELETE FROM `lab_report_section` WHERE `id` IN (30001,30002,30003,30004,30005,30006);
DELETE FROM `lab_report_template` WHERE `id` IN (30001);
DELETE FROM `lab_period_close` WHERE `id` IN (30001);
DELETE FROM `lab_perf_score` WHERE `id` IN (30001,30002,30003);
DELETE FROM `lab_collaboration_record` WHERE `id` IN (30001);
DELETE FROM `lab_ipr` WHERE `id` IN (30001,30002);
DELETE FROM `lab_one2one` WHERE `id` IN (30001);
DELETE FROM `lab_member_skill` WHERE `id` IN (30001,30002,30003,30004,30005);
DELETE FROM `lab_skill` WHERE `id` IN (30001,30002,30003);
DELETE FROM `lab_reminder` WHERE `id` IN (30001,30002);
DELETE FROM `lab_task_block_event` WHERE `id` IN (30001,30002);
DELETE FROM `lab_task_quality_gate` WHERE `id` IN (30001,30002);
DELETE FROM `lab_task_evidence` WHERE `id` IN (30001,30002);
DELETE FROM `lab_task` WHERE `id` IN (30001,30002,30003,30004,30005);
DELETE FROM `lab_goal` WHERE `id` IN (30001,30002,30003,30004);
DELETE FROM `lab_asset` WHERE `id` IN (30001,30002,30003);
DELETE FROM `lab_member` WHERE `id` IN (30001,30002,30003,30004,30005,30006);

INSERT INTO `lab_member` (`id`,`user_id`,`member_no`,`position`,`biz_line`,`role_type`,`leader_id`,`primary_responsibilities`,`backup_responsibilities`,`join_date`,`member_status`,`create_by`,`create_time`,`remark`) VALUES (30001,30001,'MGR-001','Laboratory Manager','manage','MANAGER',NULL,'Laboratory portfolio and people management','Period close and report governance','2024-01-01','ACTIVE','admin',NOW(),'Demo manager');
INSERT INTO `lab_member` (`id`,`user_id`,`member_no`,`position`,`biz_line`,`role_type`,`leader_id`,`primary_responsibilities`,`backup_responsibilities`,`join_date`,`member_status`,`create_by`,`create_time`,`remark`) VALUES (30002,30002,'ALG-001','Algorithm Lead','algorithm','LINE_LEAD',30001,'Algorithm delivery and evaluation','Platform serving review','2024-01-01','ACTIVE','admin',NOW(),'Demo algorithm lead');
INSERT INTO `lab_member` (`id`,`user_id`,`member_no`,`position`,`biz_line`,`role_type`,`leader_id`,`primary_responsibilities`,`backup_responsibilities`,`join_date`,`member_status`,`create_by`,`create_time`,`remark`) VALUES (30003,30003,'PLT-001','Platform Lead','platform','LINE_LEAD',30001,'AI platform and model serving','Algorithm deployment review','2024-01-01','ACTIVE','admin',NOW(),'Demo platform lead');
INSERT INTO `lab_member` (`id`,`user_id`,`member_no`,`position`,`biz_line`,`role_type`,`leader_id`,`primary_responsibilities`,`backup_responsibilities`,`join_date`,`member_status`,`create_by`,`create_time`,`remark`) VALUES (30004,30004,'HW-001','Hardware Lead','hardware','LINE_LEAD',30001,'Accelerator and device integration','Training cluster operations','2024-01-01','ACTIVE','admin',NOW(),'Demo hardware lead');
INSERT INTO `lab_member` (`id`,`user_id`,`member_no`,`position`,`biz_line`,`role_type`,`leader_id`,`primary_responsibilities`,`backup_responsibilities`,`join_date`,`member_status`,`create_by`,`create_time`,`remark`) VALUES (30005,30005,'PLT-002','Platform Engineer','platform','MEMBER',30003,'Serving platform development','MLOps backup','2025-01-01','ACTIVE','admin',NOW(),'Demo development member');
INSERT INTO `lab_member` (`id`,`user_id`,`member_no`,`position`,`biz_line`,`role_type`,`leader_id`,`primary_responsibilities`,`backup_responsibilities`,`join_date`,`member_status`,`create_by`,`create_time`,`remark`) VALUES (30006,30006,'ALG-002','Algorithm Engineer','algorithm','MEMBER',30002,'Evaluation experiments','Evaluation pipeline backup','2025-01-01','ACTIVE','admin',NOW(),'Demo research member');

INSERT INTO `lab_skill` (`id`,`skill_code`,`skill_name`,`skill_category`,`skill_desc`,`status`,`create_by`,`create_time`) VALUES (30001,'LLM','Large language model','algorithm','Model training and evaluation','ACTIVE','admin',NOW()),(30002,'MLOPS','MLOps','platform','Model serving and observability','ACTIVE','admin',NOW()),(30003,'EMBEDDED','Embedded systems','hardware','Device and accelerator integration','ACTIVE','admin',NOW());
INSERT INTO `lab_member_skill` (`id`,`member_id`,`skill_id`,`skill_level`,`last_verified_date`,`evidence_url`,`create_by`,`create_time`) VALUES (30001,30002,30001,5,'2026-07-15','https://example.invalid/skills/30001','admin',NOW()),(30002,30006,30001,4,'2026-07-20','https://example.invalid/skills/30002','admin',NOW()),(30003,30003,30002,5,'2026-07-10','https://example.invalid/skills/30003','admin',NOW()),(30004,30005,30002,4,'2026-07-18','https://example.invalid/skills/30004','admin',NOW()),(30005,30004,30003,5,'2026-07-12','https://example.invalid/skills/30005','admin',NOW());
INSERT INTO `lab_asset` (`id`,`asset_no`,`asset_name`,`asset_type`,`asset_stage`,`primary_owner_id`,`backup_owner_id`,`resource_url`,`repository_url`,`capacity_desc`,`status`,`create_by`,`create_time`) VALUES (30001,'AST-GPU-01','GPU training cluster','hardware','ACCEPTED',30004,30003,'https://example.invalid/assets/gpu-cluster',NULL,'8 GPU training nodes','ACTIVE','admin',NOW()),(30002,'AST-PLT-01','Model serving platform','platform','DEPLOYED',30003,30005,'https://example.invalid/assets/platform','https://example.invalid/repos/platform','Multi-tenant inference','ACTIVE','admin',NOW()),(30003,'AST-ALG-01','Evaluation pipeline','algorithm','VERIFYING',30002,30006,'https://example.invalid/assets/evaluation','https://example.invalid/repos/evaluation','Regression evaluation suite','ACTIVE','admin',NOW());
INSERT INTO `lab_goal` (`id`,`parent_id`,`goal_level`,`year`,`period`,`goal_no`,`title`,`target_value`,`accept_criteria`,`owner_id`,`weight`,`progress_mode`,`progress_rate`,`progress_desc`,`status`,`version`,`create_by`,`create_time`) VALUES (30001,0,'YEAR',2026,NULL,'G-2026-01','Build dependable AI platform','99.5% availability','Quarterly acceptance review',30001,100,'MANUAL',62,'Platform and model work in progress','ACTIVE',1,'admin',NOW()),(30002,30001,'QUARTER',2026,'2026Q3','G-2026Q3-ALG','Improve model quality','Score >= 90','Benchmark report accepted',30002,35,'MANUAL',75,'Evaluation pipeline ready','ACTIVE',1,'admin',NOW()),(30003,30001,'QUARTER',2026,'2026Q3','G-2026Q3-PLT','Expand serving capability','500 QPS','Load test accepted',30003,35,'MANUAL',58,'Release hardening underway','ACTIVE',1,'admin',NOW()),(30004,30001,'QUARTER',2026,'2026Q3','G-2026Q3-HW','Accept accelerator cluster','8 nodes online','Hardware acceptance signed',30004,30,'MANUAL',90,'Cluster accepted','COMPLETED',1,'admin',NOW());
INSERT INTO `lab_task` (`id`,`parent_id`,`goal_id`,`milestone_id`,`task_level`,`period`,`biz_line`,`task_type`,`title`,`owner_id`,`dept_id`,`plan_date`,`actual_finish_time`,`deliverable`,`perf_weight`,`goal_weight`,`workflow_status`,`result_status`,`result_desc`,`fail_reason`,`next_action`,`asset_id`,`coordination_required`,`coordination_owner_id`,`coordination_dept_id`,`coordination_content`,`coordination_support`,`coordination_desc`,`current_block_flag`,`current_block_start`,`period_lock_flag`,`version`,`create_by`,`create_time`) VALUES (30001,0,30001,30002,'month','2026-08','algorithm','key','Publish model benchmark',30002,101,'2026-08-20',NULL,'Benchmark dashboard',30,25,'PENDING_REVIEW','ONTIME','Benchmark report submitted',NULL,'Review quality gate',30003,'1',30003,101,'Platform metrics','Observability data','Needs platform metrics','0',NULL,'0',1,'admin',NOW()),(30002,30001,30001,30002,'week','2026-W32','algorithm','daily','Tune evaluation prompts',30006,101,'2026-08-09','2026-08-07 16:00:00','Prompt suite',10,8,'CONFIRMED','EXCEEDED','Expanded coverage by 15%',NULL,'Share findings',30003,'0',NULL,NULL,NULL,NULL,NULL,'0',NULL,'0',1,'admin',NOW()),(30003,0,30001,30003,'month','2026-08','platform','key','Release serving version 2.1',30003,101,'2026-08-15',NULL,'Release notes',35,30,'ACTIVE','DOING','Canary deployment running',NULL,'Complete load test',30002,'1',30005,101,'Observability integration','Metric access','Coordinate observability','1','2026-08-06 09:00:00','0',1,'admin',NOW()),(30004,30003,30001,30003,'week','2026-W32','platform','daily','Add latency dashboard',30005,101,'2026-08-08',NULL,'Grafana dashboard',12,8,'ACTIVE','DELAYED','Waiting for metric labels','Metric schema incomplete','Agree metric schema',30002,'1',30003,101,'Metric schema review','Schema decision','Metric schema review','1','2026-08-05 10:00:00','0',1,'admin',NOW()),(30005,0,30001,30004,'month','2026-08','hardware','key','Complete cluster acceptance',30004,101,'2026-08-05','2026-08-04 15:00:00','Signed acceptance',28,25,'CONFIRMED','ONTIME','All eight nodes accepted',NULL,'Archive acceptance evidence',30001,'0',NULL,NULL,NULL,NULL,NULL,'0',NULL,'0',1,'admin',NOW());
INSERT INTO `lab_task_evidence` (`id`,`task_id`,`evidence_type`,`evidence_title`,`evidence_url`,`evidence_json`,`submitter_id`,`submit_time`,`audit_status`,`auditor_id`,`audit_time`,`audit_comment`,`create_by`,`create_time`) VALUES (30001,30001,'DOCUMENT','August benchmark','https://example.invalid/evidence/benchmark',JSON_OBJECT('version','1.0'),30002,NOW(),'PENDING',NULL,NULL,NULL,'admin',NOW()),(30002,30005,'FILE','Cluster acceptance','https://example.invalid/evidence/acceptance',JSON_OBJECT('nodes',8),30004,NOW(),'APPROVED',30001,NOW(),'Accepted','admin',NOW());
INSERT INTO `lab_task_quality_gate` (`id`,`task_id`,`gate_no`,`gate_name`,`gate_status`,`evidence_id`,`checker_id`,`check_time`,`check_result`,`create_by`,`create_time`) VALUES (30001,30001,'QG-01','Benchmark reproducibility','PENDING',NULL,30001,NULL,NULL,'admin',NOW()),(30002,30005,'QG-01','Hardware acceptance','PASSED',30002,30001,NOW(),'All nodes passed','admin',NOW());
INSERT INTO `lab_task_block_event` (`id`,`task_id`,`episode_no`,`block_type`,`block_reason`,`block_start_time`,`block_end_time`,`block_status`,`resolver_id`,`resolution`,`create_by`,`create_time`) VALUES (30001,30003,1,'DEPENDENCY','Observability metrics not finalized','2026-08-06 09:00:00',NULL,'OPEN',NULL,NULL,'admin',NOW()),(30002,30004,1,'DEPENDENCY','Metric labels pending','2026-08-05 10:00:00',NULL,'OPEN',NULL,NULL,'admin',NOW());
INSERT INTO `lab_reminder` (`id`,`task_id`,`business_type`,`business_id`,`episode_no`,`recipient_id`,`reminder_type`,`reminder_level`,`reminder_date`,`title`,`reminder_content`,`read_flag`,`send_time`,`idempotency_key`,`version`,`create_by`,`create_time`) VALUES (30001,30003,'TASK',30003,1,30003,'BLOCK','WARNING','2026-08-13','Task block warning','Release is blocked by metrics','0',NOW(),'BLOCK:30003:1:WARNING:30003:2026-08-13',0,'admin',NOW()),(30002,30001,'TASK',30001,NULL,30001,'REVIEW','INFO','2026-08-07','Task review pending','Benchmark requires review','0',NOW(),'REVIEW:30001:30001:2026-08-07',0,'admin',NOW());
INSERT INTO `lab_one2one` (`id`,`member_id`,`leader_id`,`meeting_date`,`topic`,`facts_evidence`,`difficulties`,`next_action`,`manager_comment`,`status`,`create_by`,`create_time`) VALUES (30001,30006,30002,'2026-08-07','Evaluation growth','Experiment records are complete','Cross-team dataset access is slow','Document prompt methodology','Keep the evidence trail and request dataset access','OPEN','admin',NOW());
INSERT INTO `lab_ipr` (`id`,`ipr_no`,`ipr_name`,`ipr_type`,`ipr_stage`,`owner_id`,`planned_submit_date`,`actual_submit_date`,`acceptance_no`,`certificate_no`,`authorized_date`,`evidence_url`,`status`,`create_by`,`create_time`) VALUES (30001,'IPR-2026-01','Evaluation pipeline software','SOFTWARE_COPYRIGHT','SUBMITTED',30002,'2026-07-31','2026-07-25',NULL,NULL,NULL,'https://example.invalid/ipr/1','ACTIVE','admin',NOW()),(30002,'IPR-2026-02','Inference routing patent','PATENT','DRAFT',30003,'2026-10-31',NULL,NULL,NULL,NULL,NULL,'ACTIVE','admin',NOW());
INSERT INTO `lab_collaboration_record` (`id`,`task_id`,`period`,`from_member_id`,`to_member_id`,`category`,`signed_score`,`evidence_url`,`reviewer_id`,`review_status`,`review_time`,`review_comment`,`create_by`,`create_time`) VALUES (30001,30003,'2026-08',30005,30003,'CROSS_DEPT',6,'https://example.invalid/collab/1',30001,'APPROVED',NOW(),'Latency dashboard support','admin',NOW());
INSERT INTO `lab_perf_score` (`id`,`member_id`,`period`,`revision_no`,`current_flag`,`score`,`detail_json`,`red_line_flag`,`revoked_flag`,`calibration_status`,`calibrator_id`,`calibration_note`,`create_by`,`create_time`) VALUES (30001,30002,'2026-07',1,'1',92.50,JSON_OBJECT('delivery',45,'collaboration',47),'0','0','CALIBRATED',30001,'Validated at calibration','admin',NOW()),(30002,30003,'2026-07',1,'1',88.00,JSON_OBJECT('delivery',40,'collaboration',48),'0','0','CALIBRATED',30001,'Validated at calibration','admin',NOW()),(30003,30005,'2026-07',1,'1',76.00,JSON_OBJECT('delivery',35,'collaboration',41),'0','0','PENDING',NULL,NULL,'admin',NOW());
INSERT INTO `lab_period_close` (`id`,`period`,`close_status`,`close_by`,`close_time`,`close_reason`,`version`,`create_by`,`create_time`) VALUES (30001,'2026-07','CLOSED','lab_manager','2026-08-03 18:00:00','Monthly assessment closed',1,'admin',NOW());
SET @ailab_seed_month_default_flag=(SELECT CASE WHEN COUNT(*)=0 THEN '1' ELSE '0' END FROM `lab_report_template` WHERE `period_type`='MONTH' AND `latest_flag`='1' AND `default_flag`='1' AND `status`='ENABLED' AND `del_flag`='0');
INSERT INTO `lab_report_template` (`id`,`template_code`,`template_name`,`period_type`,`revision_no`,`latest_flag`,`default_flag`,`status`,`header_json`,`style_json`,`version`,`create_by`,`create_time`,`remark`) VALUES (30001,'standard_month','Standard monthly laboratory report','MONTH',1,'1',@ailab_seed_month_default_flag,'ENABLED',JSON_OBJECT('title','人工智能实验室月报','logo','ai-lab'),JSON_OBJECT('theme','blue','font','Microsoft YaHei'),1,'admin',NOW(),'Default standard monthly template');
INSERT INTO `lab_report_section` (`id`,`template_id`,`section_code`,`section_name`,`section_type`,`sort_no`,`data_source`,`query_config_json`,`render_config_json`,`style_config_json`,`manual_flag`,`visible_flag`,`sensitive_flag`,`sensitive_permission`,`version`,`create_by`,`create_time`) VALUES (30001,30001,'TASK_TABLE','任务交付','TABLE',10,'TASK_DETAIL',JSON_OBJECT('filters',JSON_ARRAY(JSON_OBJECT('field','period','operator','EQ','value','${period}'))),JSON_OBJECT('columns',JSON_ARRAY('owner','status','deliverable')),JSON_OBJECT('width','100%'),'0','1','0',NULL,1,'admin',NOW()),(30002,30001,'SCORE_STAT','绩效概览','STAT',20,'TASK_STAT',JSON_OBJECT('filters',JSON_ARRAY(JSON_OBJECT('field','period','operator','EQ','value','${period}'))),JSON_OBJECT('metrics',JSON_ARRAY('average','top')),JSON_OBJECT(),'0','1','1','lab:report:sensitive',1,'admin',NOW()),(30003,30001,'MANAGER_TEXT','目标进展','TEXT',30,'GOAL_PROGRESS',JSON_OBJECT('filters',JSON_ARRAY()),JSON_OBJECT('template','Goal progress summary'),JSON_OBJECT(),'0','1','0',NULL,1,'admin',NOW()),(30004,30001,'MANUAL_NOTE','管理小结','MANUAL',40,NULL,JSON_OBJECT('filters',JSON_ARRAY()),JSON_OBJECT('placeholder','Enter management note','required',true),JSON_OBJECT(),'1','1','0',NULL,1,'admin',NOW()),(30005,30001,'LINE_GROUP','业务线进展','GROUP_TEXT',50,'TASK_COORD',JSON_OBJECT('filters',JSON_ARRAY()),JSON_OBJECT('groupBy','bizLine','template','line update'),JSON_OBJECT(),'0','1','0',NULL,1,'admin',NOW()),(30006,30001,'PROGRESS_CHART','三季度目标进度','CHART',60,'GOAL_PROGRESS',JSON_OBJECT('filters',JSON_ARRAY()),JSON_OBJECT('chart','bar'),JSON_OBJECT(),'0','1','0',NULL,1,'admin',NOW());
INSERT INTO `lab_report_summary` (`id`,`period`,`biz_line`,`section_code`,`summary_json`,`summary_text`,`source_revision`,`create_by`,`create_time`) VALUES (30001,'2026-07','ALL','MANUAL_NOTE',JSON_OBJECT('bizLineSummary','July performance calibration completed','reasonAnalysis','Delivery evidence and cross-line reviews were closed','nextStep','Prepare Q3 milestone execution'),'July management summary',1,'admin',NOW()),(30002,'2026-08','platform','TASK_TABLE',JSON_OBJECT('active',2,'blocked',2),'Platform release needs metric coordination',1,'admin',NOW());
INSERT INTO `lab_report_instance` (`id`,`report_no`,`template_id`,`template_code`,`template_revision`,`period`,`biz_line`,`revision_no`,`lifecycle_status`,`current_flag`,`final_flag`,`sensitive_flag`,`source_type`,`source_data_json`,`source_perf_revision`,`content_json`,`content_markdown`,`json_status`,`json_path`,`json_error`,`markdown_status`,`markdown_path`,`markdown_error`,`word_status`,`word_path`,`word_error`,`pdf_status`,`pdf_path`,`pdf_error`,`version`,`create_by`,`create_time`) VALUES (30001,'RPT-2026-07-ALL',30001,'standard_month',1,'2026-07','ALL',1,'FINALIZED','1','1','1','AUTO',JSON_OBJECT('performancePins',JSON_ARRAY(JSON_OBJECT('memberId',30002,'revisionNo',1),JSON_OBJECT('memberId',30003,'revisionNo',1),JSON_OBJECT('memberId',30005,'revisionNo',1))),1,JSON_OBJECT('sections',6),'# AI Laboratory Monthly Report\nJuly closed report','SUCCESS','archive/report-30001/report-30001.json',NULL,'SUCCESS','archive/report-30001/report-30001.md',NULL,'SUCCESS','archive/report-30001/report-30001.docx',NULL,'SUCCESS','archive/report-30001/report-30001.pdf',NULL,1,'admin',NOW());
INSERT INTO `lab_report_job` (`id`,`job_no`,`report_id`,`job_type`,`job_status`,`progress_rate`,`attempt_count`,`started_time`,`finished_time`,`idempotency_key`,`version`,`create_by`,`create_time`) VALUES (30001,'RPJ-2026-07-ALL',30001,'GENERATE','SUCCESS',100,1,'2026-08-03 17:00:00','2026-08-03 17:01:00','report-2026-07-all',1,'admin',NOW());

-- Exactly five enabled Quartz jobs; replace only the named AI-lab jobs on re-run.
DELETE FROM `sys_job` WHERE `invoke_target` IN ('labScheduleTask.scanBlocks()','labScheduleTask.scanPendingTasks()','labScheduleTask.closeDuePeriods()','labScheduleTask.cleanReportTempFiles()','labScheduleTask.recoverReportJobs()');
INSERT INTO `sys_job` (`job_id`,`job_name`,`job_group`,`invoke_target`,`cron_expression`,`misfire_policy`,`concurrent`,`status`,`create_by`,`create_time`,`remark`) VALUES
(30001,'AI Lab block scan','DEFAULT','labScheduleTask.scanBlocks()','0 */15 * * * ?','3','1','0','admin',NOW(),'AI lab'),(30002,'AI Lab pending task scan','DEFAULT','labScheduleTask.scanPendingTasks()','0 0 9 * * ?','3','1','0','admin',NOW(),'AI lab'),(30003,'AI Lab period close','DEFAULT','labScheduleTask.closeDuePeriods()','0 0 2 1 * ?','3','1','0','admin',NOW(),'AI lab'),(30004,'AI Lab temporary report cleanup','DEFAULT','labScheduleTask.cleanReportTempFiles()','0 0 2 * * ?','3','1','0','admin',NOW(),'AI lab'),(30005,'AI Lab report job recovery','DEFAULT','labScheduleTask.recoverReportJobs()','0 */10 * * * ?','3','1','0','admin',NOW(),'AI lab');
