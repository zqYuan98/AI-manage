# 人工智能实验室管理系统验收清单

本清单分为可离线自动验证与需要真实服务的验收。勾选时同时记录执行人、时间、commit、浏览器版本与环境地址；禁止用单元测试结果替代真实 MySQL/Redis 或浏览器验收。

## A. 自动化门禁

- [ ] 在 clean process 中运行：`powershell -ExecutionPolicy Bypass -File scripts/verify-project.ps1`
- [ ] SQL 合同：20 张业务表、59 个字典项、48 个权限、5 个 Quartz job、6 个演示成员
- [ ] 正常 Surefire 单元测试全部通过；真实数据库类 `*IT` 未被普通 Surefire 误报为已执行
- [ ] `ruoyi-admin -am -DskipTests package` 成功
- [ ] 实验室前端源码以 `eslint --no-ignore` 定向检查通过，且全站 `npm run build:prod` 成功（上游模板的既有 lint 债务不计入本模块门禁；体积 warning 单独记录）
- [ ] 生产源码无 TODO/FIXME/UnsupportedOperationException/Not implemented 占位
- [ ] tracked JSON/Markdown 与固定 fixture 逐字节一致，DOCX 解包后的规范 OOXML/媒体条目一致，PDF magic/xref/EOF 有效
- [ ] LibreOffice 存在时 DemoReportFixtureTest 的真实 PDF 冒烟不 skip；缺失时报告明确的 capability skip

## B. 环境与启动记录

| 项目 | 执行时记录 |
| --- | --- |
| commit |  |
| JDK / Maven |  |
| Node / npm |  |
| MySQL / Redis |  |
| LibreOffice |  |
| 后端命令与端口 |  |
| 前端命令与端口 |  |
| 数据库初始化时间 |  |

- [ ] 依次执行 `sql/ry_20240629.sql`、`sql/quartz.sql`、`sql/ailab.sql`
- [ ] 使用 demo profile 启动后端，默认预期端口 `8080`
- [ ] 启动前端开发代理或部署 dist，记录实际访问 URL
- [ ] 使用真实服务运行：

```powershell
mvn -pl ruoyi-admin -am -Dspring.profiles.active=lab-it -Dtest=LabMapperMySqlIT -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] 真实 MySQL Mapper IT 全部通过，且没有 ApplicationContext 连接错误
- [ ] Redis 租约可获取、续期、失租停止写入；重启 worker 后 stale job 能恢复

## C. 角色与数据范围

- [ ] manager 看到全实验室 KPI、目标、任务、成员、资产、IPR、绩效与报告
- [ ] line lead 只写同业务线/本人允许对象；父月授权后可聚合合法周任务
- [ ] member 只能修改本人允许对象；可读公开目标/资产/非敏感定稿报告
- [ ] 无 `lab:report:sensitive` 时，历史查询不加载/返回敏感正文，下载被拒绝
- [ ] manager 在页面打开后被撤销敏感权限，再次下载仍被后端拒绝
- [ ] 越权 route query、数组筛选、空集合筛选均 fail closed，不退化为全量

## D. 业务主路径

- [ ] Dashboard KPI/健康度/趋势/成员负载与钻取集合一致
- [ ] 目标四层结构、里程碑权重、月/周任务贡献与两级舍入口径一致
- [ ] 草稿任务必填、提交、撤回、review、确认、reopen 均符合 owner/manager 门禁
- [ ] 阻塞 episode、7/14 天提醒、待填提醒和幂等键符合日历日边界
- [ ] 成员停用/启用、技能矩阵、1:1、资产 single-point risk、IPR 阶段回退权限正确
- [ ] period close 锁定快照；reopen 必填原因且产生新修订，不覆盖历史
- [ ] active red-line 只由 manager 确认；撤销需纠正证据，历史 revision 可追溯

## E. 报告与模板

- [ ] 模板设计器 native tree 拖拽、键盘移动、provider typed fields/operators/metrics 正确
- [ ] 禁止 FreeMarker eval/特殊变量/危险 builtin/赋值指令，33 层条件嵌套被拒绝
- [ ] 人工小结包含业务线小结、原因分析、下步策略；required 完整性与 optional 清空正确
- [ ] 两个编辑者并发保存小结时，旧 sourceRevision 被拒绝而非静默覆盖
- [ ] generation 经 `QUEUED → RUNNING → SUCCESS/FAILED`，轮询退避且切筛选不串状态
- [ ] JSON/Markdown/Word/PDF 独立状态、部分下载、定向 retry 与 Word→PDF 复用正确
- [ ] 杀掉 worker 后重启，stale recovery 不重复提交本机 outstanding job
- [ ] Markdown 导入创建新 revision，保留原文，不覆盖旧定稿
- [ ] finalize 只在四制品完成且无 active job 时成功；旧版本 immutable/superseded 可查
- [ ] 报告业务线路由只能取服务端授权集合，伪造业务线被拒绝

## F. 制品与视觉

- [ ] 打开 `samples/2026-07-ai-lab-monthly-report.json`，字段顺序与类型稳定
- [ ] 打开 Markdown，表格、分组、图表数据和空态可读，无结构注入
- [ ] 解包/打开 DOCX：中文字体、15/12/10.5/8.5pt、表头重复、表格对齐、标题 keep-next 正确
- [ ] 渲染 PDF 页面：标题、中文、表格、分页清晰，无截断；PDF 由真实 LibreOffice 转换
- [ ] 捕获 Dashboard 与模板设计器桌面/窄屏截图，确认 loading/error/empty/permission 状态
- [ ] 报告响应 `private, no-store`、`Pragma: no-cache`、`nosniff`；文件名与路径不泄露服务器目录

## G. 备份与安全交付

- [ ] 轮换上游默认 admin 密码、`TOKEN_SECRET`、MySQL/Redis/Druid 凭据
- [ ] 演示用户先设置独立密码再启用；未启用账号不能登录
- [ ] 完成数据库与报告归档目录的一致性备份/恢复演练
- [ ] Linux 内核/架构/seccomp 满足 pidfd；不允许 raw PID kill fallback
- [ ] 报告输出、临时目录仅服务账号可读写；Web 服务器不直接暴露目录
- [ ] 最终 `git status --short` 为空，独立 code/spec review 无 Critical/Important

## H. 本地验收记录（2026-08-09，Asia/Shanghai）

Task17 基线提交：`223cd8dabc9e606e44789c78bf80483d96ef42eb`；本轮真实环境验收在 `e709d26` 基础上完成，修复与证据随本验收提交保存。

| 项目 | 命令/端口 | 结果 |
| --- | --- | --- |
| 自动化总门禁 | `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-project.ps1` | PASS：8/8 阶段；clean backend 500 tests / 0F / 0E / 2 个条件式 LibreOffice skip；SQL 20/59/48/5/6；10 个 Mapper XML；clean admin package；前端定向 lint 与生产 build；四制品检查。门禁显式 `clean`，不会执行已删除测试遗留的 class |
| MySQL | WSL Ubuntu，`127.0.0.1:3306` | PASS：MySQL 8.0.46；演示库按 `ry_20240629.sql` → `quartz.sql` → `ailab.sql` 初始化；另用隔离库/用户执行 Mapper IT |
| Redis | WSL Ubuntu，`127.0.0.1:6379` | PASS：Redis 7.0.15，后端真实连接成功 |
| 后端/前端 | `127.0.0.1:8080` / `127.0.0.1:1024` | PASS：真实 MySQL/Redis 上启动并完成浏览器验收；验收结束后已停止两个进程，端口释放 |
| 真实 Mapper IT | `mvn -pl ruoyi-admin -am -Dspring.profiles.active=lab-it -Dtest=LabMapperMySqlIT -Dsurefire.failIfNoSpecifiedTests=false test` | PASS：隔离 MySQL 8 数据库，20 tests / 0 failures / 0 errors / 0 skipped，14.33 秒；覆盖真实 Mapper、迁移重放、`report_no`/家族唯一约束和生命周期路径 |
| LibreOffice/PDF | WSL `/usr/bin/libreoffice`；Poppler 120dpi 渲染 | PASS：tracked PDF 为真实 LibreOffice 输出，单页 Letter，中文/表格/图表完整且无裁切；Windows PATH 无 LibreOffice，因此 Windows 单测有 2 个 capability skip |
| 浏览器业务路径 | 真实 manager 登录；Dashboard/任务/成员/报告/模板 | PASS：Dashboard 指标与钻取、5 条任务、6 名成员、2026-07 已定稿报告及四制品、模板 6 个 typed section 均由真实 API 加载；桌面/窄屏无控制台错误；报告历史响应含 `private, no-store`、`no-cache`、`nosniff`；验收账号已恢复停用 |

浏览器截图保存在本地忽略目录 `output/playwright/`：`dashboard-desktop.png`、`dashboard-narrow.png`、`template-desktop.png`、`template-narrow.png`。验收过程中发现并修复了成员列表与报告历史在权限查询前被 PageHelper 提前消费的问题；修复均有回归测试。数据库/Redis 服务保留供后续复验，应用进程与临时浏览器会话均已关闭。
