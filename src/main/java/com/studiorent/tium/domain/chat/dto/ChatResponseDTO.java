package com.studiorent.tium.domain.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public class ChatResponseDTO {

    /**
     * 채팅 상대 정보. 방 생성·목록·내역 세 API가 같은 구조를 쓴다(명세 3번 API에 명시).
     *
     * <p>{@code nickname}은 별도 컬럼이 아니라 {@code member.name}이다(확정, 2026-09-10).
     * {@code profileImageUrl}은 아직 값을 채우는 API가 없어 항상 null이다.
     */
    public record OpponentDTO(
            @Schema(description = "상대방 회원 ID", example = "15")
            Long userId,

            @Schema(description = "상대방 닉네임. member.name을 쓴다", example = "김티움")
            String nickname,

            @Schema(description = "상대방 프로필 이미지 URL. 업로드 경로 미정이라 현재는 항상 null")
            String profileImageUrl
    ) {
    }

    /** 채팅방 생성 결과. */
    public record CreateRoomResultDTO(
            @Schema(description = "채팅방 ID", example = "101")
            Long roomId,

            @Schema(description = "true면 새로 만든 방, false면 기존 활성 방을 그대로 반환", example = "true")
            Boolean created,

            OpponentDTO opponent
    ) {
    }
}
