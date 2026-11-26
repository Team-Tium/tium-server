package com.studiorent.tium.domain.feedback.service.analysis;

import com.studiorent.tium.domain.feedback.converter.FeedbackConverter;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput;
import com.studiorent.tium.domain.feedback.dto.v1.FeedbackResponseDTOv1;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 분석·검증이 성공한 뒤에만 평가를 호출한다. 저장은 command 서비스가 담당한다. */
@Service
@RequiredArgsConstructor
public class ConversationFeedbackService {
    private final ConversationAnalysisService analysisService;
    private final ConversationFeedbackEvaluationService evaluationService;

    public FeedbackResponseDTOv1 generate(ConversationAnalysisInput input) {
        var analysis = analysisService.analyze(input);
        var evaluation = evaluationService.evaluate(input, analysis);
        return FeedbackConverter.fromEvaluation(evaluation, input.scope());
    }
}
