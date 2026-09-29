package com.studiorent.tium.domain.call.socket.event;

public record CallStartedEvent(
        Long callId,
        Long callerMemberId,
        Long receiverMemberId
) {
}
