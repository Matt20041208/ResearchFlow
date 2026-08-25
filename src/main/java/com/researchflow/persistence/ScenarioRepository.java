package com.researchflow.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScenarioRepository extends JpaRepository<ScenarioEntity, Long> {
    List<ScenarioEntity> findByTaskIdOrderByCreatedAtDesc(String taskId);
}
