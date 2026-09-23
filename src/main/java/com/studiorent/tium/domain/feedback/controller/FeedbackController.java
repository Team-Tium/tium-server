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
            @RequestBody @Valid FeedbackRequestDTOv1 request
    ) {
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
