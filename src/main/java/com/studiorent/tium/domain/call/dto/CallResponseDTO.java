package com.studiorent.tium.domain.call.dto;

import com.studiorent.tium.domain.call.entity.enums.CallStatus;
import com.studiorent.tium.domain.call.entity.enums.CallType;

import java.time.OffsetDateTime;
import java.util.List;

public class CallResponseDTO {

    public record CallResultDTO(
            Long callId,
            CallType type,
            CallStatus status,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            List<Long> participantIds
    ) {
    }
}
