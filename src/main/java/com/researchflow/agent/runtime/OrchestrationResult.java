package com.researchflow.agent.runtime;

import com.researchflow.model.SourceDocument;

import java.util.List;

public record OrchestrationResult(String report, List<SourceDocument> sources, SystemPlan plan) {
    public OrchestrationResult {
        sources = List.copyOf(sources);
    }
}
