package com.researchflow.controller;

import com.researchflow.subscription.SubscriptionRequest;
import com.researchflow.subscription.SubscriptionService;
import com.researchflow.subscription.SubscriptionView;
import com.researchflow.workspace.WorkspaceRole;
import com.researchflow.workspace.WorkspaceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {
    private final SubscriptionService service;
    private final WorkspaceService workspaceService;

    public SubscriptionController(SubscriptionService service, WorkspaceService workspaceService) {
        this.service = service;
        this.workspaceService = workspaceService;
    }

    @PostMapping
    public SubscriptionView create(@RequestHeader("X-User-Id") String userId,
                                   @Valid @RequestBody SubscriptionRequest request) {
        workspaceService.require(request.workspaceId(), userId, WorkspaceRole.EDITOR);
        return service.create(userId, request);
    }

    @GetMapping
    public List<SubscriptionView> list(@RequestHeader("X-User-Id") String userId,
                                       @RequestParam String workspaceId) {
        workspaceService.require(workspaceId, userId, WorkspaceRole.VIEWER);
        return service.list(workspaceId);
    }

    @PutMapping("/{id}/enabled")
    public SubscriptionView enabled(@PathVariable Long id, @RequestHeader("X-User-Id") String userId,
                                    @RequestParam boolean value) {
        var entity = service.get(id);
        workspaceService.require(entity.getWorkspaceId(), userId, WorkspaceRole.EDITOR);
        return service.setEnabled(id, value);
    }

    @PostMapping("/{id}/run")
    public ResponseEntity<Map<String, String>> run(@PathVariable Long id,
                                                    @RequestHeader("X-User-Id") String userId) {
        var entity = service.get(id);
        workspaceService.require(entity.getWorkspaceId(), userId, WorkspaceRole.EDITOR);
        return ResponseEntity.accepted().body(Map.of("taskId", service.run(id)));
    }
}
