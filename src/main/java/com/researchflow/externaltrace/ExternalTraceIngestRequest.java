package com.researchflow.externaltrace;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record ExternalTraceIngestRequest(
        @NotBlank String workspaceId,
        @NotBlank String name,
        @NotBlank String sourceSystem,
        Instant startedAt,
        Instant endedAt,
        @Size(min = 1, max = 200) List<@Valid ExternalTraceNodeRequest> nodes) {
}
