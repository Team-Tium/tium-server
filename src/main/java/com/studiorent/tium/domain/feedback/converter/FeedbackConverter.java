package com.studiorent.tium.domain.feedback.converter;

import com.studiorent.tium.domain.feedback.dto.v1.FeedbackResponseDTOv1;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Scope;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationFeedbackEvaluation;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationFeedbackEvaluation.Finding;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationFeedbackEvaluation.Impact;
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

    /** 검증된 평가를 기존 API·저장 필드에 맞춘다. 등급과 사용자용 feedback은 다시 생성하지 않는다. */
    public static FeedbackResponseDTOv1 fromEvaluation(ConversationFeedbackEvaluation evaluation, Scope scope) {
        var problems = evaluation.responseAssessments().stream()
                .filter(item -> item.finding() == Finding.NEEDS_IMPROVEMENT)
                .filter(item -> !item.resolved() || item.impact() == Impact.MAJOR)
                .map(ConversationFeedbackEvaluation.ResponseAssessment::reason).toList();
        var alternatives = evaluation.optionalAlternatives().stream()
                .map(ConversationFeedbackEvaluation.Alternative::suggestion).toList();
        String feedback = scope == null ? evaluation.feedback()
                : "통화의 최근 최대 %d개 발화를 기준으로 살펴봤어요.\n\n".formatted(scope.maxUtterances()) + evaluation.feedback();
        return new FeedbackResponseDTOv1(evaluation.overallQuality().name(), feedback,
                evaluation.strengths().stream().map(ConversationFeedbackEvaluation.Strength::text).toList(),
                problems, alternatives.isEmpty() ? "" : alternatives.getFirst(),
                alternatives.size() <= 1 ? List.of() : alternatives.subList(1, alternatives.size()));
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
