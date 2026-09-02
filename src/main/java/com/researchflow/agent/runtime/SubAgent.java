package com.researchflow.agent.runtime;

import java.util.Set;

public interface SubAgent {
    String name();
    Set<String> capabilities();
    Set<String> requiredTools();
    Object execute(AgentContext context);

    default Object execute(AgentContext context, String instruction) {
        return execute(context);
    }

    default boolean supportsRetry() {
        return false;
    }
}
