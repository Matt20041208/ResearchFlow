package com.researchflow.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskCitationRepository extends JpaRepository<TaskCitationEntity, Long> {
    List<TaskCitationEntity> findByTaskIdOrderByCitationNumberAsc(String taskId);
    void deleteByTaskId(String taskId);
}
