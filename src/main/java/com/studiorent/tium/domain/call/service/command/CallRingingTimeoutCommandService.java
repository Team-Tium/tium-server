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
public class CallRingingTimeoutCommandService {

    private final CallRepository callRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    /** Reloads and locks the call, canceling only if it is still active and unaccepted. */
    @Transactional
    public void cancelIfStillUnaccepted(Long callId) {
        callRepository.findWithLockById(callId)
                .filter(Call::isInProgress)
                .filter(call -> !call.isAccepted())
                .ifPresent(call -> {
                    call.cancel(LocalDateTime.now());
                    applicationEventPublisher.publishEvent(new CallEndedEvent(
                            call.getId(),
                            call.getCallerMemberId(),
                            call.getType(),
                            CallEndedReason.CANCELED,
                            call.getEndAt(),
                            call.getParticipants().stream()
                                    .map(MemberCall::getMemberId)
                                    .toList()
                    ));
                });
    }
}
