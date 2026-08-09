# 人工智能实验室管理系统部署说明

## 1. 运行基线

| 组件 | 要求 | 说明 |
| --- | --- | --- |
| JDK | 8（生产基线；构建也兼容更高版本） | Task11 的进程树实现已用 Temurin 8 验证，class major version 为 52 |
| Maven | 3.8+ | 根 POM 固定 Surefire 3.2.5，避免旧 Maven 静默跳过 JUnit 5 |
| Node.js / npm | Node 16 或 18，npm 8+ | 前端为 Vue 2 / Vue CLI 4；使用锁定依赖，不在生产机升级依赖 |
| MySQL | 8.0，InnoDB，`utf8mb4` | 事务隔离使用 MySQL 默认 RR，必须启用严格模式 |
| Redis | 6+ | 用于登录态、缓存和报告任务短租约；报告任务真相仍在 MySQL |
| LibreOffice | 7.x，安装 Writer | 仅 PDF 需要；JSON/Markdown/DOCX 不依赖 LibreOffice |

Linux 报告节点只支持 `x86_64` 或 `aarch64`，内核必须为 **5.3 或更新版本**。容器 seccomp 必须允许 `pidfd_open` 与 `pidfd_send_signal`。PDF 转换在无法获得稳定 pidfd lease 时会有意 fail closed；禁止改成裸 PID `kill` 或用 shell 包装 LibreOffice。Windows 使用稳定进程 HANDLE，并在终止前校验进程创建时间。

## 2. 初始化顺序

1. 创建数据库：`CREATE DATABASE ry-vue CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`。
2. 以同一 MySQL 8 连接、按顺序执行：
   1. `sql/ry_20240629.sql`
   2. `sql/quartz.sql`
   3. `sql/ailab.sql`
3. `ailab.sql` 可重复执行，用于旧库补列、回填报告快照字段和刷新演示数据；执行前仍应完成备份。
4. 启动 Redis，确认应用账号可以执行普通 String/TTL 操作。
5. 创建报告归档目录与临时目录，并只授予后端服务账号读写权限。
6. 设置环境变量后启动后端；后端健康后再部署 `ruoyi-ui/dist`。

不要把 `application-demo.yml` 当作生产密钥文件。它只给出可覆盖的本地路径和开关，数据库、Redis、token 与 Druid 凭据全部由环境变量注入。
`TOKEN_SECRET` 没有代码内 fallback；未提供时应用应拒绝启动。Swagger 与 Druid 监控默认关闭，只在受控环境显式开启。

## 3. 关键环境变量

| 环境变量 | 示例 | 生产要求 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `demo` / `druid` | demo profile group 会同时加载 druid |
| `MYSQL_URL` | `jdbc:mysql://db:3306/ry-vue?...` | 使用 TLS 或受信内网，时区固定 `GMT+8` |
| `MYSQL_USERNAME` / `MYSQL_PASSWORD` | `ailab_app` / secret | 最小权限账号，不使用 root |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | `redis` / `6379` / secret | Redis 不得暴露公网 |
| `TOKEN_SECRET` | 至少 32 字节随机值 | 必填、无默认值；轮换会使旧 token 失效 |
| `LOG_PATH` | `D:/ailab/runtime/logs` 或 `/srv/ailab/runtime/logs` | 绝对路径、启动前创建并只授予服务账号写权限 |
| `RUOYI_PROFILE` | `D:/ailab/upload` 或 `/srv/ailab/upload` | 绝对路径、服务账号专用 |
| `LAB_REPORT_OUTPUT_DIRECTORY` | `/srv/ailab/reports` | 持久盘；备份范围 |
| `LAB_REPORT_TEMP_DIRECTORY` | `/srv/ailab/reports/tmp` | 与归档同一信任边界，不对外服务 |
| `LAB_LIBREOFFICE_EXECUTABLE` | `/usr/lib/libreoffice/program/oosplash` | Linux 使用真实启动器而非 `/usr/bin/soffice` shell 包装；容器镜像必须含 Writer |
| `LAB_REPORT_CONVERSION_TIMEOUT_SECONDS` | `120` | 不建议低于 30 秒 |
| `LAB_REPORT_MAX_UPLOAD_SIZE_BYTES` | `52428800` | 最终制品硬上限，接口另有更小类型上限 |
| `DRUID_STAT_ENABLED` | `false` | 生产默认关闭；开启时同时设置 allow、用户名、强密码 |

## 4. 启动示例

### Windows PowerShell

```powershell
$env:SPRING_PROFILES_ACTIVE = 'demo'
$env:MYSQL_URL = 'jdbc:mysql://127.0.0.1:3306/ry-vue?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true'
$env:MYSQL_USERNAME = 'ailab_app'
$env:MYSQL_PASSWORD = '<从安全存储注入>'
$env:REDIS_HOST = '127.0.0.1'
$env:TOKEN_SECRET = '<至少32字节随机值>'
$env:DEMO_RUNTIME_ROOT = 'D:/ailab/runtime'
$env:LOG_PATH = 'D:/ailab/runtime/logs'
$env:LAB_LIBREOFFICE_EXECUTABLE = 'C:/Program Files/LibreOffice/program/soffice.exe'
java -jar ruoyi-admin/target/ruoyi-admin.jar
```

### Linux systemd / container entrypoint

```bash
export SPRING_PROFILES_ACTIVE=demo
export MYSQL_URL='jdbc:mysql://mysql:3306/ry-vue?useUnicode=true&characterEncoding=utf8&useSSL=true&serverTimezone=GMT%2B8'
export MYSQL_USERNAME=ailab_app
export MYSQL_PASSWORD="$(cat /run/secrets/mysql_password)"
export REDIS_HOST=redis
export REDIS_PASSWORD="$(cat /run/secrets/redis_password)"
export TOKEN_SECRET="$(cat /run/secrets/token_secret)"
export DEMO_RUNTIME_ROOT=/srv/ailab/runtime
export LOG_PATH=/srv/ailab/runtime/logs
export LAB_LIBREOFFICE_EXECUTABLE=/usr/lib/libreoffice/program/oosplash
exec /usr/lib/jvm/java-8/bin/java -jar /opt/ailab/ruoyi-admin.jar
```

容器需挂载 `/srv/ailab/runtime` 持久卷，限制服务账号权限，并在 seccomp 中显式放行 `pidfd_open`、`pidfd_send_signal`。不要授予容器特权模式来规避错误配置。

## 5. 首次登录与权限

- 上游 RuoYi 基础 SQL 带有默认管理员账号；在开放网络前立即轮换 `admin` 密码、token secret 与 Druid 凭据。
- `ailab.sql` 的六个演示用户均为禁用状态，注释明确要求管理员先设置独立强密码，再按需启用；不要复用 seed 中的哈希。
- 角色权限由 `lab_manager`、`lab_lead`、`lab_member` 菜单授权控制。敏感绩效/报告还要求 `lab:report:sensitive`，下载时会重新读取实时权限，撤权后不能继续下载历史敏感制品。
- 报告、模板和绩效写接口同时执行对象级校验；不得仅依赖前端按钮隐藏。

## 6. 备份、恢复与运维

### 一致性备份

1. 暂停新报告生成和 period close；等待当前报告 job 终态，或记录所有 `QUEUED/RUNNING` job。
2. 使用 `mysqldump --single-transaction --routines --triggers` 备份数据库。
3. 在同一变更窗口快照 `LAB_REPORT_OUTPUT_DIRECTORY`，保留目录权限与相对路径。
4. Redis 只保存短租约/缓存，不是报告事实来源；仍应按组织登录态恢复策略备份。
5. 恢复时先恢复数据库，再恢复归档目录，最后启动应用。stale recovery 会重新领取未完成 job，run token fencing 会阻止旧 worker 写入。

### 报告失败与重试

- JSON/Markdown/Word/PDF 独立记录状态。成功的上游制品不会因下游失败被删除。
- 使用报告中心的定向重试，只重跑失败制品；PDF 会复用已成功的 Word。
- 若 LibreOffice 不可用，PDF 标记可重试失败，JSON/Markdown/DOCX 仍可下载。修复可执行路径、目录权限或 pidfd/seccomp 后再重试。
- 不要手工改 `json_path/word_path/pdf_path`。制品发布使用 per-run immutable 目录与数据库 run token fence。

### 周期重开

- 仅拥有 `lab:perf:reopen` 的 manager 可以重开，必须填写原因。
- 重开会使原评分/报告历史仍保持不可变，同时允许新修订；不要直接更新 `lab_period_close` 或覆盖定稿报告。
- 重开前确认纠正证据已提交，并在操作日志中保留审批依据。

## 7. 故障排查

| 症状 | 检查与处理 |
| --- | --- |
| `Connection refused :3306` | MySQL 是否监听、库名/账号、容器网络；真实 Mapper IT 只有服务可用时才执行 |
| Redis 登录/租约失败 | Redis 密码、ACL、时钟、网络；不要把租约失败当成 job 成功 |
| PDF `executable unavailable` | `LAB_LIBREOFFICE_EXECUTABLE` 是否为可执行文件，服务账号是否可启动 |
| PDF `stable process tracking unavailable` | Linux 内核、CPU 架构、seccomp 的 pidfd syscalls；必须修环境，不能退化为裸 PID kill |
| PDF timeout / 临时目录残留 | 查看有界错误摘要和 Quartz cleanup；检查 Writer 字体、profile 目录权限、磁盘空间 |
| 中文字体替换 | Windows 安装 Microsoft YaHei；Linux 安装经许可的 CJK 字体并在模板 style 中使用可用字体 |
| 报告一直 `QUEUED/RUNNING` | 检查 Quartz `labScheduleTask.recoverReportJobs()`、Redis 与数据库；恢复器有全局容量和 keyset 背压 |
| 403 | 核对菜单权限、业务线/本人对象范围、敏感权限实时状态；不要临时扩大角色 |

部署后执行 `scripts/verify-project.ps1`，再按 [验收清单](acceptance-checklist.md) 完成真实 MySQL/Redis、浏览器和制品视觉验收。
