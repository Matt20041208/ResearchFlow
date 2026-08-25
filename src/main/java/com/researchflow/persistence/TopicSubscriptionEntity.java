package com.researchflow.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "topic_subscription")
public class TopicSubscriptionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String workspaceId;
    private String name;
    private String question;
    private int intervalMinutes;
    private boolean enabled;
    private String createdBy;
    private Instant createdAt;
    private Instant nextRunAt;
    private Instant lastRunAt;
    private String lastTaskId;

    protected TopicSubscriptionEntity() {}

    public TopicSubscriptionEntity(String workspaceId, String name, String question,
                                   int intervalMinutes, String createdBy) {
        this.workspaceId = workspaceId;
        this.name = name;
        this.question = question;
        this.intervalMinutes = intervalMinutes;
        this.enabled = true;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.nextRunAt = createdAt.plusSeconds(intervalMinutes * 60L);
    }

    public Long getId() { return id; }
    public String getWorkspaceId() { return workspaceId; }
    public String getName() { return name; }
    public String getQuestion() { return question; }
    public int getIntervalMinutes() { return intervalMinutes; }
    public boolean isEnabled() { return enabled; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getNextRunAt() { return nextRunAt; }
    public Instant getLastRunAt() { return lastRunAt; }
    public String getLastTaskId() { return lastTaskId; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void recordRun(String taskId, Instant runAt) {
        this.lastTaskId = taskId;
        this.lastRunAt = runAt;
        this.nextRunAt = runAt.plusSeconds(intervalMinutes * 60L);
    }
    public void scheduleNext(Instant from) { this.nextRunAt = from.plusSeconds(intervalMinutes * 60L); }
}
