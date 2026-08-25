package com.researchflow.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import com.researchflow.validation.ValidationRunStatus;

public interface ScenarioValidationRepository extends JpaRepository<ScenarioValidationEntity, String> {
    List<ScenarioValidationEntity> findByScenarioIdOrderByCreatedAtDesc(Long scenarioId);
    List<ScenarioValidationEntity> findByStatusIn(List<ValidationRunStatus> statuses);
}
