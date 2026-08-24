package com.researchflow.agent.runtime;

import java.util.Set;

public interface SubAgent {
    String name();
    Set<String> capabilities();
    Set<String> requiredTools();
    Object execute(AgentContext context);
}
