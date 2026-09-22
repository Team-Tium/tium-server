package com.studiorent.tium.domain.feedback.service.query;

import com.studiorent.tium.domain.feedback.dto.v1.FeedbackResponseDTOv1;
import com.studiorent.tium.domain.feedback.entity.Feedback;
import com.studiorent.tium.domain.feedback.repository.FeedbackRepository;
import com.studiorent.tium.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.studiorent.tium.global.response.code.status.ErrorStatus.FEEDBACK_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class FeedbackQueryServiceImpl implements FeedbackQueryService {

    private final FeedbackRepository feedbackRepository;

    @Override
    public FeedbackResponseDTOv1 feedbackFindById(Long feedbackId) {

        Feedback feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new BusinessException(FEEDBACK_NOT_FOUND));



        return new FeedbackResponseDTOv1(
                feedback.getOverallQuality().name(),
                feedback.getOverallFeedback(),
                feedback.getStrength(),
                feedback.getFlowProblem(),
                feedback.getPracticePoint(),
                List.of()
        );
}


}
