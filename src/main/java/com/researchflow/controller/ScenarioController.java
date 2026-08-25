package com.researchflow.controller;

import com.researchflow.scenario.ScenarioService;
import com.researchflow.scenario.ScenarioStatus;
import com.researchflow.scenario.ScenarioView;
import com.researchflow.service.ResearchTaskService;
import com.researchflow.workspace.WorkspaceRole;
import com.researchflow.workspace.WorkspaceService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/research/tasks/{taskId}/scenarios")
public class ScenarioController {
    private final ScenarioService scenarioService;
    private final ResearchTaskService taskService;
    private final WorkspaceService workspaceService;

    public ScenarioController(ScenarioService scenarioService, ResearchTaskService taskService,
                              WorkspaceService workspaceService) {
        this.scenarioService = scenarioService;
        this.taskService = taskService;
        this.workspaceService = workspaceService;
    }

    @PostMapping("/generate")
    public List<ScenarioView> generate(@PathVariable String taskId,
                                       @RequestHeader("X-User-Id") String userId) {
        require(taskId, userId, WorkspaceRole.EDITOR);
        return scenarioService.generate(taskId, userId);
    }

    @GetMapping
    public List<ScenarioView> list(@PathVariable String taskId,
                                   @RequestHeader("X-User-Id") String userId) {
        require(taskId, userId, WorkspaceRole.VIEWER);
        return scenarioService.list(taskId);
    }

    @PutMapping("/{scenarioId}/status")
    public ScenarioView review(@PathVariable String taskId, @PathVariable Long scenarioId,
                               @RequestHeader("X-User-Id") String userId,
                               @RequestParam ScenarioStatus status) {
        require(taskId, userId, WorkspaceRole.EDITOR);
        return scenarioService.review(taskId, scenarioId, status);
    }

    @DeleteMapping("/{scenarioId}")
    public void delete(@PathVariable String taskId, @PathVariable Long scenarioId,
                       @RequestHeader("X-User-Id") String userId) {
        require(taskId, userId, WorkspaceRole.EDITOR);
        scenarioService.delete(taskId, scenarioId);
    }

    private String require(String taskId, String userId, WorkspaceRole role) {
        String workspaceId = taskService.workspaceId(taskId);
        workspaceService.require(workspaceId, userId, role);
        return workspaceId;
    }
}
