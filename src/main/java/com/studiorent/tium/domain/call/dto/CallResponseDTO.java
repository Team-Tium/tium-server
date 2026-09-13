package com.studiorent.tium.domain.call.dto;

import com.studiorent.tium.domain.call.entity.enums.CallStatus;
import com.studiorent.tium.domain.call.entity.enums.CallType;

import java.time.LocalDateTime;
import java.util.List;

public class CallResponseDTO {

    public record CallResultDTO(
            Long callId,
            CallType type,
            CallStatus status,
            LocalDateTime startAt,
            LocalDateTime endAt,
            List<Long> participantIds
    ) {
    }
}
