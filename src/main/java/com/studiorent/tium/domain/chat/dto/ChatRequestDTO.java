package com.studiorent.tium.domain.chat.dto;

import com.studiorent.tium.domain.chat.entity.enums.MessageType;
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

    /**
     * 메시지 전송 요청. 이번 범위는 TEXT만 받는다. IMAGE는 2차다.
     *
     * <p>두 필드 모두 bean validation을 걸지 않고 서비스에서 검사한다.
     * 빈 내용은 {@code CHAT4001}, 타입 위반은 전용 코드로 내려가야 하는데
     * {@code @NotBlank}로 막으면 {@code COMMON400}이 먼저 나가버리기 때문이다.
     */
    public record SendMessageDTO(
            @Schema(description = "메시지 타입. 현재는 TEXT만 허용한다", example = "TEXT")
            MessageType type,

            @Schema(description = "메시지 내용", example = "저도 오늘 즐거웠어요!")
            String content
    ) {
    }
}
