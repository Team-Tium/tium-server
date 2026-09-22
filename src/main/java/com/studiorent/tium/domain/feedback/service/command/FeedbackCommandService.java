package com.studiorent.tium.domain.feedback.service.command;

import com.studiorent.tium.domain.feedback.dto.v1.FeedbackRequestDTOv1;
import com.studiorent.tium.domain.feedback.dto.v1.FeedbackResponseDTOv1;

public interface FeedbackCommandService {
     FeedbackResponseDTOv1 createFeedback(Long memberId, Long roomId, FeedbackRequestDTOv1 request);

}
