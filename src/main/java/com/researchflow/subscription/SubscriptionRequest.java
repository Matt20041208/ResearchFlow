package com.researchflow.subscription;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SubscriptionRequest(@NotBlank String workspaceId, @NotBlank String name,
                                  @NotBlank String question, @Min(5) int intervalMinutes) {
}
