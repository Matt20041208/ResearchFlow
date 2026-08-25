package com.researchflow.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "report_version")
public class ReportVersionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String taskId;
    private int versionNumber;
    @Lob
    private String content;
    private String createdBy;
    private Instant createdAt;

    protected ReportVersionEntity() {}

    public ReportVersionEntity(String taskId, int versionNumber, String content, String createdBy) {
        this.taskId = taskId;
        this.versionNumber = versionNumber;
        this.content = content;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public String getTaskId() { return taskId; }
    public int getVersionNumber() { return versionNumber; }
    public String getContent() { return content; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
