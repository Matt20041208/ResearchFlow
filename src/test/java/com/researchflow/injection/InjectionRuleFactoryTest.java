package com.researchflow.injection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchflow.agent.runtime.PlannedNode;
import com.researchflow.agent.runtime.SystemPlan;
import com.researchflow.persistence.ScenarioEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InjectionRuleFactoryTest {
    private final InjectionRuleFactory factory = new InjectionRuleFactory(new ObjectMapper());

    @Test
    void translatesDelayAndEmptyDataIntoControlledRules() {
        ScenarioEntity scenario = new ScenarioEntity("task", "workspace", "组合场景",
                "externalSources+sources", "慢响应与空数据叠加",
                "{\"nodes\":[\"externalSources\",\"sources\"],\"delayMs\":3000,\"data\":[]}",
                "验证降级链路", "HIGH");
        SystemPlan plan = new SystemPlan("goal", List.of(
                new PlannedNode("externalSources", "source-search-agent", List.of()),
                new PlannedNode("sources", "source-merge-agent", List.of("externalSources"))));

        List<InjectionRule> rules = factory.create(scenario, plan);

        assertEquals(2, rules.size());
        assertTrue(rules.stream().anyMatch(rule -> rule.nodeId().equals("externalSources")
                && rule.type() == InjectionType.DELAY && rule.delayMs() == 3000));
        assertTrue(rules.stream().anyMatch(rule -> rule.nodeId().equals("sources")
                && rule.type() == InjectionType.EMPTY_RESULT));
    }

    @Test
    void prefersPersistedStructuredRules() throws Exception {
        String rulesJson = new ObjectMapper().writeValueAsString(List.of(
                new InjectionRule("sources", InjectionType.ERROR, 0, "structured")));
        ScenarioEntity scenario = new ScenarioEntity("task", "workspace", "EXTERNAL_TRACE", "场景",
                "sources", "延迟", "{\"delayMs\":3000}", "expectation", "HIGH", rulesJson);
        SystemPlan plan = new SystemPlan("goal", List.of(
                new PlannedNode("sources", "source-merge-agent", List.of())));

        List<InjectionRule> rules = factory.create(scenario, plan);

        assertEquals(1, rules.size());
        assertEquals(InjectionType.ERROR, rules.get(0).type());
    }
}
