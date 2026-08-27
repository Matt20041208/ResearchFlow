package com.researchflow.scenario;

import com.researchflow.injection.InjectionRule;
import java.time.Instant;
import java.util.List;

public record ScenarioView(Long id, String taskId, String title, String nodeCombination, String trigger,
                           String injectedData, String expectation, String risk,
                           ScenarioStatus status, String sourceType,
                           List<InjectionRule> injectionRules, Instant createdAt) {
}
