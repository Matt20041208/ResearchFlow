package com.researchflow.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "knowledge_document")
public class KnowledgeDocumentEntity {
    @Id
    private String id;
    private String workspaceId;
    private String title;
    @Lob
    private String content;
    private String sourceUrl;
    private Instant createdAt;

    protected KnowledgeDocumentEntity() {}

    public KnowledgeDocumentEntity(String workspaceId, String title, String content, String sourceUrl) {
        this.id = UUID.randomUUID().toString();
        this.workspaceId = workspaceId;
        this.title = title;
        this.content = content;
        this.sourceUrl = sourceUrl;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getWorkspaceId() { return workspaceId; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getSourceUrl() { return sourceUrl; }
    public Instant getCreatedAt() { return createdAt; }
}
