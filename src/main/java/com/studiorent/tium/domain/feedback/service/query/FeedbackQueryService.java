package com.studiorent.tium.domain.feedback.service.query;

import com.studiorent.tium.domain.feedback.dto.v1.FeedbackResponseDTOv1;

public interface FeedbackQueryService {
    FeedbackResponseDTOv1 findByMemberIdAndRoomId(Long memberId, Long roomId);
}
