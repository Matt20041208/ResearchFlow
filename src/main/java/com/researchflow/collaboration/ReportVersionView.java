package com.researchflow.collaboration;

import java.time.Instant;

public record ReportVersionView(int versionNumber, String content, String createdBy, Instant createdAt) {
}
