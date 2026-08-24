package com.researchflow.controller;

import com.researchflow.agent.runtime.AgentRegistry;
import com.researchflow.agent.runtime.SystemAgentPlanner;
import com.researchflow.agent.runtime.SystemPlan;
import com.researchflow.model.AgentDescriptor;
import com.researchflow.model.ResearchRequest;
import com.researchflow.tool.ToolDescriptor;
import com.researchflow.tool.ToolRegistry;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system-agent")
public class SystemAgentController {
    private final SystemAgentPlanner planner;
    private final AgentRegistry registry;
    private final ToolRegistry toolRegistry;

    public SystemAgentController(SystemAgentPlanner planner, AgentRegistry registry, ToolRegistry toolRegistry) {
        this.planner = planner;
        this.registry = registry;
        this.toolRegistry = toolRegistry;
    }

    @PostMapping("/plan")
    public SystemPlan plan(@Valid @RequestBody ResearchRequest request) {
        return planner.plan(request.question());
    }

    @GetMapping("/agents")
    public List<AgentDescriptor> agents() {
        return registry.all().stream()
                .map(agent -> new AgentDescriptor(agent.name(), agent.capabilities(), agent.requiredTools()))
                .toList();
    }

    @GetMapping("/tools")
    public List<ToolDescriptor> tools() {
        return toolRegistry.all();
    }
}
