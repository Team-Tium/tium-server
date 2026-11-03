package com.studiorent.tium.domain.matching.dto;

import com.studiorent.tium.domain.matching.entity.enums.MatchingType;
import io.swagger.v3.oas.annotations.media.Schema;

public class MatchingRequestDTO {

    /**
     * 매칭 대기 요청. 현재는 CHAT만 받는다.
     *
     * <p>bean validation을 걸지 않고 서비스에서 검사한다. 타입이 비었거나 CHAT이 아니면
     * {@code MATCHING4001}로 내려가야 하는데 {@code @NotNull}로 막으면 {@code COMMON400}이 먼저 나간다.
     */
    public record EnqueueDTO(
            @Schema(description = "매칭 종류. 현재는 CHAT만 허용한다", example = "CHAT")
            MatchingType type
    ) {
    }
}
