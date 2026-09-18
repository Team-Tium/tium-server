package com.studiorent.tium.domain.feedback.dto.v1;

import java.util.List;

public record FeedbackResponseDTOv1(
        String overallQuality,
        String overallFeedback,
        List<String> strength,
        List<String> flowProblem,
        String practicePoint,
        List<String> conversationPoints
)
{}
