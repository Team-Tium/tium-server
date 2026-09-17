package com.studiorent.tium.domain.call.service.command;

import com.studiorent.tium.domain.call.converter.CallConverter;
import com.studiorent.tium.domain.call.dto.CallRequestDTO;
import com.studiorent.tium.domain.call.dto.CallResponseDTO;
import com.studiorent.tium.domain.call.entity.Call;
import com.studiorent.tium.domain.call.entity.MemberCall;
import com.studiorent.tium.domain.call.exception.CallErrorStatus;
import com.studiorent.tium.domain.call.repository.CallRepository;
import com.studiorent.tium.domain.call.socket.event.CallEndedEvent;
import com.studiorent.tium.domain.call.socket.event.CallStartedEvent;
import com.studiorent.tium.domain.member.repository.MemberRepository;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CallCommandServiceImpl implements CallCommandService {

    private final CallRepository callRepository;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    @Transactional
    public CallResponseDTO.CallResultDTO startCall(Long memberId, CallRequestDTO.CallStartDTO request) {
        validateMemberExists(memberId);

        Set<Long> participantIds = resolveOtherParticipantIds(memberId, request.participantIds());
        validateOneToOneParticipant(participantIds);

        Call call = Call.start(request.type());
        call.addParticipant(memberId);

        for (Long participantId : participantIds) {
            validateCallParticipantExists(participantId);
            call.addParticipant(participantId);
        }

        Call savedCall = callRepository.save(call);
        applicationEventPublisher.publishEvent(new CallStartedEvent(savedCall.getId(), memberId));

        return CallConverter.toCallResult(savedCall);
    }

    @Override
    @Transactional
    public CallResponseDTO.CallResultDTO endCall(Long memberId, Long callId) {
        Call call = findCall(callId);
        validateParticipant(call, memberId);

        if (call.isCompleted()) {
            throw new BusinessException(CallErrorStatus.CALL_ALREADY_COMPLETED);
        }

        call.complete();
        applicationEventPublisher.publishEvent(new CallEndedEvent(
                call.getId(),
                memberId,
                call.getType(),
                call.getEndAt(),
                extractParticipantIds(call)
        ));

        return CallConverter.toCallResult(call);
    }

    /** Finds a call with its participants for lifecycle validation. */
    private Call findCall(Long callId) {
        return callRepository.findWithParticipantsById(callId)
                .orElseThrow(() -> new BusinessException(CallErrorStatus.CALL_NOT_FOUND));
    }

    /** Validates that the authenticated member still exists. */
    private void validateMemberExists(Long memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw new BusinessException(ErrorStatus.AUTH_MEMBER_NOT_FOUND);
        }
    }

    /** Validates that a requested call participant exists before persistence. */
    private void validateCallParticipantExists(Long participantId) {
        if (!memberRepository.existsById(participantId)) {
            throw new BusinessException(CallErrorStatus.CALL_PARTICIPANT_NOT_FOUND);
        }
    }

    /** Validates that the authenticated member participates in the call. */
    private void validateParticipant(Call call, Long memberId) {
        if (!call.hasParticipant(memberId)) {
            throw new BusinessException(CallErrorStatus.CALL_FORBIDDEN);
        }
    }

    /** Removes the caller and duplicate participant IDs while preserving request order. */
    private Set<Long> resolveOtherParticipantIds(Long memberId, Set<Long> participantIds) {
        if (participantIds == null) {
            return Set.of();
        }

        Set<Long> resolvedParticipantIds = new LinkedHashSet<>(participantIds);
        resolvedParticipantIds.remove(memberId);
        return resolvedParticipantIds;
    }

    /** Validates that one-to-one call creation includes exactly one other participant. */
    private void validateOneToOneParticipant(Set<Long> participantIds) {
        if (participantIds.isEmpty()) {
            throw new BusinessException(CallErrorStatus.CALL_PARTICIPANT_REQUIRED);
        }
        if (participantIds.size() > 1) {
            throw new BusinessException(CallErrorStatus.CALL_TOO_MANY_PARTICIPANTS);
        }
    }

    /** Extracts participant IDs for post-commit realtime completion notification. */
    private List<Long> extractParticipantIds(Call call) {
        return call.getParticipants().stream()
                .map(MemberCall::getMemberId)
                .toList();
    }
}
