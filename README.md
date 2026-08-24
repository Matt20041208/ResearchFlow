# ResearchFlow

ResearchFlow 是一个面向科研研究任务的多 Agent 编排平台后端。

## 当前链路

```text
研究问题 -> Planner -> Source Search -> Evidence -> Comparison -> Writer -> Markdown 报告
```

`SourceSearchAgent` 默认调用 Crossref 检索论文；网络不可用时自动降级到离线来源。配置 `OPENAI_API_KEY` 后，`WriterAgent` 会调用 OpenAI 兼容接口生成报告，否则使用确定性模板降级。任务和 Agent 事件持久化到本地 H2 文件数据库。

## 启动

要求 JDK 17 和 Maven 3.9+：

```bash
mvn spring-boot:run
```

可选模型配置：

```bash
export OPENAI_API_KEY=your-key
export OPENAI_BASE_URL=https://api.openai.com/v1
export OPENAI_MODEL=gpt-4o-mini
```

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

## 当前设计

- System Agent 负责任务生命周期和 Agent 顺序控制。
- Search Agent 获取外部论文来源。
- Evidence Agent 对来源并行提取证据。
- Comparison Agent 汇总比较结果。
- Writer Agent 优先调用模型，失败时自动降级。
- H2 文件数据库保存任务、状态、报告和事件，应用重启后仍可查询。
- `workflow.json` 定义 Agent DAG，启动时执行拓扑排序并检测循环依赖。
- 支持任务取消、失败重试、最大尝试次数和中断任务恢复。

## 目录结构

```text
src/main/java/com/researchflow
├── agent          # 领域 Agent
├── controller     # REST/SSE 接口
├── llm            # OpenAI 兼容客户端
├── model          # API 模型
├── persistence     # JPA 持久化模型
├── service        # System Agent 与任务生命周期
└── workflow       # DAG 定义与拓扑校验
```

## 下一步

- 接入 Semantic Scholar / Crossref 论文检索。
- 接入 Spring AI，并增加结构化输出校验。
- 将固定链路升级为可配置 DAG 工作流。
- 增加 PostgreSQL、向量检索、任务持久化和人工确认节点。
