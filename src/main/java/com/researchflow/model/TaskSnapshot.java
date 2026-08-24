package com.researchflow.model;

import java.time.Instant;
import java.util.List;

public record TaskSnapshot(String taskId, String question, TaskStatus status, Instant createdAt,
                           Instant updatedAt, String report, String error, int attempts,
                           List<AgentEvent> events) {
}
