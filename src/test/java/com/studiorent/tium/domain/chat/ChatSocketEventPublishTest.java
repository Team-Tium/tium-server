package com.studiorent.tium.domain.chat;

import com.studiorent.tium.domain.chat.dto.ChatRequestDTO;
import com.studiorent.tium.domain.chat.entity.enums.MessageType;
import com.studiorent.tium.domain.chat.service.command.ChatCommandService;
import com.studiorent.tium.domain.chat.socket.event.ChatMemberLeftEvent;
import com.studiorent.tium.domain.chat.socket.event.ChatMessageReadEvent;
import com.studiorent.tium.domain.chat.socket.event.ChatMessageSentEvent;
import com.studiorent.tium.domain.member.entity.Member;
import com.studiorent.tium.domain.member.entity.enums.Provider;
import com.studiorent.tium.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/** 서비스가 소켓 발행용 이벤트를 알맞은 값으로 남기는지 본다. 테스트 트랜잭션은 롤백되므로 리스너는 돌지 않는다. */
@SpringBootTest
@Transactional
@RecordApplicationEvents
class ChatSocketEventPublishTest {

    @Autowired private ChatCommandService chatCommandService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ApplicationEvents events;

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

    private void 읽는다(Long readerId, Long messageId) {
        chatCommandService.readMessages(readerId, roomId, new ChatRequestDTO.ReadMessageDTO(messageId));
    }

    @Test
    void 전송하면_메시지와_상대_책갈피를_담은_이벤트를_남긴다() {
        Long first = 보낸다(opponentId);
        읽는다(meId, first);
        events.clear();

        Long messageId = 보낸다(meId);

        ChatMessageSentEvent event = events.stream(ChatMessageSentEvent.class).findFirst().orElseThrow();
        assertThat(event.messageId()).isEqualTo(messageId);
        assertThat(event.roomId()).isEqualTo(roomId);
        assertThat(event.senderId()).isEqualTo(meId);
        assertThat(event.opponentId()).isEqualTo(opponentId);
        assertThat(event.opponentLastReadMessageId()).isEqualTo(first);
        assertThat(event.content()).isEqualTo("안녕하세요");
    }

    @Test
    void 읽음_포인터가_움직이면_이벤트를_남긴다() {
        Long messageId = 보낸다(opponentId);

        읽는다(meId, messageId);

        ChatMessageReadEvent event = events.stream(ChatMessageReadEvent.class).findFirst().orElseThrow();
        assertThat(event.roomId()).isEqualTo(roomId);
        assertThat(event.readerId()).isEqualTo(meId);
        assertThat(event.lastReadMessageId()).isEqualTo(messageId);
    }

    @Test
    void 읽음_포인터가_그대로면_이벤트를_남기지_않는다() {
        Long first = 보낸다(opponentId);
        Long second = 보낸다(opponentId);
        읽는다(meId, second);
        events.clear();

        읽는다(meId, first);

        assertThat(events.stream(ChatMessageReadEvent.class)).isEmpty();
    }

    @Test
    void 나가면_남은_상대를_담은_이벤트를_남긴다() {
        chatCommandService.leaveRoom(meId, roomId);

        ChatMemberLeftEvent event = events.stream(ChatMemberLeftEvent.class).findFirst().orElseThrow();
        assertThat(event.roomId()).isEqualTo(roomId);
        assertThat(event.leftMemberId()).isEqualTo(meId);
        assertThat(event.opponentId()).isEqualTo(opponentId);
    }
}
