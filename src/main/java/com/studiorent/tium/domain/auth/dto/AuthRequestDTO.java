package com.studiorent.tium.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public class AuthRequestDTO {

    public record LoginDTO(
            String token,
            String authorizationCode
    ) {
    }

    public record ReissueDTO(
            String refreshToken
    ) {
    }

    public record DevTokenDTO(
            @NotBlank(message = "providerId는 필수입니다.")
            @Schema(description = "테스트 회원 식별자. 없으면 그 자리에서 회원을 만든다.", example = "test-user-1")
            String providerId
    ) {
    }

    public record DevVerifyDTO(
            @NotBlank(message = "token은 필수입니다.")
            @Schema(description = "검증할 access 또는 refresh 토큰")
            String token
    ) {
    }
}
