package com.studiorent.tium.domain.chat;

import com.studiorent.tium.domain.chat.dto.ChatRequestDTO;
import com.studiorent.tium.domain.chat.dto.ChatResponseDTO;
import com.studiorent.tium.domain.chat.entity.enums.MessageType;
import com.studiorent.tium.domain.chat.repository.ChatRoomMemberRepository;
import com.studiorent.tium.domain.chat.service.command.ChatCommandService;
import com.studiorent.tium.domain.member.entity.Member;
import com.studiorent.tium.domain.member.entity.enums.Provider;
import com.studiorent.tium.domain.member.repository.MemberRepository;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ChatReadMessagesTest {

    @Autowired private ChatCommandService chatCommandService;
    @Autowired private ChatRoomMemberRepository chatRoomMemberRepository;
    @Autowired private MemberRepository memberRepository;

    private Long meId;
    private Long opponentId;
    private Long roomId;

    @BeforeEach
    void 회원_둘과_방을_만든다() {
        meId = 회원을_만든다("me").getId();
        opponentId = 회원을_만든다("opponent").getId();
        roomId = 방을_만든다(meId, opponentId);
    }

    private Member 회원을_만든다(String prefix) {
        return memberRepository.save(Member.builder()
                .provider(Provider.KAKAO)
                .providerId(prefix + "-" + System.nanoTime())
                .name(prefix)
                .build());
    }

    private Long 방을_만든다(Long callerId, Long targetId) {
        return chatCommandService.createRoom(callerId, new ChatRequestDTO.CreateRoomDTO(targetId)).roomId();
    }

    private Long 보낸다(Long senderId, Long targetRoomId) {
        return chatCommandService.sendMessage(senderId, targetRoomId,
                new ChatRequestDTO.SendMessageDTO(MessageType.TEXT, "안녕하세요")).messageId();
    }

    private ChatResponseDTO.ReadMessageResultDTO 읽는다(Long readerId, Long messageId) {
        return chatCommandService.readMessages(readerId, roomId, new ChatRequestDTO.ReadMessageDTO(messageId));
    }

    private Long 내_포인터() {
        return chatRoomMemberRepository.findByChatRoomIdAndMemberId(roomId, meId)
                .orElseThrow().getLastReadMessageId();
    }

    @Test
    void 읽음_포인터가_요청한_메시지까지_앞으로_이동한다() {
        보낸다(opponentId, roomId);
        Long last = 보낸다(opponentId, roomId);

        ChatResponseDTO.ReadMessageResultDTO result = 읽는다(meId, last);

        assertThat(result.roomId()).isEqualTo(roomId);
        assertThat(result.lastReadMessageId()).isEqualTo(last);
        assertThat(result.readAt()).isNotNull();
        assertThat(내_포인터()).isEqualTo(last);
    }

    @Test
    void 작은_ID는_무시되고_응답에도_저장된_포인터가_나간다() {
        Long first = 보낸다(opponentId, roomId);
        Long second = 보낸다(opponentId, roomId);
        ChatResponseDTO.ReadMessageResultDTO moved = 읽는다(meId, second);

        ChatResponseDTO.ReadMessageResultDTO ignored = 읽는다(meId, first);

        assertThat(ignored.lastReadMessageId()).isEqualTo(second);
        assertThat(ignored.readAt()).isEqualTo(moved.readAt());
        assertThat(내_포인터()).isEqualTo(second);
    }

    @Test
    void 다른_방의_메시지_ID로는_포인터를_옮길_수_없다() {
        보낸다(opponentId, roomId);
        Long thirdId = 회원을_만든다("third").getId();
        Long otherRoomId = 방을_만든다(opponentId, thirdId);
        Long otherRoomMessage = 보낸다(opponentId, otherRoomId);

        assertThatThrownBy(() -> 읽는다(meId, otherRoomMessage))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("baseCode", ErrorStatus.CHAT_MESSAGE_NOT_FOUND);
        assertThat(내_포인터()).isNull();
    }

    @Test
    void 존재하지_않는_메시지_ID는_거절된다() {
        assertThatThrownBy(() -> 읽는다(meId, Long.MAX_VALUE))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("baseCode", ErrorStatus.CHAT_MESSAGE_NOT_FOUND);
    }

    @Test
    void 나간_사람은_읽음_처리를_할_수_없다() {
        Long message = 보낸다(opponentId, roomId);
        chatCommandService.leaveRoom(meId, roomId);

        assertThatThrownBy(() -> 읽는다(meId, message))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("baseCode", ErrorStatus.CHAT_NOT_ROOM_MEMBER);
    }
}
