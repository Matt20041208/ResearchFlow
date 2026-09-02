package com.researchflow.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskNodeStepRepository extends JpaRepository<TaskNodeStepEntity, Long> {
    List<TaskNodeStepEntity> findByTaskIdOrderByOccurredAtAsc(String taskId);
}
