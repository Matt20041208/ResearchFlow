package com.researchflow.validation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchflow.agent.runtime.MultiAgentOrchestrator;
import com.researchflow.agent.runtime.OrchestrationResult;
import com.researchflow.agent.runtime.PlannedNode;
import com.researchflow.agent.runtime.SystemPlan;
import com.researchflow.agent.runtime.TraceCollector;
import com.researchflow.injection.InjectedFaultException;
import com.researchflow.injection.InjectionRule;
import com.researchflow.injection.InjectionRuleFactory;
import com.researchflow.persistence.ScenarioEntity;
import com.researchflow.persistence.ScenarioRepository;
import com.researchflow.persistence.ScenarioValidationEntity;
import com.researchflow.persistence.ScenarioValidationRepository;
import com.researchflow.scenario.ScenarioStatus;
import com.researchflow.service.ResearchTaskService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ScenarioValidationService {
    private final ScenarioRepository scenarioRepository;
    private final ScenarioValidationRepository validationRepository;
    private final ResearchTaskService taskService;
    private final MultiAgentOrchestrator orchestrator;
    private final InjectionRuleFactory ruleFactory;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;

    public ScenarioValidationService(ScenarioRepository scenarioRepository,
                                     ScenarioValidationRepository validationRepository,
                                     ResearchTaskService taskService, MultiAgentOrchestrator orchestrator,
                                     InjectionRuleFactory ruleFactory,
                                     ObjectMapper objectMapper,
                                     @Value("${research-flow.validation.executor-threads:2}") int threads) {
        this.scenarioRepository = scenarioRepository;
        this.validationRepository = validationRepository;
        this.taskService = taskService;
        this.orchestrator = orchestrator;
        this.ruleFactory = ruleFactory;
        this.objectMapper = objectMapper;
        this.executor = Executors.newFixedThreadPool(Math.max(1, threads));
    }

    @Transactional
    public ValidationRunView start(String taskId, Long scenarioId, String userId) {
        ScenarioEntity scenario = scenario(taskId, scenarioId);
        if (scenario.getStatus() != ScenarioStatus.APPROVED) {
            throw new IllegalStateException("只有已采纳场景才能执行验证");
        }
        SystemPlan plan = taskService.trace(taskId).plan();
        if (plan == null) throw new IllegalStateException("原始任务没有可复用的 DAG");
        SystemPlan safePlan = safePlan(plan);
        List<InjectionRule> rules = ruleFactory.create(scenario, safePlan);
        String rulesJson = json(rules);
        ScenarioValidationEntity validation = validationRepository.save(new ScenarioValidationEntity(
                scenarioId, taskId, scenario.getWorkspaceId(), userId, rulesJson, scenario.getExpectation()));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                executor.submit(() -> execute(validation.getId(), safePlan, rules));
            }
        });
        return view(validation);
    }

    @PostConstruct
    @Transactional
    public void recoverInterruptedRuns() {
        validationRepository.findByStatusIn(List.of(ValidationRunStatus.QUEUED, ValidationRunStatus.RUNNING))
                .forEach(run -> {
                    run.setStatus(ValidationRunStatus.FAILED);
                    run.setError("应用重启导致验证运行中断");
                    run.setCompletedAt(Instant.now());
                    validationRepository.save(run);
                });
    }

    @Transactional(readOnly = true)
    public List<ValidationRunView> list(String taskId, Long scenarioId) {
        scenario(taskId, scenarioId);
        return validationRepository.findByScenarioIdOrderByCreatedAtDesc(scenarioId).stream()
                .map(this::view).toList();
    }

    @Transactional
    public ValidationRunView review(String taskId, Long scenarioId, String runId, ValidationVerdict verdict) {
        scenario(taskId, scenarioId);
        ScenarioValidationEntity run = validationRepository.findById(runId)
                .filter(entity -> entity.getScenarioId().equals(scenarioId))
                .orElseThrow(() -> new IllegalArgumentException("验证记录不存在: " + runId));
        run.setVerdict(verdict);
        return view(validationRepository.save(run));
    }

    private void execute(String runId, SystemPlan plan, List<InjectionRule> rules) {
        ScenarioValidationEntity run = validationRepository.findById(runId).orElseThrow();
        long started = System.currentTimeMillis();
        run.setStatus(ValidationRunStatus.RUNNING);
        run.setStartedAt(Instant.now());
        validationRepository.save(run);
        List<Map<String, Object>> nodeTrace = new CopyOnWriteArrayList<>();
        Map<String, String> inputs = new ConcurrentHashMap<>();
        TraceCollector collector = new TraceCollector() {
            public void nodeStarted(PlannedNode node, String agentName, String inputSummary) {
                inputs.put(node.id(), inputSummary);
            }
            public void nodeCompleted(PlannedNode node, String agentName, String outputSummary, long durationMs) {
                nodeTrace.add(Map.of("nodeId", node.id(), "agent", agentName, "status", "COMPLETED",
                        "input", inputs.getOrDefault(node.id(), ""), "output", outputSummary,
                        "durationMs", durationMs));
            }
            public void nodeFailed(PlannedNode node, String agentName, String error, long durationMs) {
                nodeTrace.add(Map.of("nodeId", node.id(), "agent", agentName, "status", "FAILED",
                        "input", inputs.getOrDefault(node.id(), ""), "error", error == null ? "" : error,
                        "durationMs", durationMs));
            }
        };
        try {
            var task = taskService.get(run.getTaskId());
            OrchestrationResult result = orchestrator.executePlan(task.question(), run.getWorkspaceId(), plan,
                    event -> {}, () -> false, Set.of(), collector, rules);
            run.setStatus(ValidationRunStatus.COMPLETED);
            run.setOutputSummary("链路在注入条件下完成，报告长度 " + result.report().length() + " 字符");
        } catch (Exception exception) {
            run.setError(rootMessage(exception));
            run.setStatus(expectedFault(exception) ? ValidationRunStatus.COMPLETED : ValidationRunStatus.FAILED);
            run.setOutputSummary(expectedFault(exception) ? "注入后观察到链路失败" : "验证运行自身失败");
        } finally {
            run.setActualTraceJson(json(nodeTrace));
            run.setDurationMs(System.currentTimeMillis() - started);
            run.setCompletedAt(Instant.now());
            validationRepository.save(run);
        }
    }

    private SystemPlan safePlan(SystemPlan plan) {
        return new SystemPlan(plan.goal(), plan.nodes().stream()
                .filter(node -> !node.agent().equals("publisher-agent"))
                .toList());
    }

    private boolean expectedFault(Exception exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof InjectedFaultException) return true;
            if (current.getMessage() != null && current.getMessage().contains("返回空")) return true;
            current = current.getCause();
        }
        return false;
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private ScenarioEntity scenario(String taskId, Long scenarioId) {
        return scenarioRepository.findById(scenarioId)
                .filter(entity -> entity.getTaskId().equals(taskId))
                .orElseThrow(() -> new IllegalArgumentException("场景不存在: " + scenarioId));
    }

    private ValidationRunView view(ScenarioValidationEntity entity) {
        List<InjectionRule> rules = List.of();
        try { rules = objectMapper.readValue(entity.getRulesJson(), new TypeReference<>() {}); }
        catch (Exception ignored) {}
        return new ValidationRunView(entity.getId(), entity.getScenarioId(), entity.getTaskId(),
                entity.getStatus(), entity.getVerdict(), rules, entity.getExpectation(),
                entity.getActualTraceJson(), entity.getOutputSummary(), entity.getError(),
                entity.getDurationMs(), entity.getCreatedAt(), entity.getStartedAt(), entity.getCompletedAt());
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("验证数据序列化失败", exception); }
    }

    @PreDestroy
    public void shutdown() { executor.shutdownNow(); }
}
