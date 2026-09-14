package com.studiorent.tium.domain.chat.converter;

import com.studiorent.tium.domain.chat.dto.ChatResponseDTO;
import com.studiorent.tium.domain.chat.entity.ChatRoom;
import com.studiorent.tium.domain.member.entity.Member;

public class ChatConverter {

    private ChatConverter() {
    }

    /** 명세의 nickname은 별도 컬럼이 아니라 member.name이다. */
    public static ChatResponseDTO.OpponentDTO toOpponent(Member opponent) {
        return new ChatResponseDTO.OpponentDTO(
                opponent.getId(),
                opponent.getName(),
                opponent.getProfileImageUrl()
        );
    }

    public static ChatResponseDTO.CreateRoomResultDTO toCreateRoomResult(ChatRoom chatRoom,
                                                                         boolean created,
                                                                         Member opponent) {
        return new ChatResponseDTO.CreateRoomResultDTO(
                chatRoom.getId(),
                created,
                toOpponent(opponent)
        );
    }
}
