package com.studiorent.tium.domain.call.socket;

import com.studiorent.tium.domain.call.dto.CallSocketDTO;
import com.studiorent.tium.domain.call.entity.Call;
import com.studiorent.tium.domain.call.entity.MemberCall;
import com.studiorent.tium.domain.call.exception.CallErrorStatus;
import com.studiorent.tium.domain.call.repository.CallRepository;
import com.studiorent.tium.domain.call.socket.event.CallEndedEvent;
import com.studiorent.tium.domain.call.socket.event.CallStartedEvent;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.socket.SocketDestinations;
import com.studiorent.tium.global.socket.SocketEvent;
import com.studiorent.tium.global.socket.SocketEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class CallSocketEventListener {

    private final CallRepository callRepository;
    private final SocketEventPublisher socketEventPublisher;

    /** Publishes incoming-call notifications to invited participants after call creation commits. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishIncomingCall(CallStartedEvent event) {
        Call call = callRepository.findWithParticipantsById(event.callId())
                .orElseThrow(() -> new BusinessException(CallErrorStatus.CALL_NOT_FOUND));

        SocketEvent<CallSocketDTO.IncomingCallDTO> socketEvent = new SocketEvent<>(
                CallSocketEventType.CALL_INCOMING,
                new CallSocketDTO.IncomingCallDTO(call.getId(), call.getType(), event.callerMemberId())
        );

        call.getParticipants().stream()
                .map(MemberCall::getMemberId)
                .filter(memberId -> !memberId.equals(event.callerMemberId()))
                .forEach(memberId -> socketEventPublisher.toMember(memberId, socketEvent));
    }

    /** Publishes call-ended notifications after completion persistence commits. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishCallEnded(CallEndedEvent event) {
        SocketEvent<CallSocketDTO.CallEndedDTO> socketEvent = new SocketEvent<>(
                CallSocketEventType.CALL_ENDED,
                new CallSocketDTO.CallEndedDTO(
                        event.callId(),
                        event.type(),
                        event.endedByMemberId(),
                        event.endAt()
                )
        );

        socketEventPublisher.toDestination(
                SocketDestinations.CALL_SUB_PREFIX + "/" + event.callId(),
                socketEvent
        );
        event.participantIds().stream()
                .filter(memberId -> !memberId.equals(event.endedByMemberId()))
                .forEach(memberId -> socketEventPublisher.toMember(memberId, socketEvent));
    }
}
