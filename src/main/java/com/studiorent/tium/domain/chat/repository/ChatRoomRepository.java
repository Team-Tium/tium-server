package com.studiorent.tium.domain.chat.repository;

import com.studiorent.tium.domain.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    /**
     * 두 회원의 활성 방을 찾는다. 키는 {@link ChatRoom#pairKeyOf(Long, Long)}로 만든다.
     *
     * <p>나간 방은 activePairKey가 null이라 여기에 잡히지 않는다 — 활성 방만 최대 1건 나온다.
     */
    Optional<ChatRoom> findByActivePairKey(String activePairKey);
}
