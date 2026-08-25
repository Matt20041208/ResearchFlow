package com.researchflow.controller;

import com.researchflow.model.ResearchRequest;
import com.researchflow.model.ApprovalRequest;
import com.researchflow.model.Citation;
import com.researchflow.model.TaskSnapshot;
import com.researchflow.model.TaskSummary;
import com.researchflow.service.ResearchTaskService;
import com.researchflow.export.ExportFormat;
import com.researchflow.export.ReportExportService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import com.researchflow.workspace.WorkspaceService;
import com.researchflow.workspace.WorkspaceRole;
import com.researchflow.billing.UsageService;
import com.researchflow.billing.UsageType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/research/tasks")
public class ResearchTaskController {
    private final ResearchTaskService taskService;
    private final ReportExportService exportService;
    private final WorkspaceService workspaceService;
    private final UsageService usageService;

    public ResearchTaskController(ResearchTaskService taskService, ReportExportService exportService,
                                  WorkspaceService workspaceService, UsageService usageService) {
        this.taskService = taskService;
        this.exportService = exportService;
        this.workspaceService = workspaceService;
        this.usageService = usageService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> create(@RequestHeader("X-User-Id") String userId,
                                                       @Valid @RequestBody ResearchRequest request) {
        workspaceService.require(request.normalizedWorkspaceId(), userId, WorkspaceRole.EDITOR);
        return ResponseEntity.accepted().body(Map.of("taskId", taskService.create(request, userId)));
    }

    @GetMapping
    public List<TaskSummary> list(@RequestHeader("X-User-Id") String userId,
                                  @RequestParam String workspaceId) {
        workspaceService.require(workspaceId, userId, WorkspaceRole.VIEWER);
        return taskService.list(workspaceId);
    }

    @GetMapping("/{taskId}")
    public TaskSnapshot get(@PathVariable String taskId, @RequestHeader("X-User-Id") String userId) {
        requireTask(taskId, userId, WorkspaceRole.VIEWER);
        return taskService.get(taskId);
    }

    @GetMapping("/{taskId}/citations")
    public List<Citation> citations(@PathVariable String taskId, @RequestHeader("X-User-Id") String userId) {
        requireTask(taskId, userId, WorkspaceRole.VIEWER);
        return taskService.citations(taskId);
    }

    @GetMapping("/{taskId}/trace")
    public com.researchflow.model.TraceView trace(@PathVariable String taskId,
                                                  @RequestHeader("X-User-Id") String userId) {
        requireTask(taskId, userId, WorkspaceRole.VIEWER);
        return taskService.trace(taskId);
    }

    @GetMapping("/{taskId}/export")
    public ResponseEntity<byte[]> export(@PathVariable String taskId,
                                         @RequestHeader("X-User-Id") String userId,
                                         @RequestParam(defaultValue = "markdown") String format) {
        String workspaceId = requireTask(taskId, userId, WorkspaceRole.VIEWER);
        var file = exportService.export(taskId, ExportFormat.parse(format));
        usageService.record(workspaceId, userId, UsageType.REPORT_EXPORTED, taskId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .contentType(MediaType.parseMediaType(file.mediaType()))
                .body(file.content());
    }

    @GetMapping("/{taskId}/events")
    public SseEmitter events(@PathVariable String taskId, @RequestHeader("X-User-Id") String userId) {
        requireTask(taskId, userId, WorkspaceRole.VIEWER);
        return taskService.events(taskId);
    }

    @PostMapping("/{taskId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable String taskId, @RequestHeader("X-User-Id") String userId) {
        requireTask(taskId, userId, WorkspaceRole.EDITOR);
        taskService.cancel(taskId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{taskId}/retry")
    public ResponseEntity<Void> retry(@PathVariable String taskId, @RequestHeader("X-User-Id") String userId) {
        requireTask(taskId, userId, WorkspaceRole.EDITOR);
        taskService.retry(taskId);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{taskId}/approve")
    public ResponseEntity<Void> approve(@PathVariable String taskId,
                                        @RequestHeader("X-User-Id") String userId,
                                        @Valid @RequestBody ApprovalRequest request) {
        requireTask(taskId, userId, WorkspaceRole.OWNER);
        taskService.approve(taskId, request.tool());
        return ResponseEntity.accepted().build();
    }

    private String requireTask(String taskId, String userId, WorkspaceRole role) {
        String workspaceId = taskService.workspaceId(taskId);
        workspaceService.require(workspaceId, userId, role);
        return workspaceId;
    }
}
