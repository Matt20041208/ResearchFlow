package com.researchflow.model;

import java.time.Instant;

public record AgentEvent(String taskId, String agent, String status, String message, Instant occurredAt) {
}
