package com.studiorent.tium.domain.chat.converter;

import com.studiorent.tium.domain.chat.dto.ChatResponseDTO;
import com.studiorent.tium.domain.chat.entity.ChatRoom;
import com.studiorent.tium.domain.chat.entity.enums.MessageType;
import com.studiorent.tium.domain.chat.entity.ChatMessage;
import com.studiorent.tium.domain.chat.repository.projection.ChatOpponentProjection;
import com.studiorent.tium.domain.chat.repository.projection.ChatRoomListProjection;
import com.studiorent.tium.domain.member.entity.Member;

import java.util.List;

public class ChatConverter {

    private ChatConverter() {
    }

    /** 응답의 nickname은 별도 컬럼이 아니라 member.name을 쓴다. */
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

    /** 방금 저장한 메시지를 전송 응답으로 옮긴다. 읽음 여부는 담지 않는다. */
    public static ChatResponseDTO.SendMessageResultDTO toSendMessageResult(ChatMessage message) {
        return new ChatResponseDTO.SendMessageResultDTO(
                message.getId(),
                message.getChatRoom().getId(),
                message.getSenderId(),
                message.getMessageType(),
                message.getContent(),
                message.getSentAt()
        );
    }

    /**
     * 목록 조회 쿼리 한 줄을 응답 한 칸으로 옮긴다.
     *
     * <p>메시지 타입이 String으로 넘어오는 것은 네이티브 쿼리라 DB에서 VARCHAR로 나오기 때문이다.
     * 여기서 enum으로 되돌린다.
     */
    public static ChatResponseDTO.ChatRoomDTO toChatRoom(ChatRoomListProjection row) {
        return new ChatResponseDTO.ChatRoomDTO(
                row.getRoomId(),
                row.getOpponentLeftAt() != null,
                new ChatResponseDTO.OpponentDTO(
                        row.getOpponentId(),
                        row.getOpponentNickname(),
                        row.getOpponentProfileImageUrl()
                ),
                new ChatResponseDTO.LastMessageDTO(
                        row.getLastMessageId(),
                        row.getLastMessageContent(),
                        MessageType.valueOf(row.getLastMessageType()),
                        row.getLastMessageSentAt()
                ),
                row.getUnreadCount()
        );
    }

    public static ChatResponseDTO.OpponentDTO toOpponent(ChatOpponentProjection row) {
        return new ChatResponseDTO.OpponentDTO(
                row.getOpponentId(),
                row.getOpponentNickname(),
                row.getOpponentProfileImageUrl()
        );
    }

    /**
     * 메시지 한 건.
     *
     * <p>isRead는 저장된 값이 아니라 상대의 읽음 포인터와 비교한 결과다.
     * 상대가 한 번도 안 읽었으면 포인터가 null이라 전부 false가 된다.
     */
    public static ChatResponseDTO.MessageDTO toMessage(ChatMessage message,
                                                       Long opponentLastReadMessageId) {
        boolean isRead = opponentLastReadMessageId != null
                && opponentLastReadMessageId >= message.getId();

        return new ChatResponseDTO.MessageDTO(
                message.getId(),
                message.getSenderId(),
                message.getMessageType(),
                message.getContent(),
                message.getSentAt(),
                isRead
        );
    }

    public static ChatResponseDTO.GetMessagesDTO toGetMessages(Long roomId,
                                                               ChatOpponentProjection opponent,
                                                               List<ChatResponseDTO.MessageDTO> messages,
                                                               boolean hasNext,
                                                               Long nextCursor) {
        return new ChatResponseDTO.GetMessagesDTO(
                roomId,
                toOpponent(opponent),
                opponent.getOpponentLeftAt() != null,
                messages,
                hasNext,
                nextCursor
        );
    }
}
