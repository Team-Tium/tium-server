package com.studiorent.tium.domain.feedback.service.command;

import com.studiorent.tium.domain.feedback.dto.v1.FeedbackResponseDTOv1;

public interface FeedbackCommandService {
     FeedbackResponseDTOv1 createFeedback(Long memberId, Long roomId);

     FeedbackResponseDTOv1 createCallFeedback(Long memberId, Long callId);
}
