# 周承诺事实模型切换运行手册

本手册用于把旧周任务工作流投影切换为独立的周承诺执行事实。生产变更必须由数据库管理员、应用负责人和业务负责人共同执行；任一停止条件不满足时，禁止跳过隔离项或手工伪造事件。

## 1. 前提与固定顺序

- MySQL 8.0、InnoDB 严格模式；Redis 6+；应用写入已冻结。
- 先备份数据库和报告归档目录，并记录备份校验值。
- 初始化顺序固定为：`ry_20240629.sql` → `quartz.sql` → 旧库数据 → `ailab.sql`（重复两次验证幂等）→ 当前测试夹具（仅 IT）。
- `LabMapperMySqlIT.DatabaseInitializer` 是真实集成测试唯一初始化入口，禁止另写一套删表脚本。
- 切换开关默认关闭：`LAB_COMMITMENT_READ_NEW_MODEL=false`、`LAB_COMMITMENT_WRITE_SELF_CLOSE=false`。

## 2. 一次性验收环境

使用独立数据库、最小权限账号和独立 Redis 端口/密码。下列密码必须由安全存储注入：

```powershell
$env:LAB_IT_DB_URL = 'jdbc:mysql://127.0.0.1:3306/ailab_commitment_it'
$env:LAB_IT_DB_USERNAME = 'ailab_commitment_it'
$env:LAB_IT_DB_PASSWORD = '<一次性强密码>'
$env:LAB_IT_REDIS_HOST = '127.0.0.1'
$env:LAB_IT_REDIS_PORT = '6381'
$env:LAB_IT_REDIS_PASSWORD = '<一次性强密码>'
$env:LOG_PATH = "$env:TEMP/ailab-commitment-it-logs"
& scripts/invoke-maven.ps1 -pl ruoyi-admin -am '-Dtest=LabMapperMySqlIT,CutoverLifecycleMySqlIT' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

必须得到 `Tests run: 24, Failures: 0, Errors: 0`。证据位于：

- `ruoyi-admin/target/surefire-reports/TEST-com.ailab.system.mapper.LabMapperMySqlIT.xml`
- `ruoyi-admin/target/surefire-reports/TEST-com.ailab.system.mapper.CutoverLifecycleMySqlIT.xml`

结束后只删除本次创建的数据库、账号和 Redis 6381 实例，不得清理共享开发库或其他 Redis 端口。

## 3. Expand 与基线迁移

1. 保持两个开关为 `false`，部署兼容读写版本。
2. 冻结周承诺写入，确认没有仍在提交的事务。
3. 执行 `sql/ailab.sql` 两次；第二次不得增加基线事件或隔离项。
4. 记录旧数据按 `workflow_status/result_status/actual_finish_time/period_lock_flag/是否有 OPEN 阻塞` 的分组数量。
5. 验证样本黄金值：5 条可判定周任务生成 5 条 `MIGRATED_BASELINE`；2 条歧义任务进入 OPEN 隔离；迁移不改写旧工作流、审核和证据字段。

```sql
SELECT resolution_status,issue_code,COUNT(*)
FROM lab_task_migration_issue WHERE del_flag='0'
GROUP BY resolution_status,issue_code ORDER BY resolution_status,issue_code;

SELECT event_type,to_status,COUNT(*)
FROM lab_task_execution_event WHERE del_flag='0'
GROUP BY event_type,to_status ORDER BY event_type,to_status;
```

停止条件：迁移报错、第二次执行数量变化、当前状态与最后事件不一致，或出现未分类组合。

## 4. 隔离项处理

- `TERMINAL_WITH_OPEN_BLOCK`：先保留截止时快照，再把原阻塞 episode 以 `MIGRATION_TERMINAL_UNRESOLVED` 明确关闭，然后把隔离项标记为 `RESOLVED`。
- `AMBIGUOUS_LEGACY_COMBINATION`：业务负责人根据原始审计记录纠正旧工作流事实；不得直接填写 `execution_status`。标记隔离项已解决后重新执行迁移，由脚本生成基线事件。
- 每次处理都记录处理人、时间、resolution code 和原始 `source_state_json`。

只有下列结果为 0 才能继续：

```sql
SELECT COUNT(*) AS open_issues
FROM lab_task_migration_issue
WHERE resolution_status='OPEN' AND del_flag='0';
```

## 5. 冻结写入时的全消费者比较

在同一 `asOf`、同一权限身份和同一数据库快照下，逐项比较旧投影与新事实：Dashboard、目标运营进度、`LabTaskService.calculateMonthProgress`、关期、提醒、管理者/成员工作台以及 11 个报告 Provider。保存分组数、主键集合、完成/到期/阻塞/未完成数量和报告 source pin。

允许差异只限已登记并解决的歧义旧数据；其他差异必须停止切换。证据保存到变更单附件和 `.acceptance/lightweight-management/`，不得提交密码或敏感报告正文。

## 6. 读切换与可回滚窗口

1. 保持写入冻结，设置 `LAB_COMMITMENT_READ_NEW_MODEL=true`、`LAB_COMMITMENT_WRITE_SELF_CLOSE=false`，原子重启全部实例。
2. 启动守卫必须确认隔离项为 0；健康检查、角色工作台与消费者比较全部通过。
3. 在仍冻结写入时排练一次回滚：读开关恢复 `false`、重启、验证旧读结果；随后再次切回新读并复验。
4. 可回滚的必要条件是从冻结开始没有任何新周执行事件；一旦发生写入，禁止回滚旧读取。

## 7. 写切换与不可回退点

1. 新读取稳定后设置 `LAB_COMMITMENT_WRITE_SELF_CLOSE=true` 并重启。
2. 解除写冻结，让验收成员执行一次真实的非迁移周承诺动作。
3. 确认 `lab.commitment.pointOfNoReturn=true`，并验证事件链、当前状态和执行版本一致。
4. 用 `READ_NEW_MODEL=false` 的配置演练启动守卫，启动必须被拒绝。

写切换后只允许前向修复：关闭入口、保留事件和当前事实、修复程序或追加受审计的补偿事件、重新部署并复验。禁止删除事件、回填旧 workflow 状态或把读取切回旧模型。

## 8. 放行与停止条件

放行前必须同时满足：隔离项 0；全消费者比较通过；真实 MySQL 24/24；SQL/XML/全量单测/打包/前端构建通过；管理者、负责人、成员真实浏览器验收通过；备份与证据可追溯。

出现以下任一情况立即停止：开放隔离项、重复迁移新增事件、事件/当前行不一致、报告定稿没有 close revision、关期快照被改写、权限撤销后仍能下载敏感报告、PONR 后旧读配置可以启动。
