package com.studiorent.tium.domain.matching.controller;

import com.studiorent.tium.domain.matching.dto.MatchingRequestDTO;
import com.studiorent.tium.domain.matching.dto.MatchingResponseDTO;
import com.studiorent.tium.domain.matching.service.command.MatchingCommandService;
import com.studiorent.tium.global.response.ApiResponse;
import com.studiorent.tium.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Matching", description = "랜덤 매칭 API")
@RestController
@RequestMapping("/api/v1/matching")
@RequiredArgsConstructor
public class MatchingController {

    private final MatchingCommandService matchingCommandService;

    @Operation(summary = "매칭 대기",
            description = "대기 중인 상대가 있으면 바로 채팅방을 만들어 방 ID와 상대를 돌려준다(matched=true). "
                    + "없으면 대기열에 들어가고(matched=false) 짝이 정해지면 MATCHED 소켓 이벤트로 알린다. "
                    + "현재는 CHAT만 받는다(MATCHING4001). 이미 대기 중이면 MATCHING4091.")
    @PostMapping
    public ApiResponse<MatchingResponseDTO.EnqueueResultDTO> enqueue(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody MatchingRequestDTO.EnqueueDTO request) {

        return ApiResponse.onSuccess(matchingCommandService.enqueue(userDetails.getMemberId(), request));
    }

    @Operation(summary = "매칭 대기 취소",
            description = "대기 중이 아니면 MATCHING4041. 짝이 이미 정해진 뒤에 취소가 도착해도 MATCHING4041이다.")
    @DeleteMapping("/me")
    public ApiResponse<Void> cancel(@AuthenticationPrincipal CustomUserDetails userDetails) {
        matchingCommandService.cancel(userDetails.getMemberId());
        return ApiResponse.onSuccess(null);
    }
}
