package com.studiorent.tium.domain.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public class ChatRequestDTO {

    /** 채팅방 생성 요청. 1:1 전용이라 상대는 항상 한 명이다. */
    public record CreateRoomDTO(
            @NotNull(message = "상대 회원 ID는 필수입니다.")
            @Schema(description = "대화할 상대 회원 ID", example = "15")
            Long opponentId
    ) {
    }
}
