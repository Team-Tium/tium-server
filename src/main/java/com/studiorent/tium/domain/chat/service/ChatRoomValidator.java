package com.studiorent.tium.domain.chat.service;

import com.studiorent.tium.domain.chat.entity.ChatRoomMember;
import com.studiorent.tium.domain.chat.repository.ChatRoomMemberRepository;
import com.studiorent.tium.domain.chat.repository.ChatRoomRepository;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 채팅방 접근 권한 검증. 내역 조회·전송·읽음·나가기가 방 존재와 참여 여부를 같은 기준으로 가르므로 한곳에 모았다.
 *
 * <p>command와 query 양쪽에서 쓰기 때문에 서비스가 아니라 별도 컴포넌트로 뒀다.
 * 한쪽 서비스에 두면 반대쪽이 그 서비스를 주입받게 되어 CQRS 분리가 무너진다.
 */
@Component
@RequiredArgsConstructor
public class ChatRoomValidator {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;

    /**
     * 요청자가 이 방에 참여 중인지 확인하고 참여 행을 돌려준다.
     *
     * <p>방이 없으면 {@code CHAT4041}, 참여한 적이 없거나 이미 나갔으면 {@code CHAT4031}이다.
     * 명세 3번 API 기준으로 "나갔다"도 403으로 본다 — 나간 사람에게 내역을 보여주지 않는다.
     *
     */
    public ChatRoomMember getJoinedMember(Long roomId, Long memberId) {
        ChatRoomMember member = getRoomMember(roomId, memberId);

        if (member.hasLeft()) {
            throw new BusinessException(ErrorStatus.CHAT_NOT_ROOM_MEMBER);
        }
        return member;
    }

    /**
     * 요청자의 참여 행을 나갔는지와 무관하게 돌려준다.
     *
     * <p>방이 없으면 {@code CHAT4041}, 참여한 적이 없으면 {@code CHAT4031}이다.
     * 나가기처럼 "이미 나갔다"를 403이 아닌 별도 코드로 알려야 하는 곳에서 쓴다.
     *
     * <p>참여 행을 먼저 찾고 없을 때만 방 존재를 확인한다. 정상 요청은 쿼리 한 번으로 끝나고
     * 두 번째 쿼리는 404와 403을 가려야 하는 실패 경로에서만 나간다.
     */
    public ChatRoomMember getRoomMember(Long roomId, Long memberId) {
        return chatRoomMemberRepository
                .findByChatRoomIdAndMemberId(roomId, memberId)
                .orElseGet(() -> {
                    if (!chatRoomRepository.existsById(roomId)) {
                        throw new BusinessException(ErrorStatus.CHAT_ROOM_NOT_FOUND);
                    }
                    throw new BusinessException(ErrorStatus.CHAT_NOT_ROOM_MEMBER);
                });
    }
}
