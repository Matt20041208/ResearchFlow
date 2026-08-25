package com.researchflow.scenario;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchflow.agent.runtime.SystemPlan;
import com.researchflow.billing.UsageService;
import com.researchflow.llm.SpringAiClient;
import com.researchflow.model.TraceNodeView;
import com.researchflow.model.TraceView;
import com.researchflow.persistence.ScenarioEntity;
import com.researchflow.persistence.ScenarioRepository;
import com.researchflow.service.ResearchTaskService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScenarioServiceTest {
    @Test
    void generatesDeterministicFallbackWhenModelIsUnavailable() throws Exception {
        ScenarioRepository repository = mock(ScenarioRepository.class);
        ResearchTaskService tasks = mock(ResearchTaskService.class);
        SpringAiClient ai = mock(SpringAiClient.class);
        UsageService usage = mock(UsageService.class);
        ScenarioService service = new ScenarioService(repository, tasks, ai, usage, new ObjectMapper());

        when(tasks.workspaceId("task-1")).thenReturn("workspace-1");
        when(tasks.trace("task-1")).thenReturn(new TraceView("task-1", new SystemPlan("goal", List.of()),
                List.of(
                        new TraceNodeView("externalSources", "source-search-agent", "COMPLETED",
                                "plan=...", "List(size=3)", null, 1200, Instant.now()),
                        new TraceNodeView("evidence", "evidence-agent", "COMPLETED",
                                "sources=...", "evidence", null, 340, Instant.now()),
                        new TraceNodeView("writer", "writer-agent", "COMPLETED",
                                "comparison=...", "report", null, 5600, Instant.now()))));
        when(ai.entity(anyString(), anyString(), eq(ScenarioBatch.class))).thenReturn(Optional.empty());
        when(repository.save(any(ScenarioEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<ScenarioView> result = service.generate("task-1", "user-1");

        assertFalse(result.isEmpty());
        assertTrue(result.stream().anyMatch(scenario -> scenario.risk().equals("HIGH")));
        assertEquals(ScenarioStatus.SUGGESTED, result.get(0).status());
        ArgumentCaptor<ScenarioEntity> captor = ArgumentCaptor.forClass(ScenarioEntity.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertTrue(captor.getAllValues().stream().allMatch(entity -> entity.getTaskId().equals("task-1")));
    }
}
