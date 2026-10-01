package com.studiorent.tium.domain.call.controller;

import com.studiorent.tium.domain.call.dto.CallRequestDTO;
import com.studiorent.tium.domain.call.dto.CallResponseDTO;
import com.studiorent.tium.domain.call.dto.CallSttResponseDTO;
import com.studiorent.tium.domain.call.service.command.CallCommandService;
import com.studiorent.tium.domain.call.service.query.CallQueryService;
import com.studiorent.tium.global.response.ApiResponse;
import com.studiorent.tium.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/call")
@RequiredArgsConstructor
public class CallController {

    private final CallCommandService callCommandService;
    private final CallQueryService callQueryService;

    @PostMapping
    public ApiResponse<CallResponseDTO.CallResultDTO> startCall(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CallRequestDTO.CallStartDTO request) {

        return ApiResponse.onSuccess(callCommandService.startCall(userDetails.getMemberId(), request));
    }

    @PostMapping("/{callId}/end")
    public ApiResponse<CallResponseDTO.CallResultDTO> endCall(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long callId) {

        return ApiResponse.onSuccess(callCommandService.endCall(userDetails.getMemberId(), callId));
    }

    @GetMapping("/feedback/recent")
    public ApiResponse<CallSttResponseDTO.RecentCallList> findRecentFeedbackCalls(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        int normalizedSize = Math.max(1, Math.min(size, 50));
        return ApiResponse.onSuccess(
                callQueryService.findRecentFeedbackCalls(userDetails.getMemberId(), cursor, normalizedSize));
    }
}
