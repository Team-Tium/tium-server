package com.studiorent.tium.domain.chat.dto;

import com.studiorent.tium.domain.chat.entity.enums.MessageType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

public class ChatResponseDTO {

    /**
     * 채팅 상대 정보. 방 생성·목록·내역 세 API가 같은 구조를 쓴다.
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


    /** 메시지 전송 결과. 방금 저장된 메시지 한 건을 그대로 돌려준다. */
    public record SendMessageResultDTO(
            @Schema(description = "생성된 메시지 ID", example = "986")
            Long messageId,

            @Schema(description = "채팅방 ID", example = "101")
            Long roomId,

            @Schema(description = "보낸 사람 ID", example = "8")
            Long senderId,

            MessageType type,

            String content,

            LocalDateTime sentAt
    ) {
    }

    /**
     * 읽음 처리 결과. 요청값이 아니라 실제로 저장된 포인터를 담는다.
     * 작은 ID가 와서 무시됐으면 기존 포인터와 그 시각이 그대로 나간다.
     */
    public record ReadMessageResultDTO(
            @Schema(description = "채팅방 ID", example = "101")
            Long roomId,

            @Schema(description = "처리된 마지막 읽음 메시지 ID", example = "986")
            Long lastReadMessageId,

            @Schema(description = "읽음 처리 시각")
            LocalDateTime readAt
    ) {
    }

    /** 채팅방 나가기 결과. */
    public record LeaveRoomResultDTO(
            @Schema(description = "채팅방 ID", example = "101")
            Long roomId,

            @Schema(description = "나간 시각")
            LocalDateTime leftAt
    ) {
    }

    /**
     * Get /chats 조회 결과
     * @param rooms
     * @param hasNext
     * @param nextCursor
     */
    public record GetChatDTO(
            List<ChatRoomDTO> rooms,
            Boolean hasNext,
            Long nextCursor
    ){
    }

    public record ChatRoomDTO(
            Long roomId,
            Boolean opponentLeft,
            OpponentDTO opponent,
            LastMessageDTO lastMessage,
            Long unreadCount
    ){
    }

    public record LastMessageDTO(
            Long messageId,
            String content,
            MessageType type,
            LocalDateTime sentAt

    ){
    }

    /** 채팅 내역 한 메시지. */
    public record MessageDTO(
            @Schema(description = "메시지 ID", example = "985")
            Long messageId,

            @Schema(description = "보낸 사람 ID. SYSTEM 타입은 null", example = "15")
            Long senderId,

            MessageType type,

            @Schema(description = "메시지 내용. IMAGE면 이미지 URL")
            String content,

            LocalDateTime sentAt,

            @Schema(description = "상대가 읽었는지. 내가 보낸 메시지에만 의미가 있다", example = "true")
            Boolean isRead
    ){
    }

    /**
     * 채팅 내역 조회 결과.
     *
     * <p>방 화면이 한 번에 필요한 걸 모두 담는다. 목록에서 넘어오지 않고 URL로 바로 들어온 경우를
     * 커버하려고 상대 정보까지 함께 내려준다.
     */
    public record GetMessagesDTO(
            Long roomId,
            OpponentDTO opponent,
            Boolean opponentLeft,
            List<MessageDTO> messages,
            Boolean hasNext,
            Long nextCursor
    ){
    }

    /** 현재 대화 흐름에서 바로 보낼 수 있는 답장 추천 목록. */
    public record SuggestRepliesDTO(
            List<String> suggestions
    ) {
    }
}
