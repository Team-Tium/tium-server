package com.studiorent.tium.domain.feedback.controller;

import com.studiorent.tium.domain.feedback.dto.v1.FeedbackRequestDTOv1;
import com.studiorent.tium.domain.feedback.dto.v1.FeedbackResponseDTOv1;
import com.studiorent.tium.domain.feedback.service.command.FeedbackCommandService;
import com.studiorent.tium.domain.feedback.service.command.FeedbackCommandServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/feedback")
public class FeedbackController {

    private final FeedbackCommandService feedbackCommandService;

    /**
     * RAG 호출을 담당하는 서비스 의존성을 주입받는다.
     */
    public FeedbackController(FeedbackCommandService feedbackCommandService) {
        this.feedbackCommandService = feedbackCommandService;
    }

    //피드백 결과확인용 조회 피드백

    /*       피드백 생성
     *        피드백 컨버터로 받아서  -> request용으로 바꾼다음에 하는 형식으로 갈거임 피드백 생성하여 하는 형식
     *        대화가 안보여서 그냥 일단 피드백 컨버터로 대화를 받는다고 생각받았다고 생각한다음
     *           request먼저 DTO 만들고 시작해야될
     *
     * */

    // 생성이랑 조회는 분리해야할듯
    @PostMapping(value = "/chats/{roomId}/feedback", produces = MediaType.APPLICATION_JSON_VALUE)
    //요청은 보내는 자신의 UserId로 보내줘야함
    public FeedbackResponseDTOv1 ConversationFeedbackCreate(@PathVariable Long roomId, @RequestBody @Valid FeedbackRequestDTOv1 request) {

        return feedbackCommandService.FeedbackCreate(roomId, request);
    }




    /*
     * 피드백 조회용
     * */





    /*
     * 피드백 삭제
     * */



    // 할말 추천


}
