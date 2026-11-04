package com.studiorent.tium.domain.feedback.dto.analysis;

import com.studiorent.tium.domain.feedback.entity.enums.RelationShip;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 서버가 선택한 평가 기준. 예시는 원본 대화의 사실로 취급하지 않는다. */
public record FeedbackEvaluationCriterion(
        @NotBlank String id,
        @NotNull RelationShip relationship,
        @NotBlank String category,
        @NotBlank String title,
        @NotBlank String principle,
        @NotBlank String detectCondition,
        @NotBlank String source
) {
}
