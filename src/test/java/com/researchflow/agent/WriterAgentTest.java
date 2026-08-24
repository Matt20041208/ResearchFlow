package com.researchflow.agent;

import com.researchflow.llm.SpringAiClient;
import com.researchflow.model.SourceDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WriterAgentTest {
    @Test
    void fallsBackToDeterministicReportWithoutApiKey() {
        SpringAiClient client = mock(SpringAiClient.class);
        when(client.complete(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(java.util.Optional.empty());
        WriterAgent agent = new WriterAgent(client);

        String report = agent.execute(new WriterAgent.Input(
                "研究问题", "比较结果", List.of(new SourceDocument("来源", "https://example.org", "摘要"))));

        assertTrue(report.contains("# 研究报告"));
        assertTrue(report.contains("比较结果"));
        assertTrue(report.contains("https://example.org"));
    }
}
