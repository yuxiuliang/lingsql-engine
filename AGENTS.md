# 🤖 Agent 架构：The LingSQL Brain

语数引擎不只是一个 SQL 生成器，它是一个由多个专业角色组成的“虚拟智库”。

## 1. 推理引擎：ReAct 推理循环
系统采用 **ReAct (Reasoning and Acting)** 架构。面对用户提问，Agent 会执行以下闭环：

1. **Thought (思考)**: AI 分析意图（例如：用户需要对比数据，我需要先找表结构）。
2. **Action (行动)**: 调用 `Youtube` 获取元数据。
3. **Observation (观察)**: 获取 Java 端返回的真实 Schema 信息。
4. **Final Answer (执行)**: 基于事实生成 SQL，并交由 `SqlRewriter` 审计执行。

## 2. 专家角色分工 (Multi-Agent Collaboration)
系统支持多角色协同处理一条请求：
* **SQL 架构师 (The Architect)**: 负责理解元数据与 Join 规则，输出标准化 SQL。
* **业务分析师 (The Analyst)**: 负责将 SQL 结果集转化为业务洞察，进行同环比分析。
* **合规审计员 (The Auditor)**: 负责检查生成的 SQL 是否违反安全规则。

## 3. 纯 Java 智能技能库 (Skills Library)
为确保系统的高可用与安全性，所有 Skills 均基于 Java 21 实现，无外部进程依赖。

| 技能 ID | 实现逻辑 | 解决问题 |
| :--- | :--- | :--- |
| `Youtube` | 基于 JDBC DatabaseMetaData | 提供真实的表字段信息，消除 AI 幻觉 |
| `calculate_metric`| 基于 Aviator 表达式引擎 | 在 JVM 内存中完成复杂的二次计算 (如复利、利润率) |
| `secure_execute` | 联动 SqlRewriter 模块 | 强制租户隔离，拦截一切非 SELECT 语句 |
| `chart_mapper` | Java 对象转 ECharts JSON | 自动识别数据特征并匹配最优图表类型 |

## 4. 自愈机制 (Self-Healing)
系统具备自发性的错误修正能力：
* **解析失败自愈**：当 SQL 报错（如 `Unknown column`）时，系统捕捉 Error，自动将错误反馈给 AI，要求 AI 重新查阅元数据并二次生成，过程对用户透明。
* **熔断切换**：当主模型（如 GPT-4）响应延迟过高时，Agent 自动切换至备用模型（如本地 Qwen），确保服务不中断。

## 5. 提示词工程 (Prompt Engineering)
系统动态拼装以下上下文信息：
- **租户 Context**: 当前租户的白名单表、隔离字段。
- **语义字典**: 业务指标与 SQL 表达式的映射关系。
- **约束指令**: 强制分页规则、禁止 Markdown 输出要求。

## 6. 会话窗口管理 (Sliding Window Memory)
- **机制**: 系统自动维护一个 `ChatHistory` 栈。
- **实体消解 (Entity Resolution)**: 在生成 SQL 前，Agent 会扫描历史 Observation（观察结果），将模糊概念映射为具体的数据库主键或唯一索引，从而支持“它的详情”、“那个设备”等连续追问。

## 7. 全局工程要求（编码、测试、提交）

### 7.1 编码要求
- 所有新增与修改文件必须使用 `UTF-8` 编码。
- 代码注释、接口文档、技术说明统一使用中文。
- 变更遵循“最小必要改动”原则，禁止与需求无关的大规模重构。
- 禁止硬编码密钥、令牌、密码、租户隔离字段等敏感信息。
- 涉及接口入参的新增字段，必须同步补充校验逻辑与错误处理。

### 7.2 测试要求
- 后端代码变更后，至少执行一次：`mvn -B test`。
- 前端代码变更后，至少执行一次：`cd web_src && npx vue-tsc --noEmit` 与 `cd web_src && npm run build`。
- 修复缺陷时应补充或更新对应测试，避免同类问题回归。
- 若存在无法执行的测试项，必须在交付说明中明确原因和影响范围。

### 7.3 提交要求
- 提交信息使用统一格式：`type(scope): 简要说明`（示例：`fix(chat): 修复历史重命名类型错误`）。
- 单次提交应聚焦单一主题，避免将无关改动混入同一提交。
- 提交前必须确认工作区无调试垃圾文件、临时日志或敏感配置泄露。
- 提交说明必须包含已执行的验证命令与结果摘要。
- 本地测试通过后方可执行 `git push`。
- 默认推送到功能分支并发起 PR，未经明确授权不得直接推送 `master/main`。
- 推送后必须检查 CI 状态；若 CI 失败，需修复后再申请合并。
