package com.researchflow.model;

import jakarta.validation.constraints.NotBlank;

public record ResearchRequest(@NotBlank String question, String workspaceId) {
    public ResearchRequest(String question) {
        this(question, "default");
    }

    public String normalizedWorkspaceId() {
        return workspaceId == null || workspaceId.isBlank() ? "default" : workspaceId.trim();
    }
}
