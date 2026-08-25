package com.researchflow.persistence;

import com.researchflow.validation.ValidationRunStatus;
import com.researchflow.validation.ValidationVerdict;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scenario_validation")
public class ScenarioValidationEntity {
    @Id
    private String id;
    private Long scenarioId;
    private String taskId;
    private String workspaceId;
    private String createdBy;
    @Enumerated(EnumType.STRING)
    private ValidationRunStatus status;
    @Enumerated(EnumType.STRING)
    private ValidationVerdict verdict;
    @Lob
    private String rulesJson;
    @Lob
    private String expectation;
    @Lob
    private String actualTraceJson;
    @Lob
    private String outputSummary;
    @Lob
    private String error;
    private long durationMs;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;

    protected ScenarioValidationEntity() {}

    public ScenarioValidationEntity(Long scenarioId, String taskId, String workspaceId, String createdBy,
                                    String rulesJson, String expectation) {
        this.id = UUID.randomUUID().toString();
        this.scenarioId = scenarioId;
        this.taskId = taskId;
        this.workspaceId = workspaceId;
        this.createdBy = createdBy;
        this.rulesJson = rulesJson;
        this.expectation = expectation;
        this.status = ValidationRunStatus.QUEUED;
        this.verdict = ValidationVerdict.NEEDS_REVIEW;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public Long getScenarioId() { return scenarioId; }
    public String getTaskId() { return taskId; }
    public String getWorkspaceId() { return workspaceId; }
    public String getCreatedBy() { return createdBy; }
    public ValidationRunStatus getStatus() { return status; }
    public ValidationVerdict getVerdict() { return verdict; }
    public String getRulesJson() { return rulesJson; }
    public String getExpectation() { return expectation; }
    public String getActualTraceJson() { return actualTraceJson; }
    public String getOutputSummary() { return outputSummary; }
    public String getError() { return error; }
    public long getDurationMs() { return durationMs; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setStatus(ValidationRunStatus status) { this.status = status; }
    public void setVerdict(ValidationVerdict verdict) { this.verdict = verdict; }
    public void setActualTraceJson(String actualTraceJson) { this.actualTraceJson = actualTraceJson; }
    public void setOutputSummary(String outputSummary) { this.outputSummary = outputSummary; }
    public void setError(String error) { this.error = error; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
