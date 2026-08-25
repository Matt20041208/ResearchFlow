package com.researchflow.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportCommentRepository extends JpaRepository<ReportCommentEntity, Long> {
    List<ReportCommentEntity> findByTaskIdOrderByCreatedAtAsc(String taskId);
}
