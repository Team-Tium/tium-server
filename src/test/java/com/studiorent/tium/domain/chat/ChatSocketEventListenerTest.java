package com.studiorent.tium.domain.chat;

import com.studiorent.tium.domain.chat.dto.ChatRequestDTO;
import com.studiorent.tium.domain.chat.dto.ChatSocketDTO;
import com.studiorent.tium.domain.chat.entity.enums.MessageType;
import com.studiorent.tium.domain.chat.service.command.ChatCommandService;
import com.studiorent.tium.domain.chat.socket.ChatSocketEventListener;
import com.studiorent.tium.domain.chat.socket.ChatSocketEventType;
import com.studiorent.tium.domain.chat.socket.event.ChatMemberLeftEvent;
import com.studiorent.tium.domain.chat.socket.event.ChatMessageReadEvent;
import com.studiorent.tium.domain.chat.socket.event.ChatMessageSentEvent;
import com.studiorent.tium.domain.member.entity.Member;
import com.studiorent.tium.domain.member.entity.enums.Provider;
import com.studiorent.tium.domain.member.repository.MemberRepository;
import com.studiorent.tium.global.socket.SocketEvent;
import com.studiorent.tium.global.socket.SocketEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 리스너가 어느 주소로 어떤 봉투를 보내는지 본다.
 *
 * <p>테스트 트랜잭션은 롤백돼 커밋 후 리스너가 스스로 돌지 않으므로, 서비스가 남긴 이벤트를 꺼내 리스너에 직접 넘긴다.
 */
@SpringBootTest
@Transactional
@RecordApplicationEvents
class ChatSocketEventListenerTest {

    @Autowired private ChatSocketEventListener listener;
    @Autowired private ChatCommandService chatCommandService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ApplicationEvents events;
    @MockitoBean private SocketEventPublisher socketEventPublisher;

    private Long meId;
    private Long opponentId;
    private Long roomId;

    @BeforeEach
    void 회원_둘과_방을_만든다() {
        meId = 회원을_만든다("me").getId();
        opponentId = 회원을_만든다("opponent").getId();
        roomId = chatCommandService.createRoom(meId, new ChatRequestDTO.CreateRoomDTO(opponentId)).roomId();
    }

    private Member 회원을_만든다(String prefix) {
        return memberRepository.save(Member.builder()
                .provider(Provider.KAKAO)
                .providerId(prefix + "-" + System.nanoTime())
                .name(prefix)
                .build());
    }

    private Long 보낸다(Long senderId) {
        return chatCommandService.sendMessage(senderId, roomId,
                new ChatRequestDTO.SendMessageDTO(MessageType.TEXT, "안녕하세요")).messageId();
    }

    private String 방_주소() {
        return "/sub/chat/rooms/" + roomId;
    }

    @SuppressWarnings("unchecked")
    private ChatSocketDTO.RoomUpdatedDTO 받은_목록_갱신(Long memberId) {
        ArgumentCaptor<SocketEvent<?>> captor = ArgumentCaptor.forClass(SocketEvent.class);
        verify(socketEventPublisher).toMember(eq(memberId), captor.capture());
        SocketEvent<?> event = captor.getValue();
        assertThat(event.type()).isEqualTo(ChatSocketEventType.ROOM_UPDATED);
        return (ChatSocketDTO.RoomUpdatedDTO) event.data();
    }

    @Test
    void 전송하면_방_주소로_새_메시지를_보낸다() {
        Long messageId = 보낸다(meId);

        listener.publishMessageSent(events.stream(ChatMessageSentEvent.class).findFirst().orElseThrow());

        ArgumentCaptor<SocketEvent<?>> captor = ArgumentCaptor.forClass(SocketEvent.class);
        verify(socketEventPublisher).toDestination(eq(방_주소()), captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(ChatSocketEventType.MESSAGE_CREATED);
        ChatSocketDTO.MessageCreatedDTO data = (ChatSocketDTO.MessageCreatedDTO) captor.getValue().data();
        assertThat(data.messageId()).isEqualTo(messageId);
        assertThat(data.senderId()).isEqualTo(meId);
    }

    @Test
    void 전송하면_보낸_사람은_안_읽은_수_0_상대는_쌓인_수를_받는다() {
        보낸다(meId);
        보낸다(meId);
        events.clear();
        Long last = 보낸다(meId);

        listener.publishMessageSent(events.stream(ChatMessageSentEvent.class).findFirst().orElseThrow());

        ChatSocketDTO.RoomUpdatedDTO mine = 받은_목록_갱신(meId);
        ChatSocketDTO.RoomUpdatedDTO opponents = 받은_목록_갱신(opponentId);
        assertThat(mine.unreadCount()).isZero();
        assertThat(opponents.unreadCount()).isEqualTo(3L);
        assertThat(opponents.lastMessage().messageId()).isEqualTo(last);
    }

    @Test
    void 상대가_읽은_뒤에_온_메시지만_안_읽은_수로_센다() {
        Long first = 보낸다(meId);
        chatCommandService.readMessages(opponentId, roomId, new ChatRequestDTO.ReadMessageDTO(first));
        events.clear();
        보낸다(meId);

        listener.publishMessageSent(events.stream(ChatMessageSentEvent.class).findFirst().orElseThrow());

        assertThat(받은_목록_갱신(opponentId).unreadCount()).isEqualTo(1L);
    }

    @Test
    void 읽으면_방_주소로_읽음_알림을_보낸다() {
        Long messageId = 보낸다(opponentId);
        chatCommandService.readMessages(meId, roomId, new ChatRequestDTO.ReadMessageDTO(messageId));

        listener.publishMessageRead(events.stream(ChatMessageReadEvent.class).findFirst().orElseThrow());

        ArgumentCaptor<SocketEvent<?>> captor = ArgumentCaptor.forClass(SocketEvent.class);
        verify(socketEventPublisher).toDestination(eq(방_주소()), captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(ChatSocketEventType.MESSAGE_READ);
        ChatSocketDTO.MessageReadDTO data = (ChatSocketDTO.MessageReadDTO) captor.getValue().data();
        assertThat(data.readerId()).isEqualTo(meId);
        assertThat(data.lastReadMessageId()).isEqualTo(messageId);
        verify(socketEventPublisher, never()).toMember(any(), any());
    }

    @Test
    void 나가면_방_주소와_남은_상대_개인_주소로_보낸다() {
        chatCommandService.leaveRoom(meId, roomId);

        listener.publishMemberLeft(events.stream(ChatMemberLeftEvent.class).findFirst().orElseThrow());

        ArgumentCaptor<SocketEvent<?>> toRoom = ArgumentCaptor.forClass(SocketEvent.class);
        ArgumentCaptor<SocketEvent<?>> toOpponent = ArgumentCaptor.forClass(SocketEvent.class);
        verify(socketEventPublisher).toDestination(eq(방_주소()), toRoom.capture());
        verify(socketEventPublisher).toMember(eq(opponentId), toOpponent.capture());
        verify(socketEventPublisher, never()).toMember(eq(meId), any());

        assertThat(toRoom.getValue().type()).isEqualTo(ChatSocketEventType.MEMBER_LEFT);
        ChatSocketDTO.MemberLeftDTO data = (ChatSocketDTO.MemberLeftDTO) toOpponent.getValue().data();
        assertThat(data.leftMemberId()).isEqualTo(meId);
    }
}
