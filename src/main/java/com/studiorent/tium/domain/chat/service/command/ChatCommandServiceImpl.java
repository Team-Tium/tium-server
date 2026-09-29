package com.studiorent.tium.domain.chat.service.command;

import com.studiorent.tium.domain.chat.converter.ChatConverter;
import com.studiorent.tium.domain.chat.dto.ChatRequestDTO;
import com.studiorent.tium.domain.chat.dto.ChatResponseDTO;
import com.studiorent.tium.domain.chat.entity.ChatMessage;
import com.studiorent.tium.domain.chat.entity.ChatRoom;
import com.studiorent.tium.domain.chat.entity.ChatRoomMember;
import com.studiorent.tium.domain.chat.entity.enums.MessageType;
import com.studiorent.tium.domain.chat.repository.ChatMessageRepository;
import com.studiorent.tium.domain.chat.repository.ChatRoomMemberRepository;
import com.studiorent.tium.domain.chat.repository.ChatRoomRepository;
import com.studiorent.tium.domain.chat.service.ChatRoomValidator;
import com.studiorent.tium.domain.chat.socket.event.ChatMemberLeftEvent;
import com.studiorent.tium.domain.chat.socket.event.ChatMessageReadEvent;
import com.studiorent.tium.domain.chat.socket.event.ChatMessageSentEvent;
import com.studiorent.tium.domain.member.entity.Member;
import com.studiorent.tium.domain.member.repository.MemberRepository;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatCommandServiceImpl implements ChatCommandService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final MemberRepository memberRepository;
    private final ChatRoomValidator chatRoomValidator;
    private final ApplicationEventPublisher applicationEventPublisher;

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

    /**
     * 메시지를 저장하고 방과 내 읽음 포인터를 함께 갱신한다.
     *
     * <p>세 쓰기가 한 트랜잭션인 이유는 각각이 다른 화면의 근거가 되기 때문이다.
     * 메시지는 대화창, {@code last_message_id}는 목록의 정렬과 미리보기,
     * 읽음 포인터는 안 읽은 배지다. 하나만 빠지면 화면끼리 어긋난다.
     *
     * <p>내 읽음 포인터를 같이 옮기는 것이 특히 중요하다. 빠뜨리면 방금 내가 보낸 메시지가
     * 내 {@code unreadCount}에 잡혀 내 방에 안 읽은 배지가 뜬다.
     * 메시지 ID가 순증가라 방금 저장한 ID 하나만 찍으면 그 아래는 전부 읽은 것이 된다.
     *
     * <p>{@code chat_room.last_message_id}에는 "앞으로만" 가드를 두지 않았다.
     * 두 명이 동시에 보내면 나중에 커밋된 트랜잭션의 메시지 ID가 그대로 덮어쓴다.
     */
    @Override
    @Transactional
    public ChatResponseDTO.SendMessageResultDTO sendMessage(Long memberId, Long roomId,
                                                            ChatRequestDTO.SendMessageDTO request) {
        if (request.type() != MessageType.TEXT) {
            throw new BusinessException(ErrorStatus.CHAT_UNSUPPORTED_MESSAGE_TYPE);
        }
        if (request.content() == null || request.content().isBlank()) {
            throw new BusinessException(ErrorStatus.CHAT_EMPTY_CONTENT);
        }

        ChatRoomMember me = chatRoomValidator.getJoinedMember(roomId, memberId);

        ChatRoomMember opponent = chatRoomMemberRepository
                .findByChatRoomIdAndMemberIdNot(roomId, memberId)
                // 1:1 방은 참여 행이 항상 2개다. 없으면 요청이 아니라 데이터가 잘못된 것이다.
                .orElseThrow(() -> new BusinessException(ErrorStatus.INTERNAL_SERVER_ERROR));

        if (opponent.hasLeft()) {
            throw new BusinessException(ErrorStatus.CHAT_OPPONENT_LEFT);
        }

        ChatRoom chatRoom = me.getChatRoom();
        ChatMessage message = chatMessageRepository.save(
                ChatMessage.ofText(chatRoom, memberId, request.content()));

        chatRoom.updateLastMessage(message.getId(), message.getSentAt());
        me.updateLastReadMessage(message.getId());

        ChatMessageSentEvent event = new ChatMessageSentEvent(
                message.getId(),
                message.getMessageType(),
                message.getContent(),
                message.getSentAt(),
                roomId,
                opponent.getMemberId(),
                memberId,
                opponent.getLastReadMessageId()
        );

        applicationEventPublisher.publishEvent(event);
        return ChatConverter.toSendMessageResult(message);
    }

    /**
     * 읽음 포인터를 요청한 메시지까지 옮긴다.
     *
     * <p>메시지가 이 방에 속하는지까지 확인한다. 다른 방의 큰 ID가 통과하면 포인터가 끝까지 밀리고,
     * 포인터는 되돌아가지 않으므로 이후 이 방에 오는 메시지가 전부 읽은 것으로 처리된다.
     *
     * <p>응답은 요청값이 아니라 저장된 값으로 만든다. 작은 ID가 와서 무시됐을 때
     * 응답과 실제 상태가 어긋나지 않게 하기 위해서다.
     */
    @Override
    @Transactional
    public ChatResponseDTO.ReadMessageResultDTO readMessages(Long memberId, Long roomId,
                                                             ChatRequestDTO.ReadMessageDTO request) {
        ChatRoomMember me = chatRoomValidator.getJoinedMember(roomId, memberId);
        Long messageId = request.lastReadMessageId();

        if (!chatMessageRepository.existsByIdAndChatRoomId(messageId, roomId)) {
            throw new BusinessException(ErrorStatus.CHAT_MESSAGE_NOT_FOUND);
        }

        boolean moved = me.updateLastReadMessage(messageId);

        // 포인터가 그대로면 상대 화면에 바뀔 것이 없어 알리지 않는다.
        if (moved) {
            applicationEventPublisher.publishEvent(
                    new ChatMessageReadEvent(roomId, memberId, me.getLastReadMessageId()));
        }
        return ChatConverter.toReadMessageResult(me);
    }

    /**
     * 채팅방에서 나간다. 나가기는 되돌릴 수 없고 재입장도 없다.
     *
     * <p>참여자의 나간 시각 기록과 방의 활성 쌍 키 해제가 한 트랜잭션이다.
     * 쌍 키만 남으면 같은 상대와 새 방을 만들 수 없고, 나간 시각만 남으면 활성 방이 둘 생길 수 있다.
     *
     * <p>상대가 나가는 순간 전송이 들어오면, 전송의 "상대가 나갔는지" 확인을 통과한 뒤에
     * 이 트랜잭션이 커밋될 수 있다. 그러면 나간 방에 메시지 한 건이 남는다.
     * 막으려면 전송까지 방 행에 잠금을 걸어야 해서 두지 않았다.
     */
    @Override
    @Transactional
    public ChatResponseDTO.LeaveRoomResultDTO leaveRoom(Long memberId, Long roomId) {
        ChatRoomMember me = chatRoomValidator.getRoomMember(roomId, memberId);

        if (me.hasLeft()) {
            throw new BusinessException(ErrorStatus.CHAT_ALREADY_LEFT);
        }

        me.leave();
        me.getChatRoom().deactivatePair();

        ChatRoomMember opponent = chatRoomMemberRepository
                .findByChatRoomIdAndMemberIdNot(roomId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorStatus.INTERNAL_SERVER_ERROR));

        applicationEventPublisher.publishEvent(
                new ChatMemberLeftEvent(roomId, memberId, opponent.getMemberId()));
        return ChatConverter.toLeaveRoomResult(me);
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
