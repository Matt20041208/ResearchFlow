package com.researchflow.knowledge;

import com.researchflow.persistence.KnowledgeDocumentEntity;
import com.researchflow.persistence.KnowledgeDocumentRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeServiceTest {
    @Test
    void searchesOnlyInsideTheRequestedWorkspace() {
        KnowledgeDocumentRepository repository = mock(KnowledgeDocumentRepository.class);
        when(repository.findByWorkspaceIdOrderByCreatedAtDesc("team-a")).thenReturn(List.of(
                new KnowledgeDocumentEntity("team-a", "医疗 AI 风险", "医疗大模型存在隐私和偏差风险", null)));
        KnowledgeService service = new KnowledgeService(repository);

        var sources = service.search("team-a", "医疗大模型风险", 5);

        assertEquals(1, sources.size());
        assertEquals("PRIVATE_KNOWLEDGE", sources.get(0).sourceType());
        assertTrue(sources.get(0).excerpt().contains("隐私"));
    }
}
