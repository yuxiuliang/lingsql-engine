# Windows 本机演示手册（不依赖 Docker）

本手册用于在一台**没有 Docker、没有 WSL 发行版**的 Windows 机器上，把 LingSQL 从零跑到「能演示 10 个问题」的状态。

目标不是替代生产部署，而是解决一件事：**让演示成本从「要准备环境」降到「双击一条命令」**。生产部署仍以 `docker-compose.yml` + `scripts/ops/flyway_migrate.sh` 为准。

---

## 1. 这套演示由什么组成

| 组件 | 端口 | 来源 | 说明 |
| --- | --- | --- | --- |
| MySQL | 3407 | 本机已安装的 `mysqld.exe` | 独立数据目录 `.local-private/demo/mysql-data`，root 空口令，仅监听 127.0.0.1 |
| Redis | 6380 | 便携版 `redis-server.exe` | 由 `install-redis-windows.ps1` 下载解压，不装服务、不写注册表 |
| 模型桩 | 18082 | `scripts/demo/stub/ten-question-stub.mjs` | OpenAI 兼容的**确定性离线桩**，不联网 |
| 应用 | 8000 | `target/lingsql-engine-*.jar` | `prod` profile，配置全部来自环境变量 |

两个数据库：

- `lingsql_demo_control`：应用自身的控制库（51 张表，按 `src/main/resources/db/migration` 的顺序执行）；
- `lingsql_demo_sales`：演示业务库（客户／商品／订单／明细／库存快照），只授权给只读账号。

演示数据有**两套，同一时刻只能加载一套**（都写 `demo_*` 五张表，后执行的会先 `DELETE` 再插入）：

| 数据集 | 如何加载 | 覆盖时间 | 实测规模 | 适用 |
| --- | --- | --- | --- | --- |
| **生成器数据集**（默认） | `init-demo-databases.ps1` 自动执行 `seed/generate-demo-data.mjs` | 固定 2026-07-01 起 92 天（7–9 月） | 600 张订单、1210 行明细、36 行库存快照，3 个商品低于安全库存 | 10 问多维度演示（按商品／地区／渠道） |
| **刷新数据集** | 手工执行 `sql/demo_sales_inventory_refresh.sql` | **相对当天**，最近 100 天 | 1201 张订单、12 个商品、12 行库存快照，4 个商品低于安全库存 | 经营日报（昨日快报／环比／滞销／异常单） |

两套数据互斥：跑一个会覆盖另一个，**切换后 10 问的数字全部改变**（见 §4）。`init-demo-databases.ps1` 会先导入 `sql/demo_sales_inventory.sql` 冻结种子，再被生成器数据集整体覆盖，因此 init 完成后的有效数据就是生成器数据集。

---

## 2. 前置条件

| 依赖 | 版本 | 检查命令 |
| --- | --- | --- |
| JDK | 25 | `java -version` |
| Maven | 3.9+ | `mvn -v` |
| Node.js | 18+ | `node -v` |
| MySQL 服务端程序 | 8.0+（本机已验证 9.5） | 默认取 `D:\Software\Tools\Database\mysql-8.4.2-winx64\bin`，可用 `-MysqlHome` 覆盖 |

> 不需要 Docker、不需要 Git Bash、不需要 WSL。`scripts/` 下原有的 43 个 `.sh` 运维脚本在这套演示里**一个都不需要执行**。

---

## 3. 首次准备（只做一次）

```powershell
# 1) 下载便携版 Redis 到 .local-private/demo/redis
pwsh -File scripts/demo/install-redis-windows.ps1

# 2) 启动整套演示栈（首次会构建 jar，耗时较长）
pwsh -File scripts/demo/start-demo-stack.ps1
```

启动脚本会按顺序完成：MySQL → 建库＋迁移＋演示数据 → Redis → 模型桩 → 应用 → 开户（租户／只读数据源／Schema 同步／模型探测与启用／租户模型授权）。

口令与端口在首次运行时随机生成并写入 `.local-private/demo/demo-secrets.json`（该目录已被 `.gitignore` 排除，**不要提交**）。

---

## 4. 日常使用

```powershell
# 启动（已有 jar 时加 -SkipBuild 跳过构建；数据不想重建可加 -SkipDataInit）
pwsh -File scripts/demo/start-demo-stack.ps1 -SkipBuild

# 跑 10 个演示问题，并生成一份证据报告
pwsh -File scripts/demo/ask-demo-questions.ps1

# 停止全部（只按 PID 文件停止自己拉起的进程，不动系统 MySQL 服务）
pwsh -File scripts/demo/stop-demo-stack.ps1
```

### 4.1 生成器数据集下的预期（默认）

**7 问「有数据」，3 问「明确拒答」。** 脚本按**实际返回**判定结果类型（有数据／明确拒答／空结果／失败），不再写死预期，因此换数据集无需改脚本。

| # | 问题 | 预期 |
| --- | --- | --- |
| Q1 | 2026 年 9 月已完成订单的销售额是多少？ | 1 行，给出金额 |
| Q2 | 哪些商品当前库存低于安全库存？ | 3 行，列出商品、仓库、缺口 |
| Q3 | 按商品统计 9 月销售额，从高到低排列。 | 12 行，给出前 5 名 |
| Q4 | 华东地区客户 9 月贡献了多少销售额？ | 1 行，给出金额 |
| Q5 | 各销售渠道的订单数量分别是多少？ | 3 行，给出各渠道单量 |
| Q6 | 哪个商品的毛利率最高？ | **12 行**（SQL 返回全部商品按毛利率降序），回答点名第一名 |
| Q7 | 2026 年 9 月有多少个客户下过单？ | 1 行，给出客户数 |
| Q8 | 2026 年 10 月的销售额是多少？ | 0 行，说明数据只到 9 月 30 日 |
| Q9 | 和 2025 年 9 月相比销售额增长了多少？ | 0 行，说明没有去年同期数据 |
| Q10 | 各客户的满意度评分是多少？ | 0 行，说明客户表没有评分字段 |

### 4.2 刷新数据集下的预期（换数据后必看）

执行 `sql/demo_sales_inventory_refresh.sql` 之后，**上表的行数与结论全部作废**，以本节为准：

```powershell
$mysql = 'D:\Software\Tools\Database\mysql-8.4.2-winx64\bin\mysql.exe'
$file  = (Resolve-Path 'sql\demo_sales_inventory_refresh.sql').Path
& cmd /c "`"$mysql`" --host=127.0.0.1 --port=3407 --user=root --default-character-set=utf8mb4 --database=lingsql_demo_sales < `"$file`""
```

**9 问给出带数字的回答，1 问说明原因（Q10）。** 实测值（2026-10-25，可直接用于比对）：

| # | 返回行数 | 实测结果 |
| --- | --- | --- |
| Q1 | 1 | ¥104,669.10 |
| Q2 | 4 | 农夫山泉缺 254／可乐缺 200／红牛缺 138／混合坚果缺 90 |
| Q3 | 11 | 第一名电水壶 1.7L ¥54,020.04 |
| Q4 | 1 | ¥36,321.95 |
| Q5 | 3 | 线上 401 / 门店 400 / 批发 400 |
| Q6 | 12 | 牙膏 120g，37.6% |
| Q7 | 1 | 8 个客户 |
| Q8 | 1 | **¥91,528.74**（10 月已有数据，不再是「答不了」） |
| Q9 | 1 | 行数为 1 但**内容仍是拒答**：聚合查询无匹配时返回一行 NULL，属正常现象 |
| Q10 | 0 | 拒答，客户表无评分字段 |

> ⚠️ 早期版本的脚本把「答得出／答不了」写死在题目清单里，换数据集后报告会与事实矛盾。**现已改为按实际返回判定**：汇总行会分别给出「有数据／明确拒答／空结果／失败」，并额外标注「返回行数>0 但属拒答」（聚合查询无匹配时返回一行 NULL，正常现象）。切换数据集后直接重跑即可，不需要改脚本。

报告默认写到 `.local-private/demo/demo-10q-report.md`，包含每问的返回行数、回答文本与实际执行的 SQL，可直接作为演示留痕。

### 4.3 经营日报（定时推送）演示

10 问证明「能问」，日报证明「不用问」。日报走的是**数字员工 + 定时调度 + 告警通道**这条独立链路。

**第一步：启动应用时必须把 webhook 地址灌进环境变量。**
告警通道只由 `OpsAlertService` 的全局配置决定，不从租户配置读；没设就等于推送通道关闭，数字员工会执行成功但 `push_delivery_status` 为空。

```powershell
pwsh -File scripts/demo/start-demo-stack.ps1 -SkipBuild -SkipDataInit `
  -AlertWebhookUrl 'https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxxx'
```

> 演示用也可以指向本机任意 HTTP 监听（例如临时起一个 Node 小服务把请求落盘），先确认链路通，再换真实群机器人。

**第二步：建一条数字员工。** 关键字段：

| 字段 | 值 | 说明 |
| --- | --- | --- |
| `tenantId` | `demo_sales` | 决定查哪个库（执行时会显式建立该租户上下文） |
| `defaultQuestion` | 例如「昨天销售额、订单数和毛利分别是多少？」 | 用刷新数据集；生成器数据集没有「昨天」 |
| `scheduleEnabled` / `scheduleCron` | `1` / `0 * * * * ?` | 每分钟第 0 秒触发，便于演示 |
| `pushEnabled` / `pushChannels` | `1` / `webhook` | 通道名固定为 `webhook`、`dingtalk`、`wecom` |
| `pushMaxRows` | `3` | 预览行数上限，默认 3 |
| `pushTitleTemplate` | `【{scenario}】{executeTime}` | 可用占位符见下 |
| `pushMessageTemplate` | `{answer}` | 同上 |

可用占位符：`tenantId`、`employeeId`、`employeeName`、`scenario`、`question`、`answer`、`pushChannels`、`resultRows`、`executeTime`、`triggerSource`、`sql`。

> ⚠️ **没有「环比」这类占位符。** 默认模板只产出一行。想要多行带环比的日报，要么把问题问成带比较的形式（让 `{answer}` 自然包含），要么扩模板（需改代码）。

**第三步：验证投递成功。** 等一个 cron 周期（最长 75 秒），然后查：

```sql
SELECT id, tenant_id, trigger_source, success, result_rows,
       push_delivery_status, push_delivery_detail
FROM t_lingsql_engine_digital_employee_run_log
ORDER BY id DESC LIMIT 3;
```

判据：`trigger_source=SCHEDULED`、`success=1`、`result_rows>0`、`push_delivery_status=1`。

> 手工调 `/admin/digital-employees/{id}/run` 时**不要**指望推送——推送只在 `triggerSource=SCHEDULED` 分支发生。手工触发请显式传 `{"triggerSource":"SCHEDULED"}`，或直接等定时。

### 4.4 语义字典与界面就绪项（演示时该期待什么）

**语义字典**（演示租户 `demo_sales` 预置 21 条）

- 来源：`sql/demo_semantic_lexicon.sql`，`init-demo-databases.ps1` 会自动导入；
- 作用：被渲染成 `术语 => 映射` 逐行注入 SQL 架构师的提示词，让模型知道「毛利」对应
  `SUM(quantity * (sale_price - unit_cost))`，而不是靠列名猜；
- 查看：管理端语义配置页，或 `GET /api/v1/admin/config/semantic?tenantId=demo_sales`；
- **改完必须刷缓存**，否则要等缓存过期才生效：`POST /api/v1/admin/refresh-semantic/demo_sales`；
- 重置：重跑 `sql/demo_semantic_lexicon.sql` 后刷缓存。

> ⚠️ `mapping` 会被拼成 `SELECT <mapping> FROM dual` 做语法校验，因此只能写**表达式**
> （列引用或聚合）。写完整 `SELECT` 或 `WHERE` 谓词会以「映射表达式语法错误」被拒绝。
> ⚠️ 用离线桩演示时**看不到**字典的效果（桩的 SQL 是固定命中的），它的价值在真实模型下体现。

**界面上已经就绪的三处**（不必再解释"为什么是英文"）

| 项 | 现在的行为 |
| --- | --- |
| 结果表头、结果概览、图表标题 | 中文列名（商品名称／销售额／毛利率…），不再出现 `product_name` |
| 执行轨迹 | 默认折叠，只留「首轮完成」状态胶囊；**仅当发生自愈重试时才自动展开** |
| 错误提示 | 后端内部术语（账本／结算／数据源配置／物理调用）会被替换为面向客户的兜底文案 |

---

## 5. 换成真实模型（演示前必做，否则展示的是离线桩）

离线桩只保证「链路通、形态对」，回答文本是模板拼装。对客户演示时应切换到真实模型：

```powershell
pwsh -File scripts/demo/provision-demo.ps1 `
  -ModelProvider 'DASHSCOPE' `
  -ModelName 'qwen-plus' `
  -ModelVersion '2026-10' `
  -ModelBaseUrl 'https://dashscope.aliyuncs.com/compatible-mode' `
  -ModelApiKey $env:LINGSQL_DEMO_MODEL_API_KEY
```

该脚本会创建或更新模型配置 → 实际调用探测（必须返回 `HEALTHY`）→ 启用 → 授权给演示租户。

> 注意顺序：**更新模型配置会把启用状态重置**，因此脚本里固定是「PUT → probe → enable → 授权」，少一步就会出现「当前租户没有已启用且验证健康的授权模型」。

> 另注：`-ModelBaseUrl` 要按供应商的 OpenAI 兼容文档填到**能拼出 `/v1/chat/completions` 的那一层**（阿里云百炼一般是 `https://dashscope.aliyuncs.com/compatible-mode/v1`，上面示例少了 `/v1`，请以供应商文档为准）。填错时的表现是 `provision-demo.ps1` 抛「模型探测未通过」，而不是 URL 解析错误——别被这个提示误导去查别的地方。

切换后 10 个问题仍然可用，但 SQL 与回答由模型真实生成，Q8–Q10 也可能出现「模型坚持给一个错误答案」的情况——这正是需要演示时主动讲的部分。

---

## 6. 目录与文件

| 路径 | 作用 |
| --- | --- |
| `scripts/demo/start-demo-stack.ps1` | 一键启动（幂等，可重复执行） |
| `scripts/demo/stop-demo-stack.ps1` | 一键停止 |
| `scripts/demo/init-demo-databases.ps1` | 建库、迁移、演示数据、只读账号 |
| `scripts/demo/provision-demo.ps1` | 开户与模型授权（可直接切真实模型） |
| `scripts/demo/ask-demo-questions.ps1` | 10 问 + 证据报告 |
| `scripts/demo/install-redis-windows.ps1` | 便携版 Redis 下载解压 |
| `scripts/demo/seed/generate-demo-data.mjs` | 确定性演示数据生成器 |
| `scripts/demo/stub/ten-question-stub.mjs` | 确定性模型桩 |
| `sql/demo_semantic_lexicon.sql` | 演示租户语义字典（21 条术语），由 init 脚本自动导入 |
| `sql/demo_sales_inventory_refresh.sql` | 刷新数据集（覆盖到当天），用于演示昨日日报 |
| `.local-private/demo/logs/` | 四个组件的 stdout／stderr |
| `.local-private/demo/pids/` | 进程 PID（停止脚本依赖） |

---

## 7. 已知边界

1. **离线桩不是智能能力。** 它按问题命中固定 SQL，回答基于真实结果拼装；换真实模型后才具备推理与兜底判断。
2. **聚合健康检查可能返回 503，这是设计使然而非故障。** 常见原因是 `physicalModelBilling` 组件：只要有一次物理模型调用停在 `DISPATCHED` 且没拿到可信用量（例如演示中途强杀过进程、模型超时），它就会被持久化为 `UNKNOWN`，计费健康随即 `DOWN`。应用可用性请以 `/actuator/health/readiness` 为准；演示脚本显式打开了健康明细（`MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`），`/actuator/health` 会直接给出是哪一项在报 DOWN。清理路径是管理端 UNKNOWN 裁决（`GET /api/v1/admin/billing/invocations/unknown`），不是删表。
3. **演示数据重建会清空 `demo_*` 五张表。** 这是刻意的：保证每次演示的数字一致。如果有别的会话／环境也在用 `lingsql_demo_sales`，请先确认再执行。
4. **端口独占。** 3407／6380／18082／8000 被本演示占用；同时开两套会互相踩。
5. **不覆盖生产约束。** 生产仍需要 Flyway 迁移任务、`lingsql_app` 最小权限账号、Redis 口令、JWT／加密密钥托管；本手册的 root 空口令只允许出现在本机演示实例上。
6. **日报推送只验证过通用 Webhook 通道。** 钉钉／企微两种 markdown payload 由单元测试保证，但**未做过真实群机器人投递**。向客户演示推送前，务必先用真实群机器人跑一次 `push_delivery_status=1` 的成功投递。
7. **数字员工的数据租户由员工自身的 `tenantId` 决定**（`run` 与 `runScheduled` 都会显式建立租户上下文）。因此不要依赖管理员请求头去切换数据源；验收日报请走定时调度路径，不要用 `/admin/digital-employees/{id}/run` 代替。

---

## 8. 排错

| 现象 | 处理 |
| --- | --- |
| 启动卡在「应用未在 180 秒内就绪」 | 脚本等的是 `/actuator/health/readiness`。**首次冷启动可能真的超过 180 秒**，脚本报错后应用往往仍在启动：先直接访问 `http://127.0.0.1:8000/actuator/health/readiness`，200 就说明只是超时误报。不是超时的话再看 `.local-private/demo/logs/app.err.log`（多半是 3407／6380 口令或端口不一致） |
| 提问返回「当前租户没有已启用且验证健康的授权模型」 | 重跑 `provision-demo.ps1`（它会重新 probe + enable） |
| 应用返回 401／会话立即失效 | 检查 Redis 是否在 6380 上运行且口令与 `demo-secrets.json` 一致 |
| `/actuator/health` 返回 503 但应用能用 | `physicalModelBilling` 有 `UNKNOWN` 记录时聚合健康会 DOWN。**以 `/actuator/health/readiness` 为准**；清理走管理端 UNKNOWN 裁决，不要删表 |
| 数字员工执行返回 500「模型已返回但资金结算失败」 | 该缺陷已修：`transitionToDispatched` 必须带 `flushAutomatically` + `clearAutomatically`，否则结算会读到仍是 `RESERVED` 的旧实体。若复现说明修复被回退 |
| 日报定时执行 `success=1` 但没收到推送 | 依次查：员工 `pushEnabled=1` 且 `pushChannels` 非空 → 应用启动时设了 `LINGSQL_ALERT_WEBHOOK_URL` → `t_lingsql_engine_digital_employee_run_log` 的 `push_delivery_detail` 里各通道结果 |
| 想彻底重来 | `pwsh -File scripts/demo/stop-demo-stack.ps1`，删除 `.local-private/demo/mysql-data` 与 `demo-secrets.json`，再执行第 3 节 |
| 端口被占用 | `Get-NetTCPConnection -LocalPort 3407` 查占用进程；确认不是系统的 3306/6379 |
