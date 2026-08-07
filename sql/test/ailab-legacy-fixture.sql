-- Immediately preceding Task 4 schema, used only by the real MySQL upgrade IT.
DROP TABLE IF EXISTS `lab_task_quality_gate`;
DROP TABLE IF EXISTS `lab_task_block_event`;

CREATE TABLE `lab_task_quality_gate` (
 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key',
 `task_id` bigint NOT NULL COMMENT 'task reference',
 `gate_no` varchar(32) NOT NULL COMMENT 'gate number',
 `gate_name` varchar(100) NOT NULL COMMENT 'gate name',
 `gate_status` varchar(16) DEFAULT 'PENDING' COMMENT 'gate status',
 `checker_id` bigint DEFAULT NULL COMMENT 'checker member',
 `check_time` datetime DEFAULT NULL COMMENT 'check time',
 `check_result` varchar(1000) DEFAULT NULL COMMENT 'check result',
 `del_flag` char(1) DEFAULT '0' COMMENT 'delete flag',
 `create_by` varchar(64) DEFAULT '' COMMENT 'creator',
 `create_time` datetime DEFAULT NULL COMMENT 'created time',
 `update_by` varchar(64) DEFAULT '' COMMENT 'updater',
 `update_time` datetime DEFAULT NULL COMMENT 'updated time',
 `remark` varchar(500) DEFAULT NULL COMMENT 'remark',
 `active_unique_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `del_flag` = '0' THEN 1 ELSE NULL END) STORED COMMENT 'active record unique marker',
 PRIMARY KEY (`id`),
 UNIQUE KEY `uk_lab_gate_task_no` (`task_id`,`gate_no`,`active_unique_flag`),
 KEY `idx_lab_gate_task_status` (`task_id`,`gate_status`),
 KEY `idx_lab_gate_checker_status` (`checker_id`,`gate_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='legacy task quality gate';

CREATE TABLE `lab_task_block_event` (
 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key',
 `task_id` bigint NOT NULL COMMENT 'task reference',
 `block_type` varchar(32) NOT NULL COMMENT 'block type',
 `block_reason` varchar(1000) NOT NULL COMMENT 'block reason',
 `block_start_time` datetime NOT NULL COMMENT 'episode start',
 `block_end_time` datetime DEFAULT NULL COMMENT 'episode end',
 `block_status` varchar(16) DEFAULT 'OPEN' COMMENT 'episode status',
 `resolver_id` bigint DEFAULT NULL COMMENT 'resolver member',
 `resolution` varchar(1000) DEFAULT NULL COMMENT 'resolution',
 `del_flag` char(1) DEFAULT '0' COMMENT 'delete flag',
 `create_by` varchar(64) DEFAULT '' COMMENT 'creator',
 `create_time` datetime DEFAULT NULL COMMENT 'created time',
 `update_by` varchar(64) DEFAULT '' COMMENT 'updater',
 `update_time` datetime DEFAULT NULL COMMENT 'updated time',
 `remark` varchar(500) DEFAULT NULL COMMENT 'remark',
 PRIMARY KEY (`id`),
 KEY `idx_lab_block_task_open` (`task_id`,`block_status`,`block_start_time`),
 KEY `idx_lab_block_start` (`block_start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='legacy task blocking episode';

INSERT INTO `lab_task_quality_gate`
 (`id`,`task_id`,`gate_no`,`gate_name`,`gate_status`,`create_by`,`create_time`)
VALUES (39991,39990,'LEGACY-QG-01','Legacy acceptance','PENDING','it',NOW());

INSERT INTO `lab_task_block_event`
 (`id`,`task_id`,`block_type`,`block_reason`,`block_start_time`,`block_status`,`create_by`,`create_time`)
VALUES
 (39991,39990,'DEPENDENCY','Legacy first episode','2026-07-01 09:00:00','CLOSED','it','2026-07-01 09:00:00'),
 (39992,39990,'DEPENDENCY','Legacy second episode','2026-07-02 09:00:00','OPEN','it','2026-07-02 09:00:00');
