package com.studiorent.tium.domain.auth.controller;

import com.studiorent.tium.domain.auth.dto.AuthRequestDTO;
import com.studiorent.tium.domain.auth.dto.AuthResponseDTO;
import com.studiorent.tium.domain.auth.service.command.AuthCommandService;
import com.studiorent.tium.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
            description = "소셜 계정이 없으면 가입 후 로그인, 있으면 바로 로그인합니다.")
    @PostMapping("/login/{provider}")
    public ApiResponse<AuthResponseDTO.LoginResultDTO> login(
            @PathVariable String provider,
            @Valid @RequestBody AuthRequestDTO.LoginDTO request) {

        return ApiResponse.onSuccess(authCommandService.login(provider, request));
    }
}
