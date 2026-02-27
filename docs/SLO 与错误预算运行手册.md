# LingSQL SLO 与错误预算运行手册

> **版本**: v1.0  
> **更新日期**: 2026-02-27  
> **适用对象**: SRE、运维值班、技术负责人

---

## 1. 目标定义

LingSQL 生产环境默认采用以下可用性目标（可按业务线调整）：

| 服务对象 | SLI | SLO 目标 | 窗口 |
|----------|-----|----------|------|
| 核心 API（不含 `/actuator`） | 成功请求率（非 5xx） | 99.9% | 30 天 |
| 登录与鉴权接口 | 成功请求率（非 5xx） | 99.95% | 30 天 |
| 管理端页面可访问性 | 可访问率（200） | 99.9% | 30 天 |

---

## 2. 错误预算计算方法

- 错误预算总额：`100% - SLO`
- 实际错误率：`5xx 请求数 / 总请求数`
- 预算消耗率：`实际错误率 / 错误预算总额`

示例（SLO=99.9%）：
- 错误预算总额 = `0.1%`
- 若当前错误率 = `0.04%`，预算消耗率 = `40%`

---

## 3. 自动化报告脚本

仓库脚本：`scripts/ops/slo_error_budget_report.sh`

### 3.1 快速执行

```bash
PROMETHEUS_URL=http://127.0.0.1:9090 \
SLO_WINDOW=30d \
SLO_TARGET=99.9 \
./scripts/ops/slo_error_budget_report.sh
```

### 3.2 生成落地报告文件

```bash
REPORT_FILE=./reports/slo_$(date +%Y%m%d).md \
./scripts/ops/slo_error_budget_report.sh
```

### 3.3 关键参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `PROMETHEUS_URL` | `http://127.0.0.1:9090` | Prometheus 地址 |
| `SLO_WINDOW` | `30d` | 统计窗口 |
| `SLO_TARGET` | `99.9` | SLO 可用性目标 |
| `SLO_TOTAL_QUERY` | 内置默认 | 总请求 PromQL |
| `SLO_ERROR_QUERY` | 内置默认 | 5xx 请求 PromQL |
| `REPORT_FILE` | 空 | 输出 Markdown 文件路径 |

脚本状态码说明：
- `0`: 执行成功，预算未耗尽
- `2`: 执行成功，但预算已耗尽（`BREACHED`）
- 其他非 0：脚本执行失败（配置或依赖异常）

### 3.4 发布前门禁联动

可通过 `scripts/ops/release_gate.sh` 将 SLO 预算检查与冒烟检查串联：

```bash
BASE_URL=https://api.lingsql.com \
PROMETHEUS_URL=http://prometheus.lingsql.svc:9090 \
RELEASE_GATE_ENABLE_SMOKE=true \
RELEASE_GATE_ENABLE_SLO=true \
RELEASE_GATE_BLOCK_ON_SLO_BREACH=true \
./scripts/ops/release_gate.sh
```

如需在平台侧执行，可直接触发 GitHub Actions 工作流 `.github/workflows/release-gate.yml`。
正式发布场景可使用 `.github/workflows/release-deploy.yml`，其中会在部署前按输入参数执行同一套门禁策略。

---

## 4. 预算状态与发布策略

| 状态 | 条件 | 发布策略 |
|------|------|----------|
| `HEALTHY` | 消耗率 < 70% | 正常发布 |
| `AT_RISK` | 70% ≤ 消耗率 < 100% | 严控变更窗口，仅发布高优先级修复 |
| `BREACHED` | 消耗率 ≥ 100% | 冻结非紧急发布，优先恢复稳定性 |

---

## 5. 建议纳入值班节奏

- 每日 09:00 生成一次滚动 30 天预算报告。
- 每次大版本发布前再执行一次，作为发布门禁输入。
- 出现 `AT_RISK` 或 `BREACHED` 时，必须在变更单附上整改计划与完成时间。

---

## 6. 常见问题

### Q1: 为什么报告总请求数为 0？

可能原因：
- Prometheus 指标名与脚本默认查询不一致。
- 查询窗口过短且没有流量。
- 采集或标签过滤条件不匹配。

处理方式：
- 在 Grafana Explore 中先验证 PromQL，再覆盖 `SLO_TOTAL_QUERY`/`SLO_ERROR_QUERY`。

### Q2: 如何按租户计算 SLO？

将查询条件增加租户标签，例如：

```bash
SLO_TOTAL_QUERY='sum(increase(http_server_requests_seconds_count{tenant_id="t001"}[30d]))' \
SLO_ERROR_QUERY='sum(increase(http_server_requests_seconds_count{tenant_id="t001",status=~"5.."}[30d]))' \
./scripts/ops/slo_error_budget_report.sh
```
