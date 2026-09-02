package com.researchflow.agent;

import com.researchflow.llm.SpringAiClient;
import com.researchflow.model.SourceDocument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoundtableAgentsTest {
    private final SpringAiClient ai = unavailableModel();

    @Test
    void criticKeepsAUsableDeterministicFallback() {
        String result = new CriticAgent(ai).execute(new CriticAgent.Input("问题", "比较", "证据"));
        assertTrue(result.contains("审查"));
    }

    @Test
    void factCheckerSummarizesSourceQualityWithoutAModel() {
        String result = new FactCheckerAgent(ai).execute(new FactCheckerAgent.Input("问题", "证据",
                List.of(new SourceDocument("id", "TEST", "来源", "url", "摘要", "摘录", 0.5))));
        assertTrue(result.contains("1 个来源"));
        assertTrue(result.contains("低于 0.60"));
    }

    @Test
    void moderatorPreservesAllReviewPerspectivesInFallback() {
        String result = new ModeratorAgent(ai).execute(
                new ModeratorAgent.Input("问题", "比较", "批判", "核验", "风险"));
        assertTrue(result.contains("比较"));
        assertTrue(result.contains("批判"));
        assertTrue(result.contains("核验"));
        assertTrue(result.contains("风险"));
    }

    private SpringAiClient unavailableModel() {
        SpringAiClient client = mock(SpringAiClient.class);
        when(client.complete(anyString(), anyString())).thenReturn(Optional.empty());
        return client;
    }
}
