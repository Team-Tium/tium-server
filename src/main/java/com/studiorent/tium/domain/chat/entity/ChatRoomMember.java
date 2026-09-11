package com.studiorent.tium.domain.chat.entity;

import com.studiorent.tium.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 채팅방 참여자. 방 하나당 정확히 2행이다.
 *
 * <p>{@code leftAt}과 {@code lastReadMessageId}가 개인별 값이라 방 테이블에 넣지 않고 분리했다.
 * 소켓 SUBSCRIBE 권한 검증도 이 테이블을 본다 — 해당 방의 행이 있고 {@code leftAt}이 null이어야 구독을 허용한다.
 */
@Entity
@Table(
        name = "chat_room_member",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chat_room_member",
                columnNames = {"chat_room_id", "member_id"}
        ),
        indexes = @Index(
                name = "idx_chat_room_member_member",
                columnList = "member_id, left_at"
        )
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatRoomMember extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /** 이 ID 이하의 상대 메시지를 모두 읽은 것으로 본다. unreadCount 계산 기준이다. */
    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    @Column(name = "last_read_at")
    private LocalDateTime lastReadAt;

    /**
     * 나간 시각. null이면 참여 중이다.
     *
     * <p>나가기는 되돌릴 수 없고 재입장도 없다. 그래서 이 값은 감사 기록용이며
     * 메시지 조회 범위를 자르는 데 쓰지 않는다. 다시 대화하려면 새 방을 만든다.
     */
    @Column(name = "left_at")
    private LocalDateTime leftAt;

    public static ChatRoomMember join(ChatRoom chatRoom, Long memberId) {
        return ChatRoomMember.builder()
                .chatRoom(chatRoom)
                .memberId(memberId)
                .build();
    }

    /** 나가기. 이미 나간 참여자면 최초 시각을 유지한다. */
    public void leave() {
        if (this.leftAt == null) {
            this.leftAt = LocalDateTime.now();
        }
    }

    public boolean hasLeft() {
        return this.leftAt != null;
    }

    /**
     * 읽음 포인터를 앞으로만 옮긴다. 저장된 값보다 작거나 같은 ID는 무시한다.
     *
     * @return 실제로 갱신됐으면 true. 소켓 MESSAGE_READ를 보낼지 판단하는 데 쓴다.
     */
    public boolean updateLastReadMessage(Long messageId) {
        if (this.lastReadMessageId != null && this.lastReadMessageId >= messageId) {
            return false;
        }
        this.lastReadMessageId = messageId;
        this.lastReadAt = LocalDateTime.now();
        return true;
    }
}
