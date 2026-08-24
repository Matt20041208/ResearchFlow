package com.researchflow.service;

import com.researchflow.agent.ComparisonAgent;
import com.researchflow.agent.EvidenceAgent;
import com.researchflow.agent.PlannerAgent;
import com.researchflow.agent.ResearchAgent;
import com.researchflow.agent.SourceSearchAgent;
import com.researchflow.agent.WriterAgent;
import com.researchflow.agent.ResearchPlan;
import com.researchflow.model.AgentEvent;
import com.researchflow.model.ResearchRequest;
import com.researchflow.model.TaskSnapshot;
import com.researchflow.model.TaskStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jakarta.annotation.PreDestroy;

@Service
public class ResearchTaskService {
    private final PlannerAgent plannerAgent;
    private final SourceSearchAgent sourceSearchAgent;
    private final EvidenceAgent evidenceAgent;
    private final ComparisonAgent comparisonAgent;
    private final WriterAgent writerAgent;
    private final ExecutorService executor;
    private final ConcurrentHashMap<String, TaskState> tasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public ResearchTaskService(PlannerAgent plannerAgent, SourceSearchAgent sourceSearchAgent,
                               EvidenceAgent evidenceAgent, ComparisonAgent comparisonAgent,
                               WriterAgent writerAgent,
                               @Value("${research-flow.executor-threads:8}") int executorThreads) {
        this.plannerAgent = plannerAgent;
        this.sourceSearchAgent = sourceSearchAgent;
        this.evidenceAgent = evidenceAgent;
        this.comparisonAgent = comparisonAgent;
        this.writerAgent = writerAgent;
        this.executor = Executors.newFixedThreadPool(executorThreads);
    }

    public String create(ResearchRequest request) {
        String taskId = UUID.randomUUID().toString();
        TaskState state = new TaskState(taskId, request.question());
        tasks.put(taskId, state);
        publish(state, "system", "CREATED", "研究任务已创建");
        executor.submit(() -> run(state));
        return taskId;
    }

    public TaskSnapshot get(String taskId) {
        TaskState state = find(taskId);
        return state.snapshot();
    }

    public SseEmitter events(String taskId) {
        TaskState state = find(taskId);
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(taskId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        state.events.forEach(event -> send(emitter, event));
        emitter.onCompletion(() -> removeEmitter(taskId, emitter));
        emitter.onTimeout(() -> removeEmitter(taskId, emitter));
        return emitter;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    private TaskState find(String taskId) {
        TaskState state = tasks.get(taskId);
        if (state == null) throw new IllegalArgumentException("研究任务不存在: " + taskId);
        return state;
    }

    private void run(TaskState state) {
        try {
            state.status = TaskStatus.RUNNING;
            publish(state, "system", "RUNNING", "System Agent 开始拆解研究任务");
            ResearchPlan plan = runAgent(state, plannerAgent, state.question);
            List<com.researchflow.model.SourceDocument> sources = runAgent(state, sourceSearchAgent, plan);
            String evidence = runEvidenceInParallel(state, sources);
            String comparison = runAgent(state, comparisonAgent, new ComparisonAgent.Input(plan, evidence));
            state.report = runAgent(state, writerAgent, new WriterAgent.Input(state.question, comparison, sources));
            state.status = TaskStatus.COMPLETED;
            publish(state, "system", "COMPLETED", "研究报告已生成");
        } catch (Exception exception) {
            state.status = TaskStatus.FAILED;
            publish(state, "system", "FAILED", "任务失败: " + exception.getMessage());
        }
    }

    private String runEvidenceInParallel(TaskState state, List<com.researchflow.model.SourceDocument> sources) {
        publish(state, evidenceAgent.name(), "RUNNING", "并行提取 " + sources.size() + " 个来源");
        List<CompletableFuture<String>> futures = sources.stream()
                .map(source -> CompletableFuture.supplyAsync(() -> evidenceAgent.extract(source), executor))
                .toList();
        String evidence = futures.stream().map(CompletableFuture::join)
                .reduce((left, right) -> left + "\n" + right).orElse("未提取到证据");
        publish(state, evidenceAgent.name(), "COMPLETED", "证据提取完成");
        return evidence;
    }

    private <I, O> O runAgent(TaskState state, ResearchAgent<I, O> agent, I input) {
        publish(state, agent.name(), "RUNNING", "开始执行");
        O output = agent.execute(input);
        publish(state, agent.name(), "COMPLETED", "执行完成");
        return output;
    }

    private void publish(TaskState state, String agent, String status, String message) {
        AgentEvent event = new AgentEvent(state.taskId, agent, status, message, Instant.now());
        state.events.add(event);
        state.updatedAt = event.occurredAt();
        emitters.getOrDefault(state.taskId, new CopyOnWriteArrayList<>()).forEach(emitter -> send(emitter, event));
    }

    private void send(SseEmitter emitter, AgentEvent event) {
        try {
            emitter.send(SseEmitter.event().name("agent-event").data(event, MediaType.APPLICATION_JSON));
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
    }

    private void removeEmitter(String taskId, SseEmitter emitter) {
        List<SseEmitter> taskEmitters = emitters.get(taskId);
        if (taskEmitters != null) taskEmitters.remove(emitter);
    }

    private static final class TaskState {
        private final String taskId;
        private final String question;
        private final Instant createdAt = Instant.now();
        private final CopyOnWriteArrayList<AgentEvent> events = new CopyOnWriteArrayList<>();
        private volatile Instant updatedAt = createdAt;
        private volatile TaskStatus status = TaskStatus.CREATED;
        private volatile String report;

        private TaskState(String taskId, String question) {
            this.taskId = taskId;
            this.question = question;
        }

        private TaskSnapshot snapshot() {
            return new TaskSnapshot(taskId, question, status, createdAt, updatedAt, report, List.copyOf(events));
        }
    }
}
