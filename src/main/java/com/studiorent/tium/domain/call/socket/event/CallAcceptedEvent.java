package com.studiorent.tium.domain.call.socket.event;

import java.time.LocalDateTime;

public record CallAcceptedEvent(
        Long callId,
        Long receiverMemberId,
        LocalDateTime acceptedAt
) {
}
