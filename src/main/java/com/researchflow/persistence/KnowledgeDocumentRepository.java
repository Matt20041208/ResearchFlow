package com.researchflow.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocumentEntity, String> {
    List<KnowledgeDocumentEntity> findByWorkspaceIdOrderByCreatedAtDesc(String workspaceId);
}
