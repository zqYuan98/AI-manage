# AI Lab 数据字典

## 1. 约定

- 主键均为 `BIGINT`；业务写入使用 `version` 乐观锁，删除优先使用 `del_flag` 或状态迁移。
- 时间按数据库时区 `GMT+8` 保存；Java 的跨时区时刻使用 `Instant`，业务日期使用 `LocalDate`。
- 周期格式：月 `YYYY-MM`、周 `YYYY-Www`、季 `YYYYQn`（绩效存储键为 `YYYY-Qn`）、年 `YYYY`。
- JSON 字段是不可变审计/渲染快照，不是授权来源；查询和下载始终重新解析当前用户角色与权限。
- `lab_report_*` 的文件列只存归档根下相对路径；API DTO 不暴露路径、run token、source pins 或原始异常。

## 2. 业务表

| 表 | 用途 | 关键键/范围 | 生命周期与不变量 |
| --- | --- | --- | --- |
| `lab_goal` | 年度目标、季度里程碑 | `parent_id`,`goal_level`,`year`,`period`,`owner_id` | YEAR→QUARTER；进度由确认任务权威聚合，状态/version 防并发覆盖 |
| `lab_task` | 月/周任务与交付事实 | `parent_id`,`goal_id`,`milestone_id`,`period`,`biz_line`,`owner_id` | DRAFT→ACTIVE→PENDING_REVIEW→CONFIRMED；周任务绑定父月，关期后锁定 |
| `lab_task_evidence` | 任务证据及审核快照 | `task_id`,`audit_status`,`submitter_id`,`auditor_id` | 提交/审核元数据完整；关期计分只读锁定事实 |
| `lab_task_quality_gate` | 任务质量门 | `task_id`,`gate_no`,`gate_status`,`evidence_id` | close 前所有周期内最低 gate 必须通过 |
| `lab_task_block_event` | 阻塞 episode | `task_id`,`episode_no`,`block_status`,`block_start_time` | OPEN/RESOLVED；健康风险和提醒读取 OPEN 事件，不信任任务冗余字段 |
| `lab_reminder` | 阻塞/待填提醒 | episode+recipient+level+date 幂等键 | 7/14 天、月末 3/1 天扫描；本人读/批量读受 scope 约束 |
| `lab_asset` | 实验室资产/成果 | `asset_no`,`primary_owner_id`,`backup_owner_id`,`asset_stage` | single-point risk 由统一 policy 派生；停用为状态迁移 |
| `lab_member` | 实验室成员身份 | `user_id`,`member_no`,`biz_line`,`role_type`,`leader_id` | sys_user 到可信角色/业务线映射；ACTIVE/INACTIVE，不以脱敏 null 判停用 |
| `lab_skill` | 技能定义 | `skill_code`,`skill_category`,`status` | manager 配置，member/lead 只读授权矩阵 |
| `lab_member_skill` | 成员技能证据 | `member_id`,`skill_id`,`skill_level` | 成员×技能唯一；验证日期/证据 URL 可审计 |
| `lab_one2one` | 1:1 纪要 | `member_id`,`leader_id`,`meeting_date`,`status` | 本人/同线/manager scope；敏感字段不进入通用日志 |
| `lab_ipr` | 知识产权台账 | `ipr_no`,`owner_id`,`ipr_stage`,`status` | 阶段前进；回退及原因 manager-only |
| `lab_collaboration_record` | 跨成员协同事实 | `task_id`,`period`,`from_member_id`,`to_member_id` | review 后进入绩效；季度 BACKUP 资格按截止期/资产/成员证据派生并快照 |
| `lab_perf_score` | 月度评分与季度校准 | `member_id`,`period`,`revision_no`,`current_flag` | current revision 唯一；关期 immutable；red-line 撤销保留旧 revision |
| `lab_period_close` | 关期/重开记录 | `period`,`close_status`,`period_version`,`version` | OPEN/CLOSED；关期固化源事实，重开仅管理者可操作且必填原因；每次重开递增 `period_version` |
| `lab_report_template` | 模板家族修订 | `template_code`,`period_type`,`revision_no` | 每 code 的 latest=max revision；每 period type 至多一个 enabled default |
| `lab_report_section` | 模板章节配置 | `template_id`,`section_code`,`sort_no` | provider-local typed filters/fields/metrics；敏感 permission 固化快照 |
| `lab_report_summary` | period×bizLine×section 人工小结 | 唯一 `(period,biz_line,section_code)` | 三字段 canonical JSON；`source_revision` 乐观并发；optional 清空为软删除 |
| `lab_report_instance` | 报告修订与四制品状态 | template+period+bizLine+revision | source pins/content/path immutable；current finalized 唯一；API 使用窄 DTO |
| `lab_report_job` | 持久报告队列 | `idempotency_key`,`job_status`,`run_token` | QUEUED→RUNNING→SUCCESS/FAILED；claim token/version fencing；stale recovery/backpressure |

## 3. 主要字典

| 字典类型 | 示例值 | 用途 |
| --- | --- | --- |
| `lab_goal_level` | YEAR, QUARTER | 目标层级 |
| `lab_goal_status` | ACTIVE, COMPLETED, TERMINATED | 目标状态 |
| `lab_task_level` | month, week | 任务层级 |
| `lab_task_workflow_status` | DRAFT, ACTIVE, PENDING_REVIEW, CONFIRMED | 任务工作流 |
| `lab_task_result_status` | DOING, ONTIME, EXCEEDED, DELAYED, UNDONE | 结果路径 |
| `lab_member_role_type` | MANAGER, LINE_LEAD, MEMBER | 业务可信角色投影 |
| `lab_member_status` | ACTIVE, INACTIVE | 成员状态 |
| `lab_ipr_stage` | DRAFT, SUBMITTED, ACCEPTED/AUTHORIZED | IPR 阶段 |
| `lab_perf_calibration_status` | PENDING, CALIBRATED | 绩效校准 |
| `lab_period_close_status` | OPEN, CLOSED | 周期状态 |
| `lab_report_lifecycle` | DRAFT, FINALIZED, SUPERSEDED | 报告生命周期；生成进度由持久 job 与各制品状态表达 |
| `lab_report_artifact_status` | NOT_REQUESTED, PENDING, SUCCESS, FAILED | 单制品状态 |
| `lab_report_job_status` | QUEUED, RUNNING, SUCCESS, FAILED | 队列状态 |

完整 seed 和可重跑迁移以 `sql/ailab.sql` 为准；`scripts/verify-sql.ps1` 对表、字典、权限、job 与演示成员数量执行静态合同校验。

## 4. 权限边界摘要

| 角色 | 读范围 | 写范围 | 敏感能力 |
| --- | --- | --- | --- |
| `lab_manager` | 全实验室 | 目标/成员/模板/报告/关期治理 | 仍需独立 `lab:report:sensitive` 才能读取/下载敏感报告 |
| `lab_lead` | 公开全局 + 同线管理视图 | 同业务线允许对象、本人数据、人工业务线小结 | 不能确认 active red-line、不能读取他人绩效详情 |
| `lab_member` | 公开目标/资产/IPR/非敏感定稿报告 + 本人视图 | 本人允许任务/证据/台账 | 无敏感报告、校准、关期权限 |

权限标识的完整集合由 `LabSqlContractTest.PERMISSIONS` 与 `sql/ailab.sql` 共同约束；前端按钮只是体验层，Controller 与 Service 必须双重门禁。
