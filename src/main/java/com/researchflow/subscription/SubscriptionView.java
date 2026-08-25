package com.researchflow.subscription;

import java.time.Instant;

public record SubscriptionView(Long id, String workspaceId, String name, String question,
                               int intervalMinutes, boolean enabled, Instant nextRunAt,
                               Instant lastRunAt, String lastTaskId) {
}
