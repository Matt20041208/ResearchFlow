package com.researchflow.workflow;

import java.util.List;

public record WorkflowDefinition(List<Node> nodes, List<Edge> edges) {
    public record Node(String id, String agent) {}
    public record Edge(String from, String to) {}
}
