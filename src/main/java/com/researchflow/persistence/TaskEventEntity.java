package com.researchflow.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "research_task_event")
public class TaskEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String taskId;
    private String agent;
    private String status;
    @Lob
    private String message;
    private Instant occurredAt;

    protected TaskEventEntity() {
    }

    public TaskEventEntity(String taskId, String agent, String status, String message, Instant occurredAt) {
        this.taskId = taskId;
        this.agent = agent;
        this.status = status;
        this.message = message;
        this.occurredAt = occurredAt;
    }

    public String getTaskId() { return taskId; }
    public String getAgent() { return agent; }
    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public Instant getOccurredAt() { return occurredAt; }
}
