package com.researchflow.agent.runtime;

import java.util.List;

public record PlannedNode(String id, String agent, List<String> dependsOn) {
    public PlannedNode {
        dependsOn = List.copyOf(dependsOn);
    }
}
