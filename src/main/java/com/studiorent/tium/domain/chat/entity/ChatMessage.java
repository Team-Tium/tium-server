package com.studiorent.tium.domain.chat.entity;

import com.studiorent.tium.domain.chat.entity.enums.MessageType;
import com.studiorent.tium.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 채팅 메시지.
 *
 * <p>인덱스 하나로 두 조회를 모두 커버한다 — 과거 방향 커서({@code id < cursor} 내림차순)와
 * 재연결 복구({@code id > after} 오름차순).
 */
@Entity
@Table(
        name = "chat_message",
        indexes = @Index(
                name = "idx_chat_message_room",
                columnList = "chat_room_id, id DESC"
        )
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatMessage extends BaseEntity {

    /** 정렬·커서·재연결 복구(after)의 기준. 전역 순증가라 방별로는 값이 건너뛴다. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    /** SYSTEM 타입은 발신자가 없어 null이다. FK 매핑 없이 컬럼 값만 둔다. */
    @Column(name = "sender_id")
    private Long senderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    private MessageType messageType;

    /** IMAGE면 업로드된 이미지 URL. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * 자리만 잡아둔 컬럼이다. 메시지 삭제 API는 명세에 없다 —
     * 메시지 내역은 신고·분쟁 대응 목적으로 보존하기로 확정돼 물리 삭제는 어차피 하지 않는다.
     */
    @Builder.Default
    @Column(name = "delete_yn", nullable = false, length = 1)
    private String deleteYn = "N";

    public static ChatMessage ofText(ChatRoom chatRoom, Long senderId, String content) {
        return ChatMessage.builder()
                .chatRoom(chatRoom)
                .senderId(senderId)
                .messageType(MessageType.TEXT)
                .content(content)
                .build();
    }

    /** sentAt은 별도 컬럼을 두지 않고 createdAt을 그대로 쓴다. 두 값이 갈릴 여지를 없애기 위해서다. */
    public LocalDateTime getSentAt() {
        return getCreatedAt();
    }

    public boolean isSentBy(Long memberId) {
        return memberId.equals(this.senderId);
    }

    public void delete() {
        this.deleteYn = "Y";
    }

    public boolean isDeleted() {
        return "Y".equals(this.deleteYn);
    }
}
