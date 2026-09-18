package com.studiorent.tium.domain.chat.repository;

import com.studiorent.tium.domain.chat.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * 메시지가 이 방에 속하는지 확인한다. 읽음 처리에서 쓴다.
     *
     * <p>존재 여부만 보면 다른 방의 큰 메시지 ID로 읽음 포인터를 끝까지 밀 수 있다.
     * 포인터는 되돌아가지 않으므로 방 조건까지 함께 건다.
     */
    boolean existsByIdAndChatRoomId(Long id, Long chatRoomId);

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

    /** senderId가 보낸 메시지 중 afterId보다 뒤에 온 것의 수. 상대의 안 읽은 수를 셀 때 쓴다. */
    long countByChatRoomIdAndSenderIdAndIdGreaterThan(Long chatRoomId,
                                                      Long senderId,
                                                      Long afterId);
}
