package com.studiorent.tium.domain.call.controller;

import com.studiorent.tium.domain.call.dto.CallSttResponseDTO;
import com.studiorent.tium.domain.call.service.CallSttService;
import com.studiorent.tium.global.response.ApiResponse;
import com.studiorent.tium.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/call")
public class CallSttController {

    private final CallSttService callSttService;

    @PostMapping(
            value = "/{callId}/stt",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ApiResponse<CallSttResponseDTO.SaveResult> saveStt(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long callId,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "startedAt", required = false) String startedAt
    ) {
        return ApiResponse.onSuccess(
                callSttService.saveStt(userDetails.getMemberId(), callId, file, startedAt));
    }

    @GetMapping(value = "/{callId}/stt", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<CallSttResponseDTO.SegmentList> findStt(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long callId
    ) {
        return ApiResponse.onSuccess(
                callSttService.findStt(userDetails.getMemberId(), callId));
    }

    @GetMapping(value = "/{callId}/feedback/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<CallSttResponseDTO.Status> findSttStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long callId
    ) {
        return ApiResponse.onSuccess(
                callSttService.findSttStatus(userDetails.getMemberId(), callId));
    }
}
