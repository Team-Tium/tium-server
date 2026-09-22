package com.studiorent.tium.domain.feedback.converter;

import com.studiorent.tium.domain.feedback.dto.v1.FeedbackResponseDTOv1;
import com.studiorent.tium.domain.feedback.entity.Feedback;
import com.studiorent.tium.domain.feedback.entity.enums.OverallQuality;

public class FeedbackConverter {

    private FeedbackConverter() {
    }

    public static Feedback toFeedback(FeedbackResponseDTOv1 response) {
        return Feedback.builder()
                .overallFeedback(response.overallFeedback())
                .overallQuality(OverallQuality.valueOf(response.overallQuality().toUpperCase()))
                .strength(response.strength())
                .flowProblem(response.flowProblem())
                .practicePoint(response.practicePoint())
                .build();
    }
}