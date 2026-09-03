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

    public record ReissueResultDTO(
            String accessToken,
            String refreshToken
    ) {
    }

    public record DevTokenResultDTO(
            Long memberId,
            String accessToken,
            String refreshToken,
            Boolean isNewMember,
            Boolean onboardingCompleted
    ) {
    }

    public record DevVerifyResultDTO(
            Boolean expired,
            Boolean validAsAccessToken,
            Boolean validAsRefreshToken,
            Long memberId,
            Boolean matchesStoredRefreshToken,
            String verdict
    ) {
    }
}
