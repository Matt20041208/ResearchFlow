package com.researchflow.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskEventRepository extends JpaRepository<TaskEventEntity, Long> {
    List<TaskEventEntity> findByTaskIdOrderByOccurredAtAsc(String taskId);
}
