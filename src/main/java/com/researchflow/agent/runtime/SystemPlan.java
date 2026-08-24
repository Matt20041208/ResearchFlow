package com.researchflow.agent.runtime;

import java.util.List;

public record SystemPlan(String goal, List<PlannedNode> nodes) {
    public SystemPlan {
        nodes = List.copyOf(nodes);
    }
}
