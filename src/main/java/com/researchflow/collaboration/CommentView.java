package com.researchflow.collaboration;

import java.time.Instant;

public record CommentView(Long id, String taskId, String authorUserId, String content, Instant createdAt) {
}
