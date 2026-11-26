package com.studiorent.tium.domain.feedback.service.analysis;

import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Speaker;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Utterance;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationFeedbackEvaluation;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationFeedbackEvaluation.Evidence;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationFeedbackEvaluation.Finding;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationFeedbackEvaluation.Impact;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationFeedbackEvaluation.ResponseAssessment;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationFeedbackEvaluation.Strength;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
        return validateEvaluation(source, target, result, true);
    }

    /** 관계 유형은 등급 검증에 필요하지 않으므로 원문과 권한 확인된 평가 대상만 받는다. */
    public void validateResult(List<Utterance> source, Speaker target, ConversationFeedbackEvaluation result) {
        validateEvaluation(source, target, result, false);
    }

    private ConversationFeedbackEvaluation validateEvaluation(
            List<Utterance> source, Speaker target, ConversationFeedbackEvaluation result, boolean filterUnlinkedStrengths) {
        var validatedSource = validateSource(source, target);
        Map<String, Utterance> messages = validatedSource.messages();
        validateStructure(target, result);
        Set<String> targetIds = new HashSet<>();
        Set<String> otherIds = new HashSet<>();
        messages.forEach((id, message) -> (message.speaker() == target ? targetIds : otherIds).add(id));

        var assessments = validateResponseAssessments(validatedSource, targetIds, otherIds, result);
        var strengths = validateStrengths(messages, targetIds, result.strengths(),
                assessments.helpfulTargetEvidence(), filterUnlinkedStrengths);
        validateAlternativesAndUncertainty(targetIds, result);
        validateGrade(result, assessments);

        if (strengths.size() == result.strengths().size()) {
            return result;
        }
        log.warn("Conversation evaluation omitted unlinked strengths: count={}", result.strengths().size() - strengths.size());
        return new ConversationFeedbackEvaluation(result.targetSpeaker(), result.overallQuality(), result.gradeReason(),
                result.essentialContextMissing(), result.uncertaintyReason(), result.contextUnderstanding(),
                result.responseAssessments(), result.feedback(), strengths, result.optionalAlternatives(), result.uncertainties());
    }

    private void validateStructure(Speaker target, ConversationFeedbackEvaluation result) {
        if (result == null || !validator.validate(result).isEmpty() || result.targetSpeaker() != target) {
            throw invalidResponse();
        }
    }

    private AssessmentSummary validateResponseAssessments(ValidatedSource source, Set<String> targetIds,
                                                          Set<String> otherIds, ConversationFeedbackEvaluation result) {
        Map<String, Utterance> messages = source.messages();
        boolean helpful = false;
        boolean unresolvedMinor = false;
        boolean major = false;
        Set<Evidence> helpfulTargetEvidence = new HashSet<>();
        for (var item : result.responseAssessments()) {
            validateIds(item.targetMessageIds(), targetIds);
            validateIds(item.respondingToMessageIds(), otherIds);
            boolean hasTargetEvidence = false;
            for (var evidence : item.evidence()) {
                validateEvidence(evidence, messages);
                if (item.targetMessageIds().contains(evidence.messageId())) {
                    hasTargetEvidence = true;
                    if (item.finding() == Finding.HELPFUL) {
                        helpfulTargetEvidence.add(evidence);
                    }
                }
            }
            if (item.finding() != Finding.UNCERTAIN && !hasTargetEvidence) {
                throw invalidResponse();
            }
            if (item.finding() == Finding.NEEDS_IMPROVEMENT) {
                if (item.impact() == Impact.NONE) {
                    throw invalidResponse();
                }
                if (item.resolved()) {
                    validateResolutionEvidence(item, source.positions());
                }
                major |= item.impact() == Impact.MAJOR;
                unresolvedMinor |= item.impact() == Impact.MINOR && !item.resolved();
            } else if (item.impact() != Impact.NONE || item.resolved()) {
                throw invalidResponse();
            }
            helpful |= item.finding() == Finding.HELPFUL;
        }
        return new AssessmentSummary(helpful, unresolvedMinor, major, helpfulTargetEvidence);
    }

    private void validateResolutionEvidence(ResponseAssessment item, Map<String, Integer> positions) {
        // 여러 발화를 함께 평가했다면 마지막 대상 발화 이후의 근거가 있어야 전체 항목의 해소를 허용한다.
        int lastTargetPosition = item.targetMessageIds().stream().mapToInt(positions::get).max()
                .orElseThrow(this::invalidResponse);
        if (item.evidence().stream().noneMatch(evidence -> positions.get(evidence.messageId()) > lastTargetPosition)) {
            throw invalidResponse();
        }
    }

    private List<Strength> validateStrengths(Map<String, Utterance> messages, Set<String> targetIds,
                                            List<Strength> strengths, Set<Evidence> helpfulTargetEvidence,
                                            boolean filterUnlinkedStrengths) {
        List<Strength> linkedStrengths = new ArrayList<>();
        for (var strength : strengths) {
            boolean hasTargetEvidence = false;
            boolean linkedToHelpful = true;
            for (var evidence : strength.evidence()) {
                // 원문이 잘못된 근거는 선택적 장점이어도 제외로 숨기지 않는다.
                validateEvidence(evidence, messages);
                if (targetIds.contains(evidence.messageId())) {
                    hasTargetEvidence = true;
                    linkedToHelpful &= helpfulTargetEvidence.contains(evidence);
                }
            }
            if (!hasTargetEvidence || (!linkedToHelpful && !filterUnlinkedStrengths)) {
                throw invalidResponse();
            }
            if (linkedToHelpful) {
                linkedStrengths.add(strength);
            }
        }
        return List.copyOf(linkedStrengths);
    }

    private void validateAlternativesAndUncertainty(Set<String> targetIds, ConversationFeedbackEvaluation result) {
        result.optionalAlternatives().forEach(item -> validateIds(item.targetMessageIds(), targetIds));
        boolean contextMissing = result.essentialContextMissing();
        boolean reasonBlank = result.uncertaintyReason().isBlank();
        if ((contextMissing && reasonBlank) || (!contextMissing && !reasonBlank)) {
            throw invalidResponse();
        }
    }

    private void validateGrade(ConversationFeedbackEvaluation result, AssessmentSummary assessments) {
        // AI가 정한 등급과 구조화된 평가 사이의 명백한 모순만 거절한다.
        boolean valid = switch (result.overallQuality()) {
            case GOOD -> assessments.helpful() && !assessments.major()
                    && !assessments.unresolvedMinor() && !result.essentialContextMissing();
            case BAD -> assessments.major();
            case AMBIGUOUS -> !assessments.major()
                    && (assessments.unresolvedMinor() || result.essentialContextMissing());
        };
        if (!valid) {
            throw invalidResponse();
        }
    }

    private ValidatedSource validateSource(List<Utterance> source, Speaker target) {
        if (source == null || source.isEmpty() || target == null) {
            throw new BusinessException(ErrorStatus.FEEDBACK_ANALYSIS_INVALID_INPUT);
        }
        Map<String, Utterance> messages = new HashMap<>();
        Map<String, Integer> positions = new HashMap<>();
        int position = 0;
        boolean targetPresent = false;
        for (var message : source) {
            if (message == null || !validator.validate(message).isEmpty()) {
                throw new BusinessException(ErrorStatus.FEEDBACK_ANALYSIS_INVALID_INPUT);
            }
            if (messages.putIfAbsent(message.messageId(), message) != null) {
                throw new BusinessException(ErrorStatus.FEEDBACK_ANALYSIS_DUPLICATE_MESSAGE_ID);
            }
            positions.put(message.messageId(), position++);
            targetPresent |= message.speaker() == target;
        }
        if (!targetPresent) {
            throw new BusinessException(ErrorStatus.FEEDBACK_ANALYSIS_TARGET_SPEAKER_MISSING);
        }
        return new ValidatedSource(messages, positions);
    }

    private record ValidatedSource(Map<String, Utterance> messages, Map<String, Integer> positions) {
    }

    private record AssessmentSummary(boolean helpful, boolean unresolvedMinor, boolean major,
                                     Set<Evidence> helpfulTargetEvidence) {
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
