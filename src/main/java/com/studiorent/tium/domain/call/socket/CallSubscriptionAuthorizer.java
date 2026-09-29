package com.studiorent.tium.domain.call.socket;

import com.studiorent.tium.domain.call.repository.CallRepository;
import com.studiorent.tium.global.socket.SocketDestinations;
import com.studiorent.tium.global.socket.SubscriptionAuthorizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CallSubscriptionAuthorizer implements SubscriptionAuthorizer {

    private final CallRepository callRepository;

    /** Determines whether this authorizer handles the shared call subscription prefix. */
    @Override
    public boolean supports(String destination) {
        return destination != null && destination.startsWith(SocketDestinations.CALL_SUB_PREFIX + "/");
    }

    /** Validates that the subscriber is a participant and the call is still active. */
    @Override
    public boolean canSubscribe(Long memberId, String destination) {
        Long callId = parseCallId(destination);
        if (callId == null) {
            return false;
        }

        return callRepository.findWithParticipantsById(callId)
                .map(call -> call.hasParticipant(memberId) && call.isInProgress())
                .orElse(false);
    }

    /** Parses only exact /sub/call/{callId} destinations. */
    private Long parseCallId(String destination) {
        String prefix = SocketDestinations.CALL_SUB_PREFIX + "/";
        if (destination == null || !destination.startsWith(prefix)) {
            return null;
        }

        String callIdText = destination.substring(prefix.length());
        if (callIdText.isBlank() || callIdText.contains("/")) {
            return null;
        }

        try {
            return Long.valueOf(callIdText);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
