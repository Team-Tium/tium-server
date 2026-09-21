package com.studiorent.tium.domain.chat.dto;

import com.studiorent.tium.domain.chat.entity.enums.MessageType;

import java.time.LocalDateTime;

public class ChatSocketDTO {

    /** 방 화면에 새 말풍선을 추가할 때 쓴다. */
    public record MessageCreatedDTO(
            Long messageId,
            Long roomId,
            Long senderId,
            MessageType type,
            String content,
            LocalDateTime sentAt
    ) {
    }

    /** 채팅 목록의 한 줄을 갱신할 때 쓴다. unreadCount는 받는 사람마다 다르다. */
    public record RoomUpdatedDTO(
            Long roomId,
            LastMessageDTO lastMessage,
            Long unreadCount
    ) {
    }

    public record LastMessageDTO(
            Long messageId,
            String content,
            LocalDateTime sentAt
    ) {
    }

    /** 상대 화면에서 lastReadMessageId 이하 메시지의 안 읽음 표시를 지울 때 쓴다. */
    public record MessageReadDTO(
            Long roomId,
            Long readerId,
            Long lastReadMessageId
    ) {
    }

    /** 방 입력창을 잠그고 목록의 방을 나감 상태로 바꿀 때 쓴다. */
    public record MemberLeftDTO(
            Long roomId,
            Long leftMemberId
    ) {
    }
}
