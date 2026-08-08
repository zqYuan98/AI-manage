-- One-time upgrade for installations initialized before report template revisions were pinned.
-- Run through the deployment migration runner exactly once, after sql/ailab.sql has created lab_report_template.
ALTER TABLE `lab_report_instance`
  ADD COLUMN `template_code` varchar(64) NULL COMMENT 'pinned template family' AFTER `template_id`,
  ADD COLUMN `template_revision` int NULL COMMENT 'pinned template revision' AFTER `template_code`;

UPDATE `lab_report_instance` r
JOIN `lab_report_template` t ON t.`id` = r.`template_id`
SET r.`template_code` = t.`template_code`,
    r.`template_revision` = t.`revision_no`
WHERE r.`template_code` IS NULL OR r.`template_revision` IS NULL;

ALTER TABLE `lab_report_instance`
  MODIFY COLUMN `template_code` varchar(64) NOT NULL COMMENT 'pinned template family',
  MODIFY COLUMN `template_revision` int NOT NULL COMMENT 'pinned template revision';
