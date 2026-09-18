package com.studiorent.tium.domain.call.socket.event;

import com.studiorent.tium.domain.call.entity.enums.CallType;

import java.time.LocalDateTime;
import java.util.List;

public record CallEndedEvent(
        Long callId,
        Long endedByMemberId,
        CallType type,
        LocalDateTime endAt,
        List<Long> participantIds
) {
}
