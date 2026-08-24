package com.researchflow.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchflow.llm.LlmClient;
import com.researchflow.model.SourceDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WriterAgentTest {
    @Test
    void fallsBackToDeterministicReportWithoutApiKey() {
        LlmClient client = new LlmClient(new ObjectMapper(), "https://example.org/v1", "", "test", 1);
        WriterAgent agent = new WriterAgent(client);

        String report = agent.execute(new WriterAgent.Input(
                "研究问题", "比较结果", List.of(new SourceDocument("来源", "https://example.org", "摘要"))));

        assertTrue(report.contains("# 研究报告"));
        assertTrue(report.contains("比较结果"));
        assertTrue(report.contains("https://example.org"));
    }
}
