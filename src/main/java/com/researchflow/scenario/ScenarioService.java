package com.researchflow.scenario;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchflow.billing.UsageService;
import com.researchflow.billing.UsageType;
import com.researchflow.llm.SpringAiClient;
import com.researchflow.model.TraceNodeView;
import com.researchflow.model.TraceView;
import com.researchflow.persistence.ScenarioEntity;
import com.researchflow.persistence.ScenarioRepository;
import com.researchflow.service.ResearchTaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ScenarioService {
    private static final String SYSTEM_PROMPT = """
            你是链路测试场景推演 Agent。你会收到一次真实 Agent 任务的执行链路 JSON，
            包含节点依赖、输入输出摘要、耗时和状态。
            请找出人工容易遗漏的异常组合，重点覆盖：
            多外部依赖同时失效、合法但极端的数据与其他临界状态叠加、
            上游延迟导致下游状态变化、异步分支时序错位、降级链路叠加后失效。
            只输出 JSON，不要 Markdown：
            {"scenarios":[{"title":"...","nodeCombination":"节点ID组合","trigger":"触发条件",
            "injectedData":"注入内容或参数","expectation":"预期关注点","risk":"HIGH|MEDIUM|LOW"}]}
            最多 6 条。""";

    private final ScenarioRepository repository;
    private final ResearchTaskService taskService;
    private final SpringAiClient aiClient;
    private final UsageService usageService;
    private final ObjectMapper objectMapper;

    public ScenarioService(ScenarioRepository repository, ResearchTaskService taskService,
                           SpringAiClient aiClient, UsageService usageService, ObjectMapper objectMapper) {
        this.repository = repository;
        this.taskService = taskService;
        this.aiClient = aiClient;
        this.usageService = usageService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<ScenarioView> generate(String taskId, String userId) {
        String workspaceId = taskService.workspaceId(taskId);
        TraceView trace = taskService.trace(taskId);
        List<ScenarioSeed> seeds = List.of();
        String chainJson = "";
        try {
            chainJson = objectMapper.writeValueAsString(trace);
            var generated = aiClient.entity(SYSTEM_PROMPT, chainJson, ScenarioBatch.class);
            if (generated.isPresent() && generated.get().scenarios() != null) {
                seeds = generated.get().scenarios();
            }
        } catch (Exception ignored) {
            // Falls back to deterministic deduction when the model is unavailable.
        }
        if (seeds.isEmpty()) seeds = fallbackSeeds(trace);
        long estimatedTokens = Math.max(1, chainJson.length() / 4L);
        usageService.record(workspaceId, userId, UsageType.TOKEN_USED, estimatedTokens, taskId);
        return seeds.stream().limit(6)
                .map(seed -> view(repository.save(new ScenarioEntity(taskId, workspaceId, seed.title(),
                        seed.nodeCombination(), seed.trigger(), seed.injectedData(),
                        seed.expectation(), seed.risk()))))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ScenarioView> list(String taskId) {
        return repository.findByTaskIdOrderByCreatedAtDesc(taskId).stream().map(this::view).toList();
    }

    @Transactional
    public ScenarioView review(String taskId, Long scenarioId, ScenarioStatus status) {
        ScenarioEntity entity = get(taskId, scenarioId);
        entity.setStatus(status);
        return view(repository.save(entity));
    }

    @Transactional
    public void delete(String taskId, Long scenarioId) {
        repository.delete(get(taskId, scenarioId));
    }

    private ScenarioEntity get(String taskId, Long scenarioId) {
        return repository.findById(scenarioId)
                .filter(entity -> entity.getTaskId().equals(taskId))
                .orElseThrow(() -> new IllegalArgumentException("场景不存在: " + scenarioId));
    }

    private ScenarioView view(ScenarioEntity entity) {
        return new ScenarioView(entity.getId(), entity.getTaskId(), entity.getTitle(),
                entity.getNodeCombination(), entity.getTrigger(), entity.getInjectedData(),
                entity.getExpectation(), entity.getRisk(), entity.getStatus(), entity.getCreatedAt());
    }

    private List<ScenarioSeed> fallbackSeeds(TraceView trace) {
        List<TraceNodeView> nodes = trace.nodes();
        List<ScenarioSeed> seeds = new ArrayList<>();
        for (int index = 0; index < nodes.size() && seeds.size() < 6; index++) {
            TraceNodeView node = nodes.get(index);
            if (!"COMPLETED".equals(node.status())) continue;
            if (seeds.size() < 6) {
                seeds.add(new ScenarioSeed(
                        "节点[" + node.agent() + "] 响应延迟叠加下游状态变化",
                        node.nodeId(),
                        "为节点注入 3 秒响应延迟",
                        "{\"node\":\"" + node.nodeId() + "\",\"delayMs\":3000}",
                        "验证下游节点在慢响应下是否产生状态不一致，任务是否仍能按时完成",
                        "MEDIUM"));
            }
            for (int other = index + 1; other < nodes.size() && seeds.size() < 6; other++) {
                TraceNodeView peer = nodes.get(other);
                if (!"COMPLETED".equals(peer.status())) continue;
                seeds.add(new ScenarioSeed(
                        "节点[" + node.agent() + "] 超时与 [" + peer.agent() + "] 边界数据叠加",
                        node.nodeId() + "+" + peer.nodeId(),
                        "同时注入慢响应与空数据",
                        "{\"nodes\":[\"" + node.nodeId() + "\",\"" + peer.nodeId()
                                + "\"],\"delayMs\":3000,\"data\":[]}",
                        "验证多异常叠加时降级链路是否仍然成立，报告是否保留有效来源",
                        "HIGH"));
            }
        }
        return seeds;
    }
}
