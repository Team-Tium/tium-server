package com.studiorent.tium.domain.call.service.command;

import com.studiorent.tium.domain.call.entity.Call;
import com.studiorent.tium.domain.call.entity.MemberCall;
import com.studiorent.tium.domain.call.entity.enums.CallEndedReason;
import com.studiorent.tium.domain.call.repository.CallRepository;
import com.studiorent.tium.domain.call.socket.event.CallEndedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CallDisconnectCommandService {

    private final CallRepository callRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    /** Marks an active call disconnected after the socket grace period expires. */
    @Transactional
    public void disconnectIfStillActive(Long callId, Long disconnectedMemberId, LocalDateTime disconnectedAt) {
        callRepository.findWithLockById(callId)
                .filter(Call::isInProgress)
                .filter(call -> call.hasParticipant(disconnectedMemberId))
                .ifPresent(call -> {
                    call.disconnect(disconnectedAt);
                    applicationEventPublisher.publishEvent(new CallEndedEvent(
                            call.getId(),
                            disconnectedMemberId,
                            call.getType(),
                            CallEndedReason.DISCONNECTED,
                            call.getEndAt(),
                            call.getParticipants().stream()
                                    .map(MemberCall::getMemberId)
                                    .toList()
                    ));
                });
    }
}
