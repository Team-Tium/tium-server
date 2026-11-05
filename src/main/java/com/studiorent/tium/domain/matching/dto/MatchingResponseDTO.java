package com.studiorent.tium.domain.matching.dto;

import com.studiorent.tium.domain.chat.dto.ChatResponseDTO;
import io.swagger.v3.oas.annotations.media.Schema;

public class MatchingResponseDTO {

    /** 매칭 대기 결과. 바로 짝이 정해졌으면 방 ID와 상대를 담고, 대기열에 들어갔으면 둘 다 null이다. */
    public record EnqueueResultDTO(
            @Schema(description = "true면 바로 짝이 정해졌다. false면 대기열에 들어갔고 결과는 MATCHED 소켓 이벤트로 온다",
                    example = "true")
            Boolean matched,

            @Schema(description = "채팅방 ID. 대기열에 들어갔으면 null", example = "101")
            Long roomId,

            @Schema(description = "매칭된 상대. 대기열에 들어갔으면 null")
            ChatResponseDTO.OpponentDTO opponent
    ) {
    }
}
