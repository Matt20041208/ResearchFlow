package com.researchflow.workspace;

import java.time.Instant;

public record MemberView(String userId, WorkspaceRole role, Instant joinedAt) {
}
