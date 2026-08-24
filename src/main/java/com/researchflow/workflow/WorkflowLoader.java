package com.researchflow.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class WorkflowLoader {
    private final List<String> executionOrder;

    public WorkflowLoader(ObjectMapper objectMapper) {
        try {
            WorkflowDefinition definition = objectMapper.readValue(
                    new ClassPathResource("workflow.json").getInputStream(), WorkflowDefinition.class);
            this.executionOrder = topologicalSort(definition);
        } catch (IOException exception) {
            throw new IllegalStateException("无法加载 workflow.json", exception);
        }
    }

    public List<String> executionOrder() {
        return executionOrder;
    }

    private List<String> topologicalSort(WorkflowDefinition definition) {
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> graph = new HashMap<>();
        for (WorkflowDefinition.Node node : definition.nodes()) {
            indegree.put(node.id(), 0);
            graph.put(node.id(), new ArrayList<>());
        }
        for (WorkflowDefinition.Edge edge : definition.edges()) {
            if (!graph.containsKey(edge.from()) || !graph.containsKey(edge.to())) {
                throw new IllegalStateException("工作流引用了不存在的节点");
            }
            graph.get(edge.from()).add(edge.to());
            indegree.compute(edge.to(), (ignored, value) -> value + 1);
        }
        ArrayDeque<String> queue = new ArrayDeque<>();
        indegree.forEach((node, degree) -> { if (degree == 0) queue.add(node); });
        List<String> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.remove();
            result.add(current);
            for (String next : graph.get(current)) {
                int degree = indegree.merge(next, -1, Integer::sum);
                if (degree == 0) queue.add(next);
            }
        }
        if (result.size() != definition.nodes().size() || new HashSet<>(result).size() != result.size()) {
            throw new IllegalStateException("工作流存在循环或重复节点");
        }
        return List.copyOf(result);
    }
}
