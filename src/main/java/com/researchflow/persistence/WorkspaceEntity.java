package com.researchflow.persistence;

import com.researchflow.billing.PlanTier;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspace")
public class WorkspaceEntity {
    @Id
    private String id;
    private String name;
    private String ownerUserId;
    @Enumerated(EnumType.STRING)
    private PlanTier planTier;
    private Instant createdAt;

    protected WorkspaceEntity() {}

    public WorkspaceEntity(String name, String ownerUserId) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.ownerUserId = ownerUserId;
        this.planTier = PlanTier.FREE;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getOwnerUserId() { return ownerUserId; }
    public PlanTier getPlanTier() { return planTier == null ? PlanTier.FREE : planTier; }
    public Instant getCreatedAt() { return createdAt; }
    public void setPlanTier(PlanTier planTier) { this.planTier = planTier; }
}
