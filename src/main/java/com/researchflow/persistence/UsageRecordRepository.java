package com.researchflow.persistence;

import com.researchflow.billing.UsageType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface UsageRecordRepository extends JpaRepository<UsageRecordEntity, Long> {
    long countByWorkspaceIdAndUsageTypeAndOccurredAtGreaterThanEqual(String workspaceId, UsageType type, Instant since);
    List<UsageRecordEntity> findByWorkspaceIdAndOccurredAtGreaterThanEqual(String workspaceId, Instant since);
}
