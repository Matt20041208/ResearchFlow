package com.researchflow.collaboration;

import jakarta.validation.constraints.NotBlank;

public record CommentRequest(@NotBlank String content) {
}
