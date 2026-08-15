# 语数 (LingSQL) - AI 驱动的多租户智能数据网关 🚀

**“让数据不仅能被看见，更能被听懂。”**

语数是一款基于 **Java 25** 开发、面向企业试点的 Text-to-SQL 自动化分析网关。它将 **大语言模型 (LLM)** 与 **关系型数据库** 组合为受控查询链路：SQL 架构师生成候选 SQL，合规审计员完成改写与安全校验，业务分析师只基于已审计结果生成答复。

---

## ✨ 核心特性

* **🛡️ 受控 SQL 安全链 (Security Guardrail)**：内置基于 JSqlParser 的 SQL 强制改写引擎，可注入 `tenant_id` 隔离条件与 `LIMIT` 分页，并拦截非只读 SQL；正式交付仍须按客户数据边界验收。
* **🧩 受控三角色编排**：SQL 架构师、合规审计员、业务分析师依次工作；审计拒绝不会进入 JDBC，角色轨迹可随会话历史持久化。
* **🔁 有上限的 SQL 自愈**：字段/表失配与 SQL 语法错误才允许重试；字段或表失配会刷新 Schema，默认最多 3 轮，可通过 `LINGSQL_SQL_EXECUTION_SELF_HEALING_MAX_ATTEMPTS` 调整至 1–5 轮。
* **🌍 多租户动态路由**：支持物理库隔离与逻辑字段隔离双模式，按租户懒加载连接池。
* **🧠 元数据与语义能力**：已内置 Schema 扫描、语义字典、Join 规则和会话记忆；版本化语义上下文、通用 RAG 知识库和 Aviator 指标计算仍在规划中。
* **🔌 插件化 Agent 底座**：已建立类型安全 Java 插件 SPI、注册中心、统一执行网关，以及白名单、系统调用来源、成本等级、调用次数和启动/返回 deadline 防护；可信 JAR、租户级授权、全链路可取消超时与 MCP 远程插件仍按安全路线逐步开放。
* **📦 可追溯供应链**：发布包生成前后端 CycloneDX SBOM、第三方许可证索引和绑定 Git commit/JAR 哈希的供应链 Manifest；开源许可与第三方组件边界见 [LICENSE](LICENSE) 和 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
* **⚖️ Spring AI 模型网关**：基于 Spring AI 2.0 对接 OpenAI 兼容模型，物理模型由系统统一管理；每个租户必须显式授权可用模型，并可独立配置优先级和权重。未授权时失败关闭，不会回退到全局模型池；主备模型从同一次冻结计划中选择并保留 Token、模型身份和路由指纹证据。
* **🧠 智能会话记忆 (Context Awareness)**：支持多轮追问。AI 能够理解“它”、“那个”、“刚才的”等代词，自动关联历史查询结果进行深度下钻。
* **🛡️ 并发会话隔离 (Session Isolation)**：通过会话标识和租户上下文隔离并行对话；实际并发容量和隔离效果须在目标部署环境验证。
* **🧾 持久管理审计**：管理操作执行前先写入 `PREPARED` 投递记录，业务结果通过唯一 `eventId` 幂等发布；未确认结果标记为 `UNKNOWN`，死信或未知状态会使审计健康检查失败。JSON 中的密码、Token、Secret、API Key、Cookie 与 Webhook 等字段统一脱敏，非结构化详情按失败关闭原则不保存原文。
* **🚀 虚拟线程运行时**：已启用 Spring Boot 虚拟线程支持，用于降低 LLM 与 SQL I/O 等待对平台线程的占用；实际容量须按部署环境压测确定。

---

## 🛠️ 技术栈

* **核心框架**: Spring Boot 4.1 / Spring Framework 7 / Java 25 (Virtual Threads)
* **SQL 引擎**: JSqlParser / JdbcTemplate / HikariCP
* **缓存/存储**: Redis (Schema 缓存与语义字典) / MySQL (配置库)
* **结果呈现**: 聊天页内置确定性结果概览与本地图表建议（柱状图/折线图）
* **AI 运行层**: Spring AI 2.0 / OpenAI 兼容协议 / 动态模型路由

### Agent 架构定位

当前实现是纯 Java 的“固定三角色受控编排”，不是允许 Agent 自主协商、任意选择工具或持续运行的开放式 Multi-Agent：

1. `IControlledAgentOrchestrator` 是聊天问答的单一编排入口，统一成功、降级和失败语义。
2. SQL 架构师和业务分析师通过 Spring AI 模型网关调用模型；合规审计员是确定性 Java 安全模块，不是 LLM Agent。
3. 候选 SQL 只能进入不可拆分的安全查询模块，并固定执行 `SqlRewriter -> SqlSecurityChecker -> JDBC`。
4. 当前恢复范围限于单次请求内的有界 SQL 自愈；插件白名单、系统调用来源、成本等级、调用次数、SQL 尝试次数和启动/返回 deadline 检查已有基础，但不能中断已运行调用；持久检查点、人工恢复、动态状态图和全链路 Token/费用预算仍属于后续阶段。

长期定位是企业级 AI 数据 Agent 插件底座：扩展点可持续增加，但插件权限、预算和数据边界必须有限且可审计。插件分层、知识库、安全不变量与实施路线见 [Agent 插件底座架构](docs/Agent插件底座架构.md)。

---

## 🚀 快速开始

### 1. 初始化数据库
标准环境执行 `scripts/ops/flyway_migrate.sh`：空库由冻结合并基线创建，已纳管库先校验再迁移；未纳管现有库只有在表、字段、默认值、生成表达式、索引和排序规则与当前基线完全一致时才允许显式接管。部分升级或未知结构会失败关闭，必须先备份并按[数据库版本迁移与旧库接管手册](docs/数据库版本迁移与旧库接管手册.md)收敛。

`sql/mysql.sql`、`sql/patch_*.sql` 与 `src/main/resources/db/manual/` 仅保留为冻结基线来源和历史交付档案，不再由应用或标准部署自动执行。租户管理采用单一 READY 开户：套餐正余额、管理员账号、MySQL 只读数据源和白名单缺一不可，并在控制库写入前验证连接与账号授权。模型资源池 (`t_lingsql_engine_ai_model_config`) 与租户模型授权 (`t_lingsql_engine_tenant_model_route`) 属于显式 provisioning；完成租户开户后必须至少授权一个已启用且真实探测为 `HEALTHY` 的模型，才可进行首问。

### 2. 定义语义字典 (可选)
在 `t_lingsql_engine_semantic_lexicon` 中定义业务术语，帮助 AI 理解“毛利”、“活跃度”等复杂概念。

### 3. 开启对话
发送 POST 请求至 `/api/v1/chat/ask`：
> **问**：“分析一下北京地区上周订单量下滑的原因。”
> **语数**：SQL 架构师生成候选 SQL -> 合规审计员改写并校验 -> 执行受控查询 -> 业务分析师基于结果生成答复。

### 4. 容器化交付（PoC 推荐）
```bash
cp .env.example .env
# 按需修改 .env 中密码与密钥
./scripts/ops/bootstrap_local_stack.sh
```

---

## 🔭 运维与监控

- **健康检查**：
  - `GET /actuator/health`
  - `GET /actuator/health/liveness`
  - `GET /actuator/health/readiness`
- **运行指标**：
  - `GET /actuator/metrics`
  - `GET /actuator/prometheus`
- **连接池观测指标**（Prometheus）：
  - `lingsql_tenant_pool_active`
  - `lingsql_tenant_pool_max`
  - `lingsql_tenant_pool_utilization`
- **会话策略开关**：
  - `LINGSQL_AUTH_SINGLE_SESSION_ENABLED`（默认 `true`，同账号新登录会使旧会话失效）
- **SQL 自愈开关**：
  - `LINGSQL_SQL_EXECUTION_SELF_HEALING_MAX_ATTEMPTS`（默认 `3`，取值范围 `1`–`5`，包含首轮）
- **租户生命周期治理**：
  - `LINGSQL_TENANT_LIFECYCLE_ENABLED`
  - `LINGSQL_TENANT_GRACE_PERIOD`（默认 `7d`）
  - `LINGSQL_TENANT_EXPIRING_SOON_DAYS`（默认 `7`）
  - `LINGSQL_TENANT_TRIAL_DEFAULT_DAYS`（默认 `14`）
  - `LINGSQL_TENANT_AUTO_SUSPEND_ENABLED`
  - `LINGSQL_TENANT_AUTO_SUSPEND_CRON`（默认 `0 0/30 * * * ?`）
  - `LINGSQL_TENANT_REMINDER_ENABLED`
  - `LINGSQL_TENANT_REMINDER_CRON`（默认 `0 0 9 * * ?`）
  - `LINGSQL_TENANT_REMINDER_DAYS`（默认 `7,3,1`）
  - `LINGSQL_TENANT_REMINDER_DEDUPE_TTL`（默认 `2d`，防止重启后同日重复提醒）
  - `LINGSQL_TENANT_RISK_ALERT_ENABLED`
  - `LINGSQL_TENANT_RISK_ALERT_CRON`（默认 `0 30 9 * * ?`）
  - `LINGSQL_TENANT_RISK_ALERT_THRESHOLD`（默认 `70`）
  - `LINGSQL_TENANT_RISK_ALERT_TOP_N`（默认 `20`）
- **告警配置（Webhook）**：
  - `LINGSQL_ALERT_ENABLED`
  - `LINGSQL_ALERT_WEBHOOK_URL`
  - `LINGSQL_ALERT_DINGTALK_WEBHOOK_URL`
  - `LINGSQL_ALERT_WECOM_WEBHOOK_URL`
  - `LINGSQL_ALERT_REQUIRE_ALL_CHANNELS`（`true`=全部通道成功才算成功）
  - `LINGSQL_ALERT_POOL_UTILIZATION_THRESHOLD`
  - `LINGSQL_ALERT_COOLDOWN`
- **发布与回滚脚本**：
  - `scripts/ops/schema_drift_guard.sh`（工程基线：实体表与冻结 Flyway 合并基线一致性检查）
  - `scripts/ops/schema_adoption_guard.sh`（未纳管旧库完整结构指纹与接管守卫）
  - `scripts/ops/flyway_migrate.sh`（短生命周期数据库迁移任务；DDL 凭据不进入应用）
  - `scripts/ops/bootstrap_local_stack.sh`（容器化一键拉起：依赖 + Flyway 迁移 + 种子 + 应用）
  - `scripts/ops/bootstrap_demo_pilot.sh`（虚构销售库存演示：开户 + 数据源/Schema + 模型验证 + 三问逐请求验收）
  - `scripts/ops/release_gate.sh`（发布门禁：冒烟 + SLO + 商用六项验收）
  - `scripts/ops/runtime_fingerprint_guard.sh`（候选 Git SHA、Flyway、数据快照与模型路由指纹校验）
  - `scripts/ops/release_blue_green.sh`（蓝绿候选准备或直接发布）
  - `scripts/ops/release_candidate_promote.sh` / `scripts/ops/release_candidate_abort.sh`（严格门禁后切流 / 失败停止未切流候选）
  - `scripts/ops/switch_traffic.sh`（Nginx 切流 Hook）
  - `scripts/ops/release_smoke_test.sh`（发布后冒烟验收）
  - `scripts/ops/release_rollback.sh`（故障快速回滚）
  - `scripts/ops/release_with_auto_rollback.sh`（发布编排：自动回滚）
  - `scripts/ops/notify_release_event.sh`（发布事件通知）
  - `scripts/ops/mysql_backup.sh` / `scripts/ops/mysql_restore.sh`（最小文件权限、gzip/SHA-256 校验、目标库显式确认的备份恢复）
  - `scripts/ops/slo_error_budget_report.sh`（SLO/错误预算报告）
  - `scripts/ops/reliability_baseline.sh`（容量基线压测与 SLA 校验）
  - `scripts/ops/npm_audit_with_fallback.sh`（npm 漏洞扫描，镜像源自动降级到官方源）
  - `scripts/ops/github_guard.sh`（本地一键门禁：后端测试 + 前端构建 + 漏洞扫描）
  - `scripts/ops/security_compliance_guard.sh`（生产安全配置与最小权限门禁）
  - `scripts/ops/p1_incident_drill.sh`（P1 事件响应演练）
  - `scripts/ops/billing_readiness_guard.sh`（计费商用验收门禁）
  - `scripts/ops/golden_question_acceptance.sh`（冻结 40 题的 FIRST/RETEST 正式验收：准确率、P95、效率、安全、逐请求审计与资金守恒）
  - `scripts/ops/commercial_acceptance_gate.sh`（工程、计费、三问、可靠性、安全和值守技术放行；不替代 40 题与客户签字）
- **CI/CD 门禁工作流**：
  - `.github/workflows/ci.yml`（持续集成：后端测试 + 前端构建 + MySQL 8.4 空库迁移/旧库接管 + npm 高危漏洞门禁）
  - `.github/workflows/security.yml`（依赖安全：Dependency Review + Trivy 高危/严重扫描）
  - `.github/workflows/codeql.yml`（代码安全：Java + JS/TS 静态分析）
  - `.github/workflows/auto-remediation.yml`（自动修复：CI/安全扫描失败后尝试自愈并自动提 PR）
  - `.github/workflows/dependabot-auto-merge.yml`（Dependabot 自动审批 + 绿灯自动合并，不依赖仓库 Auto-merge 开关）
  - `.github/workflows/release-gate.yml`（仅允许 `master` 手动触发；计费、真实问数、SLO 与 P1 演练证据均不可跳过）
  - `.github/workflows/release-deploy.yml`（测试构建 + 严格门禁 + 发布 + 失败自动回滚，仅允许 `master`）
  - `.github/dependabot.yml`（Maven/NPM 依赖自动升级）
- **租户运营 API**：
  - `GET /api/v1/admin/tenants/lifecycle/summary`（生命周期汇总）
  - `GET /api/v1/admin/tenants/lifecycle/workbench`（运营任务清单）
  - `POST /api/v1/admin/tenants/{id}/lifecycle/extend`（续期，支持 `extendDays` 或 `expireTime`）
  - `POST /api/v1/admin/tenants/{id}/lifecycle/reactivate`（恢复服务）
- **Token 审计 API**：
  - `GET /api/v1/admin/audit/token-overview`（按租户/用户/来源/关键词/时间窗聚合 Token 消耗与成功率）
  - `GET /api/v1/admin/billing/readiness/snapshot`（计费商用就绪快照）
- **数字员工 API**：
  - `GET /api/v1/admin/digital-employees`（数字员工列表）
  - `POST /api/v1/admin/digital-employees/{id}/run`（执行数字员工）
  - `GET /api/v1/admin/digital-employees/runs`（执行日志）
  - `GET /api/v1/admin/digital-employees/overview`（运营概览）
  - 数字员工当前已支持定时执行 Cron、规则告警（失败/空结果/结果行数阈值）、业务化推送模板、可选通道路由，以及“租户默认通道 -> 数字员工继承 -> 单任务覆盖”的结果摘要推送策略
  - 数字员工运行日志已补齐通道级投递诊断明细，可直接查看各通知通道的成功/失败情况与失败原因
- **运营视图增强**：
  - 租户管理页支持风险分排序与流失风险标签（LOW/MEDIUM/HIGH/CRITICAL）
  - 生命周期汇总新增 `atRisk/highRisk/criticalRisk`
  - 生命周期运营工作台输出 `P0/P1/P2` 优先级任务与建议动作
  - SQL 日志页新增 Token 审计看板（总览、模型 TopN、租户 TopN、日趋势）与 Excel 导出
  - 数字员工页支持把固定问数场景配置成“定时执行 + 规则告警 + 结果推送 + 通道路由 + 投递审计”的订阅执行器；租户管理页支持配置、测试、预检、回看、统计分析、单租户下钻、历史测试明细查看、按时间窗口和多条件筛选测试记录、自动记住常用筛选条件、从风险项与测试记录一键联动到租户分析与复测，并支持按内部/客户两种口径复制全局与单租户通知摘要，作为数字员工结果推送的默认分发策略
- **交付文档**：公开仓库只保留技术架构、接口、部署和安全基线；定价、合同、渠道、客户、经营计划与回款材料仅保存在本机忽略目录，不进入 Git。

---

## 当前实现状态（截至 2026-08-15）

### 已实现

- 多租户物理/逻辑隔离路由、JSqlParser 改写与执行前安全校验；合规审计未通过时不会调用 JDBC。
- Schema 扫描与缓存、语义字典、Join 规则、会话记忆、实体追问消解、Spring AI 2.0 模型网关与模型资源池故障切换。
- 类型安全 Java 插件 SPI、插件注册中心、统一执行网关、显式 Agent 上下文，以及插件白名单、系统调用来源、成本等级、调用次数、SQL 尝试次数和启动/返回 deadline 防护；`secure_query` 已作为系统插件接入。
- 单入口受控三角色服务端编排：SQL 架构师生成候选 SQL，合规审计员通过不可拆分的安全查询模块完成改写、校验和 JDBC 查询，业务分析师仅在查询成功后根据受限结果集生成答复。
- 有界多轮自愈：仅字段/表失配和 SQL 语法错误可恢复；表结构失配会刷新 Schema；最大轮次可配置为 1–5，默认 3。
- 结构化执行轨迹与历史回放，轨迹包含角色、轮次和阶段耗时；该轨迹不包含原始模型 CoT、完整 Prompt、底层异常或结果明细。
- 聊天页的确定性结果概览、柱状图/折线图建议和 CSV 导出；SQL 日志 Token 看板与 Excel 导出；计费账本与对账日志。
- 逐物理模型调用计费：SQL 架构师、业务分析师、Schema 扫描与语义建议统一通过结构化模型网关；每次供应商调用在 MySQL 先预留、再标记 `DISPATCHED`，并冻结实际 `modelConfigId`、模型版本和选择时路由指纹，可信用量返回后按实际费用结算并退回差额。超时、断连或用量缺失进入 `UNKNOWN` 且不自动退款；主备模型使用不同 `invocationId`。
- 管理操作持久审计投递箱：操作前意图、结果确认、幂等发布、重试、死信、UNKNOWN 状态和健康门禁已落地；它不等同于插件调用与计费事件的统一 Outbox。

### 未实现或存在边界

- 可信 JAR、MCP 远程插件、Spring AI ToolCallback 适配和插件管理 API 尚未完成；现有插件仍不接受模型任意调用。
- 通用知识库和版本化语义上下文尚未完成；当前只有语义字典、Join 规则和会话记忆基础。
- 永久记忆尚未完成；当前 Redis 会话窗口默认只保留 10 条、30 分钟，MySQL 聊天历史展示不等于长期记忆检索。
- `calculate_metric`：Aviator 指标定义、公式白名单、租户权限和指标管理界面尚未实现。
- 服务端版本化 `chart_mapper` 契约；目前图表建议仅在前端基于结果行做确定性映射，历史回放不保存图表决策。
- 多角色群聊 UI、动态规划、持久检查点、人工恢复和角色预算；当前是固定三角色服务端状态机，而非自由群聊或长期自治 Agent。
- 逐物理模型调用的预留、结算、退款、`UNKNOWN` 保留、恢复扫描和基于外部证据摘要的管理端裁决 API 已落地；计费验收会话可冻结 MySQL 余额与账本水位，并对精确请求集合生成余额差额、账本净额和调用终态一致的不可变回执。但供应商账单自动导入、人工核销界面，以及未来 MCP/Skill 非模型工具调用计费尚未完成，因此不能宣称第三方支付清算或供应商级自动对账已完成。
- PDF 分析报告、图文对账、外部工单/审批系统回执闭环与可配置审批节点，需要明确业务系统、证据口径和权限策略后实现。

## 📅 路线图 (Roadmap)

- [x] **Phase 1 (MVP)**: 多租户隔离路由、JSqlParser 改写引擎、受控 SQL 执行。
- [x] **Phase 2（当前已完成范围）**: 元数据扫描、历史记忆、前端结果图表建议、带 Schema 刷新的有界多轮自愈与结构化执行轨迹。
- [x] **Phase 3（当前已完成范围）**: SQL 架构师 / 合规审计员 / 业务分析师的固定服务端编排；群聊 UI 与动态角色协商仍未完成。
- [ ] **Phase 4（商业化）**: Token 看板、SQL 日志 Excel 导出、逐物理模型调用资金状态机、计费账本和请求级验收证据已落地；PDF 报告、图文对账、供应商账单核销及 MCP/Skill 工具计费尚未完成。

> 上述状态以仓库当前代码为准。结构化执行轨迹用于展示系统阶段与受控摘要，不等同于或暴露原始模型 CoT。

---
[深入了解 AI 运行逻辑：AGENTS.md](./AGENTS.md)
