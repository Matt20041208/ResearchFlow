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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/research/tasks")
public class ResearchTaskController {
    private final ResearchTaskService taskService;
    private final ReportExportService exportService;

    public ResearchTaskController(ResearchTaskService taskService, ReportExportService exportService) {
        this.taskService = taskService;
        this.exportService = exportService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> create(@Valid @RequestBody ResearchRequest request) {
        return ResponseEntity.accepted().body(Map.of("taskId", taskService.create(request)));
    }

    @GetMapping
    public List<TaskSummary> list() {
        return taskService.list();
    }

    @GetMapping("/{taskId}")
    public TaskSnapshot get(@PathVariable String taskId) {
        return taskService.get(taskId);
    }

    @GetMapping("/{taskId}/citations")
    public List<Citation> citations(@PathVariable String taskId) {
        return taskService.citations(taskId);
    }

    @GetMapping("/{taskId}/export")
    public ResponseEntity<byte[]> export(@PathVariable String taskId,
                                         @org.springframework.web.bind.annotation.RequestParam(defaultValue = "markdown") String format) {
        var file = exportService.export(taskId, ExportFormat.parse(format));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .contentType(MediaType.parseMediaType(file.mediaType()))
                .body(file.content());
    }

    @GetMapping("/{taskId}/events")
    public SseEmitter events(@PathVariable String taskId) {
        return taskService.events(taskId);
    }

    @PostMapping("/{taskId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable String taskId) {
        taskService.cancel(taskId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{taskId}/retry")
    public ResponseEntity<Void> retry(@PathVariable String taskId) {
        taskService.retry(taskId);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{taskId}/approve")
    public ResponseEntity<Void> approve(@PathVariable String taskId,
                                        @Valid @RequestBody ApprovalRequest request) {
        taskService.approve(taskId, request.tool());
        return ResponseEntity.accepted().build();
    }
}
