package com.studiorent.tium.domain.feedback.converter;

import com.studiorent.tium.domain.feedback.dto.v1.FeedbackResponseDTOv1;
import com.studiorent.tium.domain.feedback.entity.Feedback;
import com.studiorent.tium.domain.feedback.entity.enums.OverallQuality;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FeedbackConverter {

    private FeedbackConverter() {
    }

    public static Feedback toFeedback(Long memberId, Long roomId, FeedbackResponseDTOv1 response) {
        return Feedback.builder()
                .roomId(roomId)
                .memberId(memberId)
                .overallQuality(toOverallQuality(response.overallQuality()))
                .overallFeedback(response.overallFeedback())
                .strength(response.strength())
                .flowProblem(response.flowProblem())
                .practicePoint(response.practicePoint())
                .conversationPoints(response.conversationPoints())
                .completed(true)
                .build();
    }

    public static Feedback toCallFeedback(Long memberId, Long callId, FeedbackResponseDTOv1 response) {
        return Feedback.builder()
                .callId(callId)
                .memberId(memberId)
                .overallQuality(toOverallQuality(response.overallQuality()))
                .overallFeedback(response.overallFeedback())
                .strength(response.strength())
                .flowProblem(response.flowProblem())
                .practicePoint(response.practicePoint())
                .conversationPoints(response.conversationPoints())
                .completed(true)
                .build();
    }

    public static void complete(Feedback feedback, FeedbackResponseDTOv1 response) {
        feedback.complete(
                toOverallQuality(response.overallQuality()),
                response.overallFeedback(),
                response.strength(),
                response.flowProblem(),
                response.practicePoint(),
                response.conversationPoints());
    }

    public static FeedbackResponseDTOv1 toResponse(Feedback feedback) {
        return new FeedbackResponseDTOv1(
                feedback.getOverallQuality().name(),
                feedback.getOverallFeedback(),
                copyList(feedback.getStrength()),
                copyList(feedback.getFlowProblem()),
                feedback.getPracticePoint(),
                copyList(feedback.getConversationPoints()));
    }

    public static void validateResponse(FeedbackResponseDTOv1 response) {
        if (response == null) {
            throw new BusinessException(ErrorStatus.FEEDBACK_INVALID_RESPONSE);
        }

        toOverallQuality(response.overallQuality());
    }

    private static OverallQuality toOverallQuality(String overallQuality) {
        if (overallQuality == null || overallQuality.isBlank()) {
            throw new BusinessException(ErrorStatus.FEEDBACK_INVALID_RESPONSE);
        }

        try {
            return OverallQuality.valueOf(overallQuality.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorStatus.FEEDBACK_INVALID_RESPONSE);
        }
    }

    private static List<String> copyList(List<String> values) {
        return values == null ? List.of() : new ArrayList<>(values);
    }
}
