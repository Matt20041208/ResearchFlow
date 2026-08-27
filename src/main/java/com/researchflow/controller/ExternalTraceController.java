package com.researchflow.controller;

import com.researchflow.externaltrace.ExternalTraceIngestRequest;
import com.researchflow.externaltrace.ExternalTraceService;
import com.researchflow.externaltrace.ExternalTraceSummary;
import com.researchflow.externaltrace.ExternalTraceView;
import com.researchflow.scenario.ScenarioService;
import com.researchflow.scenario.ScenarioStatus;
import com.researchflow.scenario.ScenarioView;
import com.researchflow.workspace.WorkspaceRole;
import com.researchflow.workspace.WorkspaceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/external-traces")
public class ExternalTraceController {
    private final ExternalTraceService traceService;
    private final WorkspaceService workspaceService;
    private final ScenarioService scenarioService;

    public ExternalTraceController(ExternalTraceService traceService, WorkspaceService workspaceService,
                                   ScenarioService scenarioService) {
        this.traceService = traceService;
        this.workspaceService = workspaceService;
        this.scenarioService = scenarioService;
    }

    @PostMapping
    public ExternalTraceView ingest(@RequestHeader("X-User-Id") String userId,
                                    @Valid @RequestBody ExternalTraceIngestRequest request) {
        workspaceService.require(request.workspaceId(), userId, WorkspaceRole.EDITOR);
        return traceService.ingest(request, userId);
    }

    @GetMapping
    public List<ExternalTraceSummary> list(@RequestHeader("X-User-Id") String userId,
                                           @RequestParam String workspaceId) {
        workspaceService.require(workspaceId, userId, WorkspaceRole.VIEWER);
        return traceService.list(workspaceId);
    }

    @GetMapping("/{traceId}")
    public ExternalTraceView get(@RequestHeader("X-User-Id") String userId, @PathVariable String traceId) {
        require(traceId, userId, WorkspaceRole.VIEWER);
        return traceService.get(traceId);
    }

    @DeleteMapping("/{traceId}")
    public ResponseEntity<Void> delete(@RequestHeader("X-User-Id") String userId, @PathVariable String traceId) {
        require(traceId, userId, WorkspaceRole.EDITOR);
        traceService.delete(traceId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{traceId}/scenarios/generate")
    public List<ScenarioView> generate(@RequestHeader("X-User-Id") String userId, @PathVariable String traceId) {
        String workspaceId = require(traceId, userId, WorkspaceRole.EDITOR);
        return scenarioService.generateReference(traceId, workspaceId, traceService.trace(traceId), userId, "EXTERNAL_TRACE");
    }

    @GetMapping("/{traceId}/scenarios")
    public List<ScenarioView> scenarios(@RequestHeader("X-User-Id") String userId, @PathVariable String traceId) {
        require(traceId, userId, WorkspaceRole.VIEWER);
        return scenarioService.list(traceId);
    }

    @PutMapping("/{traceId}/scenarios/{scenarioId}/status")
    public ScenarioView review(@RequestHeader("X-User-Id") String userId, @PathVariable String traceId,
                               @PathVariable Long scenarioId, @RequestParam ScenarioStatus status) {
        require(traceId, userId, WorkspaceRole.EDITOR);
        return scenarioService.review(traceId, scenarioId, status);
    }

    private String require(String traceId, String userId, WorkspaceRole role) {
        String workspaceId = traceService.workspaceId(traceId);
        workspaceService.require(workspaceId, userId, role);
        return workspaceId;
    }
}
