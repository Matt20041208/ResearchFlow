package com.researchflow.controller;

import com.researchflow.collaboration.CollaborationService;
import com.researchflow.collaboration.CommentRequest;
import com.researchflow.collaboration.CommentView;
import com.researchflow.collaboration.ReportVersionView;
import com.researchflow.service.ResearchTaskService;
import com.researchflow.workspace.WorkspaceRole;
import com.researchflow.workspace.WorkspaceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/research/tasks/{taskId}")
public class CollaborationController {
    private final CollaborationService collaborationService;
    private final ResearchTaskService taskService;
    private final WorkspaceService workspaceService;

    public CollaborationController(CollaborationService collaborationService, ResearchTaskService taskService,
                                   WorkspaceService workspaceService) {
        this.collaborationService = collaborationService;
        this.taskService = taskService;
        this.workspaceService = workspaceService;
    }

    @GetMapping("/versions")
    public List<ReportVersionView> versions(@PathVariable String taskId,
                                            @RequestHeader("X-User-Id") String userId) {
        require(taskId, userId, WorkspaceRole.VIEWER);
        return collaborationService.versions(taskId);
    }

    @PostMapping("/comments")
    public CommentView comment(@PathVariable String taskId,
                               @RequestHeader("X-User-Id") String userId,
                               @Valid @RequestBody CommentRequest request) {
        String workspaceId = require(taskId, userId, WorkspaceRole.VIEWER);
        return collaborationService.comment(taskId, workspaceId, userId, request);
    }

    @GetMapping("/comments")
    public List<CommentView> comments(@PathVariable String taskId,
                                      @RequestHeader("X-User-Id") String userId) {
        require(taskId, userId, WorkspaceRole.VIEWER);
        return collaborationService.comments(taskId);
    }

    private String require(String taskId, String userId, WorkspaceRole role) {
        String workspaceId = taskService.workspaceId(taskId);
        workspaceService.require(workspaceId, userId, role);
        return workspaceId;
    }
}
