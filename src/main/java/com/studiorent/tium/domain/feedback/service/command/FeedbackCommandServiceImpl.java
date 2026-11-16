package com.studiorent.tium.domain.feedback.service.command;

import com.studiorent.tium.domain.call.service.CallAccessValidator;
import com.studiorent.tium.domain.feedback.converter.FeedbackConverter;
import com.studiorent.tium.domain.feedback.dto.v1.FeedbackResponseDTOv1;
import com.studiorent.tium.domain.feedback.entity.Feedback;
import com.studiorent.tium.domain.feedback.entity.enums.RelationShip;
import com.studiorent.tium.domain.feedback.repository.FeedbackRepository;
import com.studiorent.tium.domain.feedback.service.analysis.ConversationFeedbackService;
import com.studiorent.tium.domain.feedback.service.query.ConversationAnalysisInputQueryService;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class FeedbackCommandServiceImpl implements FeedbackCommandService {
    private static final long CALL_FEEDBACK_PROCESSING_TIMEOUT_MINUTES = 15;
    private final FeedbackRepository feedbackRepository;
    private final ConversationAnalysisInputQueryService inputQueryService;
    private final ConversationFeedbackService conversationFeedbackService;
    private final CallAccessValidator callAccessValidator;
    private final TransactionTemplate transactionTemplate;

    public FeedbackCommandServiceImpl(FeedbackRepository feedbackRepository,
            ConversationAnalysisInputQueryService inputQueryService,
            ConversationFeedbackService conversationFeedbackService,
            CallAccessValidator callAccessValidator, PlatformTransactionManager transactionManager) {
        this.feedbackRepository = feedbackRepository;
        this.inputQueryService = inputQueryService;
        this.conversationFeedbackService = conversationFeedbackService;
        this.callAccessValidator = callAccessValidator;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public FeedbackResponseDTOv1 createFeedback(Long memberId, Long roomId) {
        // 현재 방에는 관계를 저장하지 않으므로 FRIEND를 임의로 지정하지 않는다.
        var input = inputQueryService.findChatInput(memberId, roomId, RelationShip.UNCONFIRMED);
        var result = conversationFeedbackService.generate(input);
        feedbackRepository.save(FeedbackConverter.toFeedback(memberId, roomId, result));
        return result;
    }

    @Override
    public FeedbackResponseDTOv1 createCallFeedback(Long memberId, Long callId) {
        callAccessValidator.validateParticipant(callId, memberId);
        var existing = findExistingCallFeedback(memberId, callId);
        if (existing.isPresent()) {
            return existing.get();
        }
        Feedback myClaim = claimCallFeedback(memberId, callId);
        try {
            var input = inputQueryService.findCallInput(memberId, callId, RelationShip.UNCONFIRMED);
            var result = conversationFeedbackService.generate(input);
            completeCallFeedback(myClaim, result);
            return result;
        } catch (RuntimeException failure) {
            releaseCallFeedbackClaim(memberId, callId);
            throw failure;
        }
    }

    private Optional<FeedbackResponseDTOv1> findExistingCallFeedback(Long memberId, Long callId) {
        return transactionTemplate.execute(status ->
                feedbackRepository.findTopByMemberIdAndCallIdAndCompletedTrueOrderByCreatedAtDesc(memberId, callId)
                        .map(FeedbackConverter::toResponse));
    }

    private Feedback claimCallFeedback(Long memberId, Long callId) {
        try {
            return transactionTemplate.execute(status -> {
                callAccessValidator.validateParticipantForUpdate(callId, memberId);
                deleteExpiredCallFeedbackClaim(memberId, callId);

                if (feedbackRepository.existsByMemberIdAndCallIdAndCompletedTrue(memberId, callId)
                        || feedbackRepository.existsByMemberIdAndCallIdAndCompletedFalse(memberId, callId)) {
                    throw new BusinessException(ErrorStatus.DUPLICATE_REQUEST);
                }

                Feedback myFeedback = Feedback.callProcessing(memberId, callId);
                return feedbackRepository.save(myFeedback);
            });
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorStatus.DUPLICATE_REQUEST);
        }
    }

    private void deleteExpiredCallFeedbackClaim(Long memberId, Long callId) {
        LocalDateTime expiredBefore = LocalDateTime.now()
                .minusMinutes(CALL_FEEDBACK_PROCESSING_TIMEOUT_MINUTES);
        feedbackRepository.deleteByMemberIdAndCallIdAndCompletedFalseAndCreatedAtBefore(memberId, callId, expiredBefore);
    }

    private void completeCallFeedback(
            Feedback myClaim,
            FeedbackResponseDTOv1 myFeedback
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            FeedbackConverter.complete(myClaim, myFeedback);
            feedbackRepository.save(myClaim);
        });
    }

    private void releaseCallFeedbackClaim(Long memberId, Long callId) {
        transactionTemplate.executeWithoutResult(status ->
                feedbackRepository.deleteByMemberIdAndCallIdAndCompletedFalse(memberId, callId));
    }

}
