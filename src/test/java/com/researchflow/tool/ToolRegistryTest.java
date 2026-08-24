package com.researchflow.tool;

import com.researchflow.agent.runtime.AgentContext;
import com.researchflow.agent.runtime.SubAgent;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToolRegistryTest {
    private final ToolRegistry registry = new ToolRegistry();
    private final SubAgent publisher = new SubAgent() {
        public String name() { return "publisher-agent"; }
        public Set<String> capabilities() { return Set.of("publication"); }
        public Set<String> requiredTools() { return Set.of("report-publish"); }
        public Object execute(AgentContext context) { return "published"; }
    };

    @Test
    void blocksHighRiskToolsWithoutApproval() {
        assertThrows(ToolRegistry.ApprovalRequiredException.class,
                () -> registry.authorize(publisher, Set.of()));
    }

    @Test
    void permitsAnExplicitlyApprovedHighRiskTool() {
        assertDoesNotThrow(() -> registry.authorize(publisher, Set.of("report-publish")));
    }
}
