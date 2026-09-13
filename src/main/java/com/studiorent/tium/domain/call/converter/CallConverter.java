package com.studiorent.tium.domain.call.converter;

import com.studiorent.tium.domain.call.dto.CallResponseDTO;
import com.studiorent.tium.domain.call.entity.Call;
import com.studiorent.tium.domain.call.entity.MemberCall;

import java.util.Comparator;
import java.util.List;

public class CallConverter {

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
                call.getStartAt(),
                call.getEndAt(),
                participantIds
        );
    }
}
