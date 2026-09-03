package com.studiorent.tium.domain.auth.controller;

import com.studiorent.tium.domain.auth.dto.AuthRequestDTO;
import com.studiorent.tium.domain.auth.dto.AuthResponseDTO;
import com.studiorent.tium.domain.auth.service.command.AuthCommandService;
import com.studiorent.tium.global.response.ApiResponse;
import com.studiorent.tium.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthCommandService authCommandService;

    @Operation(summary = "소셜 회원가입/로그인",
            description = "소셜 계정이 없으면 가입 후 로그인, 있으면 바로 로그인합니다. 인증이 필요 없습니다.")
    @SecurityRequirements
    @PostMapping("/login/{provider}")
    public ApiResponse<AuthResponseDTO.LoginResultDTO> login(
            @PathVariable String provider,
            @Valid @RequestBody AuthRequestDTO.LoginDTO request) {

        return ApiResponse.onSuccess(authCommandService.login(provider, request));
    }

    @Operation(summary = "토큰 재발급",
            description = "refresh 토큰으로 access와 refresh를 모두 새로 발급합니다. 기존 refresh는 폐기됩니다. 인증이 필요 없습니다.")
    @SecurityRequirements
    @PostMapping("/reissue")
    public ApiResponse<AuthResponseDTO.ReissueResultDTO> reissue(
            @Valid @RequestBody AuthRequestDTO.ReissueDTO request) {

        return ApiResponse.onSuccess(authCommandService.reissue(request));
    }

    @Operation(summary = "로그아웃",
            description = "저장된 refresh 토큰을 삭제합니다. 이미 삭제되어 있어도 성공으로 처리합니다.")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal CustomUserDetails userDetails) {
        authCommandService.logout(userDetails.getMemberId());

        return ApiResponse.onSuccess(null);
    }
}
