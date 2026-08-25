package com.researchflow.model;

import java.time.Instant;

public record TraceNodeView(String nodeId, String agent, String status, String inputSummary,
                            String outputSummary, String errorSummary, long durationMs, Instant startedAt) {
}
