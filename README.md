# ResearchFlow

ResearchFlow 是一个面向科研和企业知识工作的多 Agent 编排平台后端。

## 第一阶段商业能力

- 私有知识库：按 workspace 隔离，支持 TXT、Markdown、DOCX、PDF 上传与文本检索。
- 混合来源研究：Crossref 外部论文和私有知识库并行检索，自动合并去重。
- 引用溯源：保存来源编号、类型、链接、原文摘录和置信度。
- 多格式交付：报告可导出 Markdown、Word 和 PDF。

## 第二阶段商业能力

- 团队空间：Workspace、Owner/Editor/Viewer 角色和数据访问控制。
- 报告协作：不可变版本历史、成员评论和作者追踪。
- 情报订阅：周期主题、自动触发多 Agent 报告、手动立即运行和启停控制。
- 套餐配额：Free/Team/Enterprise 报告、文档和订阅限制。
- 用量计量：报告、文档、导出、订阅运行、估算 Token 与成本统计。当前成本按每千 Token `$0.001` 估算，后续替换为模型供应商账单元数据。

## 第三阶段能力：链路可观测与 AI 场景推演

- 结构化执行链路：每次任务持久化 System Agent DAG 与每个节点的输入摘要、输出摘要、耗时和状态。
- 链路 API：`GET /api/research/tasks/{taskId}/trace` 返回完整 DAG 和节点执行记录。
- AI 场景推演：System Agent 读取真实链路，系统性组合节点异常，生成人工容易遗漏的刁钻测试场景。
- 场景资产：场景保存为 SUGGESTED/APPROVED/DISMISSED 状态，可审阅、采纳并沉淀为回归资产。
- 受控注入验证：已采纳场景可转换为 DELAY、ERROR、EMPTY_RESULT 规则并异步重跑原始 DAG，不修改业务代码。
- 开发者结论：验证完成后可标记 VERIFIED、DEFECT_FOUND 或 INVALID，形成可复用质量资产。
- 自动 Oracle：结合场景期望、严格注入规则、实际异常和节点轨迹生成 EXPECTED_BEHAVIOR、POTENTIAL_DEFECT 或 INCONCLUSIVE 判定，开发者保留最终裁决权。
- ReAct 节点循环：每个 DAG 节点执行后形成 Action、Observation、Decision；声明模型工具的生成式 Agent 由 Spring AI 监督器决定完成、带修正指令重试或失败，并受最大轮次与时间预算约束。
- 多角色讨论收敛：Comparison 完成后并行派遣 Critic 与 Fact Checker，再由 Moderator 按证据质量形成共识和写作约束，最后交给 Writer。
- MCP Client 工具扩展：可选连接外部 MCP Server，动态发现 MCP Tools，并通过 ReAct 的 TOOL 决策把工具结果作为下一轮 Observation；MCP 默认关闭，工具调用仍经过 Tool Registry 授权。

## 外部 Agent 链路接入

ResearchFlow 不只分析自身链路。其他 Agent 系统可以通过 `POST /api/external-traces` 上报一次结构化执行链路，经过 Workspace 权限校验、节点依赖校验和 DAG 环检测后，直接使用 AI 场景推演。

最小上报示例：

```bash
curl -X POST http://localhost:8080/api/external-traces \
  -H 'X-User-Id: owner-1' \
  -H 'Content-Type: application/json' \
  -d '{
    "workspaceId":"{workspaceId}",
    "name":"车载 Agent 请求链路",
    "sourceSystem":"AIOS-Ark",
    "nodes":[
      {"nodeId":"intent","agent":"intent-agent","dependsOn":[],"status":"SUCCESS","input":"用户请求","output":"导航意图","durationMs":42},
      {"nodeId":"map-api","agent":"map-service","dependsOn":["intent"],"status":"TIMEOUT","error":"upstream timeout","durationMs":3000,"externalBoundary":true,"asyncNode":true}
    ]
  }'
```

节点 `status` 支持 `SUCCESS`、`FAILED`、`TIMEOUT`、`CANCELLED`、`DEGRADED`。节点最多 200 个，输入输出摘要有长度限制，依赖不存在或循环依赖会被拒绝。

- 链路列表：`GET /api/external-traces?workspaceId={workspaceId}`
- 链路详情：`GET /api/external-traces/{traceId}`
- 生成场景：`POST /api/external-traces/{traceId}/scenarios/generate`
- 场景列表：`GET /api/external-traces/{traceId}/scenarios`
- 删除链路：`DELETE /api/external-traces/{traceId}`
- 推演示例：模型能从真实链路中发现"上游 64 秒空档""合并节点无相关性过滤""异步分支时序错位"等组合风险。

## 核心链路

```text
用户目标
  -> System Agent 生成结构化 DAG
  -> Agent Registry 按名称和能力路由
  -> Scheduler 并行执行同层 Sub-Agent
  -> Tool Registry 校验工具权限
  -> 节点内 ReAct 根据观察结果收敛或有限重试
  -> ReAct 按需调用 MCP Tool 并消费 Observation
  -> Critic + Fact Checker 并行审查
  -> Moderator 收敛分歧与证据边界
  -> Writer Agent 汇总 Markdown 报告
```

`SourceSearchAgent` 默认调用 Crossref 检索论文；网络不可用时自动降级到离线来源。模型访问由 Spring AI `ChatClient` 统一管理，System Agent 使用结构化输出生成 `SystemPlan`，Writer Agent 通过同一个模型客户端生成报告。任务和 Agent 事件持久化到本地 H2 文件数据库。

## 启动

要求 JDK 17 和 Node.js 18+。仓库包含 Maven Wrapper，无需预装 Maven。

一键启动前后端：

```bash
./dev.sh
```

访问 `http://localhost:5173`。首次进入输入一个用户 ID，然后创建 Workspace。

也可以分别启动：

```bash
./mvnw spring-boot:run

cd frontend
npm install
npm run dev
```

模型默认关闭，因此没有 API Key 也能运行完整的确定性降级链路。启用 DeepSeek：

```bash
cp .env.example .env
# 编辑 .env，填写 OPENAI_API_KEY
./dev.sh
```

默认 DeepSeek 配置使用 `https://api.deepseek.com` 和 `deepseek-v4-pro`。可通过 `/models` 接口确认当前账号可用的准确模型 ID。`dev.sh` 会自动加载根目录 `.env`，真实密钥不会提交到 Git。

### MCP Client 工具扩展

ResearchFlow 可选作为 MCP Client 连接远程 MCP Server，动态发现外部工具。默认关闭；启用远程 SSE MCP Server：

```bash
RESEARCH_FLOW_MCP_ENABLED=true
RESEARCH_FLOW_MCP_URL=http://localhost:9000
```

也可以将配置写入根目录 `.env` 后使用 `./dev.sh`。发现的工具会以 `mcp:<tool-name>` 形式进入 ReAct Supervisor 的可用工具列表；模型返回 `TOOL` 决策后，Tool Registry 校验工具存在并调用 MCP Server，返回结果作为下一轮 Observation。当前 MCP 工具默认为 `EXTERNAL_CALL`，真实写操作仍应在生产环境增加审批、参数校验、超时、审计和幂等控制。

ResearchFlow 也可以作为 MCP Server 对外提供科研工具。设置 `RESEARCH_FLOW_MCP_SERVER_ENABLED=true` 后，服务会通过 `/mcp/sse` 暴露 `search_papers`、`search_workspace_knowledge`、`get_research_citations` 和 `get_research_trace`；Workspace 查询工具要求调用方传入 `workspaceId` 和 `userId`，服务端会复用 Workspace Viewer 权限校验。当前这是 SSE 传输的只读工具示例，生产环境仍应使用 JWT/OIDC 身份，不应信任调用方直接传入的 userId。

详细参数结构、调用示例和错误处理见 [`MCP_API.md`](MCP_API.md)。

## API

商业 API 使用 `X-User-Id` 标识当前用户。该请求头是本地 MVP 的认证占位，生产环境应替换为 JWT、OIDC 或企业 SSO。

先创建团队空间：

```bash
curl -X POST http://localhost:8080/api/workspaces \
  -H 'X-User-Id: owner-1' \
  -H 'Content-Type: application/json' \
  -d '{"name":"Research Team"}'
```

```bash
curl -X POST http://localhost:8080/api/research/tasks \
  -H 'X-User-Id: owner-1' \
  -H 'Content-Type: application/json' \
  -d '{"question":"大模型在金融风控中的应用有哪些？","workspaceId":"{workspaceId}"}'
```

查询任务：`GET /api/research/tasks/{taskId}`

任务列表：`GET /api/research/tasks?workspaceId={workspaceId}`

订阅实时 Agent 事件：`GET /api/research/tasks/{taskId}/events`

取消任务：`POST /api/research/tasks/{taskId}/cancel`

重试任务：`POST /api/research/tasks/{taskId}/retry`

批准待审批工具：

```bash
curl -X POST http://localhost:8080/api/research/tasks/{taskId}/approve \
  -H 'Content-Type: application/json' \
  -d '{"tool":"report-publish"}'
```

预览 System Agent 计划：`POST /api/system-agent/plan`

查看 Sub-Agent 能力：`GET /api/system-agent/agents`

查看工具和风险等级：`GET /api/system-agent/tools`

### 私有知识库

上传文件：

```bash
curl -X POST http://localhost:8080/api/knowledge/documents/upload \
  -H 'X-User-Id: owner-1' \
  -F 'workspaceId={workspaceId}' \
  -F 'title=内部风控规范' \
  -F 'file=@./risk-policy.pdf'
```

也可以直接提交文本：

```bash
curl -X POST http://localhost:8080/api/knowledge/documents \
  -H 'X-User-Id: owner-1' \
  -H 'Content-Type: application/json' \
  -d '{"workspaceId":"{workspaceId}","title":"内部规范","content":"规范正文"}'
```

- 文档列表：`GET /api/knowledge/documents?workspaceId=team-a`
- 文档检索：`GET /api/knowledge/documents/search?workspaceId=team-a&query=风险`
- 删除文档：`DELETE /api/knowledge/documents/{id}`

### 引用与导出

- 引用详情：`GET /api/research/tasks/{taskId}/citations`
- Markdown：`GET /api/research/tasks/{taskId}/export?format=markdown`
- Word：`GET /api/research/tasks/{taskId}/export?format=docx`
- PDF：`GET /api/research/tasks/{taskId}/export?format=pdf`

### 团队协作与计费

- 我的空间：`GET /api/workspaces`
- 添加成员：`PUT /api/workspaces/{workspaceId}/members`
- 成员列表：`GET /api/workspaces/{workspaceId}/members`
- 修改套餐：`PUT /api/workspaces/{workspaceId}/plan?tier=TEAM`
- 报告版本：`GET /api/research/tasks/{taskId}/versions`
- 添加评论：`POST /api/research/tasks/{taskId}/comments`
- 评论列表：`GET /api/research/tasks/{taskId}/comments`
- 用量统计：`GET /api/billing/workspaces/{workspaceId}/usage`

### 链路与 AI 场景推演

- 结构化链路：`GET /api/research/tasks/{taskId}/trace`
- 生成场景：`POST /api/research/tasks/{taskId}/scenarios/generate`
- 场景列表：`GET /api/research/tasks/{taskId}/scenarios`
- 场景审阅：`PUT /api/research/tasks/{taskId}/scenarios/{scenarioId}/status?status=APPROVED`
- 删除场景：`DELETE /api/research/tasks/{taskId}/scenarios/{scenarioId}`
- 执行验证：`POST /api/research/tasks/{taskId}/scenarios/{scenarioId}/validations`
- 验证列表：`GET /api/research/tasks/{taskId}/scenarios/{scenarioId}/validations`
- 验证结论：`PUT /api/research/tasks/{taskId}/scenarios/{scenarioId}/validations/{runId}/verdict?verdict=DEFECT_FOUND`

### 主题订阅

```bash
curl -X POST http://localhost:8080/api/subscriptions \
  -H 'X-User-Id: owner-1' \
  -H 'Content-Type: application/json' \
  -d '{"workspaceId":"{workspaceId}","name":"AI 风险周报",\
       "question":"跟踪大模型风险最新进展","intervalMinutes":10080}'
```

- 订阅列表：`GET /api/subscriptions?workspaceId={workspaceId}`
- 立即执行：`POST /api/subscriptions/{id}/run`
- 启停订阅：`PUT /api/subscriptions/{id}/enabled?value=false`

## 当前设计

- Spring Boot 3.5 + Spring AI 1.0.3 提供模型自动配置、`ChatClient` 和结构化实体映射。
- System Agent 通过 `ChatClient.call().entity(SystemPlan.class)` 生成结构化 DAG，输出非法或未配置模型时使用确定性规划器。
- 每个节点由 ReAct 执行器包装；声明 `llm-completion` 的生成式 Agent 使用 Spring AI 结构化输出生成 COMPLETE、RETRY、FAIL 决策，其他节点确定性单轮收敛，避免私有观察被隐式发送给模型。
- Agent Registry 保存每个 Sub-Agent 的能力与所需工具，编排器不再硬编码调用链。
- DAG Scheduler 自动识别依赖已满足的节点，并行执行同一层节点。
- Critic Agent 与 Fact Checker Agent 并行检查逻辑、来源摘录与证据映射，Moderator Agent 在 Writer 前形成可审计共识；Writer 对模型输出的引用编号做边界校验。
- External Search Agent 和 Private Knowledge Agent 并行检索，Source Merge Agent 去重后统一进入证据链路。
- Search Agent 获取外部论文来源。
- Evidence、Comparison、Risk、Writer Agent 分别负责证据提取、比较、风险分析和报告汇总。
- Comparison Agent 汇总比较结果。
- Writer Agent 通过 Spring AI 生成报告，失败时自动降级。
- Tool Registry 将工具划分为只读、外部调用和高风险；高风险工具必须人工审批。
- H2 文件数据库保存任务、状态、报告和事件，应用重启后仍可查询。
- 支持 SSE 执行追踪、取消、失败重试、最大尝试次数、中断恢复和审批恢复。

## 目录结构

```text
ResearchFlow
├── frontend       # React + TypeScript 研究指挥台
└── src/main/java/com/researchflow
├── agent          # 领域 Agent
│   └── runtime    # System Agent、注册中心、上下文和 DAG 调度器
├── controller     # REST/SSE 接口
├── llm            # Spring AI ChatClient 适配层
├── knowledge      # 私有知识库解析、管理和检索
├── export         # Markdown、Word、PDF 导出
├── externaltrace  # 外部链路 Schema、校验和接入
├── scenario       # AI 场景推演和资产管理
├── injection      # 严格注入规则和运行时注入
├── validation     # 异步验证、自动 Oracle 和人工裁决
├── workspace      # 团队空间、成员与 RBAC
├── collaboration  # 报告版本和评论
├── subscription   # 主题订阅和定时调度
├── billing        # 套餐、配额、用量和成本
├── model          # API 模型
├── persistence     # JPA 持久化模型
├── service        # System Agent 与任务生命周期
└── tool           # 工具注册、风险等级和审批策略
```

## 下一步

- 接入 Semantic Scholar 作为第二论文来源并做结果去重。
- 用 PostgreSQL + pgvector 替换本地 H2，增加知识库检索。
- 将发布工具连接到真实文档平台，并保留审批审计。
- 接入 JWT/OIDC/企业 SSO，替换 `X-User-Id` 占位认证。
- 用分布式锁保护多实例订阅调度，并接入真实支付网关。
- 将 APPROVED 场景导出为 JUnit/JSON 回归测试资产。
- 增加 OpenTelemetry/Java SDK 自动上报外部链路，减少手动 JSON 接入。
