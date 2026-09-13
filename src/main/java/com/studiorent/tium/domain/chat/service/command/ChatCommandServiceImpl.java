package com.studiorent.tium.domain.chat.service.command;

import com.studiorent.tium.domain.chat.converter.ChatConverter;
import com.studiorent.tium.domain.chat.dto.ChatRequestDTO;
import com.studiorent.tium.domain.chat.dto.ChatResponseDTO;
import com.studiorent.tium.domain.chat.entity.ChatRoom;
import com.studiorent.tium.domain.chat.entity.ChatRoomMember;
import com.studiorent.tium.domain.chat.repository.ChatRoomMemberRepository;
import com.studiorent.tium.domain.chat.repository.ChatRoomRepository;
import com.studiorent.tium.domain.member.entity.Member;
import com.studiorent.tium.domain.member.repository.MemberRepository;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatCommandServiceImpl implements ChatCommandService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final MemberRepository memberRepository;

    /**
     * 채팅방을 확보한다. 이미 활성 방이 있으면 만들지 않고 그 방을 돌려준다.
     *
     * <p>"활성"의 정의는 {@code active_pair_key}가 살아 있는 것이다. 한쪽이라도 나가면 그 값이 null이 되므로
     * 나간 방은 조회에 걸리지 않고 새 방이 만들어진다. 과거 방의 메시지는 새 방으로 이어지지 않는다.
     *
     * <p>동시에 같은 쌍으로 방을 만들면 유니크 제약이 두 번째 요청을 막고
     * {@code DataIntegrityViolationException} → 400이 나간다. 재시도하면 기존 방을 받는다.
     * auth 도메인의 회원 동시 생성과 같은 판단이다 — 방이 2개 생기는 것만 막으면 충분하다고 봤다.
     */
    @Override
    @Transactional
    public ChatResponseDTO.CreateRoomResultDTO createRoom(Long memberId,
                                                          ChatRequestDTO.CreateRoomDTO request) {
        Long opponentId = request.opponentId();

        if (memberId.equals(opponentId)) {
            throw new BusinessException(ErrorStatus.CHAT_SELF_ROOM_NOT_ALLOWED);
        }

        Member opponent = memberRepository.findById(opponentId)
                .orElseThrow(() -> new BusinessException(ErrorStatus.MEMBER_NOT_FOUND));

        if (opponent.isWithdrawn()) {
            throw new BusinessException(ErrorStatus.MEMBER_WITHDRAWN);
        }

        String pairKey = ChatRoom.pairKeyOf(memberId, opponentId);

        return chatRoomRepository.findByActivePairKey(pairKey)
                .map(existing -> ChatConverter.toCreateRoomResult(existing, false, opponent))
                .orElseGet(() -> ChatConverter.toCreateRoomResult(
                        openRoom(memberId, opponentId), true, opponent));
    }

    /** 방과 참여자 2행을 함께 만든다. 이 둘은 항상 같은 트랜잭션이어야 한다. */
    private ChatRoom openRoom(Long memberId, Long opponentId) {
        ChatRoom chatRoom = chatRoomRepository.save(ChatRoom.create(memberId, opponentId));

        chatRoomMemberRepository.saveAll(List.of(
                ChatRoomMember.join(chatRoom, memberId),
                ChatRoomMember.join(chatRoom, opponentId)
        ));

        return chatRoom;
    }
}
