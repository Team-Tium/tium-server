package com.studiorent.tium.domain.feedback.controller;

import com.studiorent.tium.domain.feedback.dto.v1.FeedbackRequestDTOv1;
import com.studiorent.tium.domain.feedback.dto.v1.FeedbackResponseDTOv1;
import com.studiorent.tium.domain.feedback.repository.FeedbackRepository;
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
@RequestMapping("/api/v1/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackCommandService feedbackCommandService;
    private final FeedbackQueryService feedbackQueryService;

    /**
     * RAG 호출을 담당하는 서비스 의존성을 주입받는다.
     */


    //피드백 결과확인용 조회 피드백

    /*       피드백 생성
     *
     *        대화가 아직 안보여서 그냥 일단 피드백 컨버터로 대화를 받았다고 생각받았다고 생각한다음
     *           request먼저 DTO 만들고 시작
     *
     * */

    // 생성이랑 조회는 분리해야할듯
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


    /*
     * 피드백 조회
     * */

    @GetMapping(value = "/chats/{feedbackId}/feedback")
    public ApiResponse<FeedbackResponseDTOv1> ConversationFeedbackFind(
            @PathVariable Long feedbackId
    ) {

        return ApiResponse.onSuccess(feedbackQueryService.feedbackFindById(feedbackId));
    }

    // 할말 추천

    // 대화 내용을 하는게 맞을듯





}
