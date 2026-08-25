package com.researchflow.agent;

import com.researchflow.model.SourceDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceAgentTest {
    @Test
    void numbersEvidenceForTraceableCitations() {
        String evidence = new EvidenceAgent().execute(List.of(
                new SourceDocument("Source A", "https://a", "Evidence A"),
                new SourceDocument("Source B", "https://b", "Evidence B")));

        assertTrue(evidence.contains("[1]"));
        assertTrue(evidence.contains("[2]"));
    }
}
