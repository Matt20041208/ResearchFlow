package com.researchflow.validation;

import com.researchflow.injection.InjectionRule;

import java.time.Instant;
import java.util.List;

public record ValidationRunView(String id, Long scenarioId, String taskId,
                                ValidationRunStatus status, ValidationVerdict verdict,
                                List<InjectionRule> rules, String expectation, String actualTraceJson,
                                String outputSummary, String error, long durationMs,
                                Instant createdAt, Instant startedAt, Instant completedAt) {
}
