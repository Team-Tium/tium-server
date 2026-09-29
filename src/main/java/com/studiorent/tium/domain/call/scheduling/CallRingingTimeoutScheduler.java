package com.studiorent.tium.domain.call.scheduling;

import com.studiorent.tium.domain.call.service.command.CallRingingTimeoutCommandService;
import com.studiorent.tium.domain.call.socket.event.CallStartedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class CallRingingTimeoutScheduler {

    private static final Duration RINGING_TIMEOUT = Duration.ofSeconds(30);

    private final CallTaskScheduler scheduler;
    private final CallRingingTimeoutCommandService callRingingTimeoutCommandService;

    /** Schedules expiration only after creation commits, independently of incoming notification delivery. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void scheduleRingingTimeout(CallStartedEvent event) {
        scheduler.schedule(
                () -> callRingingTimeoutCommandService.cancelIfStillUnaccepted(event.callId()),
                Instant.now().plus(RINGING_TIMEOUT)
        );
    }
}
