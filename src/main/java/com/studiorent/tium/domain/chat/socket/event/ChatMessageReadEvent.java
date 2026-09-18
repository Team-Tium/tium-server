package com.studiorent.tium.domain.chat.socket.event;

public record ChatMessageReadEvent(
        Long roomId,
        Long readerId,
        Long lastReadMessageId) {
}
