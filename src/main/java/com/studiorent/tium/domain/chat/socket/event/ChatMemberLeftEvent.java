package com.studiorent.tium.domain.chat.socket.event;

public record ChatMemberLeftEvent(
        Long roomId,
        Long leftMemberId,
        Long opponentId) {
}
