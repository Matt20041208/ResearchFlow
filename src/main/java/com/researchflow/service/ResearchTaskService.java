package com.researchflow.service;

import com.researchflow.agent.ComparisonAgent;
import com.researchflow.agent.EvidenceAgent;
import com.researchflow.agent.PlannerAgent;
import com.researchflow.agent.ResearchAgent;
import com.researchflow.agent.ResearchPlan;
import com.researchflow.agent.SourceSearchAgent;
import com.researchflow.agent.WriterAgent;
import com.researchflow.model.AgentEvent;
import com.researchflow.model.ResearchRequest;
import com.researchflow.model.TaskSnapshot;
import com.researchflow.model.TaskStatus;
import com.researchflow.model.TaskSummary;
import com.researchflow.persistence.TaskEntity;
import com.researchflow.persistence.TaskEventEntity;
import com.researchflow.persistence.TaskEventRepository;
import com.researchflow.persistence.TaskRepository;
import com.researchflow.workflow.WorkflowLoader;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
import java.util.concurrent.Future;

@Service
public class ResearchTaskService {
    private final PlannerAgent plannerAgent;
    private final SourceSearchAgent sourceSearchAgent;
    private final EvidenceAgent evidenceAgent;
    private final ComparisonAgent comparisonAgent;
    private final WriterAgent writerAgent;
    private final WorkflowLoader workflowLoader;
    private final int maxAttempts;
    private final TaskRepository taskRepository;
    private final TaskEventRepository eventRepository;
    private final ExecutorService executor;
    private final ConcurrentHashMap<String, TaskState> tasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Future<?>> runningTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public ResearchTaskService(PlannerAgent plannerAgent, SourceSearchAgent sourceSearchAgent,
                               EvidenceAgent evidenceAgent, ComparisonAgent comparisonAgent,
                               WriterAgent writerAgent, TaskRepository taskRepository,
                               TaskEventRepository eventRepository,
                               WorkflowLoader workflowLoader,
                               @Value("${research-flow.executor-threads:8}") int executorThreads,
                               @Value("${research-flow.max-attempts:3}") int maxAttempts) {
        this.plannerAgent = plannerAgent;
        this.sourceSearchAgent = sourceSearchAgent;
        this.evidenceAgent = evidenceAgent;
        this.comparisonAgent = comparisonAgent;
        this.writerAgent = writerAgent;
        this.workflowLoader = workflowLoader;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.taskRepository = taskRepository;
        this.eventRepository = eventRepository;
        this.executor = Executors.newFixedThreadPool(executorThreads);
    }

    @PostConstruct
    @Transactional
    public void recoverInterruptedTasks() {
        taskRepository.findAll().stream()
                .filter(task -> task.getStatus() == TaskStatus.RUNNING)
                .forEach(task -> {
                    task.setStatus(TaskStatus.FAILED);
                    task.setError("应用重启导致任务中断");
                    task.setUpdatedAt(Instant.now());
                    taskRepository.save(task);
                });
    }

    @Transactional
    public String create(ResearchRequest request) {
        String taskId = UUID.randomUUID().toString();
        TaskState state = new TaskState(new TaskEntity(taskId, request.question()));
        tasks.put(taskId, state);
        taskRepository.save(state.entity);
        publish(state, "system", "CREATED", "研究任务已创建");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                submit(state);
            }
        });
        return taskId;
    }

    @Transactional(readOnly = true)
    public TaskSnapshot get(String taskId) {
        return find(taskId).snapshot();
    }

    @Transactional(readOnly = true)
    public List<TaskSummary> list() {
        return taskRepository.findAll().stream()
                .sorted((left, right) -> right.getUpdatedAt().compareTo(left.getUpdatedAt()))
                .map(task -> new TaskSummary(task.getId(), task.getQuestion(), task.getStatus(),
                        task.getCreatedAt(), task.getUpdatedAt(), task.getAttempts()))
                .toList();
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

    @Transactional
    public void cancel(String taskId) {
        TaskState state = find(taskId);
        if (state.entity.getStatus() != TaskStatus.CREATED && state.entity.getStatus() != TaskStatus.RUNNING) return;
        state.entity.setStatus(TaskStatus.CANCELLED);
        state.entity.setUpdatedAt(Instant.now());
        taskRepository.save(state.entity);
        Future<?> future = runningTasks.get(taskId);
        if (future != null) future.cancel(true);
        publish(state, "system", "CANCELLED", "研究任务已取消");
    }

    @Transactional
    public void retry(String taskId) {
        TaskState state = find(taskId);
        if (state.entity.getStatus() != TaskStatus.FAILED && state.entity.getStatus() != TaskStatus.CANCELLED) return;
        if (state.entity.getAttempts() >= maxAttempts) {
            throw new IllegalStateException("任务已达到最大重试次数: " + maxAttempts);
        }
        state.entity.setStatus(TaskStatus.CREATED);
        state.entity.setError(null);
        state.entity.setReport(null);
        state.entity.setUpdatedAt(Instant.now());
        taskRepository.save(state.entity);
        publish(state, "system", "RETRYING", "重新执行研究任务");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                submit(state);
            }
        });
    }

    private void submit(TaskState state) {
        Future<?> future = executor.submit(() -> run(state));
        runningTasks.put(state.entity.getId(), future);
    }

    private TaskState find(String taskId) {
        TaskState cached = tasks.get(taskId);
        if (cached != null) return cached;
        TaskEntity entity = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("研究任务不存在: " + taskId));
        TaskState restored = new TaskState(entity);
        eventRepository.findByTaskIdOrderByOccurredAtAsc(taskId).forEach(event ->
                restored.events.add(new AgentEvent(event.getTaskId(), event.getAgent(), event.getStatus(),
                        event.getMessage(), event.getOccurredAt())));
        TaskState previous = tasks.putIfAbsent(taskId, restored);
        return previous == null ? restored : previous;
    }

    private void run(TaskState state) {
        try {
            if (!markRunning(state)) return;
            if (!workflowLoader.executionOrder().equals(List.of("planner", "search", "evidence", "comparison", "writer"))) {
                throw new IllegalStateException("当前 Agent 输入输出适配器不支持该工作流");
            }
            ResearchPlan plan = runAgent(state, plannerAgent, state.entity.getQuestion());
            List<com.researchflow.model.SourceDocument> sources = runAgent(state, sourceSearchAgent, plan);
            String evidence = runEvidenceInParallel(state, sources);
            String comparison = runAgent(state, comparisonAgent, new ComparisonAgent.Input(plan, evidence));
            state.entity.setReport(runAgent(state, writerAgent,
                    new WriterAgent.Input(state.entity.getQuestion(), comparison, sources)));
            state.entity.setStatus(TaskStatus.COMPLETED);
            state.entity.setUpdatedAt(Instant.now());
            taskRepository.save(state.entity);
            publish(state, "system", "COMPLETED", "研究报告已生成");
        } catch (Exception exception) {
            if (state.entity.getStatus() == TaskStatus.CANCELLED || Thread.currentThread().isInterrupted()) return;
            state.entity.setStatus(TaskStatus.FAILED);
            state.entity.setError(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
            state.entity.setUpdatedAt(Instant.now());
            taskRepository.save(state.entity);
            publish(state, "system", "FAILED", "任务失败: " + state.entity.getError());
        } finally {
            runningTasks.remove(state.entity.getId());
        }
    }

    private boolean markRunning(TaskState state) {
        synchronized (state) {
            if (state.entity.getStatus() == TaskStatus.CANCELLED) return false;
            state.entity.setStatus(TaskStatus.RUNNING);
            state.entity.setAttempts(state.entity.getAttempts() + 1);
            state.entity.setUpdatedAt(Instant.now());
            taskRepository.save(state.entity);
        }
        publish(state, "system", "RUNNING", "System Agent 开始拆解研究任务");
        return true;
    }

    private <I, O> O runAgent(TaskState state, ResearchAgent<I, O> agent, I input) {
        publish(state, agent.name(), "RUNNING", "开始执行");
        O output = agent.execute(input);
        publish(state, agent.name(), "COMPLETED", "执行完成");
        return output;
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

    private void publish(TaskState state, String agent, String status, String message) {
        Instant occurredAt = Instant.now();
        AgentEvent event = new AgentEvent(state.entity.getId(), agent, status, message, occurredAt);
        state.events.add(event);
        state.entity.setUpdatedAt(occurredAt);
        taskRepository.save(state.entity);
        eventRepository.save(new TaskEventEntity(state.entity.getId(), agent, status, message, occurredAt));
        emitters.getOrDefault(state.entity.getId(), new CopyOnWriteArrayList<>()).forEach(emitter -> send(emitter, event));
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

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private static final class TaskState {
        private final TaskEntity entity;
        private final CopyOnWriteArrayList<AgentEvent> events = new CopyOnWriteArrayList<>();

        private TaskState(TaskEntity entity) { this.entity = entity; }

        private TaskSnapshot snapshot() {
            return new TaskSnapshot(entity.getId(), entity.getQuestion(), entity.getStatus(), entity.getCreatedAt(),
                    entity.getUpdatedAt(), entity.getReport(), entity.getError(), entity.getAttempts(), List.copyOf(events));
        }
    }
}
