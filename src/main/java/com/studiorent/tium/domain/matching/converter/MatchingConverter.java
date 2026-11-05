package com.studiorent.tium.domain.matching.converter;

import com.studiorent.tium.domain.chat.dto.ChatResponseDTO;
import com.studiorent.tium.domain.matching.dto.MatchingResponseDTO;

public class MatchingConverter {

    private MatchingConverter() {
    }

    public static MatchingResponseDTO.EnqueueResultDTO toMatched(ChatResponseDTO.CreateRoomResultDTO room) {
        return new MatchingResponseDTO.EnqueueResultDTO(true, room.roomId(), room.opponent());
    }

    public static MatchingResponseDTO.EnqueueResultDTO toWaiting() {
        return new MatchingResponseDTO.EnqueueResultDTO(false, null, null);
    }
}
