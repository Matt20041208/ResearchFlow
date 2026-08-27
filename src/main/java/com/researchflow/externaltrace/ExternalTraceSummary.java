package com.researchflow.externaltrace;

import java.time.Instant;

public record ExternalTraceSummary(String id, String workspaceId, String name, String sourceSystem,
                                   ExternalTraceStatus status, int nodeCount,
                                   Instant startedAt, Instant endedAt, Instant createdAt) {
}
