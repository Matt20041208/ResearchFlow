package com.researchflow.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskNodeExecutionRepository extends JpaRepository<TaskNodeExecutionEntity, Long> {
    List<TaskNodeExecutionEntity> findByTaskIdOrderByStartedAtAsc(String taskId);
}
