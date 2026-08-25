package com.researchflow.controller;

import com.researchflow.billing.PlanTier;
import com.researchflow.workspace.MemberRequest;
import com.researchflow.workspace.MemberView;
import com.researchflow.workspace.WorkspaceCreateRequest;
import com.researchflow.workspace.WorkspaceService;
import com.researchflow.workspace.WorkspaceView;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {
    private final WorkspaceService service;

    public WorkspaceController(WorkspaceService service) { this.service = service; }

    @PostMapping
    public WorkspaceView create(@RequestHeader("X-User-Id") String userId,
                                @Valid @RequestBody WorkspaceCreateRequest request) {
        return service.create(userId, request);
    }

    @GetMapping
    public List<WorkspaceView> list(@RequestHeader("X-User-Id") String userId) {
        return service.list(userId);
    }

    @PutMapping("/{workspaceId}/members")
    public MemberView member(@PathVariable String workspaceId,
                             @RequestHeader("X-User-Id") String userId,
                             @Valid @RequestBody MemberRequest request) {
        return service.addOrUpdateMember(workspaceId, userId, request);
    }

    @GetMapping("/{workspaceId}/members")
    public List<MemberView> members(@PathVariable String workspaceId,
                                    @RequestHeader("X-User-Id") String userId) {
        return service.members(workspaceId, userId);
    }

    @PutMapping("/{workspaceId}/plan")
    public WorkspaceView plan(@PathVariable String workspaceId,
                              @RequestHeader("X-User-Id") String userId,
                              @RequestParam PlanTier tier) {
        return service.changePlan(workspaceId, userId, tier);
    }
}
