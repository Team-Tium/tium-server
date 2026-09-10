package com.studiorent.tium.domain.chat.entity;

import com.studiorent.tium.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 1:1 채팅방. 참여자는 항상 2명이고 방 자체를 삭제하지 않는다.
 *
 * <p>방 이름·이미지 컬럼은 두지 않는다. 목록 화면의 상대방 정보는
 * {@link ChatRoomMember} → member 조인으로 만든다.
 */
@Entity
@Table(
        name = "chat_room",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chat_room_active_pair",
                columnNames = "active_pair_key"
        ),
        indexes = @Index(
                name = "idx_chat_room_last_message",
                columnList = "last_message_id DESC"
        )
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatRoom extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 활성 방 중복 방지 키. {@code "{작은 memberId}_{큰 memberId}"} 형식이다.
     *
     * <p>누군가 나가면 null로 바꾼다. MySQL UNIQUE는 null을 중복으로 보지 않으므로
     * 닫힌 방은 같은 쌍으로 몇 개든 쌓이고 살아 있는 방은 쌍당 정확히 1개가 된다.
     * 애플리케이션 조회만으로는 동시 생성 시 방이 2개 생길 수 있어 DB 제약으로 막는다.
     */
    @Column(name = "active_pair_key", length = 41)
    private String activePairKey;

    /**
     * 목록 정렬·커서 기준. 메시지 ID가 전역 순증가라 시각 내림차순과 순서가 같고
     * 커서를 Long 하나로 유지할 수 있다. FK 매핑 없이 컬럼 값만 둔다.
     */
    @Column(name = "last_message_id")
    private Long lastMessageId;

    /** 마지막 메시지 시각(표시용). */
    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    /** 두 회원 ID로 활성 쌍 키를 만든다. 순서에 무관하게 같은 값이 나온다. */
    public static String pairKeyOf(Long memberId, Long opponentId) {
        long small = Math.min(memberId, opponentId);
        long large = Math.max(memberId, opponentId);
        return small + "_" + large;
    }

    public static ChatRoom create(Long memberId, Long opponentId) {
        return ChatRoom.builder()
                .activePairKey(pairKeyOf(memberId, opponentId))
                .build();
    }

    /** 메시지를 저장할 때 함께 갱신한다. 목록 조회를 위한 반정규화다. */
    public void updateLastMessage(Long messageId, LocalDateTime sentAt) {
        this.lastMessageId = messageId;
        this.lastMessageAt = sentAt;
    }

    /**
     * 한쪽이 나갈 때 활성 쌍 키를 비운다. 같은 상대와 새 방을 만들 수 있게 하는 처리다.
     * {@link ChatRoomMember#leave()}와 같은 트랜잭션에서 호출한다.
     */
    public void deactivatePair() {
        this.activePairKey = null;
    }

    /** 양쪽 모두 나가지 않은 방인지. */
    public boolean isActive() {
        return this.activePairKey != null;
    }

    /** 메시지가 하나라도 있는지. 없는 방은 채팅방 목록에서 제외한다. */
    public boolean hasMessage() {
        return this.lastMessageId != null;
    }
}
