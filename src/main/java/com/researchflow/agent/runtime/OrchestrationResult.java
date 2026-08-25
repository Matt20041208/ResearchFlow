package com.researchflow.agent.runtime;

import com.researchflow.model.SourceDocument;

import java.util.List;

public record OrchestrationResult(String report, List<SourceDocument> sources) {
    public OrchestrationResult {
        sources = List.copyOf(sources);
    }
}
