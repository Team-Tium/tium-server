package com.studiorent.tium.domain.feedback.controller;

import com.studiorent.tium.domain.feedback.dto.v1.FeedbackRequestDTOv1;
import com.studiorent.tium.domain.feedback.dto.v1.FeedbackResponseDTOv1;
import com.studiorent.tium.domain.feedback.service.command.FeedbackCommandService;
import com.studiorent.tium.domain.feedback.service.query.FeedbackQueryService;
import com.studiorent.tium.global.response.ApiResponse;
import com.studiorent.tium.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackCommandService feedbackCommandService;
    private final FeedbackQueryService feedbackQueryService;

    @PostMapping(value = "/chats/{roomId}/feedback", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<FeedbackResponseDTOv1> createConversationFeedback(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long roomId,
            @RequestBody(required = false) @Valid FeedbackRequestDTOv1 request
    ) {
        // userId는 요청 바디로 받지 않고, 로그인 토큰에서 꺼낸 memberId를 사용한다.
        // roomId는 URL 경로에서 받은 값이며, 서비스에서 이 유저가 해당 방 참여자인지 검증한다.
        FeedbackResponseDTOv1 result =
                feedbackCommandService.createFeedback(userDetails.getMemberId(), roomId, request);

        return ApiResponse.onSuccess(result);
    }


    @GetMapping(value = "/chats/{roomId}/feedback")
    public ApiResponse<FeedbackResponseDTOv1> conversationFeedbackFind(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long roomId
    ) {
        return ApiResponse.onSuccess(feedbackQueryService.findByMemberIdAndRoomId(userDetails.getMemberId(), roomId));
    }





}
