package com.researchflow.agent.runtime;

import com.researchflow.llm.SpringAiClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SystemAgentPlanner {
    private final SpringAiClient aiClient;

    public SystemAgentPlanner(SpringAiClient aiClient) {
        this.aiClient = aiClient;
    }

    public SystemPlan plan(String question) {
        var generated = aiClient.entity(
                "你是 System Agent。请只输出 JSON，不要 Markdown。可用 agent: planner-agent, "
                        + "source-search-agent, private-knowledge-agent, source-merge-agent, evidence-agent, "
                        + "comparison-agent, risk-agent, writer-agent, publisher-agent。"
                        + "输出格式: {\"goal\":\"...\",\"nodes\":[{\"id\":\"...\",\"agent\":\"...\","
                        + "\"dependsOn\":[\"...\"]}]}。基础节点 ID 必须依次使用 plan、sources、evidence、comparison、writer。"
                        + "检索节点必须使用 externalSources、privateSources、sources，风险节点 ID 使用 risk，"
                        + "发布节点 ID 使用 publication。",
                question, SystemPlan.class);
        if (generated.isPresent()) {
            SystemPlan plan = generated.get();
            if (isCompatible(plan, question)) return plan;
        }
        return fallbackPlan(question);
    }

    private SystemPlan fallbackPlan(String question) {
        List<PlannedNode> nodes = new ArrayList<>();
        nodes.add(new PlannedNode("plan", "planner-agent", List.of()));
        nodes.add(new PlannedNode("externalSources", "source-search-agent", List.of("plan")));
        nodes.add(new PlannedNode("privateSources", "private-knowledge-agent", List.of("plan")));
        nodes.add(new PlannedNode("sources", "source-merge-agent", List.of("externalSources", "privateSources")));
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

    private boolean isCompatible(SystemPlan plan, String question) {
        if (plan.nodes() == null || plan.nodes().isEmpty()) return false;
        java.util.Map<String, String> contracts = java.util.Map.of(
                "plan", "planner-agent",
                "externalSources", "source-search-agent",
                "privateSources", "private-knowledge-agent",
                "sources", "source-merge-agent",
                "evidence", "evidence-agent",
                "comparison", "comparison-agent",
                "writer", "writer-agent");
        java.util.Map<String, String> actual = plan.nodes().stream()
                .collect(java.util.stream.Collectors.toMap(PlannedNode::id, PlannedNode::agent, (left, right) -> left));
        boolean validContracts = contracts.entrySet().stream()
                .allMatch(entry -> entry.getValue().equals(actual.get(entry.getKey())));
        boolean hasRisk = "risk-agent".equals(actual.get("risk"));
        boolean hasPublication = "publisher-agent".equals(actual.get("publication"));
        return validContracts
                && hasRisk == containsRiskIntent(question)
                && hasPublication == containsPublishIntent(question);
    }

    private boolean containsRiskIntent(String question) {
        return question.contains("风险") || question.toLowerCase().contains("risk")
                || question.contains("挑战") || question.contains("局限");
    }

    private boolean containsPublishIntent(String question) {
        return question.contains("发布") || question.toLowerCase().contains("publish");
    }
}
