package com.studiorent.tium.domain.feedback.service.analysis;

import com.studiorent.tium.domain.feedback.converter.FeedbackConverter;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput;
import com.studiorent.tium.domain.feedback.dto.v1.FeedbackResponseDTOv1;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.function.Supplier;

/** 분석·검증이 성공한 뒤에만 평가를 호출한다. 저장은 command 서비스가 담당한다. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationFeedbackService {
    private static final Set<ErrorStatus> RETRYABLE_ANALYSIS_OUTPUT_ERRORS = Set.of(
            ErrorStatus.FEEDBACK_ANALYSIS_INVALID_RESPONSE,
            ErrorStatus.FEEDBACK_ANALYSIS_CONTEXT_EVIDENCE_ID_NOT_FOUND,
            ErrorStatus.FEEDBACK_ANALYSIS_DUPLICATE_CONTEXT_EVIDENCE_ID,
            ErrorStatus.FEEDBACK_ANALYSIS_UTTERANCE_COUNT_MISMATCH,
            ErrorStatus.FEEDBACK_ANALYSIS_UTTERANCE_ORDER_MISMATCH,
            ErrorStatus.FEEDBACK_ANALYSIS_DUPLICATE_PURPOSE,
            ErrorStatus.FEEDBACK_ANALYSIS_PURPOSE_QUOTE_NOT_FOUND,
            ErrorStatus.FEEDBACK_ANALYSIS_INVALID_UNKNOWN_USAGE,
            ErrorStatus.FEEDBACK_ANALYSIS_EMOTION_QUOTE_NOT_FOUND,
            ErrorStatus.FEEDBACK_ANALYSIS_REQUEST_QUOTE_NOT_FOUND,
            ErrorStatus.FEEDBACK_ANALYSIS_INVALID_REQUEST_REFERENCE,
            ErrorStatus.FEEDBACK_ANALYSIS_RESPONSE_CONVERSION_FAILED);
    private static final Set<ErrorStatus> RETRYABLE_EVALUATION_OUTPUT_ERRORS = Set.of(
            ErrorStatus.FEEDBACK_EVALUATION_INVALID_RESPONSE,
            ErrorStatus.FEEDBACK_EVALUATION_RESPONSE_CONVERSION_FAILED);

    private final ConversationAnalysisService analysisService;
    private final ConversationFeedbackEvaluationService evaluationService;

    public FeedbackResponseDTOv1 generate(ConversationAnalysisInput input) {
        var analysis = withOutputValidationRetry(() -> analysisService.analyze(input), RETRYABLE_ANALYSIS_OUTPUT_ERRORS);
        var evaluation = withOutputValidationRetry(() -> evaluationService.evaluate(input, analysis), RETRYABLE_EVALUATION_OUTPUT_ERRORS);
        return FeedbackConverter.fromEvaluation(evaluation, input.scope());
    }

    private <T> T withOutputValidationRetry(Supplier<T> operation, Set<ErrorStatus> retryableErrors) {
        try {
            return operation.get();
        } catch (BusinessException failure) {
            if (!retryableErrors.contains(failure.getBaseCode())) {
                throw failure;
            }
            log.warn("Retrying feedback stage after invalid model output: code={}", failure.getBaseCode().getCode());
        }
        // 두 번째 실패는 그대로 전파한다. 평가 재시도는 이미 검증된 동일 analysis를 사용한다.
        return operation.get();
    }
}
