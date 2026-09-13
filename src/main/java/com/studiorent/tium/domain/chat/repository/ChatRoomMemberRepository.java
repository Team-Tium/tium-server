package com.studiorent.tium.domain.chat.repository;

import com.studiorent.tium.domain.chat.entity.ChatRoomMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {

    /** 요청자가 이 방의 참여자인지 확인할 때 쓴다. 나간 참여자도 행은 남아 있어 함께 조회된다. */
    Optional<ChatRoomMember> findByChatRoomIdAndMemberId(Long chatRoomId, Long memberId);

    /** 1:1 방이므로 "나 말고 나머지 한 명" = 상대방이다. */
    Optional<ChatRoomMember> findByChatRoomIdAndMemberIdNot(Long chatRoomId, Long memberId);
}
