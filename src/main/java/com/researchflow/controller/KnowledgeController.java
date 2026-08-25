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
import org.springframework.web.bind.annotation.RequestHeader;
import com.researchflow.workspace.WorkspaceService;
import com.researchflow.workspace.WorkspaceRole;
import com.researchflow.billing.UsageService;
import com.researchflow.billing.UsageType;

@RestController
@RequestMapping("/api/knowledge/documents")
public class KnowledgeController {
    private final KnowledgeService service;
    private final KnowledgeDocumentParser parser;
    private final WorkspaceService workspaceService;
    private final UsageService usageService;

    public KnowledgeController(KnowledgeService service, KnowledgeDocumentParser parser,
                               WorkspaceService workspaceService, UsageService usageService) {
        this.service = service;
        this.parser = parser;
        this.workspaceService = workspaceService;
        this.usageService = usageService;
    }

    @PostMapping
    public ResponseEntity<KnowledgeDocumentView> create(@RequestHeader("X-User-Id") String userId,
                                                         @Valid @RequestBody KnowledgeDocumentRequest request) {
        workspaceService.require(request.normalizedWorkspaceId(), userId, WorkspaceRole.EDITOR);
        usageService.requireDocumentCapacity(request.normalizedWorkspaceId());
        KnowledgeDocumentView document = service.create(request);
        usageService.record(request.normalizedWorkspaceId(), userId, UsageType.DOCUMENT_CREATED, document.id());
        return ResponseEntity.ok(document);
    }

    @PostMapping(value = "/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<KnowledgeDocumentView> upload(
            @RequestPart MultipartFile file,
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "default") String workspaceId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String sourceUrl) {
        String resolvedTitle = title == null || title.isBlank() ? file.getOriginalFilename() : title;
        if (resolvedTitle == null || resolvedTitle.isBlank()) resolvedTitle = "Untitled document";
        workspaceService.require(workspaceId, userId, WorkspaceRole.EDITOR);
        usageService.requireDocumentCapacity(workspaceId);
        KnowledgeDocumentView document = service.create(new KnowledgeDocumentRequest(workspaceId, resolvedTitle,
                parser.parse(file), sourceUrl));
        usageService.record(workspaceId, userId, UsageType.DOCUMENT_CREATED, document.id());
        return ResponseEntity.ok(document);
    }

    @GetMapping
    public List<KnowledgeDocumentView> list(@RequestHeader("X-User-Id") String userId,
                                            @RequestParam String workspaceId) {
        workspaceService.require(workspaceId, userId, WorkspaceRole.VIEWER);
        return service.list(workspaceId);
    }

    @GetMapping("/search")
    public List<SourceDocument> search(@RequestHeader("X-User-Id") String userId,
                                       @RequestParam String workspaceId,
                                       @RequestParam String query,
                                       @RequestParam(defaultValue = "5") int limit) {
        workspaceService.require(workspaceId, userId, WorkspaceRole.VIEWER);
        return service.search(workspaceId, query, Math.min(limit, 20));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id,
                                       @RequestHeader("X-User-Id") String userId) {
        workspaceService.require(service.workspaceId(id), userId, WorkspaceRole.EDITOR);
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
