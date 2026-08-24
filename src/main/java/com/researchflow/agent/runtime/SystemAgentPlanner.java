package com.researchflow.agent.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchflow.llm.LlmClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SystemAgentPlanner {
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public SystemAgentPlanner(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    public SystemPlan plan(String question) {
        var generated = llmClient.complete(
                "你是 System Agent。请只输出 JSON，不要 Markdown。可用 agent: planner-agent, "
                        + "source-search-agent, evidence-agent, comparison-agent, risk-agent, writer-agent, publisher-agent。"
                        + "输出格式: {\"goal\":\"...\",\"nodes\":[{\"id\":\"...\",\"agent\":\"...\","
                        + "\"dependsOn\":[\"...\"]}]}。基础节点 ID 必须依次使用 plan、sources、evidence、comparison、writer。"
                        + "风险节点 ID 使用 risk，发布节点 ID 使用 publication。",
                question);
        if (generated.isPresent()) {
            try {
                SystemPlan plan = objectMapper.readValue(cleanJson(generated.get()), SystemPlan.class);
                if (isCompatible(plan)) return plan;
            } catch (Exception ignored) {
                // Invalid model plans fall back to the deterministic planner.
            }
        }
        return fallbackPlan(question);
    }

    private SystemPlan fallbackPlan(String question) {
        List<PlannedNode> nodes = new ArrayList<>();
        nodes.add(new PlannedNode("plan", "planner-agent", List.of()));
        nodes.add(new PlannedNode("sources", "source-search-agent", List.of("plan")));
        nodes.add(new PlannedNode("evidence", "evidence-agent", List.of("sources")));
        nodes.add(new PlannedNode("comparison", "comparison-agent", List.of("plan", "evidence")));
        if (containsRiskIntent(question)) {
            nodes.add(new PlannedNode("risk", "risk-agent", List.of("evidence")));
            nodes.add(new PlannedNode("writer", "writer-agent", List.of("comparison", "risk")));
        } else {
            nodes.add(new PlannedNode("writer", "writer-agent", List.of("comparison")));
        }
        if (containsPublishIntent(question)) {
            nodes.add(new PlannedNode("publication", "publisher-agent", List.of("writer")));
        }
        return new SystemPlan(question, nodes);
    }

    private String cleanJson(String text) {
        String value = text.trim();
        if (value.startsWith("```")) {
            int firstLine = value.indexOf('\n');
            int closing = value.lastIndexOf("```");
            if (firstLine >= 0 && closing > firstLine) value = value.substring(firstLine + 1, closing).trim();
        }
        return value;
    }

    private boolean isCompatible(SystemPlan plan) {
        if (plan.nodes() == null || plan.nodes().isEmpty()) return false;
        java.util.Map<String, String> contracts = java.util.Map.of(
                "plan", "planner-agent",
                "sources", "source-search-agent",
                "evidence", "evidence-agent",
                "comparison", "comparison-agent",
                "writer", "writer-agent");
        java.util.Map<String, String> actual = plan.nodes().stream()
                .collect(java.util.stream.Collectors.toMap(PlannedNode::id, PlannedNode::agent, (left, right) -> left));
        return contracts.entrySet().stream().allMatch(entry -> entry.getValue().equals(actual.get(entry.getKey())));
    }

    private boolean containsRiskIntent(String question) {
        return question.contains("风险") || question.toLowerCase().contains("risk")
                || question.contains("挑战") || question.contains("局限");
    }

    private boolean containsPublishIntent(String question) {
        return question.contains("发布") || question.toLowerCase().contains("publish");
    }
}
