# GitHub 自动交付与漏洞响应手册

## 1. 目标

建立一套“可持续自愈”的工程闭环，覆盖以下四类动作：

- 自动发现构建错误（编译、测试、前端打包）。
- 自动发现依赖漏洞（高危/严重）。
- 自动发现代码安全风险（CodeQL）。
- 自动触发依赖升级（Dependabot）。

## 2. 工作流设计

### 2.1 `ci.yml`（基础质量门禁）

- 触发条件：`push/master`、`pull_request/master`。
- 校验内容：
  - 后端：`mvn test -DskipITs`
  - 前端：`npm ci` + `npm audit(高危及以上)` + `npm run build`
- 关键特性：
  - 使用 `concurrency` 防止同分支重复排队。
  - `npm audit` 使用脚本兜底，兼容镜像源不支持审计接口的情况。

### 2.2 `security.yml`（依赖漏洞门禁）

- 触发条件：`push/master`、`pull_request/master`、每周定时、手动触发。
- 校验内容：
  - PR 场景：`dependency-review-action` 检查新增依赖风险。
  - 全量场景：`Trivy` 扫描仓库依赖库高危/严重漏洞并上传 SARIF。

### 2.3 `codeql.yml`（静态安全分析）

- 触发条件：`push/master`、`pull_request/master`、每周定时、手动触发。
- 扫描语言：
  - Java
  - JavaScript/TypeScript

### 2.4 `dependabot.yml`（自动升级）

- Maven 与 NPM 每周自动检查依赖更新并创建 PR。

### 2.5 `auto-remediation.yml`（失败自愈 + 自动提 PR）

- 触发条件：
  - `LingSQL CI / Security / CodeQL` 在 `master` 失败后自动触发。
  - 每日定时巡检触发。
  - 手动触发。
- 执行动作：
  - 执行 `scripts/ops/github_auto_remediate.sh`。
  - 自动尝试 `npm audit fix --omit=dev`。
  - 自动重跑 `scripts/ops/github_guard.sh` 门禁。
  - 若仓库存在改动，自动创建修复 PR（`chore/auto-remediation`）。

### 2.6 `dependabot-auto-merge.yml`（依赖 PR 自动合并）

- 触发条件：
  - Dependabot 打开的依赖升级 PR（自动审批）。
  - CI/Security/CodeQL 工作流成功后（自动尝试合并）。
- 执行动作：
  - 自动审批 Dependabot PR。
  - 在门禁绿灯后执行 `squash` 自动合并并删除分支。
- 安全边界：
  - 仍受分支保护规则与必需状态检查约束，门禁未通过不会合并。
  - 不依赖仓库层面的 “Allow auto-merge” 开关，适配单分支仓库。

## 3. 本地一键自检

执行以下命令可本地模拟 GitHub 门禁：

```bash
./scripts/ops/github_guard.sh
```

执行以下命令可本地模拟自动修复流程：

```bash
./scripts/ops/github_auto_remediate.sh
```

商用阶段建议追加：

```bash
./scripts/ops/security_compliance_guard.sh
./scripts/ops/commercial_acceptance_gate.sh
```

`release-gate.yml` 与 `release-deploy.yml` 只能从远端 `master` 手动执行。正式流程不再提供关闭冒烟、SLO、计费、真实问数、运行时审计、备份恢复、商用总门禁或自动回滚的输入；缺少证据时必须失败。两者的严格门禁阶段都在受保护 Environment 的 self-hosted Runner 运行，才能访问隔离 UAT MySQL 和受保护备份目录。

触发时必须填写 `base_url`、真实 `prometheus_url`、发布标签（部署流程）以及一份真实 P1 演练的编号、发现时间、响应时间和缓解时间。独立门禁还必须选择持有 UAT Secrets 的 `environment_name`；`billing_readiness_url` 可留空并由 `base_url` 拼接。

仓库或目标 Environment 必须配置：

- `RELEASE_GATE_BILLING_TOKEN`：只用于计费就绪门禁的短期 Token。
- `RELEASE_GATE_CONTROL_TOKEN`：独立系统租户验收账号的短期 Token。当前 SQL 日志、计费就绪与账本证据组合验收需要 `ADMIN:SQLLOG:READ`、`ADMIN:BILLING:READ` 和系统管理员身份；不得复用普通试点账号或 billing Token。
- `RELEASE_GATE_PILOT_TENANT_ID`、`RELEASE_GATE_PILOT_USERNAME`、`RELEASE_GATE_PILOT_PASSWORD`：专用验证租户与普通问数账号，不得使用真实客户个人账号或授予系统管理员身份。
- `RELEASE_GATE_MYSQL_HOST`、`RELEASE_GATE_MYSQL_PORT`、`RELEASE_GATE_BACKUP_SOURCE_DB`、`RELEASE_GATE_BACKUP_DIR`、`RELEASE_GATE_BACKUP_USER`、`RELEASE_GATE_RESTORE_USER`：隔离 UAT 的连接与受保护绝对路径 Variables。
- `RELEASE_GATE_BACKUP_PASSWORD`、`RELEASE_GATE_RESTORE_PASSWORD`、`RELEASE_GATE_BACKUP_SAMPLE_QUERY`：Environment Secrets。样例查询必须是一条只读聚合 `SELECT`；恢复账号只能管理独立 UAT 的 `lingsql_restore_verify_` 前缀验证库。
- `RELEASE_NOTIFY_*`、`LINGSQL_DEPLOY_BASE`、`LINGSQL_SWITCH_HOOK`：按部署拓扑配置的通知与切换参数。
- `LINGSQL_MIGRATION_HOOK`：self-hosted Runner 上的可执行数据库迁移 Hook。它必须在目标控制库完成备份、Flyway 校验/迁移和结果留档，非零退出会阻断制品发布。

即使 `release-deploy.yml` 的目标 Environment 是 production，上述 `RELEASE_GATE_MYSQL_*` 和备份恢复 Secrets 也必须指向独立 UAT MySQL，不能指向生产控制库；生产迁移前备份仍由 `LINGSQL_MIGRATION_HOOK` 单独负责。商用门禁验证“备份可恢复”，迁移 Hook 保护“本次生产变更”，两者不能互相替代。

发布制品先执行前端依赖审计与生产构建，把管理后台写入 Spring Boot 静态资源，再执行 Schema 漂移检查及 Maven 测试；工作流会检查最终 JAR 确实包含后台首页。门禁会完成登录、三条真实问数、SQL 审计增量、计费账本增量、运行时审计、真实备份的 SHA-256 与隔离恢复双指纹、最少 100 个非 Actuator 请求的 SLO 证据和 P1 演练校验；任何 `FAIL` 或 `SKIP` 都阻断发布。验收账号、模型、数据源、UAT 备份恢复配置或 Prometheus 尚未准备好时，应先完成配置，不能临时关闭门禁。

流程包含：

1. 后端测试
2. 前端构建
3. 前端依赖漏洞审计（高危及以上）
4. Compose 配置校验、生产镜像构建与非 root 运行用户断言
5. MySQL 8.4 空库迁移、重复迁移 no-op 与当前旧库完整指纹接管

正式部署在蓝绿切换前强制运行 `LINGSQL_MIGRATION_HOOK`。应用冒烟失败时会回滚应用槽位，但不会自动执行反向数据库 DDL；因此所有生产迁移必须向前兼容上一稳定应用版本，并在执行前完成可验证备份。详细协议见[数据库版本迁移与旧库接管手册](数据库版本迁移与旧库接管手册.md)。

## 4. 漏洞处理策略

- **高危/严重漏洞**：阻断合并，必须修复或升级依赖。
- **中低危漏洞**：允许进入排期，但需要在一个迭代内闭环。
- **无可用修复版本**：保留风险记录，并加白名单说明与替代缓解措施。

## 5. 失败定位建议

- CI 报错优先看测试日志（`mvn test`）。
- 前端失败优先看 `npm ci` 与 `npm run build` 输出。
- 漏洞失败优先看 `security` 工作流的 Trivy SARIF 结果。
- 代码安全告警优先看 `CodeQL` 告警详情和数据流路径。
