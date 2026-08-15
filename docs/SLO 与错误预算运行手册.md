# LingSQL SLO 与错误预算运行手册

> 版本：v2.0
> 更新日期：2026-08-15
> 适用对象：SRE、运维值班、发布负责人

## 1. 证据边界

LingSQL 将 SLO 证据拆成两类，二者不能互相替代：

| 模式 | 目的 | 默认窗口 | 身份范围 | 发布用途 |
|------|------|----------|----------|----------|
| `HISTORICAL` | 判断当前在线服务的长期错误预算 | 30 天 | 全局在线流量 | 启动候选前的服务健康门禁 |
| `CANDIDATE` | 判断本次候选在受控负载下是否可用 | 15 分钟 | `build_commit + slot + release_id` | 候选收据与切流门禁 |

候选模式不接受自定义 PromQL。脚本强制加入三项身份过滤，并要求总请求和错误请求查询都恰好返回一条序列，实际返回标签必须与本次候选完全一致。这样历史版本的成功流量不能稀释坏候选的错误率。

## 2. 指标身份与启动约束

应用通过 Micrometer 为所有运行指标添加不可变公共标签：

- `build_commit`：来自 JAR 内的 Spring Boot build info；若启动环境声明的提交与制品不一致，应用拒绝启动。
- `slot`：`blue` 或 `green`。
- `release_id`：本次发布的唯一 ID。

`release_blue_green.sh` 为每个槽位原子生成只读的 `runtime.env`，只包含上述三项非敏感身份。systemd unit 必须消费对应槽位文件：

```ini
# /etc/systemd/system/lingsql-blue.service
[Service]
EnvironmentFile=/opt/lingsql/blue/runtime.env
ExecStart=/usr/bin/java -jar /opt/lingsql/blue/app.jar --server.port=18080
```

绿槽位对应 `/opt/lingsql/green/runtime.env`。发布脚本会检查 unit 的 `EnvironmentFiles`，路径不匹配时失败关闭。非 systemd 模式必须在模板中显式消费 `{runtime_env}`：

```bash
LINGSQL_START_CMD_TEMPLATE='set -a; source {runtime_env}; set +a; exec java -jar {artifact} --server.port={port}'
```

禁止使用 `systemctl set-environment`；它是管理器全局状态，两个并行槽位可能互相污染身份。

## 3. Prometheus 抓取要求

Prometheus 必须直接抓取蓝绿两个隔离端口，不能只抓取切流后的统一域名。例如：

```yaml
scrape_configs:
  - job_name: lingsql-blue
    static_configs:
      - targets: ['127.0.0.1:18080']
  - job_name: lingsql-green
    static_configs:
      - targets: ['127.0.0.1:28080']
```

监控采集身份需要按现有安全策略访问受保护的 `/actuator/prometheus`。候选负载完成后，工作流默认等待 20 秒供 Prometheus 抓取；实际抓取周期较长时应缩短采集周期，而不是放宽身份校验。

## 4. 历史服务 SLO

在线服务默认目标为 99.9%，错误预算计算如下：

- 错误预算总额：`100% - SLO`
- 实际错误率：`5xx 请求数 / 总请求数`
- 预算消耗率：`实际错误率 / 错误预算总额`

执行示例：

```bash
SLO_EVIDENCE_MODE=HISTORICAL \
PROMETHEUS_URL=http://127.0.0.1:9090 \
SLO_WINDOW=30d SLO_TARGET=99.9 SLO_MIN_REQUESTS=100 \
REPORT_FILE=./reports/historical_service_slo.md \
bash scripts/ops/slo_error_budget_report.sh
```

历史模式允许为现有监控拓扑覆盖 `SLO_TOTAL_QUERY` 和 `SLO_ERROR_QUERY`。覆盖查询必须经运维评审；它只影响历史报告，不能用于候选门禁。

## 5. 候选受控负载与 SLO

候选负载访问 `GET /api/v1/admin/runtime/fingerprint`，而不是静态页面。该路径至少覆盖 Bearer 认证、系统租户校验、控制库/Flyway 版本读取和激活模型路由指纹。负载脚本支持可选的 `ACCESS_TOKEN` 与 `TENANT_ID`，后者作为 `X-Tenant-Id` 发送。

```bash
ACCESS_TOKEN="$RELEASE_GATE_CONTROL_TOKEN" \
TENANT_ID="$CONTROL_TENANT_ID" \
BASE_URL=http://127.0.0.1:28080 \
TARGET_PATH=/api/v1/admin/runtime/fingerprint \
TOTAL_REQUESTS=200 CONCURRENCY=20 \
REPORT_FILE=./reports/candidate_controlled_load.md \
bash scripts/ops/reliability_baseline.sh
```

Token 只写入权限 `600` 的一次性 curl 配置文件，文件在退出时删除；Token 不出现在报告、命令参数或脚本日志。报告只记录目标地址、样本量、成功率和延迟。

负载通过后生成候选 SLO：

```bash
SLO_EVIDENCE_MODE=CANDIDATE \
PROMETHEUS_URL=http://127.0.0.1:9090 \
SLO_WINDOW=15m SLO_TARGET=99.9 SLO_MIN_REQUESTS=200 \
SLO_EXPECTED_BUILD_COMMIT=<40位小写GitSHA> \
SLO_EXPECTED_SLOT=green \
SLO_EXPECTED_RELEASE_ID=<release-id> \
SLO_EXPECTED_CANDIDATE_BASE_URL=http://127.0.0.1:28080 \
SLO_CONTROLLED_LOAD_PATH=/api/v1/admin/runtime/fingerprint \
SLO_CONTROLLED_LOAD_REPORT_FILE=./reports/candidate_controlled_load.md \
REPORT_FILE=./reports/candidate_slo.md \
bash scripts/ops/slo_error_budget_report.sh
```

候选脚本会验证受控负载报告的目标、样本数与 `PASS` 结论，并把报告 SHA-256 写入候选 SLO。随后验证 Prometheus 返回序列的三项身份标签。任何缺失、多序列、标签不符、样本不足或预算耗尽都会阻断发布。

## 6. 严格发布顺序

`.github/workflows/release-deploy.yml` 的相关顺序固定为：

1. 迁移前生成在线服务 30 天历史 SLO。
2. 为非活跃槽位写入运行身份并启动隔离候选。
3. 直接访问候选端口复核运行指纹。
4. 对运行指纹接口执行 200 次已认证受控负载。
5. 等待 Prometheus 抓取，按三项身份生成候选 SLO。
6. 将 `candidate_slo.md` 的 SHA-256 纳入不可覆盖候选收据。

`historical_service_slo.md` 证明发布前在线服务状态，`candidate_slo.md` 证明候选状态。候选收据只绑定后者；不得将历史报告复制、改名或作为候选报告使用。

## 7. 参数与退出码

| 参数 | 历史默认值 | 候选要求 |
|------|------------|----------|
| `PROMETHEUS_URL` | `http://127.0.0.1:9090` | 必填可达地址 |
| `SLO_WINDOW` | `30d` | 建议 `15m` |
| `SLO_TARGET` | `99.9` | 严格小于 100 |
| `SLO_MIN_REQUESTS` | `100` | 与受控负载样本一致，发布默认 200 |
| `SLO_TOTAL_QUERY` / `SLO_ERROR_QUERY` | 可覆盖 | 禁止设置 |
| `SLO_EXPECTED_*` | 不使用 | 提交、槽位、发布 ID、候选地址均必填 |
| `SLO_CONTROLLED_LOAD_REPORT_FILE` | 不使用 | 必须存在且结论为 `PASS` |
| `REPORT_FILE` | 可选 | 严格发布必须设置 |

- `0`：证据通过。
- `2`：证据形成但不满足发布条件，例如样本不足、标签不匹配或预算耗尽。
- 其他非 0：依赖、配置或 Prometheus 查询失败。

## 8. 排障

- 返回 0 条序列：确认 Prometheus 正在直接抓取候选端口、等待至少一个抓取周期，并检查应用指标是否携带三项标签。
- 返回多条序列：检查是否存在重复 `release_id` 或查询链路擅自增加了未聚合维度；禁止通过删掉身份过滤绕过。
- 标签为 `UNSET`：槽位 unit 未消费 `runtime.env`，应修复部署模板并重新启动候选。
- 受控负载为 401/403：核对专用系统管理员 Token、`CONTROL_TENANT_ID` 与权限，不得改打公开静态路径。
- 历史报告通过而候选失败：以候选失败为准，停止切流；历史流量不能覆盖候选缺陷。
