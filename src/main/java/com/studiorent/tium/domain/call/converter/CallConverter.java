package com.studiorent.tium.domain.call.converter;

import com.studiorent.tium.domain.call.dto.CallResponseDTO;
import com.studiorent.tium.domain.call.entity.Call;
import com.studiorent.tium.domain.call.entity.MemberCall;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

public class CallConverter {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    public static CallResponseDTO.CallResultDTO toCallResult(Call call) {
        List<Long> participantIds = call.getParticipants()
                .stream()
                .map(MemberCall::getMemberId)
                .sorted(Comparator.naturalOrder())
                .toList();

        return new CallResponseDTO.CallResultDTO(
                call.getId(),
                call.getType(),
                call.getStatus(),
                toServiceOffsetDateTime(call.getStartAt()),
                toServiceOffsetDateTime(call.getEndAt()),
                participantIds
        );
    }

    public static OffsetDateTime toServiceOffsetDateTime(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return localDateTime.atZone(SERVICE_ZONE).toOffsetDateTime();
    }
}
