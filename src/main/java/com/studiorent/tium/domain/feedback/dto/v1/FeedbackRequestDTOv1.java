package com.studiorent.tium.domain.feedback.dto.v1;

import com.studiorent.tium.domain.feedback.entity.enums.RelationShip;
import jakarta.annotation.Nullable;

public record FeedbackRequestDTOv1(
        @Nullable RelationShip relationship, // 관계
        @Nullable String goal // 목표  말잘하게하기?
) {

    public static FeedbackRequestDTOv1 empty() {
        return new FeedbackRequestDTOv1(null, null);
    }
}
