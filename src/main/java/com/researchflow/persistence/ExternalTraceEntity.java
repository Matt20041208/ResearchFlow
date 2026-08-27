package com.researchflow.persistence;

import com.researchflow.externaltrace.ExternalTraceStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "external_trace")
public class ExternalTraceEntity {
    @Id
    private String id;
    private String workspaceId;
    private String name;
    private String sourceSystem;
    @Enumerated(EnumType.STRING)
    private ExternalTraceStatus status;
    private int nodeCount;
    private Instant startedAt;
    private Instant endedAt;
    private Instant createdAt;
    private String reportedBy;
    @Lob
    private String planJson;

    protected ExternalTraceEntity() {}

    public ExternalTraceEntity(String workspaceId, String name, String sourceSystem,
                               ExternalTraceStatus status, int nodeCount, Instant startedAt,
                               Instant endedAt, String reportedBy, String planJson) {
        this.id = UUID.randomUUID().toString();
        this.workspaceId = workspaceId;
        this.name = name;
        this.sourceSystem = sourceSystem;
        this.status = status;
        this.nodeCount = nodeCount;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.reportedBy = reportedBy;
        this.planJson = planJson;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getWorkspaceId() { return workspaceId; }
    public String getName() { return name; }
    public String getSourceSystem() { return sourceSystem; }
    public ExternalTraceStatus getStatus() { return status; }
    public int getNodeCount() { return nodeCount; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public String getReportedBy() { return reportedBy; }
    public String getPlanJson() { return planJson; }
}
