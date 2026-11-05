package com.studiorent.tium.domain.matching.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public class MatchingRequestDTO {

    /**
     * 매칭 대기 요청. 현재는 CHAT만 받는다.
     *
     * <p>타입을 enum이 아니라 문자열로 받고 서비스에서 검사한다. 타입이 비었거나 CHAT이 아니면
     * {@code MATCHING4001}로 내려가야 하는데, enum으로 받으면 모르는 값이 역직렬화에서 실패해
     * {@code COMMON400}이 먼저 나간다. {@code @NotNull}을 걸지 않는 것도 같은 이유다.
     */
    public record EnqueueDTO(
            @Schema(description = "매칭 종류. 현재는 CHAT만 허용한다", example = "CHAT")
            String type
    ) {
    }
}
