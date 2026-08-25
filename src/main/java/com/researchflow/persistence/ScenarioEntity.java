package com.researchflow.persistence;

import com.researchflow.scenario.ScenarioStatus;
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
@Table(name = "scenario")
public class ScenarioEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String taskId;
    private String workspaceId;
    private String title;
    private String nodeCombination;
    private String trigger;
    private String injectedData;
    @Lob
    private String expectation;
    private String risk;
    @Enumerated(EnumType.STRING)
    private ScenarioStatus status;
    private Instant createdAt;

    protected ScenarioEntity() {}

    public ScenarioEntity(String taskId, String workspaceId, String title, String nodeCombination,
                          String trigger, String injectedData, String expectation, String risk) {
        this.taskId = taskId;
        this.workspaceId = workspaceId;
        this.title = title;
        this.nodeCombination = nodeCombination;
        this.trigger = trigger;
        this.injectedData = injectedData;
        this.expectation = expectation;
        this.risk = risk;
        this.status = ScenarioStatus.SUGGESTED;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getTaskId() { return taskId; }
    public String getWorkspaceId() { return workspaceId; }
    public String getTitle() { return title; }
    public String getNodeCombination() { return nodeCombination; }
    public String getTrigger() { return trigger; }
    public String getInjectedData() { return injectedData; }
    public String getExpectation() { return expectation; }
    public String getRisk() { return risk; }
    public ScenarioStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setStatus(ScenarioStatus status) { this.status = status; }
}
