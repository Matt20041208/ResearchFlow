package com.researchflow.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkflowLoaderTest {
    @Test
    void loadsTheConfiguredAgentOrder() {
        WorkflowLoader loader = new WorkflowLoader(new ObjectMapper());

        assertEquals(java.util.List.of("planner", "search", "evidence", "comparison", "writer"),
                loader.executionOrder());
    }
}
