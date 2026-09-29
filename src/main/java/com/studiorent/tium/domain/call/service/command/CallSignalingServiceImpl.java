package com.studiorent.tium.domain.call.service.command;

import com.studiorent.tium.domain.call.dto.CallSocketDTO;
import com.studiorent.tium.domain.call.entity.Call;
import com.studiorent.tium.domain.call.entity.MemberCall;
import com.studiorent.tium.domain.call.entity.enums.CallEndedReason;
import com.studiorent.tium.domain.call.entity.enums.CallSignalKind;
import com.studiorent.tium.domain.call.exception.CallErrorStatus;
import com.studiorent.tium.domain.call.repository.CallRepository;
import com.studiorent.tium.domain.call.socket.CallSocketEventType;
import com.studiorent.tium.domain.call.socket.event.CallAcceptedEvent;
import com.studiorent.tium.domain.call.socket.event.CallEndedEvent;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.socket.SocketDestinations;
import com.studiorent.tium.global.socket.SocketEvent;
import com.studiorent.tium.global.socket.SocketEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CallSignalingServiceImpl implements CallSignalingService {

    private final CallRepository callRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final SocketEventPublisher socketEventPublisher;

    /** Persists receiver acceptance and publishes CALL_ACCEPTED after commit. */
    @Override
    @Transactional
    public void accept(Long memberId, Long callId) {
        Call call = findCallForUpdate(callId);
        validateReceiver(call, memberId);
        validateRinging(call);

        LocalDateTime acceptedAt = LocalDateTime.now();
        call.accept(acceptedAt);
        applicationEventPublisher.publishEvent(new CallAcceptedEvent(call.getId(), memberId, acceptedAt));
    }

    /** Persists receiver rejection and publishes CALL_ENDED after commit. */
    @Override
    @Transactional
    public void reject(Long memberId, Long callId) {
        Call call = findCallForUpdate(callId);
        validateReceiver(call, memberId);
        validateRinging(call);

        LocalDateTime endAt = LocalDateTime.now();
        call.reject(endAt);
        applicationEventPublisher.publishEvent(new CallEndedEvent(
                call.getId(),
                memberId,
                call.getType(),
                CallEndedReason.REJECTED,
                call.getEndAt(),
                extractParticipantIds(call)
        ));
    }

    /** Relays one validated WebRTC signal through the call topic without persisting media metadata. */
    @Override
    @Transactional(readOnly = true)
    public void relaySignal(Long memberId, Long callId, CallSocketDTO.SignalRequestDTO request) {
        validateSignalPayload(request);
        Call call = findCall(callId);
        validateAcceptedParticipant(call, memberId);
        validateSignalRole(call, memberId, request.kind());

        socketEventPublisher.toDestination(
                SocketDestinations.CALL_SUB_PREFIX + "/" + callId,
                new SocketEvent<>(
                        CallSocketEventType.CALL_SIGNAL,
                        new CallSocketDTO.SignalDTO(
                                callId,
                                memberId,
                                request.kind(),
                                request.sdp(),
                                request.candidate()
                        )
                )
        );
    }

    /** Locks the call before acceptance or rejection to serialize lifecycle transitions. */
    private Call findCallForUpdate(Long callId) {
        return callRepository.findWithLockById(callId)
                .orElseThrow(() -> new BusinessException(CallErrorStatus.CALL_NOT_FOUND));
    }

    /** Loads a call with participants for role and lifecycle validation. */
    private Call findCall(Long callId) {
        return callRepository.findWithParticipantsById(callId)
                .orElseThrow(() -> new BusinessException(CallErrorStatus.CALL_NOT_FOUND));
    }

    /** Ensures that the sender is the receiver and the call is still waiting for acceptance. */
    private void validateRinging(Call call) {
        if (!call.isInProgress() || call.isAccepted()) {
            throw new BusinessException(CallErrorStatus.CALL_INVALID_LIFECYCLE_ACTION);
        }
    }

    /** Ensures that only the non-caller 1:1 participant can accept or reject. */
    private void validateReceiver(Call call, Long memberId) {
        if (!call.hasParticipant(memberId) || call.isCaller(memberId)) {
            throw new BusinessException(CallErrorStatus.CALL_FORBIDDEN);
        }
    }

    /** Ensures that signaling occurs only inside an accepted, non-terminal participant call. */
    private void validateAcceptedParticipant(Call call, Long memberId) {
        if (!call.hasParticipant(memberId)) {
            throw new BusinessException(CallErrorStatus.CALL_FORBIDDEN);
        }
        if (!call.isInProgress()) {
            throw new BusinessException(CallErrorStatus.CALL_ALREADY_COMPLETED);
        }
        if (!call.isAccepted()) {
            throw new BusinessException(CallErrorStatus.CALL_INVALID_LIFECYCLE_ACTION);
        }
    }

    /** Enforces caller-first OFFER, receiver ANSWER, and participant ICE authorization. */
    private void validateSignalRole(Call call, Long memberId, CallSignalKind kind) {
        if (kind == CallSignalKind.OFFER && !call.isCaller(memberId)) {
            throw new BusinessException(CallErrorStatus.CALL_FORBIDDEN);
        }
        if (kind == CallSignalKind.ANSWER && !memberId.equals(call.getReceiverMemberId())) {
            throw new BusinessException(CallErrorStatus.CALL_FORBIDDEN);
        }
    }

    /** Validates the discriminated CALL_SIGNAL payload shape without parsing SDP or ICE. */
    private void validateSignalPayload(CallSocketDTO.SignalRequestDTO request) {
        if (request == null || request.kind() == null) {
            throw new BusinessException(CallErrorStatus.CALL_INVALID_SIGNALING_PAYLOAD);
        }

        if ((request.kind() == CallSignalKind.OFFER || request.kind() == CallSignalKind.ANSWER)
                && !StringUtils.hasText(request.sdp())) {
            throw new BusinessException(CallErrorStatus.CALL_INVALID_SIGNALING_PAYLOAD);
        }

        if (request.kind() == CallSignalKind.ICE) {
            CallSocketDTO.IceCandidateDTO candidate = request.candidate();
            if (candidate == null || !StringUtils.hasText(candidate.candidate())) {
                throw new BusinessException(CallErrorStatus.CALL_INVALID_SIGNALING_PAYLOAD);
            }
        }
    }

    /** Extracts participant IDs for post-commit realtime terminal notification. */
    private List<Long> extractParticipantIds(Call call) {
        return call.getParticipants().stream()
                .map(MemberCall::getMemberId)
                .toList();
    }
}
