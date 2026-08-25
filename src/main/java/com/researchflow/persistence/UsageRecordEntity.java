package com.researchflow.persistence;

import com.researchflow.billing.UsageType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "usage_record")
public class UsageRecordEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String workspaceId;
    private String userId;
    @Enumerated(EnumType.STRING)
    private UsageType usageType;
    private long units;
    private String referenceId;
    private Instant occurredAt;

    protected UsageRecordEntity() {}

    public UsageRecordEntity(String workspaceId, String userId, UsageType usageType, long units, String referenceId) {
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.usageType = usageType;
        this.units = units;
        this.referenceId = referenceId;
        this.occurredAt = Instant.now();
    }

    public UsageType getUsageType() { return usageType; }
    public long getUnits() { return units; }
    public Instant getOccurredAt() { return occurredAt; }
}
