package com.studiorent.tium.domain.auth.dto;

public class AuthResponseDTO {

    public record LoginResultDTO(
            Long memberId,
            String accessToken,
            String refreshToken,
            Boolean isNewMember,
            Boolean onboardingCompleted
    ) {
    }
}
