package com.studiorent.tium.domain.chat.service.query;

import com.studiorent.tium.domain.chat.dto.ChatResponseDTO;

public interface ChatQueryService {

    /**
     * 내가 참여 중인 채팅방 목록을 마지막 메시지 ID 내림차순으로 한 페이지 조회한다.
     *
     * @param cursor 이전 페이지의 nextCursor. 첫 요청이면 null
     * @param size   페이지당 개수
     */
    ChatResponseDTO.GetChatDTO getChats(Long memberId, Long cursor, int size);

    ChatResponseDTO.RecentFeedbackChatListDTO getFeedbackChats(Long memberId, Long cursor, int size);

    /**
     * 채팅방 하나의 내역을 조회한다. 상대 정보와 나감 여부도 함께 내려간다.
     *
     * @param cursor 과거 방향. 이 ID보다 오래된 메시지를 최신순으로
     * @param after  미래 방향. 이 ID보다 새로운 메시지를 오래된 순으로. cursor보다 우선한다
     */
    ChatResponseDTO.GetMessagesDTO getMessages(Long memberId, Long roomId,
                                               Long cursor, Long after, int size);
}
