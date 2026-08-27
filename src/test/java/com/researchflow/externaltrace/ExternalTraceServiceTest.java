package com.researchflow.externaltrace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchflow.persistence.ExternalTraceNodeRepository;
import com.researchflow.persistence.ExternalTraceRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ExternalTraceServiceTest {
    private final ExternalTraceService service = new ExternalTraceService(
            mock(ExternalTraceRepository.class), mock(ExternalTraceNodeRepository.class), new ObjectMapper());

    @Test
    void rejectsCyclicExternalTraces() {
        var first = new ExternalTraceNodeRequest("a", "agent-a", List.of("b"),
                ExternalNodeStatus.SUCCESS, "in", "out", null, 10, null, false, false);
        var second = new ExternalTraceNodeRequest("b", "agent-b", List.of("a"),
                ExternalNodeStatus.SUCCESS, "in", "out", null, 10, null, false, false);
        var request = new ExternalTraceIngestRequest("workspace", "cycle", "external",
                null, null, List.of(first, second));

        assertThrows(IllegalArgumentException.class, () -> service.ingest(request, "user"));
    }

    @Test
    void rejectsUnknownDependencies() {
        var node = new ExternalTraceNodeRequest("a", "agent-a", List.of("missing"),
                ExternalNodeStatus.SUCCESS, null, null, null, 10, null, false, false);
        var request = new ExternalTraceIngestRequest("workspace", "invalid", "external",
                null, null, List.of(node));

        assertThrows(IllegalArgumentException.class, () -> service.ingest(request, "user"));
    }
}
