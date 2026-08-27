package com.researchflow.externaltrace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record ExternalTraceNodeRequest(
        @NotBlank String nodeId,
        @NotBlank String agent,
        List<@NotBlank String> dependsOn,
        @NotNull ExternalNodeStatus status,
        @Size(max = 100_000) String input,
        @Size(max = 100_000) String output,
        @Size(max = 20_000) String error,
        @PositiveOrZero long durationMs,
        Instant startedAt,
        boolean externalBoundary,
        boolean asyncNode) {
    public List<String> normalizedDependencies() {
        return dependsOn == null ? List.of() : List.copyOf(dependsOn);
    }
}
