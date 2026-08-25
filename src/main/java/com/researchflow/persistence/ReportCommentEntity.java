package com.researchflow.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "report_comment")
public class ReportCommentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String taskId;
    private String workspaceId;
    private String authorUserId;
    @Lob
    private String content;
    private Instant createdAt;

    protected ReportCommentEntity() {}

    public ReportCommentEntity(String taskId, String workspaceId, String authorUserId, String content) {
        this.taskId = taskId;
        this.workspaceId = workspaceId;
        this.authorUserId = authorUserId;
        this.content = content;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getTaskId() { return taskId; }
    public String getAuthorUserId() { return authorUserId; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
