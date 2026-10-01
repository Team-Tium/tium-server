package com.studiorent.tium.domain.chat.service.query;

import com.studiorent.tium.domain.chat.converter.ChatConverter;
import com.studiorent.tium.domain.chat.dto.ChatResponseDTO;
import com.studiorent.tium.domain.chat.entity.ChatMessage;
import com.studiorent.tium.domain.chat.repository.ChatMessageRepository;
import com.studiorent.tium.domain.chat.repository.ChatRoomMemberRepository;
import com.studiorent.tium.domain.chat.repository.projection.ChatOpponentProjection;
import com.studiorent.tium.domain.chat.repository.projection.ChatRoomListProjection;
import com.studiorent.tium.domain.chat.service.ChatRoomValidator;
import com.studiorent.tium.domain.call.converter.CallConverter;
import com.studiorent.tium.domain.feedback.repository.FeedbackRepository;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatQueryServiceImpl implements ChatQueryService {

    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomValidator chatRoomValidator;
    private final FeedbackRepository feedbackRepository;

    /**
     * 방 목록 한 페이지.
     *
     * <p>방이 하나도 없으면 빈 리스트가 나간다. 조회 결과가 비는 건 실패가 아니라서
     * 예외를 던지지도, null을 돌려주지도 않는다.
     */
    @Override
    @Transactional(readOnly = true)
    public ChatResponseDTO.GetChatDTO getChats(Long memberId, Long cursor, int size) {

        // size + 1개를 요청한다. 넘치면 다음 페이지가 있다는 뜻이고, 그 한 개는 응답에서 버린다.
        List<ChatRoomListProjection> rows =
                chatRoomMemberRepository.findRoomList(memberId, cursor, size + 1);

        boolean hasNext = rows.size() > size;
        List<ChatRoomListProjection> page = hasNext ? rows.subList(0, size) : rows;

        List<ChatResponseDTO.ChatRoomDTO> rooms = page.stream()
                .map(ChatConverter::toChatRoom)
                .toList();

        Long nextCursor = hasNext
                ? page.get(page.size() - 1).getLastMessageId()
                : null;

        return new ChatResponseDTO.GetChatDTO(rooms, hasNext, nextCursor);
    }

    @Override
    @Transactional(readOnly = true)
    public ChatResponseDTO.RecentFeedbackChatListDTO getFeedbackChats(Long memberId, Long cursor, int size) {
        List<ChatRoomListProjection> rows =
                chatRoomMemberRepository.findRoomList(memberId, cursor, size + 1);

        boolean hasNext = rows.size() > size;
        List<ChatRoomListProjection> page = hasNext ? rows.subList(0, size) : rows;

        List<ChatResponseDTO.RecentFeedbackChatDTO> items = page.stream()
                .map(row -> new ChatResponseDTO.RecentFeedbackChatDTO(
                        row.getRoomId(),
                        new ChatResponseDTO.OpponentDTO(
                                row.getOpponentId(),
                                row.getOpponentNickname(),
                                row.getOpponentProfileImageUrl()),
                        row.getLastMessageContent(),
                        CallConverter.toServiceOffsetDateTime(row.getLastMessageSentAt()),
                        feedbackRepository.existsByMemberIdAndRoomIdAndCompletedTrue(memberId, row.getRoomId())))
                .toList();

        Long nextCursor = hasNext
                ? page.get(page.size() - 1).getLastMessageId()
                : null;

        return new ChatResponseDTO.RecentFeedbackChatListDTO(items, hasNext, nextCursor);
    }

    /**
     * 채팅 내역 한 페이지.
     *
     * <p>{@code after}가 오면 {@code cursor}는 무시한다(명세 3번). 방향이 반대라 둘을 동시에 만족시킬 수 없다.
     *
     * <p>쿼리는 셋이다 — 참여 검증, 상대 정보, 메시지 목록. 메시지 개수와 무관하게 항상 셋이다.
     * {@code isRead}는 상대의 읽음 포인터를 미리 받아와서 자바에서 비교한다.
     */
    @Override
    @Transactional(readOnly = true)
    public ChatResponseDTO.GetMessagesDTO getMessages(Long memberId, Long roomId,
                                                      Long cursor, Long after, int size) {

        chatRoomValidator.getJoinedMember(roomId, memberId);

        ChatOpponentProjection opponent = chatRoomMemberRepository
                .findOpponent(roomId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorStatus.CHAT_ROOM_NOT_FOUND));

        Pageable limit = PageRequest.of(0, size + 1);
        List<ChatMessage> rows = findMessages(roomId, cursor, after, limit);

        boolean hasNext = rows.size() > size;
        List<ChatMessage> page = hasNext ? rows.subList(0, size) : rows;

        List<ChatResponseDTO.MessageDTO> messages = page.stream()
                .map(message -> ChatConverter.toMessage(message, opponent.getOpponentLastReadMessageId()))
                .toList();

        Long nextCursor = hasNext
                ? page.get(page.size() - 1).getId()
                : null;

        return ChatConverter.toGetMessages(roomId, opponent, messages, hasNext, nextCursor);
    }

    /** 방향에 따라 쿼리가 갈린다. after가 우선이고, 둘 다 없으면 최신부터 내려온다. */
    private List<ChatMessage> findMessages(Long roomId, Long cursor, Long after, Pageable limit) {
        if (after != null) {
            return chatMessageRepository
                    .findByChatRoomIdAndIdGreaterThanOrderByIdAsc(roomId, after, limit);
        }
        if (cursor != null) {
            return chatMessageRepository
                    .findByChatRoomIdAndIdLessThanOrderByIdDesc(roomId, cursor, limit);
        }
        return chatMessageRepository.findByChatRoomIdOrderByIdDesc(roomId, limit);
    }
}
