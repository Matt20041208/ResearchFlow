package com.researchflow.agent.runtime;

public interface TraceCollector {
    TraceCollector NOOP = new TraceCollector() {
        public void nodeStarted(PlannedNode node, String agentName, String inputSummary) {}
        public void nodeCompleted(PlannedNode node, String agentName, String outputSummary, long durationMs) {}
        public void nodeFailed(PlannedNode node, String agentName, String error, long durationMs) {}
    };

    void nodeStarted(PlannedNode node, String agentName, String inputSummary);

    void nodeCompleted(PlannedNode node, String agentName, String outputSummary, long durationMs);

    void nodeFailed(PlannedNode node, String agentName, String error, long durationMs);

    default void nodeStep(PlannedNode node, String agentName, int iteration, String action,
                          String observation, String decision, String rationale, long durationMs) {
    }
}
