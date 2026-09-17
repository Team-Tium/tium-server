package com.studiorent.tium.domain.chat;

import com.studiorent.tium.domain.chat.dto.ChatRequestDTO;
import com.studiorent.tium.domain.chat.dto.ChatResponseDTO;
import com.studiorent.tium.domain.chat.entity.ChatRoom;
import com.studiorent.tium.domain.chat.entity.ChatRoomMember;
import com.studiorent.tium.domain.chat.entity.enums.MessageType;
import com.studiorent.tium.domain.chat.repository.ChatRoomMemberRepository;
import com.studiorent.tium.domain.chat.repository.ChatRoomRepository;
import com.studiorent.tium.domain.chat.service.command.ChatCommandService;
import com.studiorent.tium.domain.chat.service.query.ChatQueryService;
import com.studiorent.tium.domain.member.entity.Member;
import com.studiorent.tium.domain.member.entity.enums.Provider;
import com.studiorent.tium.domain.member.repository.MemberRepository;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ChatLeaveRoomTest {

    @Autowired private ChatCommandService chatCommandService;
    @Autowired private ChatQueryService chatQueryService;
    @Autowired private ChatRoomRepository chatRoomRepository;
    @Autowired private ChatRoomMemberRepository chatRoomMemberRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private EntityManager entityManager;

    private Long meId;
    private Long opponentId;
    private Long roomId;

    @BeforeEach
    void 회원_둘과_방을_만든다() {
        meId = 회원을_만든다("me").getId();
        opponentId = 회원을_만든다("opponent").getId();
        roomId = 방을_만든다(meId, opponentId).roomId();
    }

    private Member 회원을_만든다(String prefix) {
        return memberRepository.save(Member.builder()
                .provider(Provider.KAKAO)
                .providerId(prefix + "-" + System.nanoTime())
                .name(prefix)
                .build());
    }

    private ChatResponseDTO.CreateRoomResultDTO 방을_만든다(Long callerId, Long targetId) {
        return chatCommandService.createRoom(callerId, new ChatRequestDTO.CreateRoomDTO(targetId));
    }

    private void 보낸다(Long senderId) {
        chatCommandService.sendMessage(senderId, roomId,
                new ChatRequestDTO.SendMessageDTO(MessageType.TEXT, "안녕하세요"));
    }

    @Test
    void 나가면_나간_시각이_기록되고_활성_쌍_키가_비워진다() {
        ChatResponseDTO.LeaveRoomResultDTO result = chatCommandService.leaveRoom(meId, roomId);

        ChatRoomMember me = chatRoomMemberRepository.findByChatRoomIdAndMemberId(roomId, meId).orElseThrow();
        ChatRoom room = chatRoomRepository.findById(roomId).orElseThrow();

        assertThat(result.roomId()).isEqualTo(roomId);
        assertThat(result.leftAt()).isNotNull().isEqualTo(me.getLeftAt());
        assertThat(room.getActivePairKey()).isNull();
    }

    @Test
    void 이미_나간_방에서_다시_나가면_거절된다() {
        chatCommandService.leaveRoom(meId, roomId);

        assertThatThrownBy(() -> chatCommandService.leaveRoom(meId, roomId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("baseCode", ErrorStatus.CHAT_ALREADY_LEFT);
    }

    @Test
    void 참여하지_않은_방에서는_나갈_수_없다() {
        Long strangerId = 회원을_만든다("stranger").getId();

        assertThatThrownBy(() -> chatCommandService.leaveRoom(strangerId, roomId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("baseCode", ErrorStatus.CHAT_NOT_ROOM_MEMBER);
    }

    @Test
    void 존재하지_않는_방에서는_나갈_수_없다() {
        assertThatThrownBy(() -> chatCommandService.leaveRoom(meId, -1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("baseCode", ErrorStatus.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    void 내가_나간_뒤_상대가_메시지를_보내면_거절된다() {
        chatCommandService.leaveRoom(meId, roomId);

        assertThatThrownBy(() -> 보낸다(opponentId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("baseCode", ErrorStatus.CHAT_OPPONENT_LEFT);
    }

    @Test
    void 나간_뒤에는_같은_상대와_새_방을_만들_수_있다() {
        chatCommandService.leaveRoom(meId, roomId);

        ChatResponseDTO.CreateRoomResultDTO second = 방을_만든다(meId, opponentId);

        assertThat(second.created()).isTrue();
        assertThat(second.roomId()).isNotEqualTo(roomId);
    }

    @Test
    void 내가_나가면_상대_목록에서_opponentLeft가_true다() {
        // 메시지가 없는 방은 목록에 나오지 않는다
        보낸다(opponentId);
        chatCommandService.leaveRoom(meId, roomId);
        entityManager.flush();
        entityManager.clear();

        ChatResponseDTO.ChatRoomDTO room = chatQueryService.getChats(opponentId, null, 20).rooms().stream()
                .filter(r -> r.roomId().equals(roomId))
                .findFirst()
                .orElseThrow();

        assertThat(room.opponentLeft()).isTrue();
    }
}
