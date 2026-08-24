package com.researchflow.persistence;

import com.researchflow.model.TaskStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "research_task")
public class TaskEntity {
    @Id
    private String id;
    private String question;
    @Enumerated(EnumType.STRING)
    private TaskStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    @Lob
    private String report;
    @Lob
    private String error;
    private int attempts;
    private String pendingApprovalTool;
    private String approvedTools;

    protected TaskEntity() {
    }

    public TaskEntity(String id, String question) {
        this.id = id;
        this.question = question;
        this.status = TaskStatus.CREATED;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public String getId() { return id; }
    public String getQuestion() { return question; }
    public TaskStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getReport() { return report; }
    public String getError() { return error; }
    public int getAttempts() { return attempts; }
    public String getPendingApprovalTool() { return pendingApprovalTool; }
    public java.util.Set<String> getApprovedTools() {
        if (approvedTools == null || approvedTools.isBlank()) return java.util.Set.of();
        return java.util.Set.of(approvedTools.split(","));
    }
    public void setStatus(TaskStatus status) { this.status = status; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setReport(String report) { this.report = report; }
    public void setError(String error) { this.error = error; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public void setPendingApprovalTool(String pendingApprovalTool) { this.pendingApprovalTool = pendingApprovalTool; }
    public void approveTool(String tool) {
        java.util.Set<String> values = new java.util.HashSet<>(getApprovedTools());
        values.add(tool);
        this.approvedTools = String.join(",", values);
        this.pendingApprovalTool = null;
    }
}
