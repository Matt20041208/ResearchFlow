package com.researchflow.model;

import java.time.Instant;

public record ReActStepView(String nodeId, String agent, int iteration, String action,
                            String observation, String decision, String rationale,
                            long durationMs, Instant occurredAt) {
}
