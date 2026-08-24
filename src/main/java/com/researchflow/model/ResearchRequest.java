package com.researchflow.model;

import jakarta.validation.constraints.NotBlank;

public record ResearchRequest(@NotBlank String question) {
}
