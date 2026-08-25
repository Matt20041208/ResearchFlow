package com.researchflow.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "task_node_execution")
public class TaskNodeExecutionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String taskId;
    private String nodeId;
    private String agent;
    private String status;
    @Lob
    private String inputSummary;
    @Lob
    private String outputSummary;
    @Lob
    private String errorSummary;
    private long durationMs;
    private Instant startedAt;

    protected TaskNodeExecutionEntity() {}

    public TaskNodeExecutionEntity(String taskId, String nodeId, String agent, String status,
                                   String inputSummary, String outputSummary, String errorSummary,
                                   long durationMs, Instant startedAt) {
        this.taskId = taskId;
        this.nodeId = nodeId;
        this.agent = agent;
        this.status = status;
        this.inputSummary = inputSummary;
        this.outputSummary = outputSummary;
        this.errorSummary = errorSummary;
        this.durationMs = durationMs;
        this.startedAt = startedAt;
    }

    public String getTaskId() { return taskId; }
    public String getNodeId() { return nodeId; }
    public String getAgent() { return agent; }
    public String getStatus() { return status; }
    public String getInputSummary() { return inputSummary; }
    public String getOutputSummary() { return outputSummary; }
    public String getErrorSummary() { return errorSummary; }
    public long getDurationMs() { return durationMs; }
    public Instant getStartedAt() { return startedAt; }
}
