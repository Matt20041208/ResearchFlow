package com.researchflow.model;

import java.time.Instant;

public record TaskSummary(String taskId, String question, TaskStatus status, Instant createdAt,
                          Instant updatedAt, int attempts) {
}
