package com.researchflow.scenario;

import java.time.Instant;

public record ScenarioView(Long id, String taskId, String title, String nodeCombination, String trigger,
                           String injectedData, String expectation, String risk,
                           ScenarioStatus status, Instant createdAt) {
}
