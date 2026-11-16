package com.studiorent.tium.domain.feedback.service.analysis;

import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Speaker;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Utterance;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationFeedbackEvaluation;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationFeedbackEvaluation.Evidence;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationFeedbackEvaluation.Finding;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationFeedbackEvaluation.Impact;
import com.studiorent.tium.domain.feedback.entity.enums.OverallQuality;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** AI 등급을 덮어쓰지 않는다. 의미적 정확성 대신 검증 가능한 형식·원문 참조·판정 모순을 검사한다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConversationFeedbackEvaluationValidator {
    private final Validator validator;

    /** 장점은 부가 설명이다. 원문은 유효하지만 HELPFUL과 연결되지 않은 장점만 제외한다. */
    public ConversationFeedbackEvaluation validateAndFilterStrengths(
            List<Utterance> source, Speaker target, ConversationFeedbackEvaluation result) {
        var messages = validateSource(source, target);
        if (result == null || !validator.validate(result).isEmpty()) {
            throw invalidResponse();
        }
        Set<Evidence> helpfulEvidence = new HashSet<>();
        result.responseAssessments().stream().filter(item -> item.finding() == Finding.HELPFUL)
                .forEach(item -> item.evidence().stream()
                        .filter(evidence -> item.targetMessageIds().contains(evidence.messageId()))
                        .forEach(helpfulEvidence::add));
        var strengths = result.strengths().stream().filter(strength -> {
            strength.evidence().forEach(evidence -> validateEvidence(evidence, messages));
            var ownEvidence = strength.evidence().stream()
                    .filter(evidence -> messages.get(evidence.messageId()).speaker() == target).toList();
            if (ownEvidence.isEmpty()) {
                throw invalidResponse();
            }
            return helpfulEvidence.containsAll(ownEvidence);
        }).toList();
        if (strengths.size() != result.strengths().size()) {
            log.warn("Conversation evaluation omitted unlinked strengths: count={}", result.strengths().size() - strengths.size());
            result = new ConversationFeedbackEvaluation(result.targetSpeaker(), result.overallQuality(), result.gradeReason(),
                    result.essentialContextMissing(), result.uncertaintyReason(), result.contextUnderstanding(),
                    result.responseAssessments(), result.feedback(), strengths, result.optionalAlternatives(), result.uncertainties());
        }
        validateResult(source, target, result);
        return result;
    }

    /** 관계 유형은 등급 검증에 필요하지 않으므로 원문과 권한 확인된 평가 대상만 받는다. */
    public void validateResult(List<Utterance> source, Speaker target, ConversationFeedbackEvaluation result) {
        Map<String, Utterance> messages = validateSource(source, target);
        if (result == null || !validator.validate(result).isEmpty() || result.targetSpeaker() != target) {
            throw invalidResponse();
        }
        Set<String> targetIds = new HashSet<>();
        Set<String> otherIds = new HashSet<>();
        messages.forEach((id, message) -> (message.speaker() == target ? targetIds : otherIds).add(id));

        boolean helpful = false;
        boolean unresolvedMinor = false;
        boolean major = false;
        Set<Evidence> helpfulTargetEvidence = new HashSet<>();
        for (var item : result.responseAssessments()) {
            validateIds(item.targetMessageIds(), targetIds);
            validateIds(item.respondingToMessageIds(), otherIds);
            item.evidence().forEach(evidence -> validateEvidence(evidence, messages));
            if (item.finding() != Finding.UNCERTAIN && item.evidence().stream()
                    .noneMatch(evidence -> item.targetMessageIds().contains(evidence.messageId()))) {
                throw invalidResponse();
            }
            if (item.finding() == Finding.NEEDS_IMPROVEMENT) {
                if (item.impact() == Impact.NONE) {
                    throw invalidResponse();
                }
                major |= item.impact() == Impact.MAJOR;
                unresolvedMinor |= item.impact() == Impact.MINOR && !item.resolved();
            } else if (item.impact() != Impact.NONE || item.resolved()) {
                throw invalidResponse();
            }
            helpful |= item.finding() == Finding.HELPFUL;
            if (item.finding() == Finding.HELPFUL) {
                item.evidence().stream()
                        .filter(evidence -> item.targetMessageIds().contains(evidence.messageId()))
                        .forEach(helpfulTargetEvidence::add);
            }
        }
        for (var strength : result.strengths()) {
            strength.evidence().forEach(evidence -> validateEvidence(evidence, messages));
            if (strength.evidence().stream().noneMatch(e -> targetIds.contains(e.messageId()))) {
                throw invalidResponse();
            }
            // 장점의 사용자 발화 근거는 HELPFUL 판정에도 있어야 한다.
            for (var evidence : strength.evidence()) {
                if (targetIds.contains(evidence.messageId()) && !helpfulTargetEvidence.contains(evidence)) {
                    throw invalidResponse();
                }
            }
        }
        result.optionalAlternatives().forEach(item -> validateIds(item.targetMessageIds(), targetIds));
        if (result.essentialContextMissing() == result.uncertaintyReason().isBlank()) {
            throw invalidResponse();
        }
        // AI가 정한 등급과 구조화된 평가 사이의 명백한 모순만 거절한다.
        OverallQuality grade = result.overallQuality();
        if ((grade == OverallQuality.GOOD && (!helpful || unresolvedMinor || major || result.essentialContextMissing()))
                || (grade == OverallQuality.BAD && !major)
                || (grade == OverallQuality.AMBIGUOUS
                    && (major || (helpful && !unresolvedMinor && !result.essentialContextMissing())))) {
            throw invalidResponse();
        }
        if (!helpful && !unresolvedMinor && !major && !result.essentialContextMissing()) {
            throw invalidResponse();
        }
    }

    private Map<String, Utterance> validateSource(List<Utterance> source, Speaker target) {
        if (source == null || source.isEmpty() || target == null) {
            throw new BusinessException(ErrorStatus.FEEDBACK_ANALYSIS_INVALID_INPUT);
        }
        Map<String, Utterance> messages = new HashMap<>();
        for (var message : source) {
            if (message == null || !validator.validate(message).isEmpty()) {
                throw new BusinessException(ErrorStatus.FEEDBACK_ANALYSIS_INVALID_INPUT);
            }
            if (messages.putIfAbsent(message.messageId(), message) != null) {
                throw new BusinessException(ErrorStatus.FEEDBACK_ANALYSIS_DUPLICATE_MESSAGE_ID);
            }
        }
        if (source.stream().noneMatch(message -> message.speaker() == target)) {
            throw new BusinessException(ErrorStatus.FEEDBACK_ANALYSIS_TARGET_SPEAKER_MISSING);
        }
        return messages;
    }

    private void validateIds(List<String> ids, Set<String> allowed) {
        if (!allowed.containsAll(ids) || new HashSet<>(ids).size() != ids.size()) {
            throw invalidResponse();
        }
    }

    private void validateEvidence(Evidence evidence, Map<String, Utterance> messages) {
        var message = messages.get(evidence.messageId());
        if (message == null || !message.text().contains(evidence.quote())) {
            throw invalidResponse();
        }
    }

    private BusinessException invalidResponse() {
        return new BusinessException(ErrorStatus.FEEDBACK_EVALUATION_INVALID_RESPONSE);
    }
}
