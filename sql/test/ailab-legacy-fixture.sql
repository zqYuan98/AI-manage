-- Immediately preceding Task 4 schema, used only by the real MySQL upgrade IT.
DROP TABLE IF EXISTS `lab_task_quality_gate`;
DROP TABLE IF EXISTS `lab_task_block_event`;
DROP TABLE IF EXISTS `lab_task`;
DROP TABLE IF EXISTS `lab_one2one`;
DROP TABLE IF EXISTS `lab_ipr`;
DROP TABLE IF EXISTS `lab_asset`;
DROP TABLE IF EXISTS `lab_skill`;

CREATE TABLE `lab_task` (
 `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary key', `parent_id` bigint DEFAULT 0 COMMENT 'parent task', `goal_id` bigint DEFAULT NULL COMMENT 'goal reference', `milestone_id` bigint DEFAULT NULL COMMENT 'milestone reference', `task_level` varchar(16) NOT NULL COMMENT 'month or week', `period` varchar(16) NOT NULL COMMENT 'business period', `biz_line` varchar(32) NOT NULL COMMENT 'business line', `task_type` varchar(16) NOT NULL COMMENT 'key or daily', `title` varchar(200) NOT NULL COMMENT 'task title', `owner_id` bigint NOT NULL COMMENT 'member owner', `dept_id` bigint DEFAULT NULL COMMENT 'department', `plan_date` date DEFAULT NULL COMMENT 'planned finish date', `actual_finish_time` datetime DEFAULT NULL COMMENT 'actual finish time', `deliverable` varchar(1000) DEFAULT NULL COMMENT 'deliverable', `perf_weight` decimal(8,2) DEFAULT 0 COMMENT 'performance weight', `goal_weight` decimal(8,2) DEFAULT 0 COMMENT 'goal contribution weight', `workflow_status` varchar(32) DEFAULT 'DRAFT' COMMENT 'workflow status', `result_status` varchar(32) DEFAULT 'DOING' COMMENT 'result status', `result_desc` varchar(1000) DEFAULT NULL COMMENT 'result description', `fail_reason` varchar(1000) DEFAULT NULL COMMENT 'failure reason', `next_action` varchar(1000) DEFAULT NULL COMMENT 'next action', `asset_id` bigint DEFAULT NULL COMMENT 'related asset', `coordination_required` char(1) DEFAULT '0' COMMENT 'requires coordination', `coordination_owner_id` bigint DEFAULT NULL COMMENT 'coordination owner', `coordination_dept_id` bigint DEFAULT NULL COMMENT 'coordination department', `coordination_content` varchar(1000) DEFAULT NULL COMMENT 'coordination content', `coordination_support` varchar(1000) DEFAULT NULL COMMENT 'requested support', `coordination_desc` varchar(1000) DEFAULT NULL COMMENT 'coordination description', `current_block_flag` char(1) DEFAULT '0' COMMENT 'currently blocked', `current_block_start` datetime DEFAULT NULL COMMENT 'current block start', `period_lock_flag` char(1) DEFAULT '0' COMMENT 'period locked', `version` int DEFAULT 0 COMMENT 'optimistic version', `del_flag` char(1) DEFAULT '0' COMMENT 'delete flag', `create_by` varchar(64) DEFAULT '' COMMENT 'creator', `create_time` datetime DEFAULT NULL COMMENT 'created time', `update_by` varchar(64) DEFAULT '' COMMENT 'updater', `update_time` datetime DEFAULT NULL COMMENT 'updated time', `remark` varchar(500) DEFAULT NULL COMMENT 'remark',
 PRIMARY KEY (`id`), KEY `idx_lab_task_parent` (`parent_id`), KEY `idx_lab_task_owner_period_workflow` (`owner_id`,`period`,`workflow_status`), KEY `idx_lab_task_period_workflow` (`period`,`workflow_status`,`period_lock_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='legacy laboratory task';

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

INSERT INTO `lab_task`
 (`id`,`parent_id`,`goal_id`,`milestone_id`,`task_level`,`period`,`biz_line`,`task_type`,`title`,`owner_id`,`dept_id`,`plan_date`,`actual_finish_time`,`deliverable`,`workflow_status`,`result_status`,`result_desc`,`fail_reason`,`next_action`,`current_block_flag`,`current_block_start`,`period_lock_flag`,`version`,`create_by`,`create_time`)
VALUES
 (39879,0,30001,30002,'month','2026-08','algorithm','key','Legacy August parent',39203,101,'2026-08-31',NULL,'Legacy parent','ACTIVE','DOING',NULL,NULL,NULL,'0',NULL,'0',0,'it',NOW()),
 (39880,39879,30001,30002,'week','2026-W31','algorithm','daily','Legacy confirmed undone',39203,101,'2026-08-02',NULL,'Legacy outcome','CONFIRMED','UNDONE',NULL,'Not completed','Carry forward','0',NULL,'0',0,'it',NOW()),
 (39881,39879,30001,30002,'week','2026-W32','algorithm','daily','Legacy confirmed done',39203,101,'2026-08-09','2026-08-09 12:00:00','Legacy outcome','CONFIRMED','ONTIME','Completed',NULL,NULL,'0',NULL,'0',0,'it',NOW()),
 (39882,39879,30001,30002,'week','2026-W32','algorithm','daily','Legacy pending delayed',39203,101,'2026-08-09','2026-08-10 12:00:00','Legacy outcome','PENDING_REVIEW','DELAYED','Completed late',NULL,NULL,'0',NULL,'0',0,'it',NOW()),
 (39883,39879,30001,30002,'week','2026-W32','algorithm','daily','Legacy pending terminal blocked',39203,101,'2026-08-09','2026-08-08 12:00:00','Legacy outcome','PENDING_REVIEW','EXCEEDED','Completed early',NULL,NULL,'1','2026-08-08 09:00:00','0',0,'it',NOW()),
 (39884,39879,30001,30002,'week','2026-W32','algorithm','daily','Legacy active terminal anomaly',39203,101,'2026-08-09',NULL,'Legacy outcome','ACTIVE','DELAYED','Stale result',NULL,NULL,'0',NULL,'0',0,'it',NOW()),
 (39885,39879,30001,30002,'week','2026-W32','algorithm','daily','Legacy active blocked',39203,101,'2026-08-09',NULL,'Legacy outcome','ACTIVE','DOING',NULL,NULL,NULL,'1','2026-08-08 09:00:00','0',0,'it',NOW()),
 (39886,39879,30001,30002,'week','2026-W31','algorithm','daily','Legacy locked cross month',39203,101,'2026-08-02','2026-08-02 12:00:00','Legacy outcome','CONFIRMED','ONTIME','Completed',NULL,NULL,'0',NULL,'1',0,'it',NOW());

INSERT INTO `lab_task_block_event`
 (`id`,`task_id`,`block_type`,`block_reason`,`block_start_time`,`block_status`,`create_by`,`create_time`)
VALUES
 (39883,39883,'DEPENDENCY','Legacy terminal block','2026-08-08 09:00:00','OPEN','it','2026-08-08 09:00:00'),
 (39885,39885,'DEPENDENCY','Legacy active block','2026-08-08 09:00:00','OPEN','it','2026-08-08 09:00:00'),
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

-- Task 8 predecessor: instance layout is complete except for immutable template-code/revision pins.
DROP TABLE IF EXISTS `lab_report_job`;
DROP TABLE IF EXISTS `lab_report_instance`;
DROP TABLE IF EXISTS `lab_report_section`;
DROP TABLE IF EXISTS `lab_report_template`;
CREATE TABLE `lab_report_section` (
 `id` bigint NOT NULL, `template_id` bigint NOT NULL, `section_code` varchar(64) NOT NULL, `section_name` varchar(200) NOT NULL,
 `section_type` varchar(32) NOT NULL, `sort_no` int NOT NULL, `data_source` varchar(128), `query_config_json` json, `render_config_json` json,
 `style_config_json` json, `manual_flag` char(1), `visible_flag` char(1), `sensitive_flag` char(1), `version` int, `del_flag` char(1) DEFAULT '0',
 `create_by` varchar(64), `create_time` datetime, `update_by` varchar(64), `update_time` datetime, `remark` varchar(500), PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE `lab_report_template` (
 `id` bigint NOT NULL, `template_code` varchar(64) NOT NULL, `template_name` varchar(200) NOT NULL, `period_type` varchar(16) NOT NULL,
 `revision_no` int NOT NULL, `latest_flag` char(1), `default_flag` char(1), `status` varchar(16), `header_json` json, `style_json` json,
 `version` int, `del_flag` char(1) DEFAULT '0', `create_by` varchar(64), `create_time` datetime, `update_by` varchar(64), `update_time` datetime, `remark` varchar(500), PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE `lab_report_instance` (
 `id` bigint NOT NULL, `report_no` varchar(64) NOT NULL, `template_id` bigint NOT NULL, `period` varchar(16) NOT NULL, `biz_line` varchar(32), `revision_no` int NOT NULL,
 `lifecycle_status` varchar(32), `current_flag` char(1), `final_flag` char(1), `sensitive_flag` char(1), `source_data_json` json, `source_perf_revision` int,
 `content_json` json, `content_markdown` longtext, `json_status` varchar(16), `json_path` varchar(1000), `json_error` varchar(2000),
 `markdown_status` varchar(16), `markdown_path` varchar(1000), `markdown_error` varchar(2000), `word_status` varchar(16), `word_path` varchar(1000), `word_error` varchar(2000),
 `pdf_status` varchar(16), `pdf_path` varchar(1000), `pdf_error` varchar(2000), `version` int, `del_flag` char(1) DEFAULT '0', `create_by` varchar(64), `create_time` datetime, `update_by` varchar(64), `update_time` datetime, `remark` varchar(500), PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO `lab_report_template` (`id`,`template_code`,`template_name`,`period_type`,`revision_no`,`latest_flag`,`default_flag`,`status`,`version`,`del_flag`,`create_by`,`create_time`) VALUES
 (39990,'legacy-report-template-39990','Legacy report template','MONTH',7,'1','1','ENABLED',0,'0','it',NOW()),
 (39993,'legacy-sensitive-only-39993','Legacy sensitive-only report template','MONTH',1,'1','0','ENABLED',0,'2','it',NOW()),
 (39994,'legacy-year-no-default','Legacy report type without a default','YEAR',1,'1','0','ENABLED',0,'0','it',NOW()),
 (39995,'legacy-null-default','Legacy invalid nullable default','QUARTER',1,NULL,'1',NULL,0,'0','it',NOW());
INSERT INTO `lab_report_section` (`id`,`template_id`,`section_code`,`section_name`,`section_type`,`sort_no`,`data_source`,`query_config_json`,`render_config_json`,`style_config_json`,`manual_flag`,`visible_flag`,`sensitive_flag`,`version`,`del_flag`,`create_by`,`create_time`) VALUES
 (39990,39990,'LEGACY_PERF','Legacy performance','STAT',10,'PERF_SUMMARY',JSON_OBJECT(),JSON_OBJECT('metrics',JSON_ARRAY('average')),JSON_OBJECT(),'0','1','0',0,'0','it',NOW()),
 (39991,39990,'LEGACY_FLAGGED','Legacy flagged sensitive section','STAT',20,'TASK_STAT',JSON_OBJECT(),JSON_OBJECT('metrics',JSON_ARRAY('average')),JSON_OBJECT(),'0','1','1',0,'0','it',NOW()),
 (39993,39993,'LEGACY_FLAG_ONLY','Legacy non-PERF sensitive section','TEXT',10,'GOAL_PROGRESS',JSON_OBJECT(),JSON_OBJECT(),JSON_OBJECT(),'0','1','1',0,'2','it',NOW());
INSERT INTO `lab_report_instance` (`id`,`report_no`,`template_id`,`period`,`biz_line`,`revision_no`,`lifecycle_status`,`current_flag`,`final_flag`,`sensitive_flag`,`version`,`del_flag`,`create_by`,`create_time`) VALUES
 (39990,'RPT-LEGACY-39990',39990,'2026-06','ALL',1,'FINAL','1','1','0',0,'0','it',NOW()),
 (39993,'RPT-LEGACY-SENSITIVE-39993',39993,'2026-06','ALL',1,'FINAL','1','1',NULL,0,'0','it',NOW());
