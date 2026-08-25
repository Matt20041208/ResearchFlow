package com.researchflow.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface TopicSubscriptionRepository extends JpaRepository<TopicSubscriptionEntity, Long> {
    List<TopicSubscriptionEntity> findByWorkspaceIdOrderByCreatedAtDesc(String workspaceId);
    List<TopicSubscriptionEntity> findByEnabledTrueAndNextRunAtLessThanEqual(Instant now);
    long countByWorkspaceIdAndEnabledTrue(String workspaceId);
}
