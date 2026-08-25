package com.researchflow.agent.runtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.researchflow.tool.ToolRegistry;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import jakarta.annotation.PreDestroy;

@Component
public class MultiAgentOrchestrator {
    private final SystemAgentPlanner planner;
    private final AgentRegistry registry;
    private final ToolRegistry toolRegistry;
    private final ExecutorService executor;

    public MultiAgentOrchestrator(SystemAgentPlanner planner, AgentRegistry registry, ToolRegistry toolRegistry,
                                  @Value("${research-flow.executor-threads:8}") int threads) {
        this.planner = planner;
        this.registry = registry;
        this.toolRegistry = toolRegistry;
        this.executor = Executors.newFixedThreadPool(Math.max(2, threads));
    }

    public OrchestrationResult execute(String question, String workspaceId,
                                       Consumer<AgentExecutionEvent> eventSink,
                                       BooleanSupplier cancellationRequested, Set<String> approvedTools) {
        SystemPlan plan = planner.plan(question);
        validate(plan);
        AgentContext context = new AgentContext(question, workspaceId);
        eventSink.accept(new AgentExecutionEvent("system-agent", "PLANNED",
                "动态规划生成 " + plan.nodes().size() + " 个 Sub-Agent 节点: "
                        + plan.nodes().stream().map(PlannedNode::id).toList()));

        Set<String> completed = new HashSet<>();
        while (completed.size() < plan.nodes().size()) {
            if (cancellationRequested.getAsBoolean() || Thread.currentThread().isInterrupted()) {
                throw new TaskCancelledException();
            }
            List<PlannedNode> ready = plan.nodes().stream()
                    .filter(node -> !completed.contains(node.id()))
                    .filter(node -> completed.containsAll(node.dependsOn()))
                    .toList();
            if (ready.isEmpty()) throw new IllegalStateException("DAG 无可执行节点，存在循环依赖");

            List<CompletableFuture<Void>> futures = ready.stream().map(node -> CompletableFuture.runAsync(() -> {
                if (cancellationRequested.getAsBoolean()) throw new TaskCancelledException();
                SubAgent agent = registry.get(node.agent());
                toolRegistry.authorize(agent, approvedTools);
                eventSink.accept(new AgentExecutionEvent(agent.name(), "RUNNING", "开始执行，依赖已满足"));
                Object result = agent.execute(context);
                validateOutput(node, result);
                context.put(node.id(), result);
                eventSink.accept(new AgentExecutionEvent(agent.name(), "COMPLETED", "执行完成"));
            }, executor)).toList();
            for (CompletableFuture<Void> future : futures) {
                try {
                    future.join();
                } catch (CompletionException exception) {
                    if (exception.getCause() instanceof RuntimeException runtimeException) throw runtimeException;
                    throw exception;
                }
            }
            ready.forEach(node -> completed.add(node.id()));
        }
        return new OrchestrationResult(context.get("writer", String.class),
                context.getList("sources", com.researchflow.model.SourceDocument.class));
    }

    private void validateOutput(PlannedNode node, Object result) {
        if (result == null) throw new IllegalStateException(node.agent() + " 返回空结果");
        if (result instanceof String text && text.isBlank()) {
            throw new IllegalStateException(node.agent() + " 返回空文本");
        }
        if (result instanceof List<?> list && list.isEmpty() && !node.id().equals("privateSources")) {
            throw new IllegalStateException(node.agent() + " 返回空列表");
        }
    }

    private void validate(SystemPlan plan) {
        Map<String, PlannedNode> nodes = new HashMap<>();
        for (PlannedNode node : plan.nodes()) {
            if (nodes.put(node.id(), node) != null) throw new IllegalStateException("重复的工作流节点: " + node.id());
            registry.get(node.agent());
        }
        for (PlannedNode node : plan.nodes()) {
            if (!nodes.keySet().containsAll(node.dependsOn())) {
                throw new IllegalStateException("节点依赖不存在: " + node.id());
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    public static class TaskCancelledException extends RuntimeException {
        public TaskCancelledException() { super("任务已取消"); }
    }
}
