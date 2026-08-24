# ResearchFlow

ResearchFlow 是一个面向科研研究任务的多 Agent 编排平台后端。

## 核心链路

```text
用户目标
  -> System Agent 生成结构化 DAG
  -> Agent Registry 按名称和能力路由
  -> Scheduler 并行执行同层 Sub-Agent
  -> Tool Registry 校验工具权限
  -> Writer Agent 汇总 Markdown 报告
```

`SourceSearchAgent` 默认调用 Crossref 检索论文；网络不可用时自动降级到离线来源。模型访问由 Spring AI `ChatClient` 统一管理，System Agent 使用结构化输出生成 `SystemPlan`，Writer Agent 通过同一个模型客户端生成报告。任务和 Agent 事件持久化到本地 H2 文件数据库。

## 启动

要求 JDK 17 和 Maven 3.9+：

```bash
mvn spring-boot:run
```

模型默认关闭，因此没有 API Key 也能运行完整的确定性降级链路。启用 OpenAI 或 OpenAI 兼容服务：

```bash
export OPENAI_API_KEY=your-key
export SPRING_AI_MODEL_CHAT=openai
export OPENAI_BASE_URL=https://api.openai.com
export OPENAI_MODEL=gpt-4o-mini
mvn spring-boot:run
```

DeepSeek 等 OpenAI 兼容服务只需替换 `OPENAI_BASE_URL` 和 `OPENAI_MODEL`。

## API

```bash
curl -X POST http://localhost:8080/api/research/tasks \
  -H 'Content-Type: application/json' \
  -d '{"question":"大模型在金融风控中的应用有哪些？"}'
```

查询任务：`GET /api/research/tasks/{taskId}`

任务列表：`GET /api/research/tasks`

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

## 当前设计

- Spring Boot 3.5 + Spring AI 1.0.3 提供模型自动配置、`ChatClient` 和结构化实体映射。
- System Agent 通过 `ChatClient.call().entity(SystemPlan.class)` 生成结构化 DAG，输出非法或未配置模型时使用确定性规划器。
- Agent Registry 保存每个 Sub-Agent 的能力与所需工具，编排器不再硬编码调用链。
- DAG Scheduler 自动识别依赖已满足的节点，并行执行同一层节点。
- Search Agent 获取外部论文来源。
- Evidence、Comparison、Risk、Writer Agent 分别负责证据提取、比较、风险分析和报告汇总。
- Comparison Agent 汇总比较结果。
- Writer Agent 通过 Spring AI 生成报告，失败时自动降级。
- Tool Registry 将工具划分为只读、外部调用和高风险；高风险工具必须人工审批。
- H2 文件数据库保存任务、状态、报告和事件，应用重启后仍可查询。
- 支持 SSE 执行追踪、取消、失败重试、最大尝试次数、中断恢复和审批恢复。

## 目录结构

```text
src/main/java/com/researchflow
├── agent          # 领域 Agent
│   └── runtime    # System Agent、注册中心、上下文和 DAG 调度器
├── controller     # REST/SSE 接口
├── llm            # Spring AI ChatClient 适配层
├── model          # API 模型
├── persistence     # JPA 持久化模型
├── service        # System Agent 与任务生命周期
└── tool           # 工具注册、风险等级和审批策略
```

## 下一步

- 接入 Semantic Scholar 作为第二论文来源并做结果去重。
- 用 PostgreSQL + pgvector 替换本地 H2，增加知识库检索。
- 将发布工具连接到真实文档平台，并保留审批审计。
- 增加前端 DAG 可视化和 Agent 运行详情页面。
