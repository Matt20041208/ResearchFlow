package com.researchflow.agent.runtime;

import com.researchflow.llm.SpringAiClient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReActNodeExecutorTest {
    @Test
    void completesInOneDeterministicIterationWhenModelIsUnavailable() {
        SpringAiClient ai = mock(SpringAiClient.class);
        when(ai.entity(anyString(), anyString(), eq(ReActDecision.class))).thenReturn(Optional.empty());
        ReActNodeExecutor executor = new ReActNodeExecutor(ai, 3, 60_000);
        AtomicInteger actions = new AtomicInteger();
        List<AgentExecutionEvent> events = new ArrayList<>();

        Object result = executor.execute(new PlannedNode("node", "test-agent", List.of()),
                agent(actions, "observation"), new AgentContext("goal", "workspace"),
                events::add, () -> false, TraceCollector.NOOP);

        assertEquals("observation", result);
        assertEquals(1, actions.get());
        assertTrue(events.stream().anyMatch(event -> event.status().equals("CONVERGED")));
    }

    @Test
    void retriesWithTheInstructionReturnedBySpringAi() {
        SpringAiClient ai = mock(SpringAiClient.class);
        when(ai.entity(anyString(), anyString(), eq(ReActDecision.class)))
                .thenReturn(Optional.of(new ReActDecision("RETRY", "证据不足", "补充证据后重试")))
                .thenReturn(Optional.of(new ReActDecision("COMPLETE", "证据充分", "")));
        ReActNodeExecutor executor = new ReActNodeExecutor(ai, 3, 60_000);
        AtomicInteger actions = new AtomicInteger();

        Object result = executor.execute(new PlannedNode("node", "test-agent", List.of()),
                agent(actions, "observation"), new AgentContext("goal", "workspace"),
                event -> {}, () -> false, TraceCollector.NOOP);

        assertEquals("observation", result);
        assertEquals(2, actions.get());
    }

    @Test
    void neverStartsAnActionAfterCancellation() {
        SpringAiClient ai = mock(SpringAiClient.class);
        ReActNodeExecutor executor = new ReActNodeExecutor(ai, 3, 60_000);
        AtomicInteger actions = new AtomicInteger();

        assertThrows(MultiAgentOrchestrator.TaskCancelledException.class, () -> executor.execute(
                new PlannedNode("node", "test-agent", List.of()), agent(actions, "observation"),
                new AgentContext("goal", "workspace"), event -> {}, () -> true, TraceCollector.NOOP));
        assertEquals(0, actions.get());
    }

    private SubAgent agent(AtomicInteger actions, Object result) {
        return new SubAgent() {
            public String name() { return "test-agent"; }
            public Set<String> capabilities() { return Set.of("test"); }
            public Set<String> requiredTools() { return Set.of("llm-completion"); }
            public Object execute(AgentContext context) {
                actions.incrementAndGet();
                return result;
            }
            public boolean supportsRetry() { return true; }
        };
    }
}
