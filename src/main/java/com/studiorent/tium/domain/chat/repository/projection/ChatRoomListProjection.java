package com.studiorent.tium.domain.chat.repository.projection;

import java.time.LocalDateTime;

/**
 * {@code GET /chats} 목록 조회 네이티브 쿼리가 돌려주는 방 한 줄.
 *
 * <p>게터 이름이 SQL의 별칭과 짝을 이룬다. 별칭을 고치면 여기도 같이 고쳐야 하며,
 * 어긋나면 해당 값이 null로 들어온다.
 *
 * <p>{@code getLastMessageType()}이 String인 것은 네이티브 쿼리라 DB에서 VARCHAR로 나오기 때문이다.
 * enum 변환은 Converter에서 한다.
 */
public interface ChatRoomListProjection {

    Long getRoomId();

    Long getOpponentId();

    String getOpponentNickname();

    String getOpponentProfileImageUrl();

    /** 나간 시각 원본. MySQL의 불리언 표현이 Integer로 넘어와 매핑이 깨지므로 판단은 자바에서 한다. */
    LocalDateTime getOpponentLeftAt();

    Long getLastMessageId();

    String getLastMessageContent();

    String getLastMessageType();

    LocalDateTime getLastMessageSentAt();

    Long getUnreadCount();
}
