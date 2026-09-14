package com.studiorent.tium.domain.call.dto;

import com.studiorent.tium.domain.call.entity.enums.CallType;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public class CallRequestDTO {

    public record CallStartDTO(
            @NotNull(message = "type is required")
            CallType type,

            Set<@NotNull(message = "participantId is required") Long> participantIds
    ) {
    }
}
