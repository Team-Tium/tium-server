package com.studiorent.tium.domain.feedback.service.analysis;

import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Utterance;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisResult;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisResult.UtteranceAnalysis;
import com.studiorent.tium.domain.feedback.dto.analysis.UtterancePurpose;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 분석 결과의 구조와 원문 인용 일치를 검증한다.
 * 목적 분류와 맥락 해석의 의미적 정확성은 검증하지 않는다.
 */
@Component
@Slf4j
public class ConversationAnalysisValidator {

    private final Validator validator; // DTO의 @NotBlank, @NotNull, @Valid 조건을 검사한다.

    public ConversationAnalysisValidator(Validator validator) {
        this.validator = validator;
    }

    public void validateInput(ConversationAnalysisInput input) {
        if (input == null || !validator.validate(input).isEmpty()) {
            throw new BusinessException(ErrorStatus.BAD_REQUEST);
        }

        Set<String> messageIds = new HashSet<>();
        boolean targetPresent = false;
        for (var message : input.messages()) {
            if (!messageIds.add(message.messageId())) { // 같은 발화 ID가 이미 있으면 add()는 false를 반환한다.
                throw new BusinessException(ErrorStatus.BAD_REQUEST);
            }
            targetPresent |= message.speaker() == input.targetSpeaker(); // 대상 화자가 한 번이라도 등장하면 true를 유지한다.
        }
        if (!targetPresent) {
            throw new BusinessException(ErrorStatus.BAD_REQUEST);
        }
    }


    public void validateResult(ConversationAnalysisInput input, ConversationAnalysisResult result) {
        validateInput(input);
        if (result == null || !validator.validate(result).isEmpty()) {
            throw invalidResponse(ErrorStatus.FEEDBACK_ANALYSIS_INVALID_RESPONSE, null);
        }

        validateContextEvidence(input, result);
        if (result.utterances().size() != input.messages().size()) {
            throw invalidResponse(ErrorStatus.FEEDBACK_ANALYSIS_UTTERANCE_COUNT_MISMATCH, null);
        }

        for (int index = 0; index < input.messages().size(); index++) {
            validateUtterance(input.messages().get(index), result.utterances().get(index));
        }
    }

    private void validateContextEvidence(ConversationAnalysisInput input, ConversationAnalysisResult result) {
        Set<String> messageIds = new HashSet<>();
        input.messages().forEach(message -> messageIds.add(message.messageId()));
        Set<String> contextIds = new HashSet<>(result.contextEvidenceMessageIds());

        if (!messageIds.containsAll(contextIds)) {
            throw invalidResponse(ErrorStatus.FEEDBACK_ANALYSIS_CONTEXT_EVIDENCE_ID_NOT_FOUND, null);
        }

        if (contextIds.size() != result.contextEvidenceMessageIds().size()) {
            throw invalidResponse(ErrorStatus.FEEDBACK_ANALYSIS_DUPLICATE_CONTEXT_EVIDENCE_ID, null);
        }
    }

    private void validateUtterance(Utterance message, UtteranceAnalysis analysis) {
        // 개수와 순서를 대조하면 발화 누락, 중복, 존재하지 않는 ID도 거를 수 있다.
        if (!message.messageId().equals(analysis.messageId())) {
            throw invalidResponse(ErrorStatus.FEEDBACK_ANALYSIS_UTTERANCE_ORDER_MISMATCH, message.messageId());
        }

        Set<UtterancePurpose> purposes = new HashSet<>();
        for (var candidate : analysis.purposeCandidates()) {
            if (!purposes.add(candidate.purpose())) {
                throw invalidResponse(ErrorStatus.FEEDBACK_ANALYSIS_DUPLICATE_PURPOSE, message.messageId());
            }
            if (!message.text().contains(candidate.evidenceQuote())) {
                throw invalidResponse(ErrorStatus.FEEDBACK_ANALYSIS_PURPOSE_QUOTE_NOT_FOUND, message.messageId());
            }
        }
        // UNKNOWN은 단독으로 사용하고, 판단하기 어려운 이유를 uncertainties에 남긴다.
        if (purposes.contains(UtterancePurpose.UNKNOWN)
                && (purposes.size() != 1 || analysis.uncertainties().isEmpty())) {
            throw invalidResponse(ErrorStatus.FEEDBACK_ANALYSIS_INVALID_UNKNOWN_USAGE, message.messageId());
        }

        validateQuotes(message, analysis.explicitEmotionQuotes(), ErrorStatus.FEEDBACK_ANALYSIS_EMOTION_QUOTE_NOT_FOUND);
        validateQuotes(message, analysis.explicitRequestQuotes(), ErrorStatus.FEEDBACK_ANALYSIS_REQUEST_QUOTE_NOT_FOUND);
    }

    private void validateQuotes(Utterance message, List<String> quotes, ErrorStatus errorStatus) {
        if (quotes.stream().anyMatch(quote -> !message.text().contains(quote))) {
            throw invalidResponse(errorStatus, message.messageId());
        }
    }

    private BusinessException invalidResponse(ErrorStatus errorStatus, String messageId) {
        // 대화 원문, 인용문, LLM 응답은 기록하지 않는다. messageId는 원본 입력의 ID만 사용한다.
        log.warn("Conversation analysis validation failed: code={}, reason={}, messageId={}",
                errorStatus.getCode(), errorStatus.name(), messageId);
        return new BusinessException(errorStatus);
    }
}
