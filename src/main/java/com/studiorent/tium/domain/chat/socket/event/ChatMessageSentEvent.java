package com.studiorent.tium.domain.chat.socket.event;

import com.studiorent.tium.domain.chat.entity.enums.MessageType;

import java.time.LocalDateTime;

public record ChatMessageSentEvent(
        Long messageId,
        MessageType type,
        String content,
        LocalDateTime sentAt,
        Long roomId,
        Long opponentId,
        Long senderId,
        Long opponentLastReadMessageId) {
}
