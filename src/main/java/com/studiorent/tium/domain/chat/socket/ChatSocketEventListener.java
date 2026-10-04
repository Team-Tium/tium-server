package com.studiorent.tium.domain.chat.socket;

import com.studiorent.tium.domain.chat.dto.ChatResponseDTO;
import com.studiorent.tium.domain.chat.dto.ChatSocketDTO;
import com.studiorent.tium.domain.chat.repository.ChatMessageRepository;
import com.studiorent.tium.domain.chat.socket.event.ChatMemberLeftEvent;
import com.studiorent.tium.domain.chat.socket.event.ChatMessageReadEvent;
import com.studiorent.tium.domain.chat.socket.event.ChatMessageSentEvent;
import com.studiorent.tium.global.socket.SocketDestinations;
import com.studiorent.tium.global.socket.SocketEvent;
import com.studiorent.tium.global.socket.SocketEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.studiorent.tium.domain.chat.socket.ChatSocketEventType.MEMBER_LEFT;
import static com.studiorent.tium.domain.chat.socket.ChatSocketEventType.MESSAGE_CREATED;
import static com.studiorent.tium.domain.chat.socket.ChatSocketEventType.MESSAGE_READ;
import static com.studiorent.tium.domain.chat.socket.ChatSocketEventType.ROOM_UPDATED;

@Component
@RequiredArgsConstructor
public class ChatSocketEventListener {

    private final SocketEventPublisher socketEventPublisher;
    private final ChatMessageRepository chatMessageRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishMessageSent(ChatMessageSentEvent event) {

        ChatSocketDTO.MessageCreatedDTO newMessage = new ChatSocketDTO.MessageCreatedDTO(
                event.messageId(),
                event.roomId(),
                event.senderId(),
                event.type(),
                event.content(),
                event.sentAt()
        );
        SocketEvent<ChatSocketDTO.MessageCreatedDTO> socketEvent =new SocketEvent<>(MESSAGE_CREATED, newMessage);

        socketEventPublisher.toDestination(roomDestination(event.roomId()), socketEvent);


        ChatResponseDTO.LastMessageDTO last = new ChatResponseDTO.LastMessageDTO(
                event.messageId(),
                event.content(),
                event.type(),
                event.sentAt()
        );

        // 보낸 사람은 전송과 함께 읽음 처리돼 안 읽은 수가 0이다.
        socketEventPublisher.toMember(
                event.senderId(), roomUpdated(event.roomId(), last, 0L)
        );

        // 상대는 한 번도 읽지 않았으면 책갈피가 null이라 0부터 센다.
        Long opponentLastRead = event.opponentLastReadMessageId() == null ? 0L : event.opponentLastReadMessageId();
        long opponentUnreadCount = chatMessageRepository.countByChatRoomIdAndSenderIdAndIdGreaterThan(
                event.roomId(), event.senderId(), opponentLastRead);

        socketEventPublisher.toMember(
                event.opponentId(), roomUpdated(event.roomId(), last, opponentUnreadCount)
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishMessageRead(ChatMessageReadEvent event) {
        ChatSocketDTO.MessageReadDTO read = new ChatSocketDTO.MessageReadDTO(
                event.roomId(),
                event.readerId(),
                event.lastReadMessageId()
        );

        socketEventPublisher.toDestination(roomDestination(event.roomId()), new SocketEvent<>(MESSAGE_READ, read));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishMemberLeft(ChatMemberLeftEvent event) {
        SocketEvent<ChatSocketDTO.MemberLeftDTO> left = new SocketEvent<>(
                MEMBER_LEFT, new ChatSocketDTO.MemberLeftDTO(event.roomId(), event.leftMemberId()));

        // 방 화면을 보고 있지 않은 상대도 목록에서 바로 알 수 있게 개인 주소로도 보낸다.
        socketEventPublisher.toDestination(roomDestination(event.roomId()), left);
        socketEventPublisher.toMember(event.opponentId(), left);
    }

    private String roomDestination(Long roomId) {
        return SocketDestinations.CHAT_SUB_PREFIX + "/rooms/" + roomId;
    }

    private SocketEvent<ChatSocketDTO.RoomUpdatedDTO> roomUpdated(Long roomId,
                                                                   ChatResponseDTO.LastMessageDTO last,
                                                                   Long unreadCount) {
        return new SocketEvent<>(ROOM_UPDATED, new ChatSocketDTO.RoomUpdatedDTO(roomId, last, unreadCount));
    }
}
