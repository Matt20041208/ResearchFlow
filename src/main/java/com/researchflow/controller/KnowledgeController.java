package com.researchflow.controller;

import com.researchflow.knowledge.KnowledgeDocumentRequest;
import com.researchflow.knowledge.KnowledgeDocumentView;
import com.researchflow.knowledge.KnowledgeService;
import com.researchflow.knowledge.KnowledgeDocumentParser;
import com.researchflow.model.SourceDocument;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/knowledge/documents")
public class KnowledgeController {
    private final KnowledgeService service;
    private final KnowledgeDocumentParser parser;

    public KnowledgeController(KnowledgeService service, KnowledgeDocumentParser parser) {
        this.service = service;
        this.parser = parser;
    }

    @PostMapping
    public ResponseEntity<KnowledgeDocumentView> create(@Valid @RequestBody KnowledgeDocumentRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @PostMapping(value = "/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<KnowledgeDocumentView> upload(
            @RequestPart MultipartFile file,
            @RequestParam(defaultValue = "default") String workspaceId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String sourceUrl) {
        String resolvedTitle = title == null || title.isBlank() ? file.getOriginalFilename() : title;
        if (resolvedTitle == null || resolvedTitle.isBlank()) resolvedTitle = "Untitled document";
        return ResponseEntity.ok(service.create(new KnowledgeDocumentRequest(workspaceId, resolvedTitle,
                parser.parse(file), sourceUrl)));
    }

    @GetMapping
    public List<KnowledgeDocumentView> list(@RequestParam(defaultValue = "default") String workspaceId) {
        return service.list(workspaceId);
    }

    @GetMapping("/search")
    public List<SourceDocument> search(@RequestParam(defaultValue = "default") String workspaceId,
                                       @RequestParam String query,
                                       @RequestParam(defaultValue = "5") int limit) {
        return service.search(workspaceId, query, Math.min(limit, 20));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
