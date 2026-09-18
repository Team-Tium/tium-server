package com.studiorent.tium.domain.call.service.command;

import com.studiorent.tium.domain.call.dto.CallSocketDTO;
import com.studiorent.tium.domain.call.entity.Call;
import com.studiorent.tium.domain.call.exception.CallErrorStatus;
import com.studiorent.tium.domain.call.service.CallAccessValidator;
import com.studiorent.tium.domain.call.socket.CallSocketEventType;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.socket.SocketDestinations;
import com.studiorent.tium.global.socket.SocketEvent;
import com.studiorent.tium.global.socket.SocketEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CallSignalingServiceImpl implements CallSignalingService {

    private final CallAccessValidator callAccessValidator;
    private final CallCommandService callCommandService;
    private final SocketEventPublisher socketEventPublisher;

    /*
    TODO(backlog): Accept/reject signaling is intentionally disabled because the current
    call UI does not require a separate accept/reject step.

    /** Publishes a transient call acceptance event after participant validation. *\/
    @Override
    public void accept(Long memberId, Long callId) {
        Call call = callAccessValidator.validateParticipant(callId, memberId);
        publishToCall(callId, CallSocketEventType.CALL_ACCEPTED, new CallSocketDTO.CallControlDTO(
                callId,
                call.getType(),
                memberId
        ));
    }

    /** Publishes a transient call rejection event after participant validation. *\/
    @Override
    public void reject(Long memberId, Long callId) {
        Call call = callAccessValidator.validateParticipant(callId, memberId);
        publishToCall(callId, CallSocketEventType.CALL_REJECTED, new CallSocketDTO.CallControlDTO(
                callId,
                call.getType(),
                memberId
        ));
    }
    */

    /** Relays an SDP offer to call participants without interpreting or persisting SDP. */
    @Override
    public void relayOffer(Long memberId, Long callId, CallSocketDTO.SdpDTO request) {
        validateSdp(request);
        Call call = callAccessValidator.validateActiveParticipant(callId, memberId);
        publishToCall(callId, CallSocketEventType.CALL_SDP_OFFER, new CallSocketDTO.SdpSignalDTO(
                callId,
                call.getType(),
                memberId,
                request.sdp()
        ));
    }

    /** Relays an SDP answer to call participants without interpreting or persisting SDP. */
    @Override
    public void relayAnswer(Long memberId, Long callId, CallSocketDTO.SdpDTO request) {
        validateSdp(request);
        Call call = callAccessValidator.validateActiveParticipant(callId, memberId);
        publishToCall(callId, CallSocketEventType.CALL_SDP_ANSWER, new CallSocketDTO.SdpSignalDTO(
                callId,
                call.getType(),
                memberId,
                request.sdp()
        ));
    }

    /** Relays an ICE candidate to call participants without persistence. */
    @Override
    public void relayIceCandidate(Long memberId, Long callId, CallSocketDTO.IceCandidateDTO request) {
        validateIceCandidate(request);
        Call call = callAccessValidator.validateActiveParticipant(callId, memberId);
        publishToCall(callId, CallSocketEventType.CALL_ICE_CANDIDATE, new CallSocketDTO.IceCandidateSignalDTO(
                callId,
                call.getType(),
                memberId,
                request.candidate(),
                request.sdpMid(),
                request.sdpMLineIndex()
        ));
    }

    /** Completes the call through the existing lifecycle service. */
    @Override
    public void end(Long memberId, Long callId) {
        callCommandService.endCall(memberId, callId);
    }

    /** Validates that an SDP signaling payload contains a required SDP value. */
    private void validateSdp(CallSocketDTO.SdpDTO request) {
        if (request == null || !StringUtils.hasText(request.sdp())) {
            throw new BusinessException(CallErrorStatus.CALL_INVALID_SIGNALING_PAYLOAD);
        }
    }

    /** Validates that an ICE signaling payload contains the required candidate value. */
    private void validateIceCandidate(CallSocketDTO.IceCandidateDTO request) {
        if (request == null || !StringUtils.hasText(request.candidate())) {
            throw new BusinessException(CallErrorStatus.CALL_INVALID_SIGNALING_PAYLOAD);
        }
    }

    /** Publishes a call-scoped event using the shared call subscription prefix. */
    private void publishToCall(Long callId, String type, Object payload) {
        socketEventPublisher.toDestination(
                SocketDestinations.CALL_SUB_PREFIX + "/" + callId,
                new SocketEvent<>(type, payload)
        );
    }
}
