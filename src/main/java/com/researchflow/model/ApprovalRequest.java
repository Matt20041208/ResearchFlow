package com.researchflow.model;

import jakarta.validation.constraints.NotBlank;

public record ApprovalRequest(@NotBlank String tool) {
}
