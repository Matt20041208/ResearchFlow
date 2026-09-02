package com.researchflow.model;

import com.researchflow.agent.runtime.SystemPlan;

import java.util.List;

public record TraceView(String taskId, SystemPlan plan, List<TraceNodeView> nodes, List<ReActStepView> steps) {
    public TraceView(String taskId, SystemPlan plan, List<TraceNodeView> nodes) {
        this(taskId, plan, nodes, List.of());
    }
}
