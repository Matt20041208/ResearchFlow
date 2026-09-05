package com.researchflow.agent.runtime;

import com.researchflow.injection.InjectedFaultException;
import com.researchflow.llm.SpringAiClient;
import com.researchflow.tool.ToolDescriptor;
import com.researchflow.tool.ToolRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@Component
public class ReActNodeExecutor {
    private static final String DECISION_PROMPT = """
            你是多 Agent 运行时的节点监督器。请根据节点目标和最新观察结果决定下一步。
            只输出 JSON：{"decision":"COMPLETE|RETRY|TOOL|FAIL","reason":"简短可审计理由","nextInstruction":"重试指令","tool":"mcp:工具名","arguments":{}}。
            不要输出思维过程。结果已满足节点职责时选择 COMPLETE；可通过修正指令改善时选择 RETRY；
            结果无法恢复或违背目标时选择 FAIL；需要外部资料时选择 TOOL，并只能选择提供的 MCP 工具。不要因为措辞风格重复执行。""";

    private final SpringAiClient aiClient;
    private final ToolRegistry toolRegistry;
    private final int maxIterations;
    private final long maxElapsedMs;

    public ReActNodeExecutor(SpringAiClient aiClient, int maxIterations, long maxElapsedMs) {
        this(aiClient, new ToolRegistry(), maxIterations, maxElapsedMs);
    }

    @Autowired
    public ReActNodeExecutor(SpringAiClient aiClient, ToolRegistry toolRegistry,
                             @Value("${research-flow.react.max-iterations:3}") int maxIterations,
                             @Value("${research-flow.react.max-elapsed-ms:60000}") long maxElapsedMs) {
        this.aiClient = aiClient;
        this.toolRegistry = toolRegistry;
        this.maxIterations = Math.max(1, maxIterations);
        this.maxElapsedMs = Math.max(1000, maxElapsedMs);
    }

    public Object execute(PlannedNode node, SubAgent agent, AgentContext context,
                          Consumer<AgentExecutionEvent> eventSink, BooleanSupplier cancellationRequested,
                          TraceCollector collector) {
        long loopStarted = System.currentTimeMillis();
        String instruction = "完成节点 " + node.id() + " 的职责，并返回可供下游消费的结果";
        Object latestResult = null;

        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            checkCancellation(cancellationRequested);
            if (System.currentTimeMillis() - loopStarted >= maxElapsedMs) {
                if (latestResult != null) {
                    eventSink.accept(new AgentExecutionEvent(agent.name(), "BUDGET_EXHAUSTED",
                            "ReAct 时间预算耗尽，使用最近一次有效观察"));
                    return latestResult;
                }
                throw new IllegalStateException("节点 " + node.id() + " 超出 ReAct 时间预算");
            }

            long actionStarted = System.currentTimeMillis();
            String action = agent.requiredTools().isEmpty()
                    ? "AGENT:" + agent.name()
                    : "TOOLS:" + String.join(",", agent.requiredTools());
            eventSink.accept(new AgentExecutionEvent(agent.name(), "ACTION",
                    "ReAct 第 " + iteration + " 轮执行: " + summarize(instruction, 180)));
            try {
                Object result = agent.execute(context, instruction);
                checkCancellation(cancellationRequested);
                validateOutput(node, result);
                latestResult = result;
                String observation = summarize(result, 900);
                ReActDecision decision = decide(node, agent, context, iteration, instruction, observation, null);
                collector.nodeStep(node, agent.name(), iteration, action, observation,
                        decision.outcome().name(), decision.normalizedReason(),
                        System.currentTimeMillis() - actionStarted);

                if (decision.outcome() == ReActDecision.Outcome.FAIL) {
                    throw new SupervisorRejectedException("节点监督器拒绝结果: " + decision.normalizedReason());
                }
                if (decision.outcome() == ReActDecision.Outcome.TOOL) {
                    if (decision.tool() == null || decision.tool().isBlank()) {
                        throw new IllegalStateException("ReAct TOOL 决策缺少工具名称");
                    }
                    toolRegistry.authorizeTool(decision.tool(), java.util.Set.of());
                    Object toolObservation = toolRegistry.invoke(decision.tool(), decision.arguments());
                    instruction = "基于 MCP 工具 " + decision.tool() + " 的结果继续完成节点："
                            + summarize(toolObservation, 900);
                    eventSink.accept(new AgentExecutionEvent(agent.name(), "TOOL_OBSERVATION",
                            "已获得 MCP 工具观察结果: " + summarize(toolObservation, 180)));
                    continue;
                }
                if (decision.outcome() == ReActDecision.Outcome.RETRY && agent.supportsRetry()
                        && iteration < maxIterations) {
                    instruction = decision.normalizedInstruction(instruction);
                    eventSink.accept(new AgentExecutionEvent(agent.name(), "RETRYING",
                            "观察结果需要修正: " + summarize(decision.normalizedReason(), 180)));
                    continue;
                }
                if (decision.outcome() == ReActDecision.Outcome.RETRY) {
                    eventSink.accept(new AgentExecutionEvent(agent.name(), "BUDGET_EXHAUSTED",
                            agent.supportsRetry() ? "达到 ReAct 最大轮次，使用最近一次有效观察"
                                    : "Agent 不支持可控重试，使用当前有效观察"));
                } else {
                    eventSink.accept(new AgentExecutionEvent(agent.name(), "CONVERGED",
                            "ReAct 节点已收敛: " + summarize(decision.normalizedReason(), 180)));
                }
                return result;
            } catch (RuntimeException exception) {
                if (!retryable(exception)) throw exception;
                String observation = "执行异常: " + rootMessage(exception);
                ReActDecision decision = decide(node, agent, context, iteration, instruction, null, observation);
                collector.nodeStep(node, agent.name(), iteration, action, observation,
                        decision.outcome().name(), decision.normalizedReason(),
                        System.currentTimeMillis() - actionStarted);
                if (decision.outcome() == ReActDecision.Outcome.RETRY && agent.supportsRetry()
                        && iteration < maxIterations) {
                    instruction = decision.normalizedInstruction(instruction);
                    eventSink.accept(new AgentExecutionEvent(agent.name(), "RETRYING",
                            "根据异常观察调整下一轮动作"));
                    continue;
                }
                throw exception;
            }
        }
        if (latestResult != null) return latestResult;
        throw new IllegalStateException("节点 " + node.id() + " 未产生有效观察");
    }

    private ReActDecision decide(PlannedNode node, SubAgent agent, AgentContext context, int iteration,
                                 String instruction, String observation, String error) {
        if (!agent.requiredTools().contains("llm-completion")) {
            return error == null
                    ? new ReActDecision("COMPLETE", "节点未声明模型监督工具，确定性执行完成", "")
                    : new ReActDecision("FAIL", "节点未声明模型监督工具，保留原始异常", "");
        }
        String prompt = "目标: " + context.question()
                + "\n节点: " + node.id()
                + "\nAgent: " + agent.name()
                + "\n能力: " + agent.capabilities()
                + "\n可用工具: " + agent.requiredTools()
                + "\n可用 MCP 工具: " + toolRegistry.all().stream()
                .map(ToolDescriptor::name).filter(name -> name.startsWith("mcp:")).toList()
                + "\n轮次: " + iteration + "/" + maxIterations
                + "\n本轮指令: " + instruction
                + "\n观察结果: " + (observation == null ? error : observation);
        return aiClient.entity(DECISION_PROMPT, prompt, ReActDecision.class)
                .orElseGet(() -> error == null
                        ? new ReActDecision("COMPLETE", "模型监督不可用，确定性单轮执行完成", "")
                        : new ReActDecision("FAIL", "模型监督不可用，保留原始异常", ""));
    }

    private void validateOutput(PlannedNode node, Object result) {
        if (result == null) throw new IncompleteObservationException(node.agent() + " 返回空结果");
        if (result instanceof String text && text.isBlank()) {
            throw new IncompleteObservationException(node.agent() + " 返回空文本");
        }
        if (result instanceof List<?> list && list.isEmpty() && !node.id().equals("privateSources")) {
            throw new IncompleteObservationException(node.agent() + " 返回空列表");
        }
    }

    private boolean retryable(RuntimeException exception) {
        return !(exception instanceof MultiAgentOrchestrator.TaskCancelledException)
                && !(exception instanceof ToolRegistry.ApprovalRequiredException)
                && !(exception instanceof InjectedFaultException)
                && !(exception instanceof SupervisorRejectedException);
    }

    private void checkCancellation(BooleanSupplier cancellationRequested) {
        if (cancellationRequested.getAsBoolean() || Thread.currentThread().isInterrupted()) {
            throw new MultiAgentOrchestrator.TaskCancelledException();
        }
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private String summarize(Object value, int maxLength) {
        if (value == null) return "null";
        String text = String.valueOf(value).replace('\n', ' ').replaceAll("\\s+", " ");
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private static final class IncompleteObservationException extends RuntimeException {
        private IncompleteObservationException(String message) {
            super(message);
        }
    }

    private static final class SupervisorRejectedException extends RuntimeException {
        private SupervisorRejectedException(String message) {
            super(message);
        }
    }
}
