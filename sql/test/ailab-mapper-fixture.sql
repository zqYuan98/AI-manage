DELETE FROM lab_task_evidence WHERE id BETWEEN 39001 AND 39099;
DELETE FROM lab_task_block_event WHERE id BETWEEN 39001 AND 39099;
DELETE FROM lab_task_quality_gate WHERE id BETWEEN 39001 AND 39099;
DELETE FROM lab_task WHERE id BETWEEN 39001 AND 39099;
DELETE FROM lab_goal WHERE id BETWEEN 39001 AND 39099;

INSERT INTO lab_goal(id,parent_id,goal_level,year,period,goal_no,title,owner_id,weight,progress_mode,progress_rate,status,version,del_flag,create_by,create_time)
VALUES
(39001,0,'YEAR',2026,NULL,'IT-YEAR-2026','IT annual goal',30001,100,'AUTO',0,'ACTIVE',0,'0','it',NOW()),
(39002,39001,'QUARTER',2026,'2026Q1','IT-2026-Q1','IT quarter one',30002,40,'AUTO',0,'ACTIVE',0,'0','it',NOW()),
(39003,39001,'QUARTER',2026,'2026Q2','IT-2026-Q2','IT quarter two',30003,60,'AUTO',0,'ACTIVE',0,'0','it',NOW());

INSERT INTO lab_task(id,parent_id,goal_id,milestone_id,task_level,period,biz_line,task_type,title,owner_id,dept_id,plan_date,actual_finish_time,deliverable,perf_weight,goal_weight,workflow_status,result_status,result_desc,coordination_required,current_block_flag,period_lock_flag,version,del_flag,create_by,create_time)
VALUES
(39001,0,39001,39002,'month','2026-01','algorithm','key','IT confirmed delivery',30002,101,'2026-01-20','2026-01-19 10:00:00','report',70,25,'CONFIRMED','ONTIME','done','0','0','0',0,'0','it',NOW()),
(39002,0,39001,39002,'month','2026-02','algorithm','key','IT undone delivery',30002,101,'2026-02-20',NULL,'report',30,75,'CONFIRMED','UNDONE',NULL,'0','0','0',0,'0','it',NOW()),
(39003,0,39001,39003,'month','2026-04','platform','key','IT delayed delivery',30003,102,'2026-04-20','2026-04-22 10:00:00','release',100,100,'CONFIRMED','DELAYED','late','0','0','0',0,'0','it',NOW()),
(39004,0,39001,39003,'month','2026-08','platform','key','IT monthly execution',30003,102,'2026-08-28',NULL,'release',0,0,'ACTIVE','DOING',NULL,'0','0','0',0,'0','it',NOW()),
(39005,39004,39001,39003,'week','2026-W32','platform','daily','IT confirmed week',30003,102,'2026-08-09','2026-08-08 10:00:00','change','0','0','CONFIRMED','EXCEEDED','done','0','0','0',0,'0','it',NOW()),
(39006,39004,39001,39003,'week','2026-W33','platform','daily','IT pending week',30003,102,'2026-08-16',NULL,'change','0','0','PENDING_REVIEW','ONTIME','submitted','0','0','0',0,'0','it',NOW());

INSERT INTO lab_task_evidence(id,task_id,evidence_type,evidence_title,evidence_url,evidence_json,submitter_id,submit_time,audit_status,auditor_id,audit_time,audit_comment,del_flag,create_by,create_time)
VALUES(39001,39001,'DOCUMENT','IT delivery evidence','https://example.invalid/it/evidence',JSON_OBJECT('source','integration'),30002,NOW(),'APPROVED',30001,NOW(),'verified','0','it',NOW());

INSERT INTO lab_task_quality_gate(id,task_id,gate_no,gate_name,gate_status,checker_id,check_time,check_result,del_flag,create_by,create_time)
VALUES(39001,39001,'IT-QG-01','IT acceptance gate','PASSED',30001,NOW(),'accepted','0','it',NOW());

INSERT INTO lab_task_block_event(id,task_id,block_type,block_reason,block_start_time,block_status,del_flag,create_by,create_time)
VALUES(39001,39004,'DEPENDENCY','IT dependency block','2026-08-08 09:00:00','OPEN','0','it',NOW());
