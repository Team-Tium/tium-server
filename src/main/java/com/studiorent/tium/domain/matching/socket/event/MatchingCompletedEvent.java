package com.studiorent.tium.domain.matching.socket.event;

public record MatchingCompletedEvent(
        Long roomId,
        Long memberId,
        Long opponentId) {
}
