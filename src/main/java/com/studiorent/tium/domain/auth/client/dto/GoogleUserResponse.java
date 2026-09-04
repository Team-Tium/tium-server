package com.studiorent.tium.domain.auth.client.dto;

public record GoogleUserResponse(
        String sub,
        String email
) {
}
