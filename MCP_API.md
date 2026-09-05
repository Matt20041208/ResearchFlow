# ResearchFlow MCP 接口文档

## 1. 功能说明

ResearchFlow 提供 MCP Server，可以把科研平台能力暴露给其他 AI Agent、MCP Client、桌面 AI 工具或自动化程序调用。

当前提供 4 个只读工具：

| Tool | 功能 |
|---|---|
| `search_papers` | 检索 Crossref 公开论文 |
| `search_workspace_knowledge` | 检索指定 Workspace 私有知识 |
| `get_research_citations` | 查询研究任务引用来源 |
| `get_research_trace` | 查询 DAG、Agent 节点和 ReAct 执行链路 |

## 2. 开启 MCP Server

在环境变量或 `.env` 中配置：

```bash
RESEARCH_FLOW_MCP_SERVER_ENABLED=true
```

启动后，MCP SSE 连接地址为：

```text
http://localhost:8080/mcp/sse
```

远程部署时替换为实际域名：

```text
https://research.example.com/mcp/sse
```

`localhost` 只能被本机访问。如果由其他机器调用，需要开放服务端口并配置域名、反向代理和 HTTPS。

## 3. MCP 调用流程

调用方不需要硬编码所有工具参数，标准 MCP Client 可以自动发现工具 Schema：

```text
连接 /mcp/sse
  -> tools/list
  -> 获取工具名称、描述和 inputSchema
  -> tools/call
  -> 获取工具结果
```

MCP 的底层请求使用 JSON-RPC。工具调用的通用格式如下：

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "工具名称",
    "arguments": {}
  }
}
```

## 4. 工具详细定义

### 4.1 search_papers

**用途**

根据关键词检索 Crossref 论文元数据。

**参数**

| 参数 | 类型 | 必填 | 范围 | 说明 |
|---|---|---:|---|---|
| `query` | string | 是 | 非空 | 论文搜索关键词 |
| `limit` | integer | 否 | 1-10 | 返回数量，默认 5 |

**调用示例**

```json
{
  "name": "search_papers",
  "arguments": {
    "query": "multi-agent research workflow",
    "limit": 5
  }
}
```

**调用方视角的业务流程**

```text
MCP Client
  -> ResearchFlow MCP Server
  -> SourceSearchAgent
  -> Crossref API
  -> 论文结果
```

**结果内容**

返回论文来源列表，包含：

- 来源 ID/DOI
- 来源类型
- 论文标题
- 论文 URL
- 摘要或摘录
- 置信度

如果 Crossref 不可用，ResearchFlow 会返回离线降级来源。该来源用于保证流程可运行，不应当作真实论文证据。

### 4.2 search_workspace_knowledge

**用途**

检索指定 Workspace 下的私有知识文档。

**参数**

| 参数 | 类型 | 必填 | 范围 | 说明 |
|---|---|---:|---|---|
| `workspaceId` | string | 是 | 非空 | 目标 Workspace ID |
| `userId` | string | 是 | 非空 | 当前用户 ID，当前为 MVP 占位身份 |
| `query` | string | 是 | 非空 | 知识检索关键词 |
| `limit` | integer | 否 | 1-10 | 返回数量，默认 5 |

**调用示例**

```json
{
  "name": "search_workspace_knowledge",
  "arguments": {
    "workspaceId": "workspace-001",
    "userId": "user-001",
    "query": "数据安全规范",
    "limit": 5
  }
}
```

**服务端处理流程**

```text
接收参数
  -> 校验参数
  -> 校验 userId 是否拥有 Workspace VIEWER 权限
  -> KnowledgeService.search()
  -> 查询知识文档
  -> 返回匹配来源
```

**当前检索方式**

当前版本使用关键词评分，不是向量检索：

- 标题命中分数更高。
- 正文命中分数较低。
- 中文查询会生成部分双字词。
- 按分数排序并返回前 N 条。

**安全说明**

当前 `userId` 是请求参数，仅适合本地 MVP 和演示。生产环境必须从已验证的 JWT/OIDC 身份中获取用户，不应信任调用方自行传入的 `userId`。

### 4.3 get_research_citations

**用途**

查询某个研究任务使用的来源和引用信息。

**参数**

| 参数 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `taskId` | string | 是 | 研究任务 ID |
| `userId` | string | 是 | 当前用户 ID |

**调用示例**

```json
{
  "name": "get_research_citations",
  "arguments": {
    "taskId": "task-001",
    "userId": "user-001"
  }
}
```

**服务端处理流程**

```text
taskId
  -> 查询任务所属 Workspace
  -> 校验用户 VIEWER 权限
  -> ResearchTaskService.citations()
  -> 查询引用记录
  -> 返回引用列表
```

**返回字段**

```json
[
  {
    "number": 1,
    "sourceId": "10.xxxx/example",
    "sourceType": "CROSSREF",
    "title": "Multi-Agent Research Workflow",
    "url": "https://doi.org/10.xxxx/example",
    "excerpt": "论文摘要或内部知识摘录",
    "confidence": 0.75
  }
]
```

### 4.4 get_research_trace

**用途**

查询研究任务的完整执行链路。

**参数**

| 参数 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `taskId` | string | 是 | 研究任务 ID |
| `userId` | string | 是 | 当前用户 ID |

**调用示例**

```json
{
  "name": "get_research_trace",
  "arguments": {
    "taskId": "task-001",
    "userId": "user-001"
  }
}
```

**返回结构**

```json
{
  "taskId": "task-001",
  "plan": {
    "goal": "研究多 Agent 系统",
    "nodes": [
      {
        "id": "writer",
        "agent": "writer-agent",
        "dependsOn": ["moderation", "sources"]
      }
    ]
  },
  "nodes": [
    {
      "nodeId": "writer",
      "agent": "writer-agent",
      "status": "COMPLETED",
      "inputSummary": "比较结果和来源",
      "outputSummary": "报告已生成",
      "durationMs": 1500
    }
  ],
  "steps": [
    {
      "nodeId": "writer",
      "agent": "writer-agent",
      "iteration": 1,
      "action": "TOOLS:llm-completion",
      "observation": "报告已生成并包含引用",
      "decision": "COMPLETE",
      "rationale": "结果满足节点目标",
      "durationMs": 1500
    }
  ]
}
```

**字段说明**

- `plan`：任务的 DAG 计划。
- `nodes`：每个 Agent 节点的整体执行结果。
- `steps`：每个节点内部的 ReAct 执行记录。
- `inputSummary`：输入摘要，不是完整上下文。
- `outputSummary`：输出摘要，不是完整输出。
- `decision`：`COMPLETE`、`RETRY` 或 `FAIL`。
- `rationale`：简短、可审计的决策理由。

## 5. Tool 发现结果

标准 MCP Client 连接后，可以通过 `tools/list` 发现如下工具：

```text
search_papers
search_workspace_knowledge
get_research_citations
get_research_trace
```

调用方不需要了解 ResearchFlow 内部的：

- Java Controller。
- Service 类。
- JPA Entity。
- H2 表结构。
- Crossref 请求细节。

调用方只需要读取 MCP 返回的：

```text
name
description
inputSchema
```

其中 `inputSchema` 会说明参数类型、必填字段和部分取值范围。

## 6. 错误处理

常见错误包括：

| 场景 | 处理方式 |
|---|---|
| 缺少必填参数 | 返回 `isError=true` |
| `limit` 不是整数 | 返回参数错误 |
| `limit` 超出范围 | 服务端限制到 1-10 |
| Workspace 无权限 | 返回权限错误 |
| taskId 不存在 | 返回资源错误 |
| Crossref 不可用 | 返回离线降级来源 |
| MCP 工具不存在 | Client 侧调用失败 |
| MCP Server 未启动 | 无法建立连接 |

MCP 返回中的 `isError=true` 表示工具调用失败，不等于 HTTP 服务本身宕机。

## 7. ResearchFlow 作为 MCP Client

ResearchFlow 也支持调用其他 MCP Server 提供的工具。

配置：

```yaml
spring:
  ai:
    mcp:
      client:
        enabled: true
        type: SYNC
        request-timeout: 20s
        sse:
          connections:
            research-tools:
              url: http://localhost:9000
```

外部工具会通过 `tools/list` 被发现，并在内部显示为：

```text
mcp:search_web
mcp:read_web_page
```

当 ReAct Supervisor 返回：

```json
{
  "decision": "TOOL",
  "reason": "需要补充外部资料",
  "nextInstruction": "",
  "tool": "mcp:search_web",
  "arguments": {
    "query": "AI healthcare research"
  }
}
```

系统执行：

```text
ReActNodeExecutor
  -> ToolRegistry 校验工具
  -> McpToolGateway 调用远程 MCP Server
  -> 返回工具结果
  -> 结果成为下一轮 Observation
```

## 8. 当前安全边界

当前工具主要是只读工具，但生产环境仍需补充：

1. JWT/OIDC 认证，不信任请求中的 `userId`。
2. MCP Server 访问鉴权和 HTTPS。
3. Workspace 数据隔离。
4. 输入参数的完整 JSON Schema 校验。
5. 工具调用超时和重试。
6. 工具调用日志和审计记录。
7. 私有内容脱敏和模型数据出境控制。
8. 写操作工具的人工审批和幂等控制。
9. MCP Server 白名单和工具风险分级。
10. Prompt Injection 防护。
