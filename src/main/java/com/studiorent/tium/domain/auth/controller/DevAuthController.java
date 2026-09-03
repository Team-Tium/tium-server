package com.studiorent.tium.domain.auth.controller;

import com.studiorent.tium.domain.auth.dto.AuthRequestDTO;
import com.studiorent.tium.domain.auth.dto.AuthResponseDTO;
import com.studiorent.tium.domain.auth.service.command.DevAuthCommandService;
import com.studiorent.tium.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dev", description = "개발 전용 API — 운영 환경에는 등록되지 않습니다")
@RestController
@RequestMapping("/dev/auth")
@Profile({"dev", "local"})
@RequiredArgsConstructor
public class DevAuthController {

    private final DevAuthCommandService devAuthCommandService;

    @Operation(summary = "임시 토큰 발급 (개발 전용)",
            description = "소셜 로그인을 거치지 않고 토큰을 발급받습니다. providerId에 해당하는 회원이 없으면 그 자리에서 만듭니다. dev·local 프로필에서만 등록됩니다.")
    @SecurityRequirements
    @PostMapping("/token")
    public ApiResponse<AuthResponseDTO.DevTokenResultDTO> issueToken(
            @Valid @RequestBody AuthRequestDTO.DevTokenDTO request) {

        return ApiResponse.onSuccess(devAuthCommandService.issueToken(request));
    }

    @Operation(summary = "토큰 검증 (개발 전용)",
            description = "토큰을 넣으면 서버가 그 토큰을 어떻게 판단하는지 알려줍니다. "
                    + "만료 여부, access/refresh 중 무엇으로 유효한지, DB 저장값과 일치하는지를 확인할 수 있습니다.")
    @PostMapping("/verify")
    public ApiResponse<AuthResponseDTO.DevVerifyResultDTO> verifyToken(
            @Valid @RequestBody AuthRequestDTO.DevVerifyDTO request) {

        return ApiResponse.onSuccess(devAuthCommandService.verifyToken(request));
    }
}
