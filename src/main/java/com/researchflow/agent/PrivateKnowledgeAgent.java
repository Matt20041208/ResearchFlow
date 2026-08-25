package com.researchflow.agent;

import com.researchflow.knowledge.KnowledgeService;
import com.researchflow.model.SourceDocument;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PrivateKnowledgeAgent {
    private final KnowledgeService knowledgeService;

    public PrivateKnowledgeAgent(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    public String name() { return "private-knowledge-agent"; }

    public List<SourceDocument> execute(String workspaceId, String question) {
        return knowledgeService.search(workspaceId, question, 5);
    }
}
