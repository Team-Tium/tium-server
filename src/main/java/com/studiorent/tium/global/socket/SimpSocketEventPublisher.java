package com.studiorent.tium.global.socket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SimpSocketEventPublisher implements SocketEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void toDestination(String destination, SocketEvent<?> event) {
        // Domain-specific destinations append paths to shared prefixes; this method accepts the full destination.
        messagingTemplate.convertAndSend(destination, event);
    }

    @Override
    public void toMember(Long memberId, SocketEvent<?> event) {
        toDestination(SocketDestinations.MEMBER_SUB_PREFIX + "/" + memberId, event);
    }
}
