package com.researchflow.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "task_node_step")
public class TaskNodeStepEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String taskId;
    private String nodeId;
    private String agent;
    private int iteration;
    private String action;
    @Lob
    private String observation;
    private String decision;
    @Lob
    private String rationale;
    private long durationMs;
    private Instant occurredAt;

    protected TaskNodeStepEntity() {}

    public TaskNodeStepEntity(String taskId, String nodeId, String agent, int iteration, String action,
                              String observation, String decision, String rationale, long durationMs,
                              Instant occurredAt) {
        this.taskId = taskId;
        this.nodeId = nodeId;
        this.agent = agent;
        this.iteration = iteration;
        this.action = action;
        this.observation = observation;
        this.decision = decision;
        this.rationale = rationale;
        this.durationMs = durationMs;
        this.occurredAt = occurredAt;
    }

    public String getTaskId() { return taskId; }
    public String getNodeId() { return nodeId; }
    public String getAgent() { return agent; }
    public int getIteration() { return iteration; }
    public String getAction() { return action; }
    public String getObservation() { return observation; }
    public String getDecision() { return decision; }
    public String getRationale() { return rationale; }
    public long getDurationMs() { return durationMs; }
    public Instant getOccurredAt() { return occurredAt; }
}
