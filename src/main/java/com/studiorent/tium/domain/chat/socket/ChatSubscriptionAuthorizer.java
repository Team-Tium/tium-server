package com.studiorent.tium.domain.chat.socket;

import com.studiorent.tium.domain.chat.repository.ChatRoomMemberRepository;
import com.studiorent.tium.global.socket.SubscriptionAuthorizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.studiorent.tium.global.socket.SocketDestinations.CHATROOM_SUB_PREFIX;

@Component
@RequiredArgsConstructor
public class ChatSubscriptionAuthorizer implements SubscriptionAuthorizer {

    private final ChatRoomMemberRepository chatRoomMemberRepository;

    @Override
    public boolean supports(String destination) {
        return destination != null && destination.startsWith(CHATROOM_SUB_PREFIX + "/");
    }

    @Override
    public boolean canSubscribe(Long memberId, String destination) {
        Long roomId = parseRoomId(destination);
        if (roomId == null) {
            return false;
        }

        return chatRoomMemberRepository.existsByChatRoomIdAndMemberIdAndLeftAtIsNull(roomId, memberId);
    }

    /** /sub/chat/rooms/{roomId} 형태일 때만 roomId를 꺼낸다. 뒤에 다른 경로가 붙으면 숫자 변환에서 걸러진다. */
    private Long parseRoomId(String destination) {
        String prefix = CHATROOM_SUB_PREFIX + "/";
        if (!destination.startsWith(prefix)) {
            return null;
        }

        try {
            return Long.parseLong(destination.substring(prefix.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
