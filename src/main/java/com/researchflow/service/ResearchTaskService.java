package com.researchflow.service;

import com.researchflow.agent.runtime.MultiAgentOrchestrator;
import com.researchflow.tool.ToolRegistry;
import com.researchflow.model.AgentEvent;
import com.researchflow.model.ResearchRequest;
import com.researchflow.model.TaskSnapshot;
import com.researchflow.model.TaskStatus;
import com.researchflow.model.TaskSummary;
import com.researchflow.persistence.TaskEntity;
import com.researchflow.persistence.TaskEventEntity;
import com.researchflow.persistence.TaskEventRepository;
import com.researchflow.persistence.TaskCitationEntity;
import com.researchflow.persistence.TaskCitationRepository;
import com.researchflow.persistence.TaskRepository;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class ResearchTaskService {
    private final MultiAgentOrchestrator orchestrator;
    private final int maxAttempts;
    private final TaskRepository taskRepository;
    private final TaskEventRepository eventRepository;
    private final TaskCitationRepository citationRepository;
    private final ExecutorService executor;
    private final ConcurrentHashMap<String, TaskState> tasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Future<?>> runningTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public ResearchTaskService(MultiAgentOrchestrator orchestrator, TaskRepository taskRepository,
                               TaskEventRepository eventRepository, TaskCitationRepository citationRepository,
                               @Value("${research-flow.executor-threads:8}") int executorThreads,
                               @Value("${research-flow.max-attempts:3}") int maxAttempts) {
        this.orchestrator = orchestrator;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.taskRepository = taskRepository;
        this.eventRepository = eventRepository;
        this.citationRepository = citationRepository;
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
        TaskState state = new TaskState(new TaskEntity(taskId, request.question(), request.normalizedWorkspaceId()));
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

    @Transactional(readOnly = true)
    public List<com.researchflow.model.Citation> citations(String taskId) {
        find(taskId);
        return citationRepository.findByTaskIdOrderByCitationNumberAsc(taskId).stream()
                .map(entity -> new com.researchflow.model.Citation(entity.getCitationNumber(), entity.getSourceId(),
                        entity.getSourceType(), entity.getTitle(), entity.getUrl(), entity.getExcerpt(),
                        entity.getConfidence()))
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
        if (state.entity.getStatus() != TaskStatus.CREATED && state.entity.getStatus() != TaskStatus.RUNNING
                && state.entity.getStatus() != TaskStatus.WAITING_APPROVAL) return;
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

    @Transactional
    public void approve(String taskId, String tool) {
        TaskState state = find(taskId);
        if (state.entity.getStatus() != TaskStatus.WAITING_APPROVAL) {
            throw new IllegalStateException("任务当前不在等待审批状态");
        }
        if (!tool.equals(state.entity.getPendingApprovalTool())) {
            throw new IllegalArgumentException("待审批工具不匹配: " + tool);
        }
        state.entity.approveTool(tool);
        state.entity.setStatus(TaskStatus.CREATED);
        state.entity.setError(null);
        state.entity.setUpdatedAt(Instant.now());
        taskRepository.save(state.entity);
        publish(state, "approval", "APPROVED", "已批准工具: " + tool);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() { submit(state); }
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
            var result = orchestrator.execute(state.entity.getQuestion(), state.entity.getWorkspaceId(),
                    event -> publish(state, event.agent(), event.status(), event.message()),
                    () -> state.entity.getStatus() == TaskStatus.CANCELLED,
                    state.entity.getApprovedTools());
            state.entity.setReport(result.report());
            citationRepository.deleteByTaskId(state.entity.getId());
            for (int index = 0; index < result.sources().size(); index++) {
                var source = result.sources().get(index);
                citationRepository.save(new TaskCitationEntity(state.entity.getId(), index + 1, source.id(),
                        source.sourceType(), source.title(), source.url(), source.excerpt(), source.confidence()));
            }
            state.entity.setStatus(TaskStatus.COMPLETED);
            state.entity.setUpdatedAt(Instant.now());
            taskRepository.save(state.entity);
            publish(state, "system", "COMPLETED", "研究报告已生成");
        } catch (MultiAgentOrchestrator.TaskCancelledException exception) {
            return;
        } catch (ToolRegistry.ApprovalRequiredException exception) {
            state.entity.setStatus(TaskStatus.WAITING_APPROVAL);
            state.entity.setPendingApprovalTool(exception.tool());
            state.entity.setError(exception.getMessage());
            state.entity.setUpdatedAt(Instant.now());
            taskRepository.save(state.entity);
            publish(state, "approval", "WAITING_APPROVAL", exception.getMessage());
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

    private void publish(TaskState state, String agent, String status, String message) {
        Instant occurredAt = Instant.now();
        AgentEvent event = new AgentEvent(state.entity.getId(), agent, status, message, occurredAt);
        synchronized (state) {
            state.events.add(event);
            state.entity.setUpdatedAt(occurredAt);
            taskRepository.save(state.entity);
            eventRepository.save(new TaskEventEntity(state.entity.getId(), agent, status, message, occurredAt));
        }
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
                    entity.getUpdatedAt(), entity.getReport(), entity.getError(), entity.getAttempts(),
                    entity.getPendingApprovalTool(), List.copyOf(events));
        }
    }
}
