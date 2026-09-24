package com.studiorent.tium.domain.feedback.service.query;

import com.studiorent.tium.domain.chat.service.ChatRoomValidator;
import com.studiorent.tium.domain.feedback.dto.v1.FeedbackResponseDTOv1;
import com.studiorent.tium.domain.feedback.entity.Feedback;
import com.studiorent.tium.domain.feedback.repository.FeedbackRepository;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class FeedbackQueryServiceImpl implements FeedbackQueryService {

    private final FeedbackRepository feedbackRepository;
    private final ChatRoomValidator chatRoomValidator;


    @Override
    @Transactional(readOnly = true)
    public FeedbackResponseDTOv1 findByMemberIdAndRoomId(Long memberId, Long roomId) {
        chatRoomValidator.getJoinedMember(roomId, memberId);

        Feedback feedback =
                feedbackRepository
                .findTopByMemberIdAndRoomIdOrderByCreatedAtDesc(memberId, roomId)
                .orElseThrow(() -> new BusinessException(ErrorStatus.FEEDBACK_NOT_FOUND));


        return  new FeedbackResponseDTOv1(
                feedback.getOverallQuality().name(),
                feedback.getOverallFeedback(),
                feedback.getStrength(),
                feedback.getFlowProblem(),
                feedback.getPracticePoint(),
                feedback.getConversationPoints()
        );
    }
}
