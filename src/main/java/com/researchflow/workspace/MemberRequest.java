package com.researchflow.workspace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MemberRequest(@NotBlank String userId, @NotNull WorkspaceRole role) {
}
