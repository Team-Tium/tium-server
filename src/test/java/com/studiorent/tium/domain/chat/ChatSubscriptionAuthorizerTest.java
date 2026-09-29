package com.studiorent.tium.domain.chat;

import com.studiorent.tium.domain.chat.dto.ChatRequestDTO;
import com.studiorent.tium.domain.chat.service.command.ChatCommandService;
import com.studiorent.tium.domain.chat.socket.ChatSubscriptionAuthorizer;
import com.studiorent.tium.domain.member.entity.Member;
import com.studiorent.tium.domain.member.entity.enums.Provider;
import com.studiorent.tium.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ChatSubscriptionAuthorizerTest {

    @Autowired private ChatSubscriptionAuthorizer authorizer;
    @Autowired private ChatCommandService chatCommandService;
    @Autowired private MemberRepository memberRepository;

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

    private String 방_주소(Object roomId) {
        return "/sub/chat/rooms/" + roomId;
    }

    @Test
    void 채팅방_주소만_맡는다() {
        assertThat(authorizer.supports(방_주소(roomId))).isTrue();
        assertThat(authorizer.supports("/sub/call/" + roomId)).isFalse();
        assertThat(authorizer.supports("/sub/users/" + meId)).isFalse();
        assertThat(authorizer.supports(null)).isFalse();
    }

    @Test
    void 참여_중인_방은_구독할_수_있다() {
        assertThat(authorizer.canSubscribe(meId, 방_주소(roomId))).isTrue();
        assertThat(authorizer.canSubscribe(opponentId, 방_주소(roomId))).isTrue();
    }

    @Test
    void 참여하지_않은_방은_구독할_수_없다() {
        Long strangerId = 회원을_만든다("stranger").getId();

        assertThat(authorizer.canSubscribe(strangerId, 방_주소(roomId))).isFalse();
    }

    @Test
    void 나간_방은_구독할_수_없다() {
        chatCommandService.leaveRoom(meId, roomId);

        assertThat(authorizer.canSubscribe(meId, 방_주소(roomId))).isFalse();
    }

    @Test
    void 존재하지_않는_방은_구독할_수_없다() {
        assertThat(authorizer.canSubscribe(meId, 방_주소(-1L))).isFalse();
    }

    @Test
    void 형식이_잘못된_주소는_구독할_수_없다() {
        assertThat(authorizer.canSubscribe(meId, 방_주소("abc"))).isFalse();
        assertThat(authorizer.canSubscribe(meId, 방_주소(roomId + "/xyz"))).isFalse();
        assertThat(authorizer.canSubscribe(meId, 방_주소(""))).isFalse();
    }
}
