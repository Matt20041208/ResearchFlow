package com.researchflow.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExternalTraceNodeRepository extends JpaRepository<ExternalTraceNodeEntity, Long> {
    List<ExternalTraceNodeEntity> findByTraceIdOrderByStartedAtAsc(String traceId);
    void deleteByTraceId(String traceId);
}
