# ResearchFlow 后端与 Agent 面试讲解手册

> 适用代码版本：`27f5ff2 feat: add react execution and agent deliberation`
>
> 这份文档用于解释当前仓库真实实现。带有“生产化方案”字样的内容是演进方向，不代表已经完成。

## 1. 项目一句话定位

ResearchFlow 是一个面向科研和企业知识工作的多 Agent 研究平台。用户提交研究目标后，系统使用 Spring AI 生成受约束的 DAG 计划，调度多个 Agent 完成外部检索、私有知识检索、证据整理、比较分析、多角色审查和报告写作，并持久化任务事件、节点轨迹、ReAct 步骤和引用来源。系统还可以根据真实执行链路生成故障场景，通过受控注入和自动 Oracle 辅助验证 Agent 工作流。

它解决的不是“如何再做一个聊天框”，而是以下工程问题：

1. 如何把复杂目标拆成可执行、可并行、可追踪的任务图。
2. 如何限制模型生成的计划只能选择已注册 Agent 和工具，防止任意执行。
3. 如何让多个角色对结论进行批判、核验和收敛。
4. 如何在模型或外部网络不可用时保持系统可演示、可测试。
5. 如何记录 Agent 每一步行为，并把真实链路转换为质量验证资产。
6. 如何在团队 Workspace 中管理知识、报告、成员、订阅和用量。

## 2. 面试开场

### 2.1 一分钟版本

> ResearchFlow 是我基于 Spring Boot 3.5 和 Spring AI 1.0.3 实现的多 Agent 研究编排平台。它不是把所有能力放进一个 Prompt，而是先由 System Agent 生成结构化 DAG，再经过严格的节点和依赖契约校验。调度器会并行执行外部检索和私有知识检索，也会并行执行 Critic 与 Fact Checker，最后由 Moderator 收敛分歧并交给 Writer 生成报告。生成式 Agent 外面还有一个有最大轮次和时间预算的 ReAct 监督循环，可以根据观察结果决定完成、带修正指令重试或失败。任务计划、事件、节点摘要、ReAct 决策和引用都会持久化，并支持 SSE、取消、重试、工具审批、故障注入和自动验证。当前是工程化 MVP，生产化还需要补真实认证、持久队列、数据库迁移、向量检索和完整可观测性。

### 2.2 三分钟版本

先讲业务问题：传统 ChatBot 通常是一次模型调用，复杂任务的资料来源、执行步骤、失败位置和结论依据都不透明。ResearchFlow 把研究任务建模成一张受控 DAG，让每个阶段都有明确输入、输出和责任角色。

再讲执行过程：

1. 用户创建研究任务，Controller 先做 Workspace 权限检查。
2. Service 在事务中保存任务，事务提交后再交给本地线程池。
3. System Agent 通过 Spring AI `ChatClient` 生成 `SystemPlan`，模型不可用时使用确定性计划。
4. `AgentRegistry` 将计划中的 Agent 名称映射到具体实现和工具声明。
5. `MultiAgentOrchestrator` 找到依赖已满足的节点，同层并行执行。
6. 生成式 Agent 由 `ReActNodeExecutor` 包装，形成 Action、Observation、Decision 循环。
7. Comparison 后并行执行 Critic 和 Fact Checker，Moderator 根据证据质量形成共识。
8. Writer 只使用提供的来源生成报告，并校验引用编号是否越界。
9. 系统保存报告、引用、事件、节点轨迹和每轮 ReAct 步骤。

最后讲质量闭环：平台能读取真实 DAG 轨迹，由场景 Agent 生成延迟、异常、空结果等组合场景；开发者批准后，验证服务会在移除发布节点的安全计划上重新执行并注入故障，自动 Oracle 给出预期行为、潜在缺陷或无法判断，再由开发者做最终裁决。

## 3. 技术栈与职责

| 技术 | 在项目中的作用 |
|---|---|
| Java 17 | Record、文本块、Stream、`HttpClient`、并发工具 |
| Spring Boot 3.5 | Web、配置、依赖注入、生命周期、定时任务 |
| Spring AI 1.0.3 | `ChatModel`、`ChatClient`、文本生成、结构化实体映射 |
| Spring Web MVC | REST API、Multipart、`SseEmitter` |
| Spring Data JPA | 实体映射、Repository、事务数据访问 |
| H2 File Database | MVP 文件数据库，重启后保留数据 |
| Apache POI | DOCX 文档解析与报告导出 |
| PDFBox | PDF 文本解析 |
| OpenPDF | PDF 报告生成 |
| JDK HttpClient | 调用 Crossref 论文元数据 API |
| Maven Wrapper | 固定 Maven 运行方式 |
| JUnit 5 + Mockito | Agent、服务、规则和降级逻辑测试 |

后端入口是 [ResearchFlowApplication](src/main/java/com/researchflow/ResearchFlowApplication.java)，其中：

- `@SpringBootApplication` 启动自动配置和组件扫描。
- `@EnableScheduling` 启用主题订阅定时扫描。

## 4. 总体架构

```text
React / API Client
        |
        | REST + SSE + X-User-Id
        v
Controller Layer
        |
        | Workspace RBAC
        v
Domain Services
        |
        +--------------------+
        |                    |
        v                    v
Agent Runtime            Business Modules
        |                    |
        v                    v
Spring AI / Crossref     Spring Data JPA
        |                    |
        +---------+----------+
                  v
             H2 File DB
```

代码使用“按业务能力分包”，而不是把所有类机械地放进 controller/service/dao 三个目录：

```text
com.researchflow
├── agent              具体领域 Agent
│   └── runtime        DAG、Registry、Context、ReAct、Trace
├── billing            套餐、配额、用量
├── collaboration      报告版本与评论
├── controller         REST/SSE 接口
├── export             Markdown、DOCX、PDF 导出
├── externaltrace      外部 Agent 链路接入
├── injection          受控故障注入
├── knowledge          私有知识解析、管理和搜索
├── llm                Spring AI 适配层
├── model              API DTO 和状态模型
├── persistence        JPA Entity 和 Repository
├── scenario           场景生成与管理
├── service            研究任务生命周期
├── subscription       订阅与调度
├── tool               工具描述和审批策略
├── validation         验证执行与自动 Oracle
└── workspace          Workspace 与 RBAC
```

这种分包方式的优点是一个业务能力的 Service、DTO、枚举可以放在一起，模块边界更容易理解。当前 `persistence` 仍是共享包，所以它还不是严格的领域驱动模块，也不是多模块 Maven 工程。

## 5. 核心业务链路

默认研究 DAG：

```text
plan
  |
  +----------------------+
  v                      v
externalSources      privateSources
  |                      |
  +----------+-----------+
             v
           sources
             |
             v
          evidence
             |
             v
         comparison
             |
      +------+-------+----------------+
      v              v                v
   critic        factCheck       risk(可选)
      +--------------+----------------+
                     v
                 moderation
                     |
                     v
                   writer
                     |
                     v
              publication(可选)
```

两个重要并行点：

- `externalSources` 和 `privateSources` 同时执行，减少混合检索总耗时。
- `critic` 和 `factCheck` 同时执行，分别检查逻辑与证据，不互相阻塞。

业务场景示例：用户提出“分析企业采用多 Agent 系统的风险和治理方法”。系统同时检索 Crossref 公开论文和团队内部文档，合并证据后完成比较；因为问题包含“风险”，计划中加入 Risk Agent；Critic 查找逻辑跳跃，Fact Checker 检查来源摘录和证据映射，Moderator 形成共识，Writer 输出带引用的 Markdown 报告。

## 6. Spring AI 接入

### 6.1 为什么封装 SpringAiClient

[SpringAiClient](src/main/java/com/researchflow/llm/SpringAiClient.java) 是模型访问的统一适配层：

```java
public Optional<String> complete(String systemPrompt, String userPrompt)

public <T> Optional<T> entity(
        String systemPrompt,
        String userPrompt,
        Class<T> responseType)
```

构造器通过 `ObjectProvider<ChatModel>` 获取可选模型：

- 配置模型时，用 `ChatClient.builder(model).build()` 创建客户端。
- 没有模型时，`chatClient` 为 `null`，业务调用返回 `Optional.empty()`。
- 文本生成使用 `.call().content()`。
- 结构化输出使用 `.call().entity(responseType)`。

封装的价值：

1. 领域 Agent 不依赖具体供应商 SDK。
2. System Plan、ReAct Decision、Scenario Batch 等结构化输出统一处理。
3. 所有 Agent 使用一致的 system/user prompt 调用方式。
4. 模型关闭时可以进入确定性 fallback，方便开发和测试。

### 6.2 OpenAI 兼容模型配置

[application.yml](src/main/resources/application.yml) 使用 Spring AI OpenAI Starter：

```yaml
spring:
  ai:
    chat:
      client:
        enabled: false
    model:
      chat: ${SPRING_AI_MODEL_CHAT:none}
    openai:
      api-key: ${OPENAI_API_KEY:disabled}
      base-url: ${OPENAI_BASE_URL:https://api.openai.com}
      chat:
        options:
          model: ${OPENAI_MODEL:gpt-4o-mini}
          temperature: 0.2
```

通过 OpenAI 兼容协议，可以切换到 DeepSeek 等供应商。真实密钥只能放在环境变量或未提交的 `.env` 中，不能写入源码、README 或 Git 历史。

### 6.3 模型在项目中的使用位置

| 使用位置 | 输出形式 | 作用 |
|---|---|---|
| `SystemAgentPlanner` | `SystemPlan` | 生成受约束 DAG |
| `ReActNodeExecutor` | `ReActDecision` | 判断完成、重试或失败 |
| `CriticAgent` | 文本 | 检查逻辑和证据缺口 |
| `FactCheckerAgent` | 文本 | 检查来源和证据映射 |
| `ModeratorAgent` | 文本 | 收敛多角色结论 |
| `WriterAgent` | 文本 | 生成最终报告 |
| `ScenarioService` | `ScenarioBatch` | 从真实链路生成测试场景 |
| `ValidationAssessmentService` | 结构化判断 | 自动 Oracle |

### 6.4 降级策略

`SpringAiClient` 捕获模型异常并返回 `Optional.empty()`，各业务层再提供 fallback：

- System Agent 返回确定性 DAG。
- Critic 返回固定的证据边界审查意见。
- Fact Checker 统计来源数量和低置信度来源。
- Moderator 拼接各角色结论形成保守共识。
- Writer 基于已有 comparison/moderation 生成模板报告。
- Scenario Service 根据节点组合生成规则化场景。
- Validation Assessment 使用关键词规则判断。

面试时要说明：降级保证的是“流程可运行”，不是“离线情况下仍具有相同研究质量”。当前实现还会静默吞掉所有模型异常，生产环境应增加错误分类、日志、超时、重试、熔断、供应商指标和真实 Token Usage。

## 7. System Agent 与 DAG 规划

### 7.1 数据结构

[SystemPlan](src/main/java/com/researchflow/agent/runtime/SystemPlan.java)：

```java
public record SystemPlan(String goal, List<PlannedNode> nodes) {}
```

[PlannedNode](src/main/java/com/researchflow/agent/runtime/PlannedNode.java)：

```java
public record PlannedNode(
        String id,
        String agent,
        List<String> dependsOn) {}
```

节点 ID 是上下文数据键，例如 `sources`、`evidence`、`moderation`。`agent` 是 Registry 中注册的运行时名称，`dependsOn` 决定拓扑依赖。

### 7.2 规划流程

[SystemAgentPlanner.plan](src/main/java/com/researchflow/agent/runtime/SystemAgentPlanner.java) 的流程：

```text
用户问题
  -> Spring AI 结构化生成 SystemPlan
  -> 校验节点 ID、Agent 绑定和精确依赖
  -> 合法：接受模型计划
  -> 非法/模型不可用：使用 fallbackPlan
```

严格校验包括：

1. 必须包含标准研究节点。
2. 一个节点 ID 只能出现一次。
3. 每个 ID 必须绑定指定 Agent。
4. 不允许模型添加白名单外节点。
5. 每个节点必须具有精确的上游依赖。
6. Risk 和 Publication 必须与用户意图一致。

风险意图通过“风险、risk、挑战、局限”等关键词识别；发布意图通过“发布、publish”识别。

### 7.3 为什么不完全信任模型生成的 DAG

LLM 输出是不确定的。若直接执行模型给出的任意节点和依赖，可能产生：

- 调用未注册 Agent。
- Writer 在 Evidence 之前执行。
- 循环依赖导致任务无法推进。
- 绕过 Fact Checker 或审批节点。
- 插入高风险工具。

因此当前设计是“模型辅助规划 + 确定性契约验证”，优先保证可执行性、安全性和类型兼容性。

面试准确表述：

> 当前是受约束的动态规划。模型可以生成结构化计划，并根据风险、发布意图形成可选分支，但主体拓扑仍受固定契约限制，不是任意 Agent 自由组图。

生产化扩展方向：为每个 Agent 声明输入/输出 Schema、能力标签和策略约束，再通过通用拓扑校验、类型检查和 Policy Engine 接受更多动态组合。

## 8. Agent Registry 与上下文

### 8.1 SubAgent 统一协议

[SubAgent](src/main/java/com/researchflow/agent/runtime/SubAgent.java) 定义：

```java
String name();
Set<String> capabilities();
Set<String> requiredTools();
Object execute(AgentContext context);
Object execute(AgentContext context, String instruction);
boolean supportsRetry();
```

- `capabilities` 描述 Agent 能力，可用于展示和监督 Prompt。
- `requiredTools` 声明需要的工具，用于执行前授权。
- 带 `instruction` 的重载让 ReAct 重试不只是重复调用，而能消费修正要求。
- `supportsRetry` 防止对不可控或非幂等 Agent 自动重试。

### 8.2 AgentRegistry

[AgentRegistry](src/main/java/com/researchflow/agent/runtime/AgentRegistry.java) 使用适配器将不同方法签名转换为统一 `SubAgent`：

```text
planner-agent           -> PlannerAgent.execute(question)
source-search-agent     -> SourceSearchAgent.execute(plan)
private-knowledge-agent -> PrivateKnowledgeAgent.execute(workspaceId, question)
source-merge-agent      -> SourceMergeAgent.execute(external, private)
evidence-agent          -> EvidenceAgent.execute(sources)
comparison-agent        -> ComparisonAgent.execute(plan, evidence)
critic-agent            -> CriticAgent.execute(question, comparison, evidence, instruction)
fact-checker-agent      -> FactCheckerAgent.execute(question, evidence, sources, instruction)
moderator-agent         -> ModeratorAgent.execute(comparison, critic, factCheck, risk, instruction)
writer-agent            -> WriterAgent.execute(comparison, moderation, sources, instruction)
```

它实现了运行时路由，编排器不需要 `if (agentName.equals(...))` 硬编码具体 Agent。

### 8.3 AgentContext

[AgentContext](src/main/java/com/researchflow/agent/runtime/AgentContext.java) 是任务内共享黑板：

```java
private final Map<String, Object> values = new ConcurrentHashMap<>();
```

初始写入：

- `question`
- `workspaceId`

每个节点完成后以节点 ID 保存输出，例如：

```text
context["plan"]       = ResearchPlan
context["sources"]    = List<SourceDocument>
context["evidence"]   = String
context["critic"]     = String
context["moderation"] = String
context["writer"]     = String
```

使用 `ConcurrentHashMap` 是因为同层节点并行写不同 Key。当前类型安全依赖 `get(key, type)` 和 `getList(key, elementType)` 的运行时强转，字符串键仍可能在重构时出错。

生产化可以引入：

- 强类型 Port/Input/Output。
- 节点 Schema Registry。
- 编译期生成的 Context Key。
- JSON Schema 校验和版本化 Payload。

## 9. DAG 调度器

[MultiAgentOrchestrator](src/main/java/com/researchflow/agent/runtime/MultiAgentOrchestrator.java) 是执行核心。

### 9.1 调度算法

```text
completed = 空集合

while completed 数量 < 全部节点数量：
    ready = 未完成且所有 dependsOn 都已完成的节点
    ready 为空：说明 DAG 不可推进或存在环
    使用 CompletableFuture 并行执行 ready
    等待该层全部结束
    将 ready 加入 completed
```

该实现属于分层并行调度：同一层并行，下一层等待当前层全部完成。

### 9.2 单节点执行顺序

```text
Registry 获取 Agent
  -> ToolRegistry.authorize
  -> TraceCollector.nodeStarted
  -> 发送 RUNNING 事件
  -> FaultInjectionRuntime.before
  -> ReActNodeExecutor.execute
  -> FaultInjectionRuntime.after
  -> 校验输出非空
  -> 写入 AgentContext
  -> TraceCollector.nodeCompleted
  -> 发送 COMPLETED 事件
```

### 9.3 并行失败与取消

每一层维护：

- `AtomicBoolean layerAborted`
- `AtomicReference<RuntimeException> firstFailure`

任一节点失败后设置 `layerAborted=true`，同层节点在 ReAct 执行前后检查组合取消信号。`CompletableFuture.allOf` 等待当前层收束，最终抛出第一个失败，避免失败后下层继续执行。

局限：取消是协作式的。正在执行的 HTTP 或模型阻塞调用不一定立刻响应线程中断；当前也不是节点完成一个就立即释放下游的持续调度，而是整层屏障。

### 9.4 输出校验

调度器拒绝：

- `null`
- 空字符串
- 空列表

`privateSources` 允许空列表，因为 Workspace 可以没有匹配的私有文档。该规则保证下游不会收到意外空结果，同时保留“没有内部知识”这一正常业务状态。

## 10. ReAct 节点循环

### 10.1 当前实现是什么

[ReActNodeExecutor](src/main/java/com/researchflow/agent/runtime/ReActNodeExecutor.java) 实现受约束的：

```text
Action：执行当前节点被授权的领域动作
  -> Observation：摘要化执行结果或异常
  -> Decision：Spring AI 判断 COMPLETE / RETRY / FAIL
  -> RETRY 时携带 nextInstruction 进入下一轮
```

配置：

```yaml
research-flow:
  react:
    max-iterations: 3
    max-elapsed-ms: 60000
```

### 10.2 ReActDecision

[ReActDecision](src/main/java/com/researchflow/agent/runtime/ReActDecision.java) 包含：

```java
String decision;        // COMPLETE / RETRY / FAIL
String reason;          // 简短、可审计理由
String nextInstruction; // 下一轮修正要求
```

系统 Prompt 明确要求不输出隐藏思维过程，只输出简短决策理由。这样既能审计运行决策，也避免存储 Chain-of-Thought。

### 10.3 哪些 Agent 使用模型监督

只有声明 `llm-completion` 工具的生成式 Agent 才把观察交给 Spring AI：

- Critic
- Fact Checker
- Moderator
- Writer

检索、私有知识和确定性处理节点不把观察隐式发送给外部模型，降低私有数据外发风险和模型调用成本。

只有接收 `instruction` 的 Agent 支持可控重试。不可控、固定逻辑或未来可能产生外部副作用的 Agent 不应该自动重试。

### 10.4 停止条件

1. 监督器返回 `COMPLETE`。
2. 监督器返回 `FAIL`，节点失败。
3. 达到最大轮次，使用最近一次有效观察。
4. 超过时间预算，有有效观察则返回最近结果，否则失败。
5. 用户取消、执行代次失效或线程中断。
6. 高风险工具未审批或受控注入异常，直接向上抛出，不被 ReAct 吞掉。

### 10.5 模型不可用时

- 成功结果：确定性 `COMPLETE`。
- 执行异常：保留原始异常并失败。
- 不会因为监督模型不可用无限重试。

### 10.6 这是不是完整 ReAct

准确回答：

> 当前是节点级、受约束 ReAct。它实现了观察驱动的完成、修正重试和失败决策，但 Action 仍由 Agent 角色和 Registry 预先绑定。它还不是由模型在任意工具集合中自主选择 ToolCallback 的开放式 ReAct。

完整工具自主选择还需要：

1. 将工具实现注册为可执行 `ToolCallback`。
2. 模型输出工具名称和结构化参数。
3. Tool Gateway 统一做参数校验、权限、超时、审计和幂等。
4. 把工具返回值作为 Observation 再交给模型。
5. 对外部写操作使用审批、幂等键和补偿机制。

## 11. 多角色讨论与收敛

### 11.1 为什么增加讨论层

仅让 Writer 汇总上游结果容易产生：

- 对证据质量不加区分。
- 忽略相互矛盾的来源。
- 把推测写成确定事实。
- 引用编号存在但语义并不支持结论。

因此在 Writer 前加入多个职责不同的审查角色。

### 11.2 Critic Agent

[CriticAgent](src/main/java/com/researchflow/agent/CriticAgent.java) 接收问题、候选分析、证据和本轮修正指令，检查：

- 证据缺口。
- 逻辑跳跃。
- 相互矛盾的结论。
- 需要降低置信度的部分。

模型不可用时返回保守约束，提醒结论只能限定在已有证据内。

### 11.3 Fact Checker Agent

[FactCheckerAgent](src/main/java/com/researchflow/agent/FactCheckerAgent.java) 接收 Evidence 和 SourceDocument 列表。Prompt 中包含来源标题、类型、置信度和最多 500 字摘录，要求仅依据提供的来源进行核验。

它检查的是“给定证据和给定来源之间的一致性”，不是独立访问互联网证明世界事实。Fallback 只统计来源和低置信度来源。

### 11.4 Risk Agent

Risk Agent 当前在 Registry 中使用固定文本，关注数据偏差、可重复性、隐私合规和部署成本。它用于展示可选风险分支，但还不是读取具体证据后生成分析的独立 LLM Agent。

### 11.5 Moderator Agent

[ModeratorAgent](src/main/java/com/researchflow/agent/ModeratorAgent.java) 汇总 Comparison、Critic、Fact Checker 和可选 Risk，要求：

- 区分已经达成的共识。
- 保留未解决的分歧。
- 标记证据不足。
- 给出 Writer 必须遵守的写作边界。

它不是简单多数表决，而是强调按证据质量收敛。

### 11.6 Writer Agent

[WriterAgent](src/main/java/com/researchflow/agent/WriterAgent.java) 同时消费原始比较、Moderator 共识、来源和 ReAct 修正指令。它要求每个事实性结论使用 `[1]` 形式引用，并进行引用编号边界校验：

- 引用必须大于等于 1。
- 引用不能超过来源数量。
- 有来源时，模型生成结果至少要出现一个引用。
- 校验失败时退回确定性报告。

该校验只能防止不存在的引用编号，不能证明引用内容在语义上支持对应结论。更强实现需要 Claim Extraction、NLI/Entailment、来源交叉验证和人工抽检。

### 11.7 圆桌讨论的准确定位

当前是“多角色并行审查 + Moderator 单轮收敛 + 每个生成式节点内部可有限 ReAct 重试”。它不是多个角色自由发言很多轮的开放聊天室。

这样设计的优点：

- DAG 可预测。
- 调用次数有上限。
- 每个角色输入输出明确。
- 容易记录和定位失败。
- 不会出现无限争论。

进一步演进可让 Moderator 输出 `CONSENSUS / NEED_MORE_EVIDENCE / REPLAN`，在未收敛时向 Planner 发起局部重规划。

## 12. 各 Agent 的真实能力

| Agent | 当前实现 | 是否调用模型 | 关键边界 |
|---|---|---:|---|
| Planner Agent | 将问题转换为简单搜索计划 | 否 | 与 System Agent 不是一回事 |
| Source Search Agent | 调 Crossref，失败返回离线占位来源 | 否 | 不是全文搜索 |
| Private Knowledge Agent | 调 Workspace 关键词搜索 | 否 | 不是向量 RAG |
| Source Merge Agent | 私有来源优先，按 URL/标题去重 | 否 | 只做精确去重 |
| Evidence Agent | 编号拼接标题和摘要 | 否 | 不是 LLM 语义抽取 |
| Comparison Agent | 基于计划和证据生成模板比较 | 否 | 当前不是定量分析模型 |
| Risk Agent | 固定风险提示 | 否 | 尚未读取具体证据 |
| Critic Agent | 逻辑和证据缺口审查 | 是 | 结果是建议，不是事实证明 |
| Fact Checker Agent | 核查摘录与证据映射 | 是 | 不独立获取外部真相 |
| Moderator Agent | 按证据收敛角色意见 | 是 | 当前是单轮收敛 |
| Writer Agent | 生成报告并检查引用编号 | 是 | 不能验证语义蕴含 |
| Publisher Agent | 返回模拟发布结果 | 否 | 没有连接真实发布平台 |

## 13. 工具注册与高风险审批

### 13.1 ToolRegistry

[ToolRegistry](src/main/java/com/researchflow/tool/ToolRegistry.java) 注册四个工具：

| 工具 | 含义 | 风险 |
|---|---|---|
| `crossref-search` | 检索公开论文元数据 | `READ_ONLY` |
| `private-knowledge-search` | 检索 Workspace 私有知识 | `READ_ONLY` |
| `llm-completion` | 调用外部模型 | `EXTERNAL_CALL` |
| `report-publish` | 向外部目标发布报告 | `HIGH_RISK` |

执行 Agent 前，Orchestrator 调用：

```java
toolRegistry.authorize(agent, approvedTools);
```

若 Agent 声明的工具没有注册，则拒绝执行；若工具为高风险且未审批，则抛出 `ApprovalRequiredException`。

### 13.2 审批流程

```text
Publisher 需要 report-publish
  -> ToolRegistry 发现未审批
  -> 抛 ApprovalRequiredException
  -> Task 状态变为 WAITING_APPROVAL
  -> Owner 调用 approve API
  -> TaskEntity 记录 approvedTools
  -> 任务重新提交执行
```

为什么审批要求 Owner：发布属于外部副作用，Editor 可以发起研究，但只有 Workspace Owner 能授权高风险行为。

### 13.3 当前限制

ToolRegistry 当前是“声明 + 授权”，不是完整工具执行网关：

- Crossref 由 SourceSearchAgent 直接调用。
- 知识库由 PrivateKnowledgeAgent 直接调用。
- 模型由各生成式 Agent 直接调用。
- Publisher 仍是模拟返回。

审批后会重新执行整张 DAG，而不是从阻塞节点 checkpoint 恢复，可能重复检索和模型调用。真实发布工具还必须增加：

- 审批人与审批原因。
- 参数快照和报告版本 Hash。
- 审批有效期。
- 幂等键。
- 审计日志。
- 节点级恢复与补偿。

### 13.4 MCP Client 扩展

项目还可以作为 MCP Client 连接外部 MCP Server。Spring AI MCP Client 会发现远程工具，系统将其统一暴露为 `mcp:<tool-name>`，再通过 Tool Registry 检查工具是否存在，交给 ReAct 决策调用；工具返回值会作为下一轮 Observation。

ResearchFlow 也提供 MCP Server 模式，通过 `/mcp/sse` 暴露 `search_papers`、`search_workspace_knowledge`、`get_research_citations` 和 `get_research_trace` 四个只读科研工具。MCP、ReAct 和 DAG 的职责不同：DAG 决定 Agent 之间的依赖，ReAct 决定当前节点是否继续或调用工具，MCP 负责连接或暴露外部工具；当前 MCP 默认关闭，远程工具还需要补充超时、审计、幂等和生产级身份认证。

## 14. 外部检索与私有知识

### 14.1 Crossref 外部检索

[SourceSearchAgent](src/main/java/com/researchflow/agent/SourceSearchAgent.java) 使用 JDK `HttpClient`：

1. 将 ResearchPlan 的搜索词 URL 编码。
2. 请求 `query.bibliographic`。
3. 默认获取 5 条结果。
4. 连接超时 10 秒，请求超时 20 秒。
5. 从 Crossref JSON 中提取 DOI、标题、URL 和摘要。
6. 去除摘要中的 HTML 标签。
7. 外部异常或空结果时返回离线占位来源。

业务价值：无需依赖模型记忆即可获得真实论文元数据和可追溯 URL。

边界：Crossref 主要提供元数据，并不保证有全文摘要；离线占位来源只能用于保持工作流可运行，不应该被描述为真实研究证据。

### 14.2 私有知识库业务场景

企业研究不只依赖公开资料，还需要内部制度、历史报告和项目文档。知识库按 Workspace 隔离，让 External Search 与 Private Knowledge Search 并行，再统一合并。

支持输入：

- 直接提交文本。
- TXT。
- Markdown。
- DOCX。
- PDF。

[KnowledgeDocumentParser](src/main/java/com/researchflow/knowledge/KnowledgeDocumentParser.java) 的实现：

- TXT/Markdown：UTF-8 读取。
- DOCX：Apache POI 提取普通段落。
- PDF：PDFBox `PDFTextStripper` 提取文本。
- 扫描版 PDF 没有 OCR。
- DOCX 表格、图片、页眉页脚没有完整处理。
- Servlet 文件和请求上限为 20MB。

### 14.3 当前检索算法

[KnowledgeService](src/main/java/com/researchflow/knowledge/KnowledgeService.java) 当前不是向量数据库，而是 JVM 内词项评分：

- 完整查询命中标题：加 4 分。
- 完整查询命中正文：加 2 分。
- 分词命中标题：每词加 2 分。
- 分词命中正文：每词加 1 分。
- 中文查询可能生成双字 bigram。
- 按分数降序返回前 N 条。
- 摘录取正文前 500 字。
- 置信度由分数映射，最高 0.95。

复杂度接近 `O(文档数 × 文档长度)`，因为会加载 Workspace 全部文档并在线评分。它适合 MVP，不适合大规模知识库。

### 14.4 Source Merge

[SourceMergeAgent](src/main/java/com/researchflow/agent/SourceMergeAgent.java) 将私有来源放在外部来源之前，按 URL 或规范化标题去重，最多保留 10 条。

生产化 RAG 方案：

```text
文件上传
  -> 异步解析和病毒扫描
  -> 文档切块
  -> Metadata + Embedding
  -> BM25 与向量混合召回
  -> Reranker
  -> 命中片段和引用定位
  -> Agent Context
```

面试不要说当前已经使用 pgvector、Embedding 或语义向量检索，因为代码中没有这些实现。

## 15. 研究任务状态机

核心类是 [ResearchTaskService](src/main/java/com/researchflow/service/ResearchTaskService.java)。

### 15.1 状态流转

```text
CREATED
   |
   v
RUNNING -------------> COMPLETED
   |
   +-----------------> FAILED --retry--> CREATED
   |
   +-----------------> WAITING_APPROVAL --approve--> CREATED
   |
   +-----------------> CANCELLED --retry--> CREATED
```

### 15.2 创建任务

`create()` 在事务中完成：

1. 检查 Workspace 月报告配额。
2. 生成 UUID Task ID。
3. 创建 `TaskEntity`。
4. 保存 `CREATED` 事件。
5. 记录 `REPORT_CREATED` 用量。
6. 注册 `TransactionSynchronization.afterCommit()`。
7. 事务成功提交后才将任务交给线程池。

为什么使用 `afterCommit`：如果事务回滚，不能启动一个数据库中不存在的异步任务。

仍存在的窗口：数据库提交成功后，如果进程在 `submit()` 前崩溃，任务可能长期停留在 `CREATED`。生产环境通常使用 Transactional Outbox 或持久消息队列解决。

### 15.3 执行任务

`run()` 的主要步骤：

1. 校验当前执行 generation。
2. 状态改为 `RUNNING`，尝试次数加一。
3. 读取已保存计划，或生成并提前持久化新计划。
4. 创建 TraceCollector。
5. 调用 Orchestrator 执行 DAG。
6. 保存报告版本和引用。
7. 估算 Token 用量。
8. 在 generation 仍有效时标记 `COMPLETED`。

计划提前持久化的价值：即使任务中途失败或等待审批，也能查看原始 DAG。

### 15.4 取消与 generation token

只依赖 `Future.cancel(true)` 不够，因为真正的 Agent 节点运行在 Orchestrator 的另一个线程池中。因此 TaskState 维护 `AtomicLong generation`：

- 每次提交新执行，generation 加一。
- 取消时使当前 generation 失效。
- Orchestrator 和 ReAct 反复检查 generation 是否仍有效。
- 旧执行即使稍后返回，也不能把新状态覆盖成 `COMPLETED`。

这是“线程中断 + 业务代次令牌”的组合取消策略。

取消仍是 best effort。外部 HTTP 或模型 SDK 如果不响应中断，物理调用可能继续到超时，只是结果不会再被正常提交。

### 15.5 重试

- 只允许 `FAILED` 或 `CANCELLED` 重试。
- 最大任务尝试次数默认 3。
- 清理当前错误和报告状态。
- 复用已持久化的 SystemPlan。
- 事务提交后重新执行。

任务级重试与 ReAct 节点级重试不同：

| 类型 | 范围 | 目的 |
|---|---|---|
| ReAct Retry | 单个生成式 Agent 节点 | 根据观察修正输出 |
| Task Retry | 整个研究任务 | 从失败或取消状态重新执行 DAG |

当前重试不会删除历史 NodeExecution 和 NodeStep，也没有 attemptId，因此 trace 可能混合多次尝试。

### 15.6 应用重启恢复

`@PostConstruct recoverInterruptedTasks()` 在启动时把旧的 `RUNNING` 任务标记为 `FAILED`，错误原因是应用重启导致中断。

这是“检测中断”，不是“断点续跑”。真正恢复需要节点 checkpoint、输入输出快照、任务租约和幂等执行。

## 16. SSE 实时事件

研究任务通过 `GET /api/research/tasks/{taskId}/events` 返回 `SseEmitter`：

- Timeout 为 0，表示服务端不主动超时。
- 事件名为 `agent-event`。
- 新订阅者先收到历史事件回放。
- 后续 Agent 状态实时发送。
- 发送异常时完成 emitter。
- 连接关闭后从列表移除。

常见事件状态：

```text
CREATED
RUNNING
PLANNED
ACTION
RETRYING
CONVERGED
BUDGET_EXHAUSTED
COMPLETED
FAILED
WAITING_APPROVAL
CANCELLED
```

为什么选择 SSE：服务端只需要单向推送任务状态，比 WebSocket 简单，浏览器原生支持自动重连。

当前限制：

- 没有 Event ID 和 `Last-Event-ID`。
- 没有心跳。
- 历史回放可能与实时事件交错。
- 多实例之间不共享 emitter。
- 内存 Map 缺少完整的清理和容量限制。
- 当前前端研究页主要使用轮询，尚未真正消费 SSE。

## 17. 执行链路与持久化

### 17.1 三个观测层级

1. Task Event：面向用户的状态事件。
2. Node Execution：节点输入摘要、输出摘要、状态和总耗时。
3. ReAct Step：每一轮 Action、Observation、Decision、Rationale 和耗时。

相关实体：

- [TaskEventEntity](src/main/java/com/researchflow/persistence/TaskEventEntity.java)
- [TaskNodeExecutionEntity](src/main/java/com/researchflow/persistence/TaskNodeExecutionEntity.java)
- [TaskNodeStepEntity](src/main/java/com/researchflow/persistence/TaskNodeStepEntity.java)

### 17.2 TraceCollector

[TraceCollector](src/main/java/com/researchflow/agent/runtime/TraceCollector.java) 是运行时与持久化层之间的回调接口：

```java
nodeStarted(...)
nodeCompleted(...)
nodeFailed(...)
nodeStep(...)
```

Orchestrator 不直接依赖 JPA Repository，因此验证服务可以提供另一套 Collector，将轨迹写入验证结果而不是生产任务表。

### 17.3 Trace API

`GET /api/research/tasks/{taskId}/trace` 返回：

```text
taskId
plan
nodes[]
steps[]
```

Observation 和节点摘要会截断，避免把完整上下文全部写入数据库。系统保存的是可审计摘要，不是模型隐藏思维链。

当前不足：NodeExecution 的 `startedAt` 实际在完成/失败回调时创建，更接近结束时间；重试记录没有 attemptId；前端类型已支持 `steps`，但报告页目前没有展示步骤列表。

## 18. 外部 Agent 链路接入

业务场景：企业已有其他 Agent 系统，例如车载 Agent、客服 Agent 或研发助手，希望把一次真实执行链路交给 ResearchFlow 做异常场景推演，而不是重新接入整个运行时。

[ExternalTraceService](src/main/java/com/researchflow/externaltrace/ExternalTraceService.java) 接收结构化节点并校验：

1. 至少一个节点，最多 200 个。
2. 节点 ID 必须匹配安全正则。
3. 节点 ID 不允许重复。
4. 每个依赖必须存在。
5. 节点耗时不能超过 24 小时。
6. 结束时间不能早于开始时间。
7. 使用 Kahn 拓扑算法检查环。

节点支持状态：

- `SUCCESS`
- `FAILED`
- `TIMEOUT`
- `CANCELLED`
- `DEGRADED`

链路聚合：

- 任意 FAILED/TIMEOUT：整个链路 `FAILED`。
- 否则任意 CANCELLED/DEGRADED：`PARTIAL`。
- 否则：`COMPLETED`。

外部节点还可以标记：

- `externalBoundary`：是否跨外部系统边界。
- `asyncNode`：是否为异步节点。

输入输出允许较长文本，但落库前会截断到 20,000 字。生产化应增加 Schema Version、幂等 Trace ID、OpenTelemetry 接入、数据脱敏和租户级保留策略。

## 19. AI 场景推演

[ScenarioService](src/main/java/com/researchflow/scenario/ScenarioService.java) 将真实 TraceView 序列化为 JSON，交给 Spring AI 场景 Agent，重点寻找：

- 多个外部依赖同时失效。
- 合法但极端的数据和边界状态叠加。
- 上游延迟导致下游状态变化。
- 异步分支时序错位。
- 多层降级叠加后失效。

模型输出最多 6 个场景，每个场景包含：

```text
title
nodeCombination
trigger
injectedData
expectation
risk
injectionRules[]
```

场景状态：

```text
SUGGESTED -> APPROVED
          -> DISMISSED
```

模型规则会经过归一化：

- nodeId 必须来自真实 DAG。
- 每个场景最多 4 条规则。
- 无有效规则时补充默认安全延迟规则。
- 整体最多保存 6 个场景。

模型不可用时，fallback 会从已完成节点生成单节点延迟和双节点组合场景。

已知问题：外部链路成功节点使用 `SUCCESS`，而 fallback 当前主要识别 `COMPLETED`，所以无模型情况下外部链路可能生成零个 fallback 场景。这是一个面试时可以主动指出的真实改进点。

## 20. 故障注入与验证

### 20.1 注入规则

[InjectionRule](src/main/java/com/researchflow/injection/InjectionRule.java) 支持：

| 类型 | 行为 |
|---|---|
| `DELAY` | 节点执行前 sleep，最大 10 秒 |
| `ERROR` | 节点执行前抛受控异常 |
| `EMPTY_RESULT` | 节点执行后把结果替换为空值 |

[InjectionRuleFactory](src/main/java/com/researchflow/injection/InjectionRuleFactory.java) 优先读取场景中的结构化规则，也可以根据节点组合、JSON 注入描述和关键词推导规则。

### 20.2 为什么不修改业务 Agent

[FaultInjectionRuntime](src/main/java/com/researchflow/injection/FaultInjectionRuntime.java) 在 Orchestrator 边界进行 before/after 注入，因此不用在每个 Agent 中编写测试分支，业务代码与验证机制解耦。

### 20.3 验证流程

[ScenarioValidationService](src/main/java/com/researchflow/validation/ScenarioValidationService.java)：

```text
APPROVED Scenario
  -> 读取原始任务 DAG
  -> 移除 publisher-agent
  -> 生成安全注入规则
  -> 保存 QUEUED ValidationRun
  -> afterCommit 后异步执行
  -> 收集节点轨迹
  -> 自动 Oracle 评估
  -> 开发者给最终 Verdict
```

验证运行状态：

- `QUEUED`
- `RUNNING`
- `COMPLETED`
- `FAILED`

人工结论：

- `NEEDS_REVIEW`
- `VERIFIED`
- `DEFECT_FOUND`
- `INVALID`

### 20.4 “验证完成”不等于“业务成功”

若注入 ERROR 后链路按预期失败，这次实验本身是成功完成的，因此 ValidationRun 可以是 `COMPLETED`，再由 Oracle 判断该失败是否符合预期。需要区分：

1. 验证基础设施是否正常完成。
2. 被测业务链路是否成功。
3. 实际行为是否符合预期。

### 20.5 自动 Oracle

[ValidationAssessmentService](src/main/java/com/researchflow/validation/ValidationAssessmentService.java) 输出：

- `EXPECTED_BEHAVIOR`
- `POTENTIAL_DEFECT`
- `INCONCLUSIVE`

模型可用时综合场景期望、注入规则、实际输出、错误和节点轨迹；模型不可用时使用关键词和规则进行确定性判断。自动结果只是建议，开发者保留最终裁决权。

当前验证在同一进程内重新执行正常 DAG，不是隔离沙箱；没有取消 API，也不能真正重放外部系统。因此应表述为“受控验证原型”，不能表述为生产级 Chaos Engineering 平台。

## 21. Workspace 与 RBAC

[WorkspaceService](src/main/java/com/researchflow/workspace/WorkspaceService.java) 提供团队空间和角色权限。

角色层级：

```text
OWNER > EDITOR > VIEWER
```

典型权限：

| 操作 | 最低角色 |
|---|---|
| 查看任务、知识、报告、用量 | VIEWER |
| 评论报告 | VIEWER |
| 创建研究任务 | EDITOR |
| 上传/删除知识文档 | EDITOR |
| 创建订阅、生成场景、执行验证 | EDITOR |
| 添加成员、修改套餐 | OWNER |
| 批准高风险工具 | OWNER |

创建 Workspace 时，在同一事务中：

1. 保存 Workspace。
2. 将创建者保存为 Owner 成员。

成员管理规则：

- 不能修改原 Owner 的角色。
- 不能通过普通成员接口转移所有权。
- Owner 可以新增或更新 Editor/Viewer。

当前身份来自 `X-User-Id` 请求头，它只是本地 MVP 的身份占位，不是真正认证。任何客户端都可以伪造该 Header，所以不能说项目已经完成安全登录或企业级租户隔离。

生产化：Spring Security Resource Server + JWT/OIDC，将 userId 和 tenantId 从经过签名验证的 Token 中提取；在 Service 使用方法级授权，并在数据库层增加外键、租户条件或 Row Level Security。

## 22. 报告、引用与协作

### 22.1 引用溯源

研究任务完成后，将最终 `SourceDocument` 保存为 `TaskCitationEntity`：

- citationNumber
- sourceId
- sourceType
- title
- url
- excerpt
- confidence

报告中使用 `[1]` 编号，引用 API 可以返回完整来源信息。其价值是让用户知道结论来自公开论文还是私有知识，而不是只看到一段模型文本。

### 22.2 报告版本

[CollaborationService](src/main/java/com/researchflow/collaboration/CollaborationService.java) 在每次成功生成报告后保存不可通过 API 修改的版本：

- taskId
- workspaceId
- versionNumber
- content
- createdBy
- createdAt

当前版本号使用 `countByTaskId() + 1`，并发下可能重复。生产环境应增加 `(task_id, version_number)` 唯一约束，再使用数据库锁、序列或原子版本字段。

### 22.3 评论

Viewer 及以上成员可以对报告添加评论，记录作者和时间。当前没有评论编辑、删除、回复、锚点和 Mention。

## 23. 多格式导出

[ReportExportService](src/main/java/com/researchflow/export/ReportExportService.java) 支持：

| 格式 | 实现 |
|---|---|
| Markdown | UTF-8 原始报告 |
| DOCX | Apache POI 创建段落 |
| PDF | OpenPDF 生成 PDF |

导出时记录 `REPORT_EXPORTED` 用量。

当前转换是轻量级 Markdown 处理：DOCX 主要识别 H1/H2，PDF 使用中文字体配置，复杂表格、图片、代码块和完整 Markdown 样式没有实现。所有内容在内存中生成，大报告可改为流式输出或对象存储异步生成。

## 24. 主题订阅与定时调度

业务场景：团队希望每周自动生成“AI 风险周报”，不需要人工重复提交同一问题。

[SubscriptionService](src/main/java/com/researchflow/subscription/SubscriptionService.java) 支持：

- 创建周期主题。
- 列出 Workspace 订阅。
- 启用或停用。
- 手动立即运行。
- 保存下次运行时间、上次运行时间和最后任务 ID。

最小周期为 5 分钟。订阅触发任务时，`triggerType` 为 `SUBSCRIPTION`。

[SubscriptionScheduler](src/main/java/com/researchflow/subscription/SubscriptionScheduler.java) 使用：

```java
@Scheduled(fixedDelayString = "${research-flow.subscription.scan-interval-ms:60000}")
```

每轮查询到期订阅并触发任务，失败时将运行时间向后推迟一个周期。

单实例 MVP 可以工作，多实例会重复扫描和执行。生产化方案：

- ShedLock 或数据库租约。
- `SELECT ... FOR UPDATE SKIP LOCKED` 抢占到期任务。
- 分布式任务队列。
- 幂等触发 ID。
- 调度分页和失败重试策略。

## 25. 套餐、配额与用量

套餐：

| 套餐 | 月报告数 | 启用订阅数 | 文档数 |
|---|---:|---:|---:|
| FREE | 10 | 1 | 20 |
| TEAM | 300 | 20 | 2000 |
| ENTERPRISE | 近似无限 | 近似无限 | 近似无限 |

[UsageService](src/main/java/com/researchflow/billing/UsageService.java) 记录：

- `REPORT_CREATED`
- `DOCUMENT_CREATED`
- `REPORT_EXPORTED`
- `SUBSCRIPTION_RUN`
- `TOKEN_USED`

月周期按 UTC 月初计算。报告创建时占用配额，即使任务后续失败也不会自动返还。

Token 当前通过字符数除以 4 粗略估算，成本按固定单价计算，不是模型供应商真实账单。这个模块应称为“套餐配额与用量原型”，不能称为完整支付计费系统。

并发风险：配额检查和数据写入是 Check-Then-Act，没有锁或原子计数，并发请求可能同时通过检查。生产环境可为 Workspace + Billing Period 建立配额行，通过数据库原子更新、悲观锁或 Redis Lua 脚本扣减。

## 26. Controller 与主要 API

### 26.1 Workspace

```text
POST /api/workspaces
GET  /api/workspaces
PUT  /api/workspaces/{workspaceId}/members
GET  /api/workspaces/{workspaceId}/members
PUT  /api/workspaces/{workspaceId}/plan?tier=TEAM
```

### 26.2 Research Task

```text
POST /api/research/tasks
GET  /api/research/tasks?workspaceId=...
GET  /api/research/tasks/{taskId}
GET  /api/research/tasks/{taskId}/events
GET  /api/research/tasks/{taskId}/trace
GET  /api/research/tasks/{taskId}/citations
POST /api/research/tasks/{taskId}/cancel
POST /api/research/tasks/{taskId}/retry
POST /api/research/tasks/{taskId}/approve
GET  /api/research/tasks/{taskId}/export?format=...
```

### 26.3 System Agent

```text
POST /api/system-agent/plan
GET  /api/system-agent/agents
GET  /api/system-agent/tools
```

分别用于预览计划、查看 Agent 能力和查看工具风险。

### 26.4 Knowledge

```text
POST   /api/knowledge/documents
POST   /api/knowledge/documents/upload
GET    /api/knowledge/documents?workspaceId=...
GET    /api/knowledge/documents/search?workspaceId=...&query=...
DELETE /api/knowledge/documents/{id}
```

### 26.5 Collaboration / Billing / Subscription

```text
GET  /api/research/tasks/{taskId}/versions
POST /api/research/tasks/{taskId}/comments
GET  /api/research/tasks/{taskId}/comments
GET  /api/billing/workspaces/{workspaceId}/usage

POST /api/subscriptions
GET  /api/subscriptions?workspaceId=...
PUT  /api/subscriptions/{id}/enabled?value=...
POST /api/subscriptions/{id}/run
```

### 26.6 Scenario / Validation

```text
POST   /api/research/tasks/{taskId}/scenarios/generate
GET    /api/research/tasks/{taskId}/scenarios
PUT    /api/research/tasks/{taskId}/scenarios/{scenarioId}/status
DELETE /api/research/tasks/{taskId}/scenarios/{scenarioId}
POST   /api/research/tasks/{taskId}/scenarios/{scenarioId}/validations
GET    /api/research/tasks/{taskId}/scenarios/{scenarioId}/validations
PUT    /api/research/tasks/{taskId}/scenarios/{scenarioId}/validations/{runId}/verdict
```

### 26.7 External Trace

```text
POST   /api/external-traces
GET    /api/external-traces?workspaceId=...
GET    /api/external-traces/{traceId}
DELETE /api/external-traces/{traceId}
POST   /api/external-traces/{traceId}/scenarios/generate
GET    /api/external-traces/{traceId}/scenarios
PUT    /api/external-traces/{traceId}/scenarios/{scenarioId}/status
```

Controller 的通用模式：先根据资源找到 workspaceId，再调用 `WorkspaceService.require()`，然后进入领域 Service。优点是入口权限清晰；缺点是授权主要在 Controller，内部 Service 调用若绕过入口，需要自己保证权限。

## 27. JPA 数据模型

主要关系：

```text
Workspace
├── WorkspaceMember
├── KnowledgeDocument
├── ResearchTask
│   ├── TaskEvent
│   ├── TaskCitation
│   ├── TaskNodeExecution
│   ├── TaskNodeStep
│   ├── ReportVersion
│   ├── ReportComment
│   └── Scenario
│       └── ScenarioValidation
├── TopicSubscription
├── UsageRecord
└── ExternalTrace
    └── ExternalTraceNode
```

当前实体主要使用普通 ID 字段关联，没有大量 `@ManyToOne`：

优点：

- 避免复杂懒加载和序列化问题。
- 查询边界明确。
- DTO 转换简单。

缺点：

- 数据库引用完整性弱。
- 删除容易产生孤儿数据。
- 没有对象导航和级联。

其他特点：

- 枚举使用 `EnumType.STRING`，比 ORDINAL 更安全。
- 报告、计划、规则、输入输出摘要使用 `@Lob`。
- 任务、Workspace、文档等使用 UUID 字符串主键。
- 事件、评论、版本等使用数据库 IDENTITY Long。
- 大多数表没有 `@Version` 乐观锁。
- 除 Workspace Member 外，唯一约束和索引较少。

H2 配置：

```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/researchflow;AUTO_SERVER=TRUE
  jpa:
    hibernate:
      ddl-auto: update
    open-in-view: false
```

`open-in-view:false` 避免 Controller 层隐式数据库访问。`ddl-auto:update` 适合 MVP，但生产环境应使用 PostgreSQL + Flyway/Liquibase 管理可审计迁移。

## 28. 事务与异步知识点

### 28.1 afterCommit

研究任务和验证任务在事务内创建记录，但只在事务提交成功后启动线程：

```java
TransactionSynchronizationManager.registerSynchronization(
    new TransactionSynchronization() {
        public void afterCommit() {
            executor.submit(...);
        }
    });
```

避免了“数据库回滚，但后台任务已经开始”的问题。

### 28.2 异步任务不是一个大事务

`run()` 在自建线程池执行，没有一个包住全部步骤的长事务。节点事件、步骤、引用、版本和最终状态由多个 Repository 调用分别提交。

好处：

- 不持有长事务和数据库连接。
- 节点轨迹可以逐步落库。
- 失败时保留部分诊断信息。

代价：

- 任务整体不是原子操作。
- 可能出现部分数据已保存、最终任务失败。
- 需要状态机、幂等和补偿保证一致性。

### 28.3 为什么不用 @Async

项目直接创建 `ExecutorService`，可以明确区分：

- 任务线程池。
- DAG 节点线程池。
- 验证线程池。

但当前线程池使用默认无界队列，没有线程命名、MDC 传播、指标和拒绝策略。生产环境建议配置 Spring `ThreadPoolTaskExecutor` 或使用持久队列/工作流引擎。

### 28.4 多实例问题

以下状态仍在 JVM 内：

- TaskState Cache。
- Running Future。
- SSE Emitter。
- Executor Queue。

因此当前是单实例架构。多实例生产化需要数据库任务租约、消息队列、共享事件系统、幂等消费者和分布式调度。

## 29. 异常处理

[ApiExceptionHandler](src/main/java/com/researchflow/controller/ApiExceptionHandler.java) 处理：

| 异常 | HTTP 状态 |
|---|---:|
| Bean Validation | 400 |
| `SecurityException` | 403 |
| `IllegalStateException` | 409 |
| `IllegalArgumentException` | 404 |

目前 `IllegalArgumentException` 同时表示资源不存在和请求参数非法，所以有些非法格式会错误地返回 404。生产环境应建立明确异常类型：

- `ResourceNotFoundException -> 404`
- `BadRequestException -> 400`
- `ConflictException -> 409`
- `UnauthorizedException -> 401`
- `ForbiddenException -> 403`

同时增加稳定错误码、Trace ID、字段错误列表和结构化日志。

## 30. 安全边界

已经具备的控制：

- Workspace 角色检查。
- 高风险工具默认拒绝。
- 外部 DAG 节点数和结构校验。
- 上传大小限制。
- `.env` 和数据库文件被 Git 忽略。
- 私有检索节点不自动把 Observation 发给 ReAct 监督器。

尚未完成：

1. `X-User-Id` 可伪造，没有真实认证。
2. H2 Console 默认开启，数据库密码为空。
3. 部分 System Agent API 没有身份限制。
4. 私有摘录会进入 Fact Checker 和 Writer 的模型 Prompt，需要数据出境策略。
5. 上传文件没有 MIME/文件魔数、病毒扫描和解析沙箱。
6. 外部文档可能包含 Prompt Injection。
7. 没有租户并发限制和 API Rate Limit。
8. 审批记录缺少审批人、参数快照和有效期。

生产优先级：先认证和数据边界，再扩展更多 Agent。可采用 Spring Security + OIDC/JWT、方法级授权、模型 Provider Allowlist、敏感信息脱敏、DLP、上传隔离和审计日志。

## 31. 测试与验证

当前已经验证：

- 22 个测试套件。
- 38 个测试全部通过。
- Spring Boot Context 和新增 JPA Repository 可加载。
- 前端 TypeScript 和 Vite Production Build 通过。
- 实际端到端任务完成。
- 端到端结果包含 10 个 DAG 节点和 10 条 ReAct Step。

测试覆盖的重点：

- System Plan fallback、风险分支和依赖契约拒绝。
- ReAct 单轮完成、修正重试和取消前阻断。
- Critic、Fact Checker、Moderator 离线 fallback。
- Writer 非法引用编号降级。
- Tool Registry 高风险审批。
- 注入规则和 Fault Runtime。
- 外部 DAG 依赖与环校验。
- 知识搜索、导出、计费、Workspace 等局部逻辑。

明显缺口：

- Orchestrator 并发失败测试。
- TaskService 完整状态机和事务测试。
- 取消/重试/审批竞态。
- SSE 顺序、断线重连和资源清理。
- Scheduler 多实例重复执行。
- 真实模型集成测试和质量评测。
- Prompt Injection 与私有数据外发测试。
- Claim-to-Citation 语义一致性评测。
- Testcontainers 数据库集成测试。

面试时不要把“38 个测试通过”说成“生产正确性已证明”。它证明的是当前局部行为和 Context 可启动。

## 32. 关键设计取舍

### 32.1 为什么采用 DAG，而不是固定串行流程

- 能表达依赖关系。
- 能并行无依赖节点。
- 可以记录节点级状态和耗时。
- 可以对单节点做故障注入。
- 能让 System Agent 输出结构化执行计划。

### 32.2 为什么不是完全开放的自治 Agent

开放自治意味着模型可以任意选 Agent、工具和依赖，风险包括不可预测成本、循环执行、越权工具调用和难以复现。项目选择白名单 Agent、精确 DAG 契约、最大 ReAct 轮次和高风险审批，用部分自主性换取工程可控性。

### 32.3 为什么保留确定性 Agent

检索、去重、状态机、授权和格式校验更适合确定性代码。并不是所有步骤都应该调用 LLM：

- 确定性代码成本低、可测试、可复现。
- LLM 适合语义规划、批判、核验、收敛和写作。
- 混合架构比“所有逻辑都交给模型”更稳定。

### 32.4 为什么不保存模型思维链

- Chain-of-Thought 不是稳定业务协议。
- 可能包含敏感信息。
- 容易增加存储与合规风险。
- 审计需要的是输入摘要、动作、观察、结果和简短决策理由。

因此 ReAct Step 保存可审计 rationale，不保存隐藏推理全文。

### 32.5 为什么模型失败选择 fallback

主要用于本地开发、演示和外部依赖降级。生产环境不能对所有错误静默 fallback，需要区分：

- 配置缺失：开发环境允许 fallback。
- 临时限流/超时：可重试或切换模型。
- 认证失败：立即失败并告警。
- 输出格式错误：有限重试后 fallback。
- 安全策略拒绝：不能绕过。

## 33. 当前项目最值得讲的亮点

1. 模型生成计划后仍进行严格契约校验，不盲目信任 LLM。
2. DAG 同层并行执行，检索和审查都有实际并行点。
3. Agent Registry 统一能力和工具声明，编排器与具体实现解耦。
4. 节点级 ReAct 有轮次、时间、重试能力和可审计 Step。
5. 私有检索节点不会被监督器隐式外发，体现数据边界意识。
6. Critic、Fact Checker、Moderator 角色职责不同，不是简单重复 Prompt。
7. 计划、事件、节点、ReAct Step、引用、版本全部可持久化。
8. 工具风险分级和 Owner 审批体现 Human-in-the-loop。
9. 真实 Trace 可以继续生成场景、注入故障和自动评估。
10. 模型关闭仍有确定性流程，便于测试和演示。

## 34. 不能夸大的地方

建议主动、准确地使用以下表述：

| 推荐表述 | 不应表述为 |
|---|---|
| 受约束的 Spring AI DAG 规划 | 完全动态、自主创建任意工作流 |
| 节点级 ReAct 监督与有限重试 | 模型自主选择任意工具的完整 ReAct |
| 关键词私有知识检索原型 | 向量数据库 RAG |
| 来源溯源与引用编号校验 | 已彻底解决模型幻觉 |
| 给定来源摘录上的 Fact Check | 独立事实真伪证明 |
| Workspace RBAC 原型 | 企业级安全多租户 |
| H2 持久化和中断检测 | 分布式、Exactly-once、断点续跑 |
| 用量与套餐配额原型 | 完整商业计费支付系统 |
| 受控进程内故障注入 | 生产级 Chaos Engineering |
| 应用层报告版本 | 数据库不可篡改审计历史 |

面试中主动讲清边界不会减分，反而能体现工程判断力。

## 35. 生产化演进路线

### 第一阶段：可靠性和安全

1. Spring Security + OIDC/JWT。
2. Service 方法级授权和数据库租户约束。
3. PostgreSQL + Flyway。
4. 测试使用独立 Profile 和 Testcontainers。
5. 关闭生产 H2 Console。
6. 文件扫描、解析沙箱和数据脱敏。

### 第二阶段：持久执行

1. Transactional Outbox。
2. RabbitMQ/Kafka 或工作流引擎。
3. Task Run/Attempt 独立实体。
4. 节点 checkpoint 和幂等键。
5. 任务租约、心跳和超时接管。
6. 多实例 Scheduler 抢占。

### 第三阶段：完整 Agent Runtime

1. Spring AI `ToolCallback` 注册。
2. 结构化 Tool Action 和参数 Schema。
3. Tool Gateway 统一权限、限流、超时和审计。
4. Moderator 输出 `REPLAN`，支持局部 DAG 重规划。
5. Agent Memory 分层：Task Memory、Workspace Memory、Long-term Memory。
6. 模型路由、降级模型和成本预算。

### 第四阶段：研究质量

1. 文档 Chunking。
2. BM25 + Embedding 混合召回。
3. Reranker。
4. DOI、来源可访问性和重复论文校验。
5. Claim-to-Citation Entailment。
6. Golden Dataset 和人工质量评分。

### 第五阶段：可观测性

1. Spring Boot Actuator。
2. Micrometer Metrics。
3. OpenTelemetry Trace。
4. 模型 Token、Latency、Error、Fallback 指标。
5. Tool Call Trace 和 Prompt Version。
6. SSE/Event 总线和多实例广播。

## 36. 高频面试问题与回答

### 36.1 System Agent 和 Planner Agent 有什么区别

System Agent 负责生成整个执行 DAG，输出 `SystemPlan`；Planner Agent 是 DAG 中的一个普通领域节点，只生成研究查询和关注方向。前者是运行时编排规划，后者是业务研究计划。

### 36.2 为什么用了 Spring AI，而不是直接调用 HTTP

Spring AI 提供统一的 `ChatModel/ChatClient` 抽象和结构化实体映射，减少供应商 SDK 耦合。项目通过 OpenAI 兼容协议切换模型，同时把业务 Agent 与模型客户端隔离。

### 36.3 `.entity(SystemPlan.class)` 有什么风险

模型可能返回无法解析、字段为空、重复节点或非法依赖，所以反序列化成功不代表可以执行。项目还会做严格业务契约校验，不合法就 fallback。

### 36.4 你的 DAG 真的是动态的吗

是受约束动态：模型生成结构化计划，Risk/Publication 可根据意图变化，但标准节点和依赖被固定契约保护。这样是为了类型安全、可预测性和工具安全。真正任意组图需要 Agent I/O Schema 和通用类型检查。

### 36.5 如何判断一个节点可以执行

节点未完成，并且 `completed` 集合包含其全部 `dependsOn`。每轮找出全部 ready 节点并行执行。

### 36.6 如何检查 DAG 环

内部计划若无法找到 ready 节点但仍有未完成节点，则存在不可推进依赖；外部链路接入时使用 Kahn 算法，根据入度为 0 的节点逐步访问，访问数小于节点数即有环。

### 36.7 为什么 AgentContext 使用 ConcurrentHashMap

同层 Agent 会并发写不同节点输出，普通 HashMap 不安全。ConcurrentHashMap 保证并发读写基础安全，但类型仍是运行时强转，后续应使用强类型 Schema。

### 36.8 ReAct 如何避免死循环

最多 3 轮、总时间预算 60 秒、Agent 必须声明支持重试；监督器只能输出 COMPLETE/RETRY/FAIL。取消和高风险审批异常不会被吞掉。

### 36.9 这是不是完整 ReAct

不是开放式完整 ReAct，是受约束节点级 ReAct。Action 是预先绑定的领域能力，模型负责根据 Observation 决定完成或带指令重试。下一步才是标准 ToolCallback 和模型选工具。

### 36.10 为什么只让部分 Agent 使用监督模型

检索和私有知识节点包含潜在敏感内容，而且固定逻辑重复执行没有价值。只有声明 `llm-completion` 的生成式 Agent 才调用监督器，兼顾成本、隐私和质量。

### 36.11 多 Agent 为什么比一个大 Prompt 好

可以形成明确职责、并行执行、单节点失败定位、工具最小权限和可审计中间产物。但项目目前没有质量 Benchmark 证明一定优于单 Prompt，所以应说架构上更可控，而不是绝对效果更好。

### 36.12 Critic 和 Fact Checker 有什么区别

Critic 检查推理结构、证据缺口和结论强度；Fact Checker 检查给定证据是否能映射到给定来源摘录。前者偏逻辑，后者偏来源一致性。

### 36.13 Moderator 如何收敛

它汇总 Comparison、Critic、Fact Checker 和 Risk，按照证据质量区分共识、分歧和证据不足，并输出 Writer 约束。当前是一轮收敛，不是无限圆桌讨论。

### 36.14 如何降低幻觉

公开和私有来源检索、来源合并、证据编号、Fact Checker、Moderator、Writer 引用要求和引用编号边界校验共同降低风险。但还没有语义蕴含检查，不能说完全解决幻觉。

### 36.15 为什么高风险工具要审批

读取数据和向外部系统写数据的风险不同。发布具有外部副作用，必须由 Owner 确认。生产环境还要把审批绑定到具体报告版本和参数，并使用幂等键。

### 36.16 为什么异步任务在 afterCommit 后启动

避免事务回滚后后台任务仍执行。缺点是提交到线程池之间仍有崩溃窗口，生产环境用 Outbox + MQ 解决。

### 36.17 任务取消为什么需要 generation

外层 Future 中断不能保证内层 Agent 立即停止。generation 让旧运行在业务层失效，即使稍后返回也不能覆盖当前状态。

### 36.18 为什么不用一个长事务包住整张 DAG

模型和外部 HTTP 可能运行很久，长事务会持有连接和锁。项目选择逐步落库，保留失败诊断信息，代价是需要状态机和补偿处理部分成功。

### 36.19 SSE 和 WebSocket 怎么选

这里是服务端单向推送任务事件，SSE 更简单，支持标准 HTTP 和浏览器自动重连。若需要客户端双向实时控制或高频交互，再考虑 WebSocket。

### 36.20 应用重启如何恢复

当前把 RUNNING 标记 FAILED，属于中断检测，不是断点续跑。真正恢复需要 Attempt、节点 checkpoint、幂等工具和持久任务队列。

### 36.21 为什么用 H2

MVP 零配置、文件模式可以重启保留数据。生产环境要换 PostgreSQL，并用 Flyway 管理迁移，因为 H2 并发、运维和 SQL 兼容能力有限。

### 36.22 私有知识库是不是 RAG

从业务流程上属于检索增强生成，但当前 Retriever 是关键词评分，不是向量 RAG。准确说法是“私有知识混合检索原型”，后续升级为分块、Embedding、混合召回和 Rerank。

### 36.23 配额为什么可能超卖

当前是先统计再写入，两个并发请求可能同时通过。需要数据库锁、唯一周期额度行、原子更新或 Redis Lua。

### 36.24 订阅为什么多实例会重复

每个实例都会扫描所有到期订阅，没有分布式锁和任务租约。可用 ShedLock、数据库抢占或队列调度。

### 36.25 自动 Oracle 能替代人工吗

不能。它基于模型或关键词推断，只用于缩小排查范围。最终 Verdict 仍由开发者决定。

### 36.26 为什么验证时移除 Publisher

故障验证可能多次重跑 DAG，不能真的重复发布外部内容。移除高风险副作用节点可以降低验证对真实业务的影响。

### 36.27 当前最大的生产风险是什么

首先是 `X-User-Id` 可伪造，其次是任务执行和调度仍是单 JVM 内存协调。应先完成真实认证、持久任务执行和数据库约束，而不是继续堆 Agent 数量。

### 36.28 如何做模型成本控制

当前已有最大 ReAct 轮次和粗略 Token 计量。生产环境应增加每任务 Token Budget、按角色选模型、Prompt 缓存、Provider Usage Metadata、超预算停止和模型调用指标。

### 36.29 如何防 Prompt Injection

把检索文档视为不可信数据，明确区分系统指令和引用内容；对工具调用做白名单与参数 Schema；不允许文档内容直接修改系统策略；增加内容扫描、来源标记、最小权限和高风险人工审批。

### 36.30 如果让你继续做，第一步是什么

不是继续增加角色，而是建立真实 JWT/OIDC 身份、隔离测试 Profile、PostgreSQL/Flyway、Attempt 模型和持久任务队列。这些决定系统能否安全横向扩展。

## 37. 面试现场代码导览顺序

如果面试官要求打开代码，按下面顺序讲最清晰：

1. [ResearchTaskController](src/main/java/com/researchflow/controller/ResearchTaskController.java)：HTTP 入口和 RBAC。
2. [ResearchTaskService](src/main/java/com/researchflow/service/ResearchTaskService.java)：状态机、事务提交后异步、取消和持久化。
3. [SystemAgentPlanner](src/main/java/com/researchflow/agent/runtime/SystemAgentPlanner.java)：Spring AI 结构化计划和严格校验。
4. [MultiAgentOrchestrator](src/main/java/com/researchflow/agent/runtime/MultiAgentOrchestrator.java)：DAG 并行调度。
5. [AgentRegistry](src/main/java/com/researchflow/agent/runtime/AgentRegistry.java)：Agent、能力和工具绑定。
6. [ReActNodeExecutor](src/main/java/com/researchflow/agent/runtime/ReActNodeExecutor.java)：Action/Observation/Decision。
7. [CriticAgent](src/main/java/com/researchflow/agent/CriticAgent.java)、[FactCheckerAgent](src/main/java/com/researchflow/agent/FactCheckerAgent.java)、[ModeratorAgent](src/main/java/com/researchflow/agent/ModeratorAgent.java)：讨论层。
8. [WriterAgent](src/main/java/com/researchflow/agent/WriterAgent.java)：报告与引用校验。
9. [ToolRegistry](src/main/java/com/researchflow/tool/ToolRegistry.java)：风险与审批。
10. [ScenarioValidationService](src/main/java/com/researchflow/validation/ScenarioValidationService.java)：故障验证闭环。

## 38. 最终总结

ResearchFlow 当前最核心的技术主线是：

```text
Spring AI 结构化规划
  -> 严格 DAG 契约
  -> Agent Registry 路由
  -> 同层并行调度
  -> 受约束 ReAct 监督
  -> Critic / Fact Checker / Moderator 收敛
  -> Writer 引用输出
  -> Trace 持久化
  -> Scenario / Injection / Oracle 质量闭环
```

面试时应突出“模型能力与确定性工程控制的结合”：模型负责语义理解和内容生成，Java 代码负责状态机、依赖、权限、预算、数据边界、持久化和失败处理。这个项目真正有价值的地方不是 Agent 名字多，而是每个模型行为都被放进可验证、可追踪、有限制的后端执行框架中。
