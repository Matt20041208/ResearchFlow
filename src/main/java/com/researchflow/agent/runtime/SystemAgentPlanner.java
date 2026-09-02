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
                        + "comparison-agent, risk-agent, critic-agent, fact-checker-agent, moderator-agent, "
                        + "writer-agent, publisher-agent。"
                        + "输出格式: {\"goal\":\"...\",\"nodes\":[{\"id\":\"...\",\"agent\":\"...\","
                        + "\"dependsOn\":[\"...\"]}]}。基础节点 ID 必须使用 plan、externalSources、privateSources、"
                        + "sources、evidence、comparison、critic、factCheck、moderation、writer。"
                        + "检索节点必须使用 externalSources、privateSources、sources，风险节点 ID 使用 risk，"
                        + "发布节点 ID 使用 publication。critic 与 factCheck 应并行，moderation 汇总二者，"
                        + "writer 必须在 moderation 后执行。",
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
        List<String> moderationDependencies = new ArrayList<>(List.of("comparison", "critic", "factCheck"));
        if (containsRiskIntent(question)) {
            nodes.add(new PlannedNode("risk", "risk-agent", List.of("evidence")));
            moderationDependencies.add("risk");
        }
        nodes.add(new PlannedNode("critic", "critic-agent", List.of("comparison", "evidence")));
        nodes.add(new PlannedNode("factCheck", "fact-checker-agent", List.of("evidence", "sources")));
        nodes.add(new PlannedNode("moderation", "moderator-agent", moderationDependencies));
        nodes.add(new PlannedNode("writer", "writer-agent", List.of("moderation", "sources")));
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
                "critic", "critic-agent",
                "factCheck", "fact-checker-agent",
                "moderation", "moderator-agent",
                "writer", "writer-agent");
        java.util.Map<String, PlannedNode> actual = new java.util.HashMap<>();
        for (PlannedNode node : plan.nodes()) {
            if (node.id() == null || node.agent() == null || node.dependsOn() == null) return false;
            if (actual.put(node.id(), node) != null) return false;
        }
        java.util.Set<String> allowed = new java.util.HashSet<>(contracts.keySet());
        allowed.add("risk");
        allowed.add("publication");
        if (!allowed.containsAll(actual.keySet())) return false;
        boolean validContracts = contracts.entrySet().stream()
                .allMatch(entry -> actual.containsKey(entry.getKey())
                        && entry.getValue().equals(actual.get(entry.getKey()).agent()));
        boolean hasRisk = actual.containsKey("risk") && "risk-agent".equals(actual.get("risk").agent());
        boolean hasPublication = actual.containsKey("publication")
                && "publisher-agent".equals(actual.get("publication").agent());
        return validContracts
                && hasRisk == containsRiskIntent(question)
                && hasPublication == containsPublishIntent(question)
                && dependenciesMatch(actual, hasRisk, hasPublication);
    }

    private boolean dependenciesMatch(java.util.Map<String, PlannedNode> nodes,
                                      boolean hasRisk, boolean hasPublication) {
        if (!dependsOn(nodes, "plan")) return false;
        if (!dependsOn(nodes, "externalSources", "plan")) return false;
        if (!dependsOn(nodes, "privateSources", "plan")) return false;
        if (!dependsOn(nodes, "sources", "externalSources", "privateSources")) return false;
        if (!dependsOn(nodes, "evidence", "sources")) return false;
        if (!dependsOn(nodes, "comparison", "plan", "evidence")) return false;
        if (!dependsOn(nodes, "critic", "comparison", "evidence")) return false;
        if (!dependsOn(nodes, "factCheck", "evidence", "sources")) return false;
        if (hasRisk && !dependsOn(nodes, "risk", "evidence")) return false;
        String[] moderation = hasRisk
                ? new String[]{"comparison", "critic", "factCheck", "risk"}
                : new String[]{"comparison", "critic", "factCheck"};
        if (!dependsOn(nodes, "moderation", moderation)) return false;
        if (!dependsOn(nodes, "writer", "moderation", "sources")) return false;
        return !hasPublication || dependsOn(nodes, "publication", "writer");
    }

    private boolean dependsOn(java.util.Map<String, PlannedNode> nodes, String nodeId,
                              String... expectedDependencies) {
        PlannedNode node = nodes.get(nodeId);
        return node != null && node.dependsOn().size() == expectedDependencies.length
                && java.util.Set.copyOf(node.dependsOn())
                .equals(java.util.Set.of(expectedDependencies));
    }

    private boolean containsRiskIntent(String question) {
        return question.contains("风险") || question.toLowerCase().contains("risk")
                || question.contains("挑战") || question.contains("局限");
    }

    private boolean containsPublishIntent(String question) {
        return question.contains("发布") || question.toLowerCase().contains("publish");
    }
}
