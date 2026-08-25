package com.researchflow.knowledge;

import java.time.Instant;

public record KnowledgeDocumentView(String id, String workspaceId, String title,
                                    String content, String sourceUrl, Instant createdAt) {
}
