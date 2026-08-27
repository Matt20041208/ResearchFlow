package com.researchflow.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExternalTraceRepository extends JpaRepository<ExternalTraceEntity, String> {
    List<ExternalTraceEntity> findByWorkspaceIdOrderByCreatedAtDesc(String workspaceId);
}
