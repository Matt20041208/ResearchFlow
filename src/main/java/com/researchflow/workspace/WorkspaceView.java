package com.researchflow.workspace;

import com.researchflow.billing.PlanTier;

import java.time.Instant;

public record WorkspaceView(String id, String name, String ownerUserId, PlanTier planTier,
                            WorkspaceRole currentUserRole, Instant createdAt) {
}
