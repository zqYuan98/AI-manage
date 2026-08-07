-- Immediately preceding Task 4 schema, used only by the real MySQL upgrade IT.
DROP TABLE IF EXISTS `lab_task_quality_gate`;
DROP TABLE IF EXISTS `lab_task_block_event`;
DROP TABLE IF EXISTS `lab_one2one`;
DROP TABLE IF EXISTS `lab_ipr`;
DROP TABLE IF EXISTS `lab_asset`;
DROP TABLE IF EXISTS `lab_skill`;

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

CREATE TABLE `lab_asset` (
 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key',
 `asset_no` varchar(64) NOT NULL COMMENT 'asset number',
 `asset_name` varchar(200) NOT NULL COMMENT 'asset name',
 `asset_type` varchar(32) NOT NULL COMMENT 'asset type',
 `asset_stage` varchar(32) DEFAULT 'VERIFYING' COMMENT 'asset stage',
 `primary_owner_id` bigint NOT NULL COMMENT 'primary owner',
 `backup_owner_id` bigint DEFAULT NULL COMMENT 'backup owner',
 `resource_url` varchar(1000) DEFAULT NULL COMMENT 'resource URL',
 `repository_url` varchar(1000) DEFAULT NULL COMMENT 'repository URL',
 `capacity_desc` varchar(1000) DEFAULT NULL COMMENT 'capacity description',
 `status` varchar(16) DEFAULT 'ACTIVE' COMMENT 'asset status',
 `del_flag` char(1) DEFAULT '0' COMMENT 'delete flag',
 `create_by` varchar(64) DEFAULT '' COMMENT 'creator',
 `create_time` datetime DEFAULT NULL COMMENT 'created time',
 `update_by` varchar(64) DEFAULT '' COMMENT 'updater',
 `update_time` datetime DEFAULT NULL COMMENT 'updated time',
 `remark` varchar(500) DEFAULT NULL COMMENT 'remark',
 `active_unique_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `del_flag` = '0' THEN 1 ELSE NULL END) STORED COMMENT 'active record unique marker',
 PRIMARY KEY (`id`),
 UNIQUE KEY `uk_lab_asset_no` (`asset_no`,`active_unique_flag`),
 KEY `idx_lab_asset_primary_status` (`primary_owner_id`,`status`),
 KEY `idx_lab_asset_backup` (`backup_owner_id`),
 KEY `idx_lab_asset_type_stage` (`asset_type`,`asset_stage`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='legacy laboratory asset';

CREATE TABLE `lab_skill` (
 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key',
 `skill_code` varchar(64) NOT NULL COMMENT 'skill code',
 `skill_name` varchar(100) NOT NULL COMMENT 'skill name',
 `skill_category` varchar(64) DEFAULT NULL COMMENT 'skill category',
 `skill_desc` varchar(1000) DEFAULT NULL COMMENT 'skill description',
 `status` varchar(16) DEFAULT 'ACTIVE' COMMENT 'status',
 `del_flag` char(1) DEFAULT '0' COMMENT 'delete flag',
 `create_by` varchar(64) DEFAULT '' COMMENT 'creator',
 `create_time` datetime DEFAULT NULL COMMENT 'created time',
 `update_by` varchar(64) DEFAULT '' COMMENT 'updater',
 `update_time` datetime DEFAULT NULL COMMENT 'updated time',
 `remark` varchar(500) DEFAULT NULL COMMENT 'remark',
 `active_unique_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `del_flag` = '0' AND `status` = 'ACTIVE' THEN 1 ELSE NULL END) STORED COMMENT 'active record unique marker',
 PRIMARY KEY (`id`),
 UNIQUE KEY `uk_lab_skill_code` (`skill_code`,`active_unique_flag`),
 UNIQUE KEY `uk_lab_skill_name` (`skill_name`,`active_unique_flag`),
 KEY `idx_lab_skill_category` (`skill_category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='legacy skill dictionary';

CREATE TABLE `lab_one2one` (
 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key',
 `member_id` bigint NOT NULL COMMENT 'member reference',
 `leader_id` bigint NOT NULL COMMENT 'leader reference',
 `meeting_date` date NOT NULL COMMENT 'meeting date',
 `topic` varchar(500) NOT NULL COMMENT 'topic',
 `feedback` varchar(2000) DEFAULT NULL COMMENT 'feedback',
 `action_items` varchar(2000) DEFAULT NULL COMMENT 'action items',
 `status` varchar(16) DEFAULT 'OPEN' COMMENT 'status',
 `del_flag` char(1) DEFAULT '0' COMMENT 'delete flag',
 `create_by` varchar(64) DEFAULT '' COMMENT 'creator',
 `create_time` datetime DEFAULT NULL COMMENT 'created time',
 `update_by` varchar(64) DEFAULT '' COMMENT 'updater',
 `update_time` datetime DEFAULT NULL COMMENT 'updated time',
 `remark` varchar(500) DEFAULT NULL COMMENT 'remark',
 PRIMARY KEY (`id`),
 KEY `idx_lab_one2one_member_date` (`member_id`,`meeting_date`),
 KEY `idx_lab_one2one_leader` (`leader_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='legacy one to one record';

CREATE TABLE `lab_ipr` (
 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key',
 `ipr_no` varchar(64) NOT NULL COMMENT 'IPR number',
 `ipr_name` varchar(300) NOT NULL COMMENT 'IPR name',
 `ipr_type` varchar(32) NOT NULL COMMENT 'IPR type',
 `ipr_stage` varchar(32) DEFAULT 'DRAFTING' COMMENT 'IPR stage',
 `owner_id` bigint NOT NULL COMMENT 'member owner',
 `application_no` varchar(128) DEFAULT NULL COMMENT 'application number',
 `submit_date` date DEFAULT NULL COMMENT 'submit date',
 `authorized_date` date DEFAULT NULL COMMENT 'authorized date',
 `evidence_url` varchar(1000) DEFAULT NULL COMMENT 'evidence URL',
 `del_flag` char(1) DEFAULT '0' COMMENT 'delete flag',
 `create_by` varchar(64) DEFAULT '' COMMENT 'creator',
 `create_time` datetime DEFAULT NULL COMMENT 'created time',
 `update_by` varchar(64) DEFAULT '' COMMENT 'updater',
 `update_time` datetime DEFAULT NULL COMMENT 'updated time',
 `remark` varchar(500) DEFAULT NULL COMMENT 'remark',
 `active_unique_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `del_flag` = '0' THEN 1 ELSE NULL END) STORED COMMENT 'active record unique marker',
 PRIMARY KEY (`id`),
 UNIQUE KEY `uk_lab_ipr_no` (`ipr_no`,`active_unique_flag`),
 KEY `idx_lab_ipr_owner_stage` (`owner_id`,`ipr_stage`),
 KEY `idx_lab_ipr_type` (`ipr_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='legacy intellectual property right';

INSERT INTO `lab_task_quality_gate`
 (`id`,`task_id`,`gate_no`,`gate_name`,`gate_status`,`create_by`,`create_time`)
VALUES (39991,39990,'LEGACY-QG-01','Legacy acceptance','PENDING','it',NOW());

INSERT INTO `lab_task_block_event`
 (`id`,`task_id`,`block_type`,`block_reason`,`block_start_time`,`block_status`,`create_by`,`create_time`)
VALUES
 (39991,39990,'DEPENDENCY','Legacy first episode','2026-07-01 09:00:00','CLOSED','it','2026-07-01 09:00:00'),
 (39992,39990,'DEPENDENCY','Legacy second episode','2026-07-02 09:00:00','OPEN','it','2026-07-02 09:00:00');

INSERT INTO `lab_asset`
 (`id`,`asset_no`,`asset_name`,`asset_type`,`asset_stage`,`primary_owner_id`,`status`,`create_by`,`create_time`)
VALUES
 (39993,'LEGACY-ASSET-A','Legacy shared asset','algorithm','DEPLOYED',39203,'ACTIVE','it',NOW()),
 (39994,'LEGACY-ASSET-B','Legacy shared asset','algorithm','DEPLOYED',39203,'ACTIVE','it',NOW());

INSERT INTO `lab_skill`
 (`id`,`skill_code`,`skill_name`,`skill_category`,`status`,`del_flag`,`create_by`,`create_time`)
VALUES
 (39993,'LEGACY-SKILL-A','Legacy Duplicate Skill','algorithm','INACTIVE','0','it',NOW()),
 (39994,'LEGACY-SKILL-B','Legacy Duplicate Skill','algorithm','ACTIVE','0','it',NOW()),
 (39995,'LEGACY-SKILL-C','Legacy Duplicate Skill [LEGACY-SKILL-B:39994]','algorithm','ACTIVE','0','it',NOW()),
 (39996,'LEGACY-SKILL-D','Legacy Mixed Skill','algorithm','INACTIVE','0','it',NOW()),
 (39997,'LEGACY-SKILL-D','Legacy Mixed Skill','algorithm','ACTIVE','0','it',NOW()),
 (39998,'LEGACY-SKILL-F','Legacy Inactive Skill','algorithm','INACTIVE','0','it',NOW()),
 (39999,'LEGACY-SKILL-F','Legacy Inactive Skill','algorithm','INACTIVE','0','it',NOW()),
 (40000,'LEGACY-SKILL-D','Legacy Mixed Skill','algorithm','ACTIVE','2','it',NOW()),
 (40001,'LEGACY-SKILL-D-LEGACY-39997','Legacy Code Collision','algorithm','ACTIVE','0','it',NOW());

INSERT INTO `lab_one2one`
 (`id`,`member_id`,`leader_id`,`meeting_date`,`topic`,`feedback`,`action_items`,`status`,`create_by`,`create_time`)
VALUES (39991,39203,39202,'2026-06-16','Legacy discussion','Legacy one-to-one feedback','Legacy action item','OPEN','it',NOW());

INSERT INTO `lab_ipr`
 (`id`,`ipr_no`,`ipr_name`,`ipr_type`,`ipr_stage`,`owner_id`,`application_no`,`submit_date`,`evidence_url`,`create_by`,`create_time`)
VALUES
 (39991,'IPR-LEGACY-39991','Legacy filing','PATENT','ACCEPTED',39203,'LEGACY-APPLICATION-39991','2026-06-15','https://example.invalid/legacy/ipr','it',NOW()),
 (39992,'IPR-LEGACY-39992','Legacy draft filing','PATENT','DRAFTING',39203,NULL,NULL,'https://example.invalid/legacy/ipr-draft','it',NOW());
