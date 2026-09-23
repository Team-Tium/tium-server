package com.studiorent.tium.domain.feedback.converter;

import com.studiorent.tium.domain.feedback.dto.v1.FeedbackResponseDTOv1;
import com.studiorent.tium.domain.feedback.entity.Feedback;
import com.studiorent.tium.domain.feedback.entity.enums.OverallQuality;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;

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
                .build();
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
}
