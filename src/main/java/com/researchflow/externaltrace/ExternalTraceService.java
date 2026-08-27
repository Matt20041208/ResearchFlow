package com.researchflow.externaltrace;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchflow.agent.runtime.PlannedNode;
import com.researchflow.agent.runtime.SystemPlan;
import com.researchflow.model.TraceNodeView;
import com.researchflow.model.TraceView;
import com.researchflow.persistence.ExternalTraceEntity;
import com.researchflow.persistence.ExternalTraceNodeEntity;
import com.researchflow.persistence.ExternalTraceNodeRepository;
import com.researchflow.persistence.ExternalTraceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ExternalTraceService {
    private final ExternalTraceRepository traceRepository;
    private final ExternalTraceNodeRepository nodeRepository;
    private final ObjectMapper objectMapper;

    public ExternalTraceService(ExternalTraceRepository traceRepository,
                                ExternalTraceNodeRepository nodeRepository, ObjectMapper objectMapper) {
        this.traceRepository = traceRepository;
        this.nodeRepository = nodeRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ExternalTraceView ingest(ExternalTraceIngestRequest request, String userId) {
        validate(request);
        List<PlannedNode> planNodes = request.nodes().stream()
                .map(node -> new PlannedNode(node.nodeId(), node.agent(), node.normalizedDependencies()))
                .toList();
        SystemPlan plan = new SystemPlan(request.name(), planNodes);
        ExternalTraceEntity trace = traceRepository.save(new ExternalTraceEntity(request.workspaceId(),
                request.name().trim(), request.sourceSystem().trim(), status(request.nodes()),
                request.nodes().size(), request.startedAt(), request.endedAt(), userId, json(plan)));
        for (ExternalTraceNodeRequest node : request.nodes()) {
            nodeRepository.save(new ExternalTraceNodeEntity(trace.getId(), node.nodeId(), node.agent(),
                    json(node.normalizedDependencies()), node.status(), trim(node.input(), 20_000),
                    trim(node.output(), 20_000), trim(node.error(), 20_000), node.durationMs(),
                    node.startedAt(), node.externalBoundary(), node.asyncNode()));
        }
        return view(trace);
    }

    @Transactional(readOnly = true)
    public List<ExternalTraceSummary> list(String workspaceId) {
        return traceRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId).stream()
                .map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public ExternalTraceView get(String traceId) {
        return view(entity(traceId));
    }

    @Transactional(readOnly = true)
    public TraceView trace(String traceId) {
        ExternalTraceEntity entity = entity(traceId);
        SystemPlan plan = parsePlan(entity.getPlanJson());
        List<TraceNodeView> nodes = nodeRepository.findByTraceIdOrderByStartedAtAsc(traceId).stream()
                .map(node -> new TraceNodeView(node.getNodeId(), node.getAgent(), node.getStatus().name(),
                        node.getInputSummary(), node.getOutputSummary(), node.getErrorSummary(),
                        node.getDurationMs(), node.getStartedAt(), node.isExternalBoundary(), node.isAsyncNode()))
                .toList();
        return new TraceView(traceId, plan, nodes);
    }

    @Transactional
    public void delete(String traceId) {
        entity(traceId);
        nodeRepository.deleteByTraceId(traceId);
        traceRepository.deleteById(traceId);
    }

    @Transactional(readOnly = true)
    public String workspaceId(String traceId) { return entity(traceId).getWorkspaceId(); }

    private ExternalTraceView view(ExternalTraceEntity entity) {
        return new ExternalTraceView(summary(entity), trace(entity.getId()));
    }

    private ExternalTraceSummary summary(ExternalTraceEntity entity) {
        return new ExternalTraceSummary(entity.getId(), entity.getWorkspaceId(), entity.getName(),
                entity.getSourceSystem(), entity.getStatus(), entity.getNodeCount(), entity.getStartedAt(),
                entity.getEndedAt(), entity.getCreatedAt());
    }

    private ExternalTraceEntity entity(String traceId) {
        return traceRepository.findById(traceId)
                .orElseThrow(() -> new IllegalArgumentException("外部链路不存在: " + traceId));
    }

    private void validate(ExternalTraceIngestRequest request) {
        if (request.nodes() == null || request.nodes().isEmpty()) throw new IllegalArgumentException("链路至少包含一个节点");
        if (request.startedAt() != null && request.endedAt() != null && request.endedAt().isBefore(request.startedAt())) {
            throw new IllegalArgumentException("链路结束时间不能早于开始时间");
        }
        Map<String, ExternalTraceNodeRequest> nodes = new LinkedHashMap<>();
        for (ExternalTraceNodeRequest node : request.nodes()) {
            if (!node.nodeId().matches("[A-Za-z0-9_.:-]{1,100}")) {
                throw new IllegalArgumentException("节点 ID 格式不合法: " + node.nodeId());
            }
            if (node.durationMs() > 86_400_000L) throw new IllegalArgumentException("节点耗时超过 24 小时: " + node.nodeId());
            if (nodes.put(node.nodeId(), node) != null) throw new IllegalArgumentException("节点 ID 重复: " + node.nodeId());
        }
        for (ExternalTraceNodeRequest node : request.nodes()) {
            if (!nodes.keySet().containsAll(node.normalizedDependencies())) {
                throw new IllegalArgumentException("节点依赖不存在: " + node.nodeId());
            }
        }
        ensureAcyclic(request.nodes());
    }

    private void ensureAcyclic(List<ExternalTraceNodeRequest> nodes) {
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> graph = new HashMap<>();
        nodes.forEach(node -> { indegree.put(node.nodeId(), node.normalizedDependencies().size()); graph.put(node.nodeId(), new ArrayList<>()); });
        nodes.forEach(node -> node.normalizedDependencies().forEach(parent -> graph.get(parent).add(node.nodeId())));
        ArrayDeque<String> queue = new ArrayDeque<>();
        indegree.forEach((id, degree) -> { if (degree == 0) queue.add(id); });
        int visited = 0;
        while (!queue.isEmpty()) {
            String current = queue.remove();
            visited++;
            for (String next : graph.get(current)) if (indegree.merge(next, -1, Integer::sum) == 0) queue.add(next);
        }
        if (visited != nodes.size()) throw new IllegalArgumentException("链路存在循环依赖");
    }

    private ExternalTraceStatus status(List<ExternalTraceNodeRequest> nodes) {
        if (nodes.stream().anyMatch(node -> node.status() == ExternalNodeStatus.FAILED || node.status() == ExternalNodeStatus.TIMEOUT)) return ExternalTraceStatus.FAILED;
        if (nodes.stream().anyMatch(node -> node.status() == ExternalNodeStatus.CANCELLED || node.status() == ExternalNodeStatus.DEGRADED)) return ExternalTraceStatus.PARTIAL;
        return ExternalTraceStatus.COMPLETED;
    }

    private SystemPlan parsePlan(String value) {
        try { return objectMapper.readValue(value, SystemPlan.class); }
        catch (Exception exception) { throw new IllegalStateException("外部链路计划损坏", exception); }
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("链路序列化失败", exception); }
    }

    private String trim(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max) + "...";
    }
}
