# ResearchFlow

ResearchFlow 是一个面向科研研究任务的多 Agent 编排平台 MVP。

## 当前链路

```text
研究问题 -> Planner -> Source Search -> Evidence -> Comparison -> Writer -> Markdown 报告
```

当前 Agent 使用演示数据，后续替换 `SourceSearchAgent` 为论文检索适配器，并在 Agent 实现中接入真实模型。

## 启动

要求 JDK 17 和 Maven 3.9+：

```bash
mvn spring-boot:run
```

## API

```bash
curl -X POST http://localhost:8080/api/research/tasks \
  -H 'Content-Type: application/json' \
  -d '{"question":"大模型在金融风控中的应用有哪些？"}'
```

查询任务：`GET /api/research/tasks/{taskId}`

订阅实时 Agent 事件：`GET /api/research/tasks/{taskId}/events`

## 下一步

- 接入 Semantic Scholar / Crossref 论文检索。
- 接入 Spring AI，并增加结构化输出校验。
- 将固定链路升级为可配置 DAG 工作流。
- 增加 PostgreSQL、向量检索、任务持久化和人工确认节点。
