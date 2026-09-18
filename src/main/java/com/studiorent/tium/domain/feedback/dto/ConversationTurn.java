package com.studiorent.tium.domain.feedback.dto;

import jakarta.validation.constraints.NotBlank;

public record ConversationTurn(
        @NotBlank String speaker,
        @NotBlank String message) {

}
