package com.studiorent.tium.domain.feedback.dto.analysis;

import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Speaker;
import com.studiorent.tium.domain.feedback.entity.enums.OverallQuality;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** 목적·맥락에 따른 2차 평가 응답. 전체 등급은 AI가 반환하고 서버는 근거와 일관성을 검증한다. */
public record ConversationFeedbackEvaluation(
        @NotNull Speaker targetSpeaker,
        @NotNull OverallQuality overallQuality,
        @NotBlank String gradeReason,
        @NotNull Boolean essentialContextMissing,
        @NotNull String uncertaintyReason,
        @NotBlank String contextUnderstanding,
        @NotNull List<@NotNull @Valid ResponseAssessment> responseAssessments,
        @NotBlank String feedback,
        @NotNull List<@NotNull @Valid Strength> strengths,
        @NotNull List<@NotNull @Valid Alternative> optionalAlternatives,
        @NotNull List<@NotBlank String> uncertainties
) {
    public enum Finding { HELPFUL, NEEDS_IMPROVEMENT, UNCERTAIN }

    /** 개선 필요의 영향. 도움이 된 반응과 판단 보류에는 NONE을 사용한다. */
    public enum Impact { NONE, MINOR, MAJOR }

    public record Evidence(@NotBlank String messageId, @NotBlank String quote) {
    }

    public record ResponseAssessment(
            @NotEmpty List<@NotBlank String> targetMessageIds,
            @NotNull List<@NotBlank String> respondingToMessageIds,
            @NotBlank String function,
            @NotNull Finding finding,
            @NotNull Impact impact,
            @NotNull Boolean resolved,
            @NotNull List<@NotNull @Valid Evidence> evidence,
            @NotBlank String reason
    ) {
    }

    public record Strength(
            @NotBlank String text,
            @NotEmpty List<@NotNull @Valid Evidence> evidence
    ) {
    }

    public record Alternative(
            @NotEmpty List<@NotBlank String> targetMessageIds,
            @NotBlank String intendedMeaning,
            @NotBlank String suggestion,
            @NotBlank String reason
    ) {
    }
}
