package com.researchflow.knowledge;

import com.researchflow.model.SourceDocument;
import com.researchflow.persistence.KnowledgeDocumentEntity;
import com.researchflow.persistence.KnowledgeDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class KnowledgeService {
    private final KnowledgeDocumentRepository repository;

    public KnowledgeService(KnowledgeDocumentRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public KnowledgeDocumentView create(KnowledgeDocumentRequest request) {
        return toView(repository.save(new KnowledgeDocumentEntity(request.normalizedWorkspaceId(),
                request.title().trim(), request.content().trim(), request.sourceUrl())));
    }

    @Transactional(readOnly = true)
    public List<KnowledgeDocumentView> list(String workspaceId) {
        return repository.findByWorkspaceIdOrderByCreatedAtDesc(normalize(workspaceId)).stream()
                .map(this::toView).toList();
    }

    @Transactional
    public void delete(String id) {
        if (!repository.existsById(id)) throw new IllegalArgumentException("知识文档不存在: " + id);
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public String workspaceId(String id) {
        return repository.findById(id).map(KnowledgeDocumentEntity::getWorkspaceId)
                .orElseThrow(() -> new IllegalArgumentException("知识文档不存在: " + id));
    }

    @Transactional(readOnly = true)
    public List<SourceDocument> search(String workspaceId, String query, int limit) {
        Set<String> terms = terms(query);
        return repository.findByWorkspaceIdOrderByCreatedAtDesc(normalize(workspaceId)).stream()
                .map(document -> new ScoredDocument(document, score(document, query, terms)))
                .filter(result -> result.score > 0)
                .sorted(Comparator.comparingDouble(ScoredDocument::score).reversed())
                .limit(Math.max(1, limit))
                .map(result -> toSource(result.document, result.score))
                .toList();
    }

    private double score(KnowledgeDocumentEntity document, String query, Set<String> terms) {
        String title = document.getTitle().toLowerCase(Locale.ROOT);
        String content = document.getContent().toLowerCase(Locale.ROOT);
        String normalizedQuery = query.toLowerCase(Locale.ROOT).trim();
        double score = title.contains(normalizedQuery) ? 4 : content.contains(normalizedQuery) ? 2 : 0;
        for (String term : terms) {
            if (title.contains(term)) score += 2;
            if (content.contains(term)) score += 1;
        }
        return score;
    }

    private Set<String> terms(String query) {
        Set<String> result = new LinkedHashSet<>();
        Arrays.stream(query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(value -> value.length() > 1).forEach(result::add);
        String compact = query.replaceAll("\\s+", "");
        if (result.size() <= 1 && compact.length() >= 2) {
            for (int index = 0; index < compact.length() - 1; index++) {
                result.add(compact.substring(index, index + 2).toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }

    private SourceDocument toSource(KnowledgeDocumentEntity document, double rawScore) {
        String excerpt = document.getContent().length() > 500
                ? document.getContent().substring(0, 500) + "..." : document.getContent();
        double confidence = Math.min(0.95, 0.5 + rawScore / 20.0);
        return new SourceDocument(document.getId(), "PRIVATE_KNOWLEDGE", document.getTitle(),
                document.getSourceUrl(), excerpt, excerpt, confidence);
    }

    private KnowledgeDocumentView toView(KnowledgeDocumentEntity entity) {
        return new KnowledgeDocumentView(entity.getId(), entity.getWorkspaceId(), entity.getTitle(),
                entity.getContent(), entity.getSourceUrl(), entity.getCreatedAt());
    }

    private String normalize(String workspaceId) {
        return workspaceId == null || workspaceId.isBlank() ? "default" : workspaceId.trim();
    }

    private record ScoredDocument(KnowledgeDocumentEntity document, double score) {}
}
