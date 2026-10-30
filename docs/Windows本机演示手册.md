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
# 演示当天第一件事：一键总验（八步：单测 + 自检 + 文档一致性 + 演练 + 界面路由 + 前后端契约 + 审计留痕 + 计费一致）
pwsh -File scripts/demo/verify-all.ps1

# 只想快速看环境（跳过单测，实测 18 秒左右）
pwsh -File scripts/demo/verify-all.ps1 -SkipTests

# 或者只跑自检这一项
pwsh -File scripts/demo/demo-preflight.ps1

# 启动（已有 jar 时加 -SkipBuild 跳过构建；数据不想重建可加 -SkipDataInit）
pwsh -File scripts/demo/start-demo-stack.ps1 -SkipBuild

# 演示日前一天没刷新过数据时：加 -RefreshDemoData 把演示数据对齐到当天
# （启动时会打印「演示数据最新日期」，若早于昨天会给出黄色警告）
pwsh -File scripts/demo/start-demo-stack.ps1 -SkipBuild -SkipDataInit -RefreshDemoData

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

**9 问给出带数字的回答，1 问说明原因（Q10）。**

> ⚠️ **本节不写具体金额。** 演示数据每次 `-RefreshDemoData` 都会重算，写死的数字必然过期
> （2026-10-27 刷新后，原先记录的 Q1 `¥104,669.10`、Q4 `¥36,321.95`、Q8 `¥91,528.74`
> 全部与实际不符——产品本身没问题，是我们把某一版的数值抄进了文档）。
> **要看当前值，跑 `ask-demo-questions.ps1` 读它生成的报告**，那份是当场算出来的。

| # | 预期形状 | 检查点 |
| --- | --- | --- |
| Q1 | 1 行 | 单个金额（9 月销售额） |
| Q2 | 4 行 | 4 个商品名 + 各自缺口数量 |
| Q3 | 11 行 | 按销售额降序，首行为金额最大者 |
| Q4 | 1 行 | 单个金额（华东 9 月销售额，应小于 Q1） |
| Q5 | 3 行 | 三个渠道各一单量 |
| Q6 | 12 行 | 首行为毛利率最高的商品 |
| Q7 | 1 行 | 客户数 |
| Q8 | 1 行 | 单个金额（10 月销售额） |
| Q9 | 1 行 | **行数 1 但内容仍是拒答**：聚合无匹配时返回一行 NULL，属正常 |
| Q10 | 0 行 | 拒答，客户表无评分字段 |

> ⚠️ 早期版本的脚本把「答得出／答不了」写死在题目清单里，换数据集后报告会与事实矛盾。**现已改为按实际返回判定**：汇总行会分别给出「有数据／明确拒答／空结果／失败」，并额外标注「返回行数>0 但属拒答」（聚合查询无匹配时返回一行 NULL，正常现象）。切换数据集后直接重跑即可，不需要改脚本。

报告默认写到 `.local-private/demo/demo-10q-<租户>-<时间戳>.md`（如 `demo-10q-demo_sales-20261027-065854.md`），
包含每问的返回行数、回答文本与实际执行的 SQL，可直接作为演示留痕。
**文件名带租户与时间戳**：此前是固定名 `demo-10q-report.md`，换个租户试跑会把演示报告覆盖掉。
脚本在有「失败」问题时返回非零退出码，可作为门禁；空结果与拒答属合法结果，不判失败但会提示检查数据。

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

### 4.5 演示账号能看到哪些页面（权限决定，不是功能缺失）

2026-10-27 首次用浏览器看界面时发现：`demo_admin` 登录后侧边栏**只有 3 项**
（智能问答、问答历史、管理后台），仪表盘/数字员工/语义字典等页面都看不到。

原因：权限 = **角色权限 ∩ 租户权限池**，两者默认都只预置了 13 条（问答 + 组织管理）。
`provision-demo.ps1` 现在最后一步会调 `grant-demo-permissions.ps1` 把它们补齐到全部权限（幂等）。

- 补完后重新登录：菜单 **25 个节点**（4 个顶级 + 管理后台下 21 个）；
- 这是**演示环境的便利设置**，真实客户应按最小权限发放，不要照抄；
- 改完权限必须**重新登录**——权限在登录时解析进令牌。

## 5. 换成真实模型（演示前必做，否则展示的是离线桩）

离线桩只保证「链路通、形态对」，回答文本是模板拼装。对客户演示时应切换到真实模型：

```powershell
pwsh -File scripts/demo/provision-demo.ps1 `
  -ModelProvider 'DASHSCOPE' `
  -ModelName 'qwen-plus' `
  -ModelVersion '2026-10' `
  -ModelBaseUrl 'https://dashscope.aliyuncs.com/compatible-mode/v1' `
  -ModelApiKey $env:LINGSQL_DEMO_MODEL_API_KEY
```

该脚本会创建或更新模型配置 → 实际调用探测（必须返回 `HEALTHY`）→ 启用 → 授权给演示租户。

> 注意顺序：**更新模型配置会把启用状态重置**，因此脚本里固定是「PUT → probe → enable → 授权」，少一步就会出现「当前租户没有已启用且验证健康的授权模型」。

> 另注：`-ModelBaseUrl` 要填到**能拼出 `/v1/chat/completions` 的那一层**（上面示例已含 `/v1`；各家路径不同，以供应商的 OpenAI 兼容文档为准）。填错时的表现是 `provision-demo.ps1` 抛「模型探测未通过」，而不是 URL 解析错误——别被这个提示误导去查别的地方。

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
| `scripts/demo/demo-preflight.ps1` | 演示前自检（端口／就绪／数据新鲜度／语义字典／cron／实问两题） |
| `scripts/demo/docs-consistency-check.ps1` | 文档一致性检查 21 项（常量 vs 源码、租户／员工清单 vs 数据库、索引完整性、**markdown 结构：表格是否被空行截断、章节编号是否重复**） |
| `scripts/demo/verify-all.ps1` | 一键总验八步：单测 → 演示前自检 → 文档一致性 → 黄金问题演练 → 界面路由 → 前后端契约 → 审计留痕 → 计费与调用一致，输出总表。**演示前跑 `-SkipTests` 版（实测 14.8 秒），全绿再开讲** |
| `scripts/demo/morning-check.ps1` | 每日巡检：今天跑没跑、推没推出去、数字对不对 |
| `scripts/demo/ui-routes-check.ps1` | 界面路由可用性：前端每条路由都要返回界面，不能是 JSON |
| `scripts/demo/api-contract-check.ps1` | 前后端契约：前端引用的接口路径，后端是否都有映射 |
| `scripts/demo/grant-demo-permissions.ps1` | 给演示租户补齐权限池与角色权限（否则菜单只有 3 项） |
| `scripts/demo/run-sales-package-validator.ps1` | 在 Windows 上跑「可签约销售包」校验器（`bash` 包壳跑不了时用它；jar 在 `.local-private/commercial/target/`）。**jar 可从源码重建**：`cd .local-private/commercial; mvn -B -o -DskipTests package`，2026-10-30 实测离线 6.1 秒，重建前后对同一模板的拒答完全一致 |
| `scripts/demo/install-redis-windows.ps1` | 便携版 Redis 下载解压 |
| `scripts/demo/checks-self-test.ps1` | 检查脚本自检：临时注入可控故障，证明「审计留痕」「计费一致」「界面路由」「前后端契约」「文档步数」「文档结构」「构建对齐」「日报 cron」「租户隔离」**九条检查真的会红**；跑完自动还原（含 jar 时间戳与文件哈希校验）、清理探针 |
| `scripts/demo/billing-call-consistency-check.ps1` | 计费与调用一致性：成功的提问必须付了架构师+分析师两次钱，失败的至少一次，且不留非终态 |
| `scripts/demo/audit-trail-check.ps1` | 审计留痕检查：每条提问都必须有 request_id/问题/租户/用户，成功的必须有实际执行的 SQL、失败的必须有原因 |
| `scripts/demo/suite-freshness-check.ps1` | 校验 40 题黄金问题 Suite 的期望值是否还对得上当前数据（`-Refresh` 按当前数据重算，改前自动备份）。**演示数据一刷新，Suite 就过期**，拿过期 Suite 做复测会把"量具坏了"看成"产品坏了" |
| `scripts/demo/seed/generate-demo-data.mjs` | 确定性演示数据生成器 |
| `scripts/demo/stub/ten-question-stub.mjs` | 确定性模型桩 |
| `sql/demo_semantic_lexicon.sql` | 演示租户语义字典（21 条术语），由 init 脚本自动导入 |
| `sql/demo_sales_inventory_refresh.sql` | 刷新数据集（覆盖到当天），用于演示昨日日报 |
| `.local-private/demo/logs/` | 四个组件的 stdout／stderr |
| `.local-private/demo/pids/` | 进程 PID（停止脚本依赖） |

---

## 7. 每天早上的三分钟巡检（上线后每天做）

> 演示环境用不到；**客户上线后每天 08:05 左右做一遍**，比事后解释便宜得多。

**① 今天到底跑没跑**

```sql
SELECT id, employee_id, trigger_source, success,
       DATE_FORMAT(create_time,'%H:%i:%s') AS 执行时间,
       result_rows, push_delivery_status
FROM t_lingsql_engine_digital_employee_run_log
WHERE create_time >= CURDATE() ORDER BY id;
```

- 应看到：三条日报各一条 `SCHEDULED`、`success=1`
- 时间应在 08:00–08:15 之间（轮询 15 秒，实测偏差约 13 秒）
- **没有记录** → 员工被停用或 cron 被改（查 `schedule_enabled` / `schedule_cron`）

**② 推送到底有没有发出去**

看上一行的 `push_delivery_status`：

| 值 | 含义 |
| --- | --- |
| `1` | 成功，明细里有每个通道的 HTTP 状态 |
| `0` | 尝试了但失败，`push_delivery_detail` 里写明哪个通道挂了 |
| `NULL` | **根本没尝试**——通常是没有配置任何通道（`系统告警总开关未启用`） |

> **手工执行不会推送，这是设计如此**（2026-10-27 核对代码：`sendScheduledAlertIfNecessary`
> 与 `sendScheduledResultIfNecessary` 都显式要求 `triggerSource == SCHEDULED`）。
> 原因是手工执行用于调试，不应该把测试内容发到客户群里。
> 实测也一致：`MANUAL` 的 `push_delivery_status` 恒为 `NULL`，`SCHEDULED` 才会出现 0 或 1。
>
> **对演示的影响**：想真的让客户手机响，只有两条路——① 把 cron 设到下一分钟，等一轮（约 15 秒后）；
> ② 更稳的做法：提前抓一条真实推送原文，现场直接发给他，**不要当场等**。

**③ 数字对不对（抽一条）**

挑一条日报，用同一口径直接查库比对。不一致就问清楚口径，**不要自己改 SQL 去凑**。

一条命令把三步都跑完：

```powershell
pwsh -File scripts/demo/morning-check.ps1            # 默认 demo_sales
pwsh -File scripts/demo/morning-check.ps1 -TenantId <租户>
```

> 三条都过 → 当天不用管。任一条不对 → 把 run log 的 `id` 记下来，那是唯一能追溯的凭据。

### 7.1 模型短暂故障会触发熔断（实测 5 分 38 秒后自愈）

2026-10-27 07:33:50 我为了验证"端口检查会不会红"，把模型桩停了几秒。系统按设计做了这些事：

| 时间 | 事件 |
| --- | --- |
| 07:33:50 | 连续 3 次调用失败 → **模型被熔断**：`is_active=0`、`fuse_time` 写入、`fuse_reason=失败次数达到阈值: 3` |
| 07:33:50–07:39:28 | 租户的所有提问返回 **`code=200` 但 `rows=0`**（界面上是"没有查到数据"，不是报错） |
| 07:39:28 | 冷却（`cooldown_seconds=300`）到期后，`ModelRecoveryScheduler` 半开探测成功 → `is_active=1`，自动恢复 |

**要看懂的两件事**：

1. **恢复是自动的，但不是立刻的**：冷却 300 秒 + 调度器 60 秒一轮，最坏约 6 分钟。
   这段时间客户提问会"查不到数据"，容易被误认为"你们系统没数"。
2. **熔断期间返回的是 200 而不是报错**：所以只看 HTTP 状态码的监控发现不了。
   判断方式是看返回行数或 `last_probe_status`／`is_active`，别只看状态码。

**手工恢复**（不想等）：

```powershell
POST /api/v1/admin/config/model/1/probe     # 探测，成功会清除熔断
```


---

## 8. 已知边界

1. **离线桩不是智能能力。** 它按问题命中固定 SQL，回答基于真实结果拼装；换真实模型后才具备推理与兜底判断。
2. **聚合健康检查可能返回 503，这是设计使然而非故障。** 常见原因是 `physicalModelBilling` 组件：只要有一次物理模型调用停在 `DISPATCHED` 且没拿到可信用量（例如演示中途强杀过进程、模型超时），它就会被持久化为 `UNKNOWN`，计费健康随即 `DOWN`。应用可用性请以 `/actuator/health/readiness` 为准；演示脚本显式打开了健康明细（`MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`），`/actuator/health` 会直接给出是哪一项在报 DOWN。清理路径是管理端 UNKNOWN 裁决（`GET /api/v1/admin/billing/invocations/unknown`），不是删表。
3. **演示数据重建会清空 `demo_*` 五张表。** 这是刻意的：保证每次演示的数字一致。如果有别的会话／环境也在用 `lingsql_demo_sales`，请先确认再执行。
4. **端口独占。** 3407／6380／18082／8000 被本演示占用；同时开两套会互相踩。
5. **破坏性请求不会在"语言层"被拒绝，保护在 SQL 层。** 2026-10-31 实测：问「删除所有演示订单记录」，
   离线桩生成的是 `SELECT ... WHERE 1 = 0`，系统按"没查到数据"正常返回（`code=200`、带 sql、rows=0）——
   **不会提示"你不能删除"**。真正的保障是 `SqlSecurityChecker`：任何非 SELECT 语句一律 `SECURITY_WRITE_BLOCKED`
   （`SqlGuardrailTest`、`SqlSecurityAllowlistTest` 守着）。对外解释时要说清是"只能查、不能改"，
   不要说成"它会识别并拒绝你的危险意图"——后者目前没有实现。
5. **不覆盖生产约束。** 生产仍需要 Flyway 迁移任务、`lingsql_app` 最小权限账号、Redis 口令、JWT／加密密钥托管；本手册的 root 空口令只允许出现在本机演示实例上。
6. **日报推送只验证过通用 Webhook 通道。** 钉钉／企微两种 markdown payload 由单元测试保证，但**未做过真实群机器人投递**。向客户演示推送前，务必先用真实群机器人跑一次 `push_delivery_status=1` 的成功投递。
7. **数字员工的数据租户由员工自身的 `tenantId` 决定**（`run` 与 `runScheduled` 都会显式建立租户上下文）。因此不要依赖管理员请求头去切换数据源；验收日报请走定时调度路径，不要用 `/admin/digital-employees/{id}/run` 代替。

---

## 9. 排错

| 现象 | 处理 |
| --- | --- |
| 启动卡在「应用未在 180 秒内就绪」 | 脚本等的是 `/actuator/health/readiness`。**首次冷启动可能真的超过 180 秒**，脚本报错后应用往往仍在启动：先直接访问 `http://127.0.0.1:8000/actuator/health/readiness`，200 就说明只是超时误报。不是超时的话再看 `.local-private/demo/logs/app.err.log`（多半是 3407／6380 口令或端口不一致） |
| 提问返回「当前租户没有已启用且验证健康的授权模型」 | 重跑 `provision-demo.ps1`（它会重新 probe + enable） |
| 应用返回 401／会话立即失效 | 检查 Redis 是否在 6380 上运行且口令与 `demo-secrets.json` 一致 |
| `/actuator/health` 返回 503 但应用能用 | `physicalModelBilling` 有 `UNKNOWN` 记录时聚合健康会 DOWN。**以 `/actuator/health/readiness` 为准**；清理走管理端 UNKNOWN 裁决，不要删表 |
| **四个端口突然全掉** | 极可能是**终止了后台的启动任务**。`start-demo-stack.ps1` 把 MySQL／Redis／桩／应用都挂在自己的进程树下，在 Windows 上终止该任务会**连带杀掉全部子进程**（2026-10-30 实测踩过一次）。要停栈请用 `stop-demo-stack.ps1`，不要杀启动脚本 |
| 启动脚本打印完「演示栈已就绪」后进程不退出 | **已知现象，不是故障**。脚本用 `Start-Process -RedirectStandardOutput/Error` 启动四个子进程，重定向管道由子进程持有，父进程退出时会等管道清理，于是挂住。2026-10-30 试过在脚本末尾加 `[Environment]::Exit(0)`，**实测无效**（进程仍存活 10 分钟以上），已撤销。判断栈是否就绪请看 `readiness`，不要看脚本有没有退出；**更不要杀这个进程**（会连带杀掉整栈） |
| 数字员工执行返回 500「模型已返回但资金结算失败」 | 该缺陷已修：`transitionToDispatched` 必须带 `flushAutomatically` + `clearAutomatically`，否则结算会读到仍是 `RESERVED` 的旧实体。若复现说明修复被回退 |
| 日报定时执行 `success=1` 但没收到推送 | 依次查：员工 `pushEnabled=1` 且 `pushChannels` 非空 → 应用启动时设了 `LINGSQL_ALERT_WEBHOOK_URL` → `t_lingsql_engine_digital_employee_run_log` 的 `push_delivery_detail` 里各通道结果 |
| 登录突然返回 429「请求过于频繁」 | 登录限流默认 **20 次/分钟，触发后封 10 分钟**（响应头 `Retry-After: 600`）。本机的自检、演练、巡检、检查脚本自检会在几分钟内登录很多次，很容易踩线——2026-10-31 实测把 `demo_admin` 锁死 10 分钟，preflight 因此误报"菜单只有 1 项"。**演示用的启动脚本已把 `LINGSQL_RATE_LIMIT_LOGIN_MAX_REQUESTS` 放宽到 300**；真被锁了，删 Redis 键 `lingsql:ratelimit:login:block:<ip>` 可立即解封。生产不要照抄放宽值。**自检已能识别**：被限流时 preflight 会直接报"被登录限流（HTTP 429）"并跳过后续检查，不再把限流误报成"菜单只有 1 项" |
| 提问返回 403「今日调用次数已达上限」 | **这是每日调用额度，不是权限问题**。限额来自计费策略的等级表（默认约 100 次/天），计数口径是**当日 `sql_execute_log` 行数**——自检、演练、反复提问都会消耗它。演示环境已在开户脚本 `[8/8]` 写入策略版本 `DEMO_DAY1`（5000 次/天）；若被改回默认，会看到这个 403。也可临时插入同名策略行覆盖 `service_level_limit`（**策略实时生效，不需要重启**，2026-10-31 实测）。`preflight` 的实问失败时会直接打印服务端的 `message`，先看它再猜原因 |
| 提问返回 200 但一行都没有 | **先看是不是模型熔断**（`is_active=0`）：熔断期间问什么都返回空，看起来像"查不到数据"。`preflight` 现在会直接报「模型熔断中（is_active=0，原因=…）」并跳过实问；恢复用 `POST /api/v1/admin/config/model/1/probe` 或等冷却 |
| `scripts/ops/*.sh` 验收脚本在本机跑不了 | 它们需要 **bash + jq + curl**。本机 `bash.exe` 是 WSL 存根且**未安装发行版**，`jq` 也没有（2026-10-30 实测）——这是环境限制，不是脚本坏了。要么装 WSL 发行版 + jq，要么到 Linux 服务器上跑。**本机能跑的只有** `scripts/demo/verify-all.ps1` 那一套，它**不能**替代 40 题正式验收 |
| 想彻底重来 | `pwsh -File scripts/demo/stop-demo-stack.ps1`，删除 `.local-private/demo/mysql-data` 与 `demo-secrets.json`，再执行第 3 节 |
| 端口被占用 | `Get-NetTCPConnection -LocalPort 3407` 查占用进程；确认不是系统的 3306/6379 |
