package com.researchflow.workspace;

import jakarta.validation.constraints.NotBlank;

public record WorkspaceCreateRequest(@NotBlank String name) {
}
