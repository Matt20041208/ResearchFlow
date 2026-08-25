package com.researchflow.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportVersionRepository extends JpaRepository<ReportVersionEntity, Long> {
    List<ReportVersionEntity> findByTaskIdOrderByVersionNumberDesc(String taskId);
    long countByTaskId(String taskId);
}
