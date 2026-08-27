package com.researchflow.persistence;

import com.researchflow.externaltrace.ExternalNodeStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "external_trace_node")
public class ExternalTraceNodeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String traceId;
    private String nodeId;
    private String agent;
    @Lob
    private String dependenciesJson;
    @Enumerated(EnumType.STRING)
    private ExternalNodeStatus status;
    @Lob
    private String inputSummary;
    @Lob
    private String outputSummary;
    @Lob
    private String errorSummary;
    private long durationMs;
    private Instant startedAt;
    private boolean externalBoundary;
    private boolean asyncNode;

    protected ExternalTraceNodeEntity() {}

    public ExternalTraceNodeEntity(String traceId, String nodeId, String agent, String dependenciesJson,
                                   ExternalNodeStatus status, String inputSummary, String outputSummary,
                                   String errorSummary, long durationMs, Instant startedAt,
                                   boolean externalBoundary, boolean asyncNode) {
        this.traceId = traceId;
        this.nodeId = nodeId;
        this.agent = agent;
        this.dependenciesJson = dependenciesJson;
        this.status = status;
        this.inputSummary = inputSummary;
        this.outputSummary = outputSummary;
        this.errorSummary = errorSummary;
        this.durationMs = durationMs;
        this.startedAt = startedAt;
        this.externalBoundary = externalBoundary;
        this.asyncNode = asyncNode;
    }

    public String getTraceId() { return traceId; }
    public String getNodeId() { return nodeId; }
    public String getAgent() { return agent; }
    public String getDependenciesJson() { return dependenciesJson; }
    public ExternalNodeStatus getStatus() { return status; }
    public String getInputSummary() { return inputSummary; }
    public String getOutputSummary() { return outputSummary; }
    public String getErrorSummary() { return errorSummary; }
    public long getDurationMs() { return durationMs; }
    public Instant getStartedAt() { return startedAt; }
    public boolean isExternalBoundary() { return externalBoundary; }
    public boolean isAsyncNode() { return asyncNode; }
}
