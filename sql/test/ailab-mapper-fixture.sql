DELETE FROM lab_task_evidence WHERE id BETWEEN 39001 AND 39099;
DELETE FROM lab_task_block_event WHERE id BETWEEN 39001 AND 39099;
DELETE FROM lab_task_quality_gate WHERE id BETWEEN 39001 AND 39099;
DELETE FROM lab_task WHERE id BETWEEN 39001 AND 39099;
DELETE FROM lab_goal WHERE id BETWEEN 39001 AND 39099;
DELETE FROM lab_ipr WHERE id BETWEEN 39301 AND 39399;
DELETE FROM lab_one2one WHERE id BETWEEN 39301 AND 39399;
DELETE FROM lab_asset WHERE id BETWEEN 39301 AND 39399;
DELETE FROM lab_member_skill WHERE id BETWEEN 39301 AND 39399;
DELETE FROM lab_skill WHERE id BETWEEN 39301 AND 39399;
DELETE FROM lab_member WHERE id IN (39201,39202,39203);
DELETE FROM sys_user_role WHERE user_id IN (39101,39102,39103);
DELETE FROM sys_user WHERE user_id IN (39101,39102,39103);

INSERT INTO sys_user(user_id,dept_id,user_name,nick_name,user_type,email,phonenumber,sex,avatar,password,status,del_flag,create_by,create_time,remark)
VALUES
(39101,100,'it_lab_manager','IT Lab Manager','00','it-manager@example.test','','0','','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu7','0','0','it',NOW(),'Active MySQL IT account'),
(39102,101,'it_algorithm_lead','IT Algorithm Lead','00','it-lead@example.test','','0','','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu8','0','0','it',NOW(),'Active MySQL IT account'),
(39103,101,'it_algorithm_member','IT Algorithm Member','00','it-member@example.test','','0','','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu9','0','0','it',NOW(),'Active MySQL IT account');
INSERT INTO sys_user_role(user_id,role_id) VALUES (39101,30001),(39102,30002),(39103,30003);
INSERT INTO lab_member(id,user_id,member_no,position,biz_line,role_type,leader_id,primary_responsibilities,backup_responsibilities,join_date,member_status,version,del_flag,create_by,create_time,remark)
VALUES
(39201,39101,'IT-MGR-01','Laboratory Manager','manage','MANAGER',NULL,'IT portfolio','IT governance','2026-01-01','ACTIVE',0,'0','it',NOW(),'MySQL IT member'),
(39202,39102,'IT-ALG-LEAD','Algorithm Lead','algorithm','LINE_LEAD',39201,'IT algorithm delivery','IT evaluation backup','2026-01-01','ACTIVE',0,'0','it',NOW(),'MySQL IT member'),
(39203,39103,'IT-ALG-MEMBER','Algorithm Engineer','algorithm','MEMBER',39202,'IT evaluation','IT data backup','2026-01-01','ACTIVE',0,'0','it',NOW(),'MySQL IT member');

INSERT INTO lab_skill(id,skill_code,skill_name,skill_category,skill_desc,status,version,del_flag,create_by,create_time)
VALUES(39301,'IT-JAVA','IT Java','platform','IT skill','ACTIVE',0,'0','it',NOW()),
      (39302,'IT-EVAL','IT Evaluation','algorithm','IT skill','ACTIVE',0,'0','it',NOW());
INSERT INTO lab_member_skill(id,member_id,skill_id,skill_level,last_verified_date,evidence_url,version,del_flag,create_by,create_time)
VALUES(39301,39203,39301,3,'2026-07-01','https://example.invalid/it/skill',0,'0','it',NOW());
INSERT INTO lab_asset(id,asset_no,asset_name,asset_version,asset_type,asset_stage,primary_owner_id,backup_owner_id,critical_flag,status,version,del_flag,create_by,create_time)
VALUES(39301,'IT-ASSET-1','IT critical model','v1','algorithm','DEPLOYED',39203,NULL,'1','ACTIVE',0,'0','it',NOW()),
      (39302,'IT-ASSET-2','IT backed model','v1','algorithm','DEPLOYED',39203,39202,'1','ACTIVE',0,'0','it',NOW());
INSERT INTO lab_one2one(id,member_id,leader_id,meeting_date,topic,facts_evidence,difficulties,next_action,manager_comment,status,version,del_flag,create_by,create_time)
VALUES(39301,39203,39202,'2026-08-01','IT growth','IT facts','IT difficulty','IT next','IT manager comment','OPEN',0,'0','it',NOW());
INSERT INTO lab_ipr(id,ipr_no,ipr_name,ipr_type,ipr_stage,owner_id,planned_submit_date,actual_submit_date,acceptance_no,certificate_no,status,version,del_flag,create_by,create_time)
VALUES(39301,'IT-IPR-1','IT patent','PATENT','ACCEPTED',39203,'2026-07-31','2026-07-20','IT-ACCEPT-1',NULL,'ACTIVE',0,'0','it',NOW());

INSERT INTO lab_goal(id,parent_id,goal_level,year,period,goal_no,title,owner_id,weight,progress_mode,progress_rate,status,version,del_flag,create_by,create_time)
VALUES
(39001,0,'YEAR',2026,NULL,'IT-YEAR-2026','IT annual goal',39201,100,'AUTO',0,'ACTIVE',0,'0','it',NOW()),
(39002,39001,'QUARTER',2026,'2026Q1','IT-2026-Q1','IT quarter one',39202,40,'AUTO',0,'ACTIVE',0,'0','it',NOW()),
(39003,39001,'QUARTER',2026,'2026Q2','IT-2026-Q2','IT quarter two',30003,60,'AUTO',0,'ACTIVE',0,'0','it',NOW());

INSERT INTO lab_task(id,parent_id,goal_id,milestone_id,task_level,period,biz_line,task_type,title,owner_id,dept_id,plan_date,actual_finish_time,deliverable,perf_weight,goal_weight,workflow_status,result_status,result_desc,coordination_required,current_block_flag,period_lock_flag,version,del_flag,create_by,create_time)
VALUES
(39001,0,39001,39002,'month','2026-01','algorithm','key','IT confirmed delivery',39202,101,'2026-01-20','2026-01-19 10:00:00','report',70,25,'CONFIRMED','ONTIME','done','0','0','0',0,'0','it',NOW()),
(39002,0,39001,39002,'month','2026-02','algorithm','key','IT undone delivery',39202,101,'2026-02-20',NULL,'report',30,75,'CONFIRMED','UNDONE',NULL,'0','0','0',0,'0','it',NOW()),
(39003,0,39001,39003,'month','2026-04','platform','key','IT delayed delivery',30003,102,'2026-04-20','2026-04-22 10:00:00','release',100,100,'CONFIRMED','DELAYED','late','0','0','0',0,'0','it',NOW()),
(39004,0,39001,39003,'month','2026-08','platform','key','IT monthly execution',30003,102,'2026-08-28',NULL,'release',0,0,'ACTIVE','DOING',NULL,'0','0','0',0,'0','it',NOW()),
(39005,39004,39001,39003,'week','2026-W32','platform','daily','IT confirmed week',30003,102,'2026-08-09','2026-08-08 10:00:00','change','0','0','CONFIRMED','EXCEEDED','done','0','0','0',0,'0','it',NOW()),
(39006,39004,39001,39003,'week','2026-W33','platform','daily','IT pending week',30003,102,'2026-08-16',NULL,'change','0','0','PENDING_REVIEW','ONTIME','submitted','0','0','0',0,'0','it',NOW()),
(39007,0,39001,39002,'month','2026-03','algorithm','daily','IT algorithm member task',39203,101,'2026-03-20',NULL,'notes',0,0,'ACTIVE','DOING',NULL,'0','0','0',0,'0','it',NOW()),
(39008,0,39001,39002,'month','2026-03','algorithm','daily','IT member pending review',39203,101,'2026-03-21','2026-03-20 10:00:00','notes',0,0,'PENDING_REVIEW','ONTIME','submitted','0','0','0',0,'0','it',NOW()),
(39009,0,39001,39002,'month','2026-03','algorithm','daily','IT lead pending self review',39202,101,'2026-03-22','2026-03-21 10:00:00','notes',0,0,'PENDING_REVIEW','ONTIME','submitted','0','0','0',0,'0','it',NOW());

INSERT INTO lab_task_evidence(id,task_id,evidence_type,evidence_title,evidence_url,evidence_json,submitter_id,submit_time,audit_status,auditor_id,audit_time,audit_comment,del_flag,create_by,create_time)
VALUES(39001,39001,'DOCUMENT','IT delivery evidence','https://example.invalid/it/evidence',JSON_OBJECT('source','integration'),39202,NOW(),'APPROVED',39201,NOW(),'verified','0','it',NOW());

INSERT INTO lab_task_quality_gate(id,task_id,gate_no,gate_name,gate_status,evidence_id,checker_id,check_time,check_result,del_flag,create_by,create_time)
VALUES(39001,39001,'IT-QG-01','IT acceptance gate','PASSED',39001,39201,NOW(),'accepted','0','it',NOW());

INSERT INTO lab_task_block_event(id,task_id,episode_no,block_type,block_reason,block_start_time,block_status,del_flag,create_by,create_time)
VALUES(39001,39004,1,'DEPENDENCY','IT dependency block','2026-08-08 09:00:00','OPEN','0','it',NOW());
