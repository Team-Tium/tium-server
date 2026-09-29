package com.studiorent.tium.domain.call.socket;

import com.studiorent.tium.domain.call.converter.CallConverter;
import com.studiorent.tium.domain.call.dto.CallSocketDTO;
import com.studiorent.tium.domain.call.entity.Call;
import com.studiorent.tium.domain.call.exception.CallErrorStatus;
import com.studiorent.tium.domain.call.repository.CallRepository;
import com.studiorent.tium.domain.call.socket.event.CallAcceptedEvent;
import com.studiorent.tium.domain.call.socket.event.CallEndedEvent;
import com.studiorent.tium.domain.call.socket.event.CallStartedEvent;
import com.studiorent.tium.domain.member.repository.MemberRepository;
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
    private final MemberRepository memberRepository;
    private final SocketEventPublisher socketEventPublisher;

    /** Publishes the incoming-call notification to the receiver after call creation commits. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishIncomingCall(CallStartedEvent event) {
        Call call = callRepository.findWithParticipantsById(event.callId())
                .orElseThrow(() -> new BusinessException(CallErrorStatus.CALL_NOT_FOUND));
        String callerNickname = memberRepository.findById(event.callerMemberId())
                .map(member -> member.getName())
                .orElse(null);

        SocketEvent<CallSocketDTO.IncomingCallDTO> socketEvent = new SocketEvent<>(
                CallSocketEventType.CALL_INCOMING,
                new CallSocketDTO.IncomingCallDTO(
                        call.getId(),
                        call.getType(),
                        new CallSocketDTO.CallerDTO(event.callerMemberId(), callerNickname),
                        call.getStartAt()
                )
        );

        socketEventPublisher.toMember(event.receiverMemberId(), socketEvent);
    }

    /** Publishes receiver acceptance to the call topic and receiver personal devices after commit. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishCallAccepted(CallAcceptedEvent event) {
        SocketEvent<CallSocketDTO.AcceptedDTO> socketEvent = new SocketEvent<>(
                CallSocketEventType.CALL_ACCEPTED,
                new CallSocketDTO.AcceptedDTO(
                        event.callId(),
                        CallConverter.toServiceOffsetDateTime(event.acceptedAt()))
        );

        socketEventPublisher.toDestination(
                SocketDestinations.CALL_SUB_PREFIX + "/" + event.callId(),
                socketEvent
        );
        socketEventPublisher.toMember(event.receiverMemberId(), socketEvent);
    }

    /** Publishes call-ended notifications after terminal persistence commits. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishCallEnded(CallEndedEvent event) {
        SocketEvent<CallSocketDTO.CallEndedDTO> socketEvent = new SocketEvent<>(
                CallSocketEventType.CALL_ENDED,
                new CallSocketDTO.CallEndedDTO(
                        event.callId(),
                        event.endedByMemberId(),
                        event.reason(),
                        CallConverter.toServiceOffsetDateTime(event.endAt())
                )
        );

        socketEventPublisher.toDestination(
                SocketDestinations.CALL_SUB_PREFIX + "/" + event.callId(),
                socketEvent
        );
        event.participantIds().stream()
                .forEach(memberId -> socketEventPublisher.toMember(memberId, socketEvent));
    }
}
