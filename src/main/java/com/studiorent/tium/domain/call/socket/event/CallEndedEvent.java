package com.studiorent.tium.domain.call.socket.event;

import com.studiorent.tium.domain.call.entity.enums.CallEndedReason;
import com.studiorent.tium.domain.call.entity.enums.CallType;

import java.time.LocalDateTime;
import java.util.List;

public record CallEndedEvent(
        Long callId,
        Long endedByMemberId,
        CallType type,
        CallEndedReason reason,
        LocalDateTime endAt,
        List<Long> participantIds
) {
}
