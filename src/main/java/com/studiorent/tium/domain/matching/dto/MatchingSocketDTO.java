package com.studiorent.tium.domain.matching.dto;

import com.studiorent.tium.domain.chat.dto.ChatResponseDTO;

public class MatchingSocketDTO {

    /** 매칭이 성사됐을 때 두 사람에게 각각 보낸다. opponent는 받는 사람 기준의 상대다. */
    public record MatchedDTO(
            Long roomId,
            ChatResponseDTO.OpponentDTO opponent
    ) {
    }
}
