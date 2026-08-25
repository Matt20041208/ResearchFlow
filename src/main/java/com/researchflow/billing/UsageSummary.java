package com.researchflow.billing;

import java.time.Instant;
import java.util.Map;

public record UsageSummary(String workspaceId, PlanTier planTier, Instant periodStart,
                           Map<UsageType, Long> usage, Map<String, Integer> limits,
                           double estimatedCostUsd) {
}
