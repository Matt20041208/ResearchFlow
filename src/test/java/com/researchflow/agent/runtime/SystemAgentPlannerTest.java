package com.researchflow.agent.runtime;

import com.researchflow.llm.SpringAiClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemAgentPlannerTest {
    private final SpringAiClient aiClient = mock(SpringAiClient.class);
    private final SystemAgentPlanner planner = new SystemAgentPlanner(aiClient);

    SystemAgentPlannerTest() {
        when(aiClient.entity(anyString(), anyString(), eq(SystemPlan.class)))
                .thenReturn(java.util.Optional.empty());
    }

    @Test
    void createsTheStandardResearchPlan() {
        SystemPlan plan = planner.plan("研究多 Agent 系统");

        assertEquals(10, plan.nodes().size());
        assertTrue(plan.nodes().stream().anyMatch(node -> node.agent().equals("private-knowledge-agent")));
        assertTrue(plan.nodes().stream().anyMatch(node -> node.agent().equals("critic-agent")));
        assertTrue(plan.nodes().stream().anyMatch(node -> node.agent().equals("fact-checker-agent")));
        assertEquals("writer-agent", plan.nodes().get(9).agent());
    }

    @Test
    void addsRiskAgentWhenTheGoalRequiresRiskAnalysis() {
        SystemPlan plan = planner.plan("分析大模型医疗应用的风险和挑战");

        assertTrue(plan.nodes().stream().anyMatch(node -> node.agent().equals("risk-agent")));
        assertTrue(plan.nodes().stream().filter(node -> node.id().equals("moderation"))
                .findFirst().orElseThrow().dependsOn().contains("risk"));
    }

    @Test
    void addsControlledPublisherWhenTheGoalRequestsPublication() {
        SystemPlan plan = planner.plan("研究多 Agent 系统并发布报告");

        assertTrue(plan.nodes().stream().anyMatch(node -> node.agent().equals("publisher-agent")));
    }

    @Test
    void acceptsACompatibleStructuredPlanFromSpringAi() {
        SystemPlan generated = new SystemPlan("goal", java.util.List.of(
                new PlannedNode("plan", "planner-agent", java.util.List.of()),
                new PlannedNode("externalSources", "source-search-agent", java.util.List.of("plan")),
                new PlannedNode("privateSources", "private-knowledge-agent", java.util.List.of("plan")),
                new PlannedNode("sources", "source-merge-agent", java.util.List.of("externalSources", "privateSources")),
                new PlannedNode("evidence", "evidence-agent", java.util.List.of("sources")),
                new PlannedNode("comparison", "comparison-agent", java.util.List.of("plan", "evidence")),
                new PlannedNode("critic", "critic-agent", java.util.List.of("comparison", "evidence")),
                new PlannedNode("factCheck", "fact-checker-agent", java.util.List.of("evidence", "sources")),
                new PlannedNode("moderation", "moderator-agent", java.util.List.of("comparison", "critic", "factCheck")),
                new PlannedNode("writer", "writer-agent", java.util.List.of("moderation", "sources"))));
        when(aiClient.entity(anyString(), anyString(), eq(SystemPlan.class)))
                .thenReturn(java.util.Optional.of(generated));

        assertEquals(generated, planner.plan("任意研究目标"));
    }

    @Test
    void rejectsAPlanWhoseDependenciesViolateAgentContracts() {
        SystemPlan generated = new SystemPlan("goal", java.util.List.of(
                new PlannedNode("plan", "planner-agent", java.util.List.of()),
                new PlannedNode("externalSources", "source-search-agent", java.util.List.of()),
                new PlannedNode("privateSources", "private-knowledge-agent", java.util.List.of("plan")),
                new PlannedNode("sources", "source-merge-agent", java.util.List.of("externalSources", "privateSources")),
                new PlannedNode("evidence", "evidence-agent", java.util.List.of("sources")),
                new PlannedNode("comparison", "comparison-agent", java.util.List.of("plan", "evidence")),
                new PlannedNode("critic", "critic-agent", java.util.List.of("comparison", "evidence")),
                new PlannedNode("factCheck", "fact-checker-agent", java.util.List.of("evidence", "sources")),
                new PlannedNode("moderation", "moderator-agent", java.util.List.of("comparison", "critic", "factCheck")),
                new PlannedNode("writer", "writer-agent", java.util.List.of("moderation", "sources"))));
        when(aiClient.entity(anyString(), anyString(), eq(SystemPlan.class)))
                .thenReturn(java.util.Optional.of(generated));

        SystemPlan result = planner.plan("任意研究目标");

        assertEquals(java.util.List.of("plan"), result.nodes().stream()
                .filter(node -> node.id().equals("externalSources")).findFirst().orElseThrow().dependsOn());
    }
}
