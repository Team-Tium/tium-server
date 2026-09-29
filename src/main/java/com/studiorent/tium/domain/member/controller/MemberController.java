package com.studiorent.tium.domain.member.controller;

import com.studiorent.tium.domain.member.dto.MemberRequestDTO;
import com.studiorent.tium.domain.member.dto.MemberResponseDTO;
import com.studiorent.tium.domain.member.service.command.MemberCommandService;
import com.studiorent.tium.domain.member.service.query.MemberQueryService;
import com.studiorent.tium.global.response.ApiResponse;
import com.studiorent.tium.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 두 엔드포인트의 경로에 공통 접두어가 없어 클래스 레벨 @RequestMapping을 두지 않는다.
 * 경로는 프론트와 합의된 명세를 그대로 따르며 /api/v1 접두어를 붙이지 않는다(auth와 동일).
 */
@Tag(name = "Member", description = "회원 정보 API")
@RestController
@RequiredArgsConstructor
public class MemberController {

    private final MemberQueryService memberQueryService;
    private final MemberCommandService memberCommandService;

    @Operation(summary = "내 정보 조회",
            description = "access 토큰의 회원 정보를 반환합니다. 온보딩 전에도 호출할 수 있으며, "
                    + "이 경우 이름·전화번호·주소·성별·생년월일·자기소개서가 전부 null입니다.")
    @GetMapping("/users/me")
    public ApiResponse<MemberResponseDTO.MyProfileDTO> getMyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ApiResponse.onSuccess(memberQueryService.getMyProfile(userDetails.getMemberId()));
    }

    @Operation(summary = "온보딩 정보 저장",
            description = "이름·생년월일·주소·성별 등을 저장하고 온보딩을 완료 처리합니다. "
                    + "모든 필드가 선택값이고 보내지 않은 필드는 기존 값을 유지하므로, "
                    + "다시 호출하면 프로필 수정으로도 쓸 수 있습니다.")
    @PostMapping("/onboarding/profile")
    public ApiResponse<MemberResponseDTO.MyProfileDTO> saveOnboardingProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody MemberRequestDTO.OnboardingProfileDTO request) {

        return ApiResponse.onSuccess(
                memberCommandService.saveOnboardingProfile(userDetails.getMemberId(), request));
    }
}
