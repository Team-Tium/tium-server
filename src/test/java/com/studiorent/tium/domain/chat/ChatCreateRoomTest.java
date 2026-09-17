package com.studiorent.tium.domain.chat;

import com.studiorent.tium.domain.chat.dto.ChatRequestDTO;
import com.studiorent.tium.domain.chat.dto.ChatResponseDTO;
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
class ChatCreateRoomTest {

    @Autowired private ChatCommandService chatCommandService;
    @Autowired private ChatRoomMemberRepository chatRoomMemberRepository;
    @Autowired private MemberRepository memberRepository;

    private Long meId;
    private Long opponentId;

    @BeforeEach
    void 회원_둘을_만든다() {
        meId = 회원을_만든다("me").getId();
        opponentId = 회원을_만든다("opponent").getId();
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

    @Test
    void 처음_만들면_created가_true고_참여자가_2명_생긴다() {
        ChatResponseDTO.CreateRoomResultDTO result = 방을_만든다(meId, opponentId);

        assertThat(result.created()).isTrue();
        assertThat(result.roomId()).isNotNull();
        assertThat(chatRoomMemberRepository.findByChatRoomIdAndMemberId(result.roomId(), meId)).isPresent();
        assertThat(chatRoomMemberRepository.findByChatRoomIdAndMemberId(result.roomId(), opponentId)).isPresent();
    }

    @Test
    void 활성_방이_이미_있으면_새로_만들지_않고_그_방을_준다() {
        Long firstRoomId = 방을_만든다(meId, opponentId).roomId();

        ChatResponseDTO.CreateRoomResultDTO second = 방을_만든다(meId, opponentId);

        assertThat(second.created()).isFalse();
        assertThat(second.roomId()).isEqualTo(firstRoomId);
    }

    @Test
    void 상대가_먼저_만든_방도_같은_방으로_취급된다() {
        Long firstRoomId = 방을_만든다(meId, opponentId).roomId();

        ChatResponseDTO.CreateRoomResultDTO fromOpponent = 방을_만든다(opponentId, meId);

        assertThat(fromOpponent.created()).isFalse();
        assertThat(fromOpponent.roomId()).isEqualTo(firstRoomId);
    }

    @Test
    void 한쪽이_나간_방밖에_없으면_새_방이_만들어진다() {
        Long firstRoomId = 방을_만든다(meId, opponentId).roomId();
        chatCommandService.leaveRoom(meId, firstRoomId);

        ChatResponseDTO.CreateRoomResultDTO second = 방을_만든다(meId, opponentId);

        assertThat(second.created()).isTrue();
        assertThat(second.roomId()).isNotEqualTo(firstRoomId);
    }

    @Test
    void 상대_닉네임은_회원_이름을_쓰고_프로필_이미지는_아직_null이다() {
        ChatResponseDTO.OpponentDTO opponent = 방을_만든다(meId, opponentId).opponent();

        assertThat(opponent.userId()).isEqualTo(opponentId);
        assertThat(opponent.nickname()).isEqualTo("opponent");
        assertThat(opponent.profileImageUrl()).isNull();
    }

    @Test
    void 자기_자신과는_방을_만들_수_없다() {
        assertThatThrownBy(() -> 방을_만든다(meId, meId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("baseCode", ErrorStatus.CHAT_SELF_ROOM_NOT_ALLOWED);
    }

    @Test
    void 존재하지_않는_회원과는_방을_만들_수_없다() {
        assertThatThrownBy(() -> 방을_만든다(meId, -1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("baseCode", ErrorStatus.MEMBER_NOT_FOUND);
    }

    @Test
    void 탈퇴한_회원과는_방을_만들_수_없다() {
        Member withdrawn = memberRepository.findById(opponentId).orElseThrow();
        withdrawn.withdraw();

        assertThatThrownBy(() -> 방을_만든다(meId, opponentId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("baseCode", ErrorStatus.MEMBER_WITHDRAWN);
    }
}
