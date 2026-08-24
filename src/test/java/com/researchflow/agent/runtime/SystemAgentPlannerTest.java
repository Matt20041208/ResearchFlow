package com.researchflow.agent.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchflow.llm.LlmClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemAgentPlannerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SystemAgentPlanner planner = new SystemAgentPlanner(
            new LlmClient(objectMapper, "https://example.org/v1", "", "test", 1), objectMapper);

    @Test
    void createsTheStandardResearchPlan() {
        SystemPlan plan = planner.plan("研究多 Agent 系统");

        assertEquals(5, plan.nodes().size());
        assertEquals("writer-agent", plan.nodes().get(4).agent());
    }

    @Test
    void addsRiskAgentWhenTheGoalRequiresRiskAnalysis() {
        SystemPlan plan = planner.plan("分析大模型医疗应用的风险和挑战");

        assertTrue(plan.nodes().stream().anyMatch(node -> node.agent().equals("risk-agent")));
        assertTrue(plan.nodes().stream().filter(node -> node.id().equals("writer"))
                .findFirst().orElseThrow().dependsOn().contains("risk"));
    }

    @Test
    void addsControlledPublisherWhenTheGoalRequestsPublication() {
        SystemPlan plan = planner.plan("研究多 Agent 系统并发布报告");

        assertTrue(plan.nodes().stream().anyMatch(node -> node.agent().equals("publisher-agent")));
    }
}
