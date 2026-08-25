package com.researchflow.knowledge;

import jakarta.validation.constraints.NotBlank;

public record KnowledgeDocumentRequest(String workspaceId, @NotBlank String title,
                                       @NotBlank String content, String sourceUrl) {
    public String normalizedWorkspaceId() {
        return workspaceId == null || workspaceId.isBlank() ? "default" : workspaceId.trim();
    }
}
