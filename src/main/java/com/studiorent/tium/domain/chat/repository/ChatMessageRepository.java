package com.studiorent.tium.domain.chat.repository;

import com.studiorent.tium.domain.chat.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** 첫 진입. 최신 메시지부터 내려온다. */
    List<ChatMessage> findByChatRoomIdOrderByIdDesc(Long chatRoomId, Pageable pageable);

    /** 과거 방향. cursor보다 오래된 메시지를 최신순으로. */
    List<ChatMessage> findByChatRoomIdAndIdLessThanOrderByIdDesc(Long chatRoomId,
                                                                 Long cursor,
                                                                 Pageable pageable);

    /** 미래 방향. after보다 새로운 메시지를 오래된 순으로. 소켓 재연결 복구용이다. */
    List<ChatMessage> findByChatRoomIdAndIdGreaterThanOrderByIdAsc(Long chatRoomId,
                                                                   Long after,
                                                                   Pageable pageable);
}
