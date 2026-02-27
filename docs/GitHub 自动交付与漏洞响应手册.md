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

## 3. 本地一键自检

执行以下命令可本地模拟 GitHub 门禁：

```bash
./scripts/ops/github_guard.sh
```

商用阶段建议追加：

```bash
./scripts/ops/security_compliance_guard.sh
./scripts/ops/commercial_acceptance_gate.sh
```

在 GitHub 手动工作流中，`release-gate.yml` 与 `release-deploy.yml` 现已支持以下商用验收输入：

- `run_commercial_gate`：是否执行商用四块门禁。
- `billing_readiness_url`：计费验收接口地址（可留空并基于 `base_url` 自动拼接）。
- `allow_skip_billing_readiness`：未配置计费接口时是否允许跳过。
- `commercial_require_slo`：商用门禁内未配置 Prometheus 时是否阻断。

流程包含：

1. 后端测试
2. 前端构建
3. 前端依赖漏洞审计（高危及以上）

## 4. 漏洞处理策略

- **高危/严重漏洞**：阻断合并，必须修复或升级依赖。
- **中低危漏洞**：允许进入排期，但需要在一个迭代内闭环。
- **无可用修复版本**：保留风险记录，并加白名单说明与替代缓解措施。

## 5. 失败定位建议

- CI 报错优先看测试日志（`mvn test`）。
- 前端失败优先看 `npm ci` 与 `npm run build` 输出。
- 漏洞失败优先看 `security` 工作流的 Trivy SARIF 结果。
- 代码安全告警优先看 `CodeQL` 告警详情和数据流路径。
