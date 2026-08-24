package com.researchflow.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceSearchAgentTest {
    @Test
    void fallsBackWhenExternalSearchIsUnavailable() {
        SourceSearchAgent agent = new SourceSearchAgent(new ObjectMapper(), "http://[invalid]", 2);

        var sources = agent.execute(new ResearchPlan("问题", "问题", "重点"));

        assertFalse(sources.isEmpty());
        assertTrue(sources.get(0).summary().contains("离线"));
    }
}
