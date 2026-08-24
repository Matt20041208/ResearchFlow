package com.researchflow.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlannerAgentTest {
    @Test
    void createsSearchableResearchPlan() {
        ResearchPlan plan = new PlannerAgent().execute("大模型 在 医疗 中的应用");

        assertEquals("大模型+在+医疗+中的应用", plan.searchQuery());
        assertEquals("方法、证据与实际应用", plan.focus());
    }
}
